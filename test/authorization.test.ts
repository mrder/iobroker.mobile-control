import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { ExposureService } from '../src/exposure';
import { AuthorizationService } from '../src/authorization';
import type { ExposureRule } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

const STATE_ID = 'zigbee.0.living_room.temperature';

async function setup(): Promise<{ exposure: ExposureService; authorization: AuthorizationService; store: CollectionStore<ExposureRule> }> {
    const adapter = createFakeAdapter();
    const store = new CollectionStore<ExposureRule>(adapter, 'exposureRules');
    await store.init();
    const exposure = new ExposureService(adapter, store);
    const authorization = new AuthorizationService(exposure);
    return { exposure, authorization, store };
}

function baseRule(overrides: Partial<ExposureRule>): ExposureRule {
    return {
        id: overrides.id ?? Math.random().toString(36),
        scope: 'state',
        target: STATE_ID,
        roleId: null,
        userId: null,
        deviceId: null,
        deny: false,
        read: false,
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

describe('AuthorizationService.resolve priority', () => {
    it('denies by default when no rule matches', async () => {
        const { authorization } = await setup();
        const result = authorization.resolve(STATE_ID, { userId: 'u1', deviceId: 'd1', roleId: 'viewer' });
        assert.equal(result.read, false);
        assert.equal(result.write, false);
    });

    it('role grant is used when nothing more specific exists', async () => {
        const { store, authorization } = await setup();
        await store.put(baseRule({ id: 'role-rule', roleId: 'viewer', read: true }));
        const result = authorization.resolve(STATE_ID, { userId: 'u1', deviceId: 'd1', roleId: 'viewer' });
        assert.equal(result.read, true);
        assert.equal(result.write, false);
    });

    it('user grant overrides role grant', async () => {
        const { store, authorization } = await setup();
        await store.put(baseRule({ id: 'role-rule', roleId: 'viewer', read: true, write: false }));
        await store.put(baseRule({ id: 'user-rule', userId: 'u1', read: true, write: true }));
        const result = authorization.resolve(STATE_ID, { userId: 'u1', deviceId: 'd1', roleId: 'viewer' });
        assert.equal(result.write, true);
    });

    it('device grant overrides user and role grants', async () => {
        const { store, authorization } = await setup();
        await store.put(baseRule({ id: 'role-rule', roleId: 'viewer', read: true }));
        await store.put(baseRule({ id: 'user-rule', userId: 'u1', read: true, write: false }));
        await store.put(baseRule({ id: 'device-rule', deviceId: 'd1', read: true, write: true, localOnly: true }));
        const result = authorization.resolve(STATE_ID, { userId: 'u1', deviceId: 'd1', roleId: 'viewer' });
        assert.equal(result.write, true);
        assert.equal(result.localOnly, true);
    });

    it('an explicit deny at any level wins over every grant, regardless of specificity', async () => {
        const { store, authorization } = await setup();
        await store.put(baseRule({ id: 'device-rule', deviceId: 'd1', read: true, write: true }));
        await store.put(baseRule({ id: 'role-deny', roleId: 'viewer', deny: true }));
        const result = authorization.resolve(STATE_ID, { userId: 'u1', deviceId: 'd1', roleId: 'viewer' });
        assert.equal(result.read, false);
        assert.equal(result.write, false);
    });

    it('system.* states can never be exposed even with a matching rule', async () => {
        const { store, authorization } = await setup();
        await store.put(baseRule({ id: 'bad-rule', scope: 'pattern', target: 'system.*', roleId: 'administrator', read: true }));
        const result = authorization.resolve('system.adapter.admin.0.alive', { userId: 'u1', deviceId: 'd1', roleId: 'administrator' });
        assert.equal(result.read, false);
    });

    it('rules for other users/devices/roles do not leak into an unrelated context', async () => {
        const { store, authorization } = await setup();
        await store.put(baseRule({ id: 'other-user', userId: 'someone-else', read: true, write: true }));
        const result = authorization.resolve(STATE_ID, { userId: 'u1', deviceId: 'd1', roleId: 'viewer' });
        assert.equal(result.read, false);
    });
});
