import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { ExposureService } from '../src/exposure';
import { AuthorizationService } from '../src/authorization';
import { CatalogService } from '../src/catalog';
import { ApiError } from '../src/lib/errors';
import type { ExposureRule, PublicObjectMapping } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

const STATE_ID = 'zigbee.0.living_room.temperature';
const CTX = { userId: 'u1', deviceId: 'd1', roleId: 'viewer' };

async function setup() {
    const adapter = createFakeAdapter({
        getForeignObjectsAsync: async () => ({
            [STATE_ID]: {
                type: 'state',
                common: { name: 'Wohnzimmer Temperatur', role: 'value.temperature', type: 'number', unit: '°C' },
                native: {},
            },
            'system.adapter.admin.0.alive': {
                type: 'state',
                common: { name: 'alive', role: 'indicator', type: 'boolean' },
                native: {},
            },
        }),
    });

    const exposureStore = new CollectionStore<ExposureRule>(adapter, 'exposureRules');
    const mappingsStore = new CollectionStore<PublicObjectMapping>(adapter, 'objectMappings');
    await Promise.all([exposureStore.init(), mappingsStore.init()]);

    const exposure = new ExposureService(adapter, exposureStore);
    const authorization = new AuthorizationService(exposure);
    const catalog = new CatalogService(exposure, authorization, mappingsStore);

    return { exposureStore, exposure, catalog };
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

describe('CatalogService', () => {
    it('resolveAuthorized rejects an unknown public object UUID with OBJECT_NOT_FOUND', async () => {
        const { catalog } = await setup();
        assert.throws(
            () => catalog.resolveAuthorized('not-a-real-public-uuid', CTX, 'read'),
            (err: unknown) => err instanceof ApiError && err.code === 'OBJECT_NOT_FOUND',
        );
    });

    it('resolveAuthorized rejects a valid but unauthorized object with READ_FORBIDDEN / WRITE_FORBIDDEN', async () => {
        const { catalog } = await setup();
        const mapping = await catalog.getOrCreateMapping(STATE_ID);

        assert.throws(
            () => catalog.resolveAuthorized(mapping.id, CTX, 'read'),
            (err: unknown) => err instanceof ApiError && err.code === 'READ_FORBIDDEN',
        );
    });

    it('revoking (deleting) an exposure rule immediately removes access - no caching/staleness', async () => {
        const { exposureStore, catalog } = await setup();
        const mapping = await catalog.getOrCreateMapping(STATE_ID);
        const rule = baseRule({ roleId: 'viewer', read: true });
        await exposureStore.put(rule);

        // access granted while the rule exists
        const { stateId } = catalog.resolveAuthorized(mapping.id, CTX, 'read');
        assert.equal(stateId, STATE_ID);

        // admin revokes the exposure rule
        await exposureStore.delete(rule.id);

        assert.throws(
            () => catalog.resolveAuthorized(mapping.id, CTX, 'read'),
            (err: unknown) => err instanceof ApiError && err.code === 'READ_FORBIDDEN',
        );
    });

    it('effectiveCatalog only includes objects the caller can read and never system.*', async () => {
        const { exposureStore, catalog } = await setup();
        await exposureStore.put(baseRule({ roleId: 'viewer', read: true }));

        const { objects } = await catalog.effectiveCatalog(CTX);
        assert.equal(objects.length, 1);
        assert.equal(objects[0].name, 'Wohnzimmer Temperatur');
        assert.ok(!objects.some((o) => o.name === 'alive'));
    });

    it('effectiveCatalog is empty when no exposure rule grants read access', async () => {
        const { catalog } = await setup();
        const { objects } = await catalog.effectiveCatalog(CTX);
        assert.equal(objects.length, 0);
    });

    it('the same internal state id always maps to the same public UUID across calls', async () => {
        const { catalog } = await setup();
        const first = await catalog.getOrCreateMapping(STATE_ID);
        const second = await catalog.getOrCreateMapping(STATE_ID);
        assert.equal(first.id, second.id);
    });
});
