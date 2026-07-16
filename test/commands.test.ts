import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { ExposureService } from '../src/exposure';
import { AuthorizationService } from '../src/authorization';
import { CatalogService } from '../src/catalog';
import { AuditService } from '../src/audit';
import { RateLimiter } from '../src/security/rateLimiter';
import { ReplayGuard } from '../src/security/replayGuard';
import { CommandsService, type CommandExecutionContext } from '../src/commands';
import { ApiError } from '../src/lib/errors';
import type { AuditEvent, CommandRecord, ExposureRule, PublicObjectMapping } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

const SWITCH_STATE_ID = 'zigbee.0.plug.state';
const DIMMER_STATE_ID = 'zigbee.0.dimmer.level';
const MIXED_STATE_ID = 'zigbee.0.untyped.state';

function baseRule(overrides: Partial<ExposureRule>): ExposureRule {
    return {
        id: overrides.id ?? Math.random().toString(36),
        scope: 'state',
        target: SWITCH_STATE_ID,
        roleId: null,
        userId: null,
        deviceId: null,
        deny: false,
        read: true,
        write: false,
        history: false,
        min: null,
        max: null,
        step: null,
        allowedValues: null,
        localOnly: false,
        confirmPolicy: 'NONE',
        displayName: null,
        suggestedWidgets: null,
        createdAt: Date.now(),
        ...overrides,
    };
}

async function setup(rateLimitPerMinute = 60) {
    const setForeignStateCalls: Array<{ id: string; val: unknown }> = [];
    const foreignObjects: Record<string, { common: { type: string } }> = {
        [SWITCH_STATE_ID]: { common: { type: 'boolean' } },
        [DIMMER_STATE_ID]: { common: { type: 'number' } },
        [MIXED_STATE_ID]: { common: { type: 'mixed' } },
    };

    const adapter = createFakeAdapter({
        getForeignObjectAsync: async (id: string) => (foreignObjects[id] ? { common: foreignObjects[id].common } : null),
        setForeignStateAsync: async (id: string, state: { val: unknown; ack: boolean }) => {
            setForeignStateCalls.push({ id, val: state.val });
            return { id };
        },
    });

    const exposureStore = new CollectionStore<ExposureRule>(adapter, 'exposureRules');
    const mappingsStore = new CollectionStore<PublicObjectMapping>(adapter, 'objectMappings');
    const commandsStore = new CollectionStore<CommandRecord>(adapter, 'commands');
    const auditStore = new CollectionStore<AuditEvent>(adapter, 'auditEvents');
    await Promise.all([exposureStore.init(), mappingsStore.init(), commandsStore.init(), auditStore.init()]);

    const exposure = new ExposureService(adapter, exposureStore);
    const authorization = new AuthorizationService(exposure);
    const catalog = new CatalogService(exposure, authorization, mappingsStore);
    const audit = new AuditService(auditStore);
    const rateLimiter = new RateLimiter(rateLimitPerMinute);
    const replayGuard = new ReplayGuard();
    const commands = new CommandsService(adapter, commandsStore, catalog, audit, rateLimiter, replayGuard);

    return { exposureStore, catalog, commands, setForeignStateCalls };
}

const CTX: CommandExecutionContext = { userId: 'u1', deviceId: 'd1', roleId: 'viewer', ip: '192.168.1.5', isLocalNetwork: true };

