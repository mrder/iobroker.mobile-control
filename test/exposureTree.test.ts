import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { ExposureService } from '../src/exposure';
import type { ExposureRule } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

// Regression test for the folder-tree admin UI feature: browseObjectTree() must return both
// leaf states AND their container objects (channel/device/folder), each correctly tagged with
// `kind`, so the admin tab can render a real, named folder hierarchy instead of a flat dump of
// only the leaf datapoints (which had no way to show "what folder does this belong to").
describe('ExposureService.browseObjectTree', () => {
    it('includes both states and their container objects, tagged with the right kind', async () => {
        const adapter = createFakeAdapter({
            getForeignObjectsAsync: async () => ({
                growmanager: {
                    type: 'adapter',
                    common: { name: 'growmanager' },
                    native: {},
                },
                'growmanager.0': {
                    type: 'instance',
                    common: { name: 'growmanager.0' },
                    native: {},
                },
                'growmanager.0.database': {
                    type: 'folder',
                    common: { name: 'Datenbank' },
                    native: {},
                },
                'growmanager.0.database.strains': {
                    type: 'state',
                    common: { name: 'Sortenwiki', role: 'json', type: 'string' },
                    native: {},
                },
                'system.adapter.admin.0.alive': {
                    type: 'state',
                    common: { name: 'alive', role: 'indicator', type: 'boolean' },
                    native: {},
                },
            }),
        });
        const store = new CollectionStore<ExposureRule>(adapter, 'exposureRules');
        await store.init();
        const exposure = new ExposureService(adapter, store);

        const entries = await exposure.browseObjectTree();

        // system.* stays blocked regardless of type
        assert.ok(!entries.some((e) => e.id.startsWith('system.')));

        const leaf = entries.find((e) => e.id === 'growmanager.0.database.strains');
        assert.equal(leaf?.kind, 'state');
        assert.equal(leaf?.role, 'json');

        const folder = entries.find((e) => e.id === 'growmanager.0.database');
        assert.equal(folder?.kind, 'container');
        assert.equal(folder?.name, 'Datenbank');
        assert.equal(folder?.unit, null);

        const adapterNode = entries.find((e) => e.id === 'growmanager');
        assert.equal(adapterNode?.kind, 'container');
    });

    it('a rule granted on a container scope matches every state underneath it', async () => {
        const adapter = createFakeAdapter();
        const store = new CollectionStore<ExposureRule>(adapter, 'exposureRules');
        await store.init();
        const exposure = new ExposureService(adapter, store);

        await exposure.create({
            scope: 'channel',
            target: 'growmanager.0.database',
            roleId: 'viewer',
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
        });

        const matches = exposure.matchingRules('growmanager.0.database.strains');
        assert.equal(matches.length, 1);
        assert.equal(exposure.matchingRules('growmanager.0.other.thing').length, 0);
    });

    // Regression test: a real-world adapter (zigbee's own objects have been observed to trip
    // this) can hand back an object whose common shape breaks an assumption somewhere in the
    // per-entry parsing below - without per-entry isolation, one such object used to throw and
    // wipe out the ENTIRE tree (and by extension every user's whole catalog, since
    // CatalogService.effectiveCatalog builds from this same list) instead of just that one entry.
    it('a single malformed object is skipped (and logged) without losing the rest of the tree', async () => {
        const warnCalls: string[] = [];
        const poisoned = new Proxy(
            {},
            {
                get(): never {
                    throw new Error('boom');
                },
            },
        );
        const adapter = createFakeAdapter({
            log: {
                info: () => undefined,
                warn: (msg: string) => {
                    warnCalls.push(msg);
                },
                error: () => undefined,
                debug: () => undefined,
                silly: () => undefined,
            },
            getForeignObjectsAsync: async () => ({
                'zigbee.0.broken': poisoned,
                'zigbee.0.good.state': {
                    type: 'state',
                    common: { name: 'Steckdose', role: 'switch', type: 'boolean' },
                    native: {},
                },
            }),
        });
        const store = new CollectionStore<ExposureRule>(adapter, 'exposureRules');
        await store.init();
        const exposure = new ExposureService(adapter, store);

        const entries = await exposure.browseObjectTree();

        assert.deepEqual(entries.map((e) => e.id), ['zigbee.0.good.state']);
        assert.equal(warnCalls.length, 1);
        assert.match(warnCalls[0], /zigbee\.0\.broken/);
    });
});
