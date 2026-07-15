import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { ExposureService } from '../src/exposure';
import { ExposureProfilesService } from '../src/exposure/profiles';
import type { ExposureProfile, ExposureRule } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

async function setup() {
    const adapter = createFakeAdapter();
    const exposureStore = new CollectionStore<ExposureRule>(adapter, 'exposureRules');
    const profilesStore = new CollectionStore<ExposureProfile>(adapter, 'exposureProfiles');
    await Promise.all([exposureStore.init(), profilesStore.init()]);

    const exposure = new ExposureService(adapter, exposureStore);
    const profiles = new ExposureProfilesService(profilesStore, exposure);
    return { exposure, profiles };
}

describe('ExposureProfilesService', () => {
    it('snapshots an existing role\'s rules into a reusable, owner-less profile', async () => {
        const { exposure, profiles } = await setup();
        await exposure.create({
            scope: 'state',
            target: 'zigbee.0.temp',
            roleId: 'viewer',
            userId: null,
            deviceId: null,
            deny: false,
            read: true,
            write: false,
            history: true,
            min: null,
            max: null,
            step: null,
            allowedValues: null,
            localOnly: false,
            confirmPolicy: 'NONE',
            displayName: null,
            suggestedWidgets: null,
        });

        const profile = await profiles.createFromOwner('Basis-Sensoren', 'Nur Lesen', 'role', 'viewer');
        assert.equal(profile.rules.length, 1);
        assert.equal(profile.rules[0].target, 'zigbee.0.temp');
        // templates must not carry an owner - that is filled in on apply
        assert.ok(!('roleId' in profile.rules[0]));
    });

    it('applying a profile to a different owner materializes independent rules', async () => {
        const { exposure, profiles } = await setup();
        await exposure.create({
            scope: 'state',
            target: 'zigbee.0.temp',
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
        const profile = await profiles.createFromOwner('Basis-Sensoren', null, 'role', 'viewer');

        const applied = await profiles.applyTo(profile.id, 'user', 'u42');
        assert.equal(applied.length, 1);
        assert.equal(applied[0].userId, 'u42');
        assert.equal(applied[0].roleId, null);
        assert.equal(applied[0].target, 'zigbee.0.temp');

        // original role rule is untouched, plus the new user-owned rule now also exists
        assert.equal(exposure.list().length, 2);
    });

    it('deleting a profile does not affect rules that were already applied from it', async () => {
        const { exposure, profiles } = await setup();
        await exposure.create({
            scope: 'state',
            target: 'zigbee.0.temp',
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
        const profile = await profiles.createFromOwner('Basis-Sensoren', null, 'role', 'viewer');
        await profiles.applyTo(profile.id, 'user', 'u42');

        await profiles.delete(profile.id);

        assert.equal(profiles.get(profile.id), undefined);
        assert.equal(exposure.list().length, 2);
    });
});