describe('CommandsService actuator pipeline', () => {
    it('executes a valid boolean write when a write rule exists', async () => {
        const { exposureStore, catalog, commands, setForeignStateCalls } = await setup();
        await exposureStore.put(baseRule({ userId: 'u1', write: true }));
        const mapping = await catalog.getOrCreateMapping(SWITCH_STATE_ID);

        const record = await commands.execute(CTX, { commandId: 'c1', objectId: mapping.id, value: true, timestamp: new Date().toISOString(), nonce: 'n1' });

        assert.equal(record.status, 'executed');
        assert.deepEqual(setForeignStateCalls, [{ id: SWITCH_STATE_ID, val: true }]);
    });

    it('rejects a write when no exposure rule grants write access', async () => {
        const { catalog, commands } = await setup();
        const mapping = await catalog.getOrCreateMapping(SWITCH_STATE_ID);

        await assert.rejects(
            () => commands.execute(CTX, { commandId: 'c1', objectId: mapping.id, value: true, timestamp: new Date().toISOString(), nonce: 'n1' }),
            (err: unknown) => err instanceof ApiError && err.code === 'WRITE_FORBIDDEN',
        );
    });

    it('rejects a value whose type does not match the underlying state', async () => {
        const { exposureStore, catalog, commands } = await setup();
        await exposureStore.put(baseRule({ userId: 'u1', write: true }));
        const mapping = await catalog.getOrCreateMapping(SWITCH_STATE_ID);

        await assert.rejects(
            () => commands.execute(CTX, { commandId: 'c1', objectId: mapping.id, value: 'on', timestamp: new Date().toISOString(), nonce: 'n1' }),
            (err: unknown) => err instanceof ApiError && err.code === 'VALUE_INVALID',
        );
    });

    it('rejects a complex object value even against a state with no specific expected type', async () => {
        // Regression test: validateValue's boolean/number/string checks only fire when
        // expectedType matches one of those three - a state typed "mixed" (or untyped) used to
        // fall through all three branches unchecked, letting a client write an arbitrary nested
        // object straight into the ioBroker state.
        const { exposureStore, catalog, commands } = await setup();
        await exposureStore.put(baseRule({ target: MIXED_STATE_ID, userId: 'u1', write: true }));
        const mapping = await catalog.getOrCreateMapping(MIXED_STATE_ID);

        await assert.rejects(
            () =>
                commands.execute(CTX, {
                    commandId: 'c1',
                    objectId: mapping.id,
                    value: { evil: 'payload' },
                    timestamp: new Date().toISOString(),
                    nonce: 'n1',
                }),
            (err: unknown) => err instanceof ApiError && err.code === 'VALUE_INVALID',
        );
    });

    it('still allows scalar values (string) through a "mixed"-typed state', async () => {
        const { exposureStore, catalog, commands, setForeignStateCalls } = await setup();
        await exposureStore.put(baseRule({ target: MIXED_STATE_ID, userId: 'u1', write: true }));
        const mapping = await catalog.getOrCreateMapping(MIXED_STATE_ID);

        const record = await commands.execute(CTX, {
            commandId: 'c1',
            objectId: mapping.id,
            value: 'ok',
            timestamp: new Date().toISOString(),
            nonce: 'n1',
        });
        assert.equal(record.status, 'executed');
        assert.deepEqual(setForeignStateCalls, [{ id: MIXED_STATE_ID, val: 'ok' }]);
    });

    it('enforces min/max range for numeric actuators', async () => {
        const { exposureStore, catalog, commands } = await setup();
        await exposureStore.put(baseRule({ target: DIMMER_STATE_ID, userId: 'u1', write: true, min: 0, max: 100 }));
        const mapping = await catalog.getOrCreateMapping(DIMMER_STATE_ID);

        await assert.rejects(
            () => commands.execute(CTX, { commandId: 'c1', objectId: mapping.id, value: 150, timestamp: new Date().toISOString(), nonce: 'n1' }),
            (err: unknown) => err instanceof ApiError && err.code === 'VALUE_INVALID',
        );

        const record = await commands.execute(CTX, {
            commandId: 'c2',
            objectId: mapping.id,
            value: 80,
            timestamp: new Date().toISOString(),
            nonce: 'n2',
        });
        assert.equal(record.status, 'executed');
    });

    it('rate-limits commands per device', async () => {
        const { exposureStore, catalog, commands } = await setup(1);
        await exposureStore.put(baseRule({ userId: 'u1', write: true }));
        const mapping = await catalog.getOrCreateMapping(SWITCH_STATE_ID);

        await commands.execute(CTX, { commandId: 'c1', objectId: mapping.id, value: true, timestamp: new Date().toISOString(), nonce: 'n1' });
        await assert.rejects(
            () => commands.execute(CTX, { commandId: 'c2', objectId: mapping.id, value: false, timestamp: new Date().toISOString(), nonce: 'n2' }),
            (err: unknown) => err instanceof ApiError && err.code === 'RATE_LIMITED',
        );
    });

    it('rejects a replayed nonce even with a different commandId', async () => {
        const { exposureStore, catalog, commands } = await setup();
        await exposureStore.put(baseRule({ userId: 'u1', write: true }));
        const mapping = await catalog.getOrCreateMapping(SWITCH_STATE_ID);

        await commands.execute(CTX, { commandId: 'c1', objectId: mapping.id, value: true, timestamp: new Date().toISOString(), nonce: 'same-nonce' });
        await assert.rejects(
            () => commands.execute(CTX, { commandId: 'c2', objectId: mapping.id, value: false, timestamp: new Date().toISOString(), nonce: 'same-nonce' }),
            (err: unknown) => err instanceof ApiError && err.code === 'REPLAY_DETECTED',
        );
    });

    it('is idempotent for a repeated commandId - does not execute twice', async () => {
        const { exposureStore, catalog, commands, setForeignStateCalls } = await setup();
        await exposureStore.put(baseRule({ userId: 'u1', write: true }));
        const mapping = await catalog.getOrCreateMapping(SWITCH_STATE_ID);

        const request = { commandId: 'same-id', objectId: mapping.id, value: true, timestamp: new Date().toISOString(), nonce: 'n1' };
        const first = await commands.execute(CTX, request);
        const second = await commands.execute(CTX, { ...request, nonce: 'n1' });

        assert.equal(first.id, second.id);
        assert.equal(setForeignStateCalls.length, 1);
    });

    it('blocks a DIALOG-confirm-policy command until the client marks it confirmed', async () => {
        const { exposureStore, catalog, commands } = await setup();
        await exposureStore.put(baseRule({ userId: 'u1', write: true, confirmPolicy: 'DIALOG' }));
        const mapping = await catalog.getOrCreateMapping(SWITCH_STATE_ID);

        await assert.rejects(
            () => commands.execute(CTX, { commandId: 'c1', objectId: mapping.id, value: true, timestamp: new Date().toISOString(), nonce: 'n1' }),
            (err: unknown) => err instanceof ApiError && err.code === 'CONFIRMATION_REQUIRED',
        );

        const record = await commands.execute(CTX, {
            commandId: 'c2',
            objectId: mapping.id,
            value: true,
            timestamp: new Date().toISOString(),
            nonce: 'n2',
            confirmed: true,
        });
        assert.equal(record.status, 'executed');
    });

    it('enforces LOCAL_ONLY rules against the request origin', async () => {
        const { exposureStore, catalog, commands } = await setup();
        await exposureStore.put(baseRule({ userId: 'u1', write: true, localOnly: true }));
        const mapping = await catalog.getOrCreateMapping(SWITCH_STATE_ID);

        await assert.rejects(
            () =>
                commands.execute(
                    { ...CTX, isLocalNetwork: false },
                    { commandId: 'c1', objectId: mapping.id, value: true, timestamp: new Date().toISOString(), nonce: 'n1' },
                ),
            (err: unknown) => err instanceof ApiError && err.code === 'LOCAL_ONLY',
        );
    });
});
