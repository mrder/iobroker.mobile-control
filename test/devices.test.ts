import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { DevicesService } from '../src/devices';
import type { Device } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

async function setup() {
    const adapter = createFakeAdapter();
    const store = new CollectionStore<Device>(adapter, 'devices');
    await store.init();
    return { devices: new DevicesService(store) };
}

const NEW_DEVICE = {
    userId: 'user-1',
    roleId: 'viewer',
    name: 'Test-Tablet',
    platform: 'android',
    appVersion: '1.0.0',
    publicKey: 'pub-key',
    fingerprint: 'fp-1',
};

describe('DevicesService.hasRecentlyActiveDevice', () => {
    it('is false when there are no devices at all', async () => {
        const { devices } = await setup();
        assert.equal(devices.hasRecentlyActiveDevice(5 * 60_000), false);
    });

    it('is false for an approved device that has never made a request (lastSeenAt still null)', async () => {
        const { devices } = await setup();
        const device = await devices.register(NEW_DEVICE);
        await devices.approve(device.id);
        assert.equal(devices.hasRecentlyActiveDevice(5 * 60_000), false);
    });

    it('is true right after touch() on an approved device', async () => {
        const { devices } = await setup();
        const device = await devices.register(NEW_DEVICE);
        await devices.approve(device.id);
        await devices.touch(device.id, '192.168.1.50');
        assert.equal(devices.hasRecentlyActiveDevice(5 * 60_000), true);
    });

    it('is false once the activity falls outside the given window', async () => {
        const { devices } = await setup();
        const device = await devices.register(NEW_DEVICE);
        await devices.approve(device.id);
        await devices.touch(device.id, '192.168.1.50');
        // A 0ms window means "must have been active this exact instant" - any elapsed time excludes it.
        assert.equal(devices.hasRecentlyActiveDevice(0), false);
    });

    it('is false for a device that was active but is no longer approved (e.g. revoked)', async () => {
        const { devices } = await setup();
        const device = await devices.register(NEW_DEVICE);
        await devices.approve(device.id);
        await devices.touch(device.id, '192.168.1.50');
        await devices.revoke(device.id);
        assert.equal(devices.hasRecentlyActiveDevice(5 * 60_000), false);
    });

    it('is false for a pending device that was somehow touched but never approved', async () => {
        const { devices } = await setup();
        const device = await devices.register(NEW_DEVICE);
        await devices.touch(device.id, '192.168.1.50');
        assert.equal(devices.hasRecentlyActiveDevice(5 * 60_000), false);
    });
});
