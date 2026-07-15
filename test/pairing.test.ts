import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { RolesService } from '../src/roles';
import { UsersService } from '../src/users';
import { DevicesService } from '../src/devices';
import { PairingService, type PairingConfig } from '../src/pairing';
import { ApiError } from '../src/lib/errors';
import type { Device, PairingClaim, PairingInvite, Role, User } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

async function setup(configOverrides: Partial<PairingConfig> = {}) {
    const adapter = createFakeAdapter();
    const rolesStore = new CollectionStore<Role>(adapter, 'roles');
    const usersStore = new CollectionStore<User>(adapter, 'users');
    const devicesStore = new CollectionStore<Device>(adapter, 'devices');
    const invitesStore = new CollectionStore<PairingInvite>(adapter, 'pairingInvites');
    const claimsStore = new CollectionStore<PairingClaim>(adapter, 'pairingClaims');
    await Promise.all([rolesStore.init(), usersStore.init(), devicesStore.init(), invitesStore.init(), claimsStore.init()]);

    const roles = new RolesService(rolesStore);
    await roles.ensureBuiltInRoles();
    const users = new UsersService(usersStore, roles);
    const devices = new DevicesService(devicesStore);
    const pairing = new PairingService(invitesStore, claimsStore, users, roles, devices, {
        publicUrl: 'https://example.test/mobile-control',
        instanceId: 'mobile-control.0',
        serverFingerprint: 'sha256/test-fingerprint',
        inviteTtlMinutes: 10,
        requireAdminApproval: true,
        ...configOverrides,
    });

    const user = await users.create('Test User', 'viewer');
    return { pairing, devices, user };
}

const CLAIM_PARAMS = { deviceName: 'Test Phone', platform: 'android', appVersion: '1.0', publicKey: 'fake-spki-key' };

describe('PairingService end-to-end flow', () => {
    it('creates an invite whose QR payload matches the invite and embeds server identity', async () => {
        const { pairing, user } = await setup();
        const { invite, qrPayload } = await pairing.createInvite(user.id, 'viewer');

        assert.equal(qrPayload.pairingId, invite.id);
        assert.equal(qrPayload.version, 1);
        assert.equal(qrPayload.serverFingerprint, 'sha256/test-fingerprint');
        assert.equal(qrPayload.instanceId, 'mobile-control.0');
    });

    it('claiming with the correct secret puts the device into waiting_for_approval when admin approval is required', async () => {
        const { pairing, user } = await setup({ requireAdminApproval: true });
        const { invite, qrPayload } = await pairing.createInvite(user.id, 'viewer');

        const result = await pairing.claim({ pairingId: invite.id, pairingSecret: qrPayload.pairingSecret, ...CLAIM_PARAMS });
        assert.equal(result.status, 'waiting_for_approval');

        const status = await pairing.status(result.claimId);
        assert.equal(status.status, 'waiting_for_approval');
    });

    it('claiming auto-approves the device when admin approval is not required', async () => {
        const { pairing, devices, user } = await setup({ requireAdminApproval: false });
        const { invite, qrPayload } = await pairing.createInvite(user.id, 'viewer');

        const result = await pairing.claim({ pairingId: invite.id, pairingSecret: qrPayload.pairingSecret, ...CLAIM_PARAMS });
        assert.equal(result.status, 'approved');

        const claim = await pairing.status(result.claimId);
        const device = devices.require(claim.deviceId!);
        assert.equal(device.status, 'approved');
    });

    it('admin approveClaim() marks both the claim and its device as approved', async () => {
        const { pairing, devices, user } = await setup({ requireAdminApproval: true });
        const { invite, qrPayload } = await pairing.createInvite(user.id, 'viewer');
        const { claimId } = await pairing.claim({ pairingId: invite.id, pairingSecret: qrPayload.pairingSecret, ...CLAIM_PARAMS });

        const approved = await pairing.approveClaim(claimId);
        assert.equal(approved.status, 'approved');
        assert.equal(devices.require(approved.deviceId!).status, 'approved');
    });

    it('admin rejectClaim() marks both the claim and its device as rejected, blocking future login', async () => {
        const { pairing, devices, user } = await setup({ requireAdminApproval: true });
        const { invite, qrPayload } = await pairing.createInvite(user.id, 'viewer');
        const { claimId } = await pairing.claim({ pairingId: invite.id, pairingSecret: qrPayload.pairingSecret, ...CLAIM_PARAMS });

        const rejected = await pairing.rejectClaim(claimId);
        assert.equal(rejected.status, 'rejected');
        const device = devices.require(rejected.deviceId!);
        assert.equal(device.status, 'rejected');
        assert.equal(devices.isUsable(device), false);
    });

    it('rejects claiming with a wrong secret', async () => {
        const { pairing, user } = await setup();
        const { invite } = await pairing.createInvite(user.id, 'viewer');

        await assert.rejects(
            () => pairing.claim({ pairingId: invite.id, pairingSecret: 'totally-wrong-secret', ...CLAIM_PARAMS }),
            (err: unknown) => err instanceof ApiError && err.code === 'PAIRING_INVALID',
        );
    });

    it('rejects claiming an unknown pairing id', async () => {
        const { pairing } = await setup();
        await assert.rejects(
            () => pairing.claim({ pairingId: 'does-not-exist', pairingSecret: 'x', ...CLAIM_PARAMS }),
            (err: unknown) => err instanceof ApiError && err.code === 'PAIRING_INVALID',
        );
    });

    it('rejects reusing an already-claimed invite (single-use enforcement)', async () => {
        const { pairing, user } = await setup();
        const { invite, qrPayload } = await pairing.createInvite(user.id, 'viewer');
        await pairing.claim({ pairingId: invite.id, pairingSecret: qrPayload.pairingSecret, ...CLAIM_PARAMS, deviceName: 'First' });

        await assert.rejects(
            () => pairing.claim({ pairingId: invite.id, pairingSecret: qrPayload.pairingSecret, ...CLAIM_PARAMS, deviceName: 'Second' }),
            (err: unknown) => err instanceof ApiError && err.code === 'PAIRING_INVALID',
        );
    });

    it('rejects a claim after the invite has already expired', async () => {
        // A negative TTL puts expiresAt in the past the instant the invite is created.
        const { pairing, user } = await setup({ inviteTtlMinutes: -1 });
        const { invite, qrPayload } = await pairing.createInvite(user.id, 'viewer');

        await assert.rejects(
            () => pairing.claim({ pairingId: invite.id, pairingSecret: qrPayload.pairingSecret, ...CLAIM_PARAMS }),
            (err: unknown) => err instanceof ApiError && err.code === 'PAIRING_EXPIRED',
        );
    });

    it('a freshly created claim is not expired', async () => {
        const { pairing, user } = await setup({ requireAdminApproval: true, inviteTtlMinutes: 10 });
        const { invite, qrPayload } = await pairing.createInvite(user.id, 'viewer');
        const { claimId } = await pairing.claim({ pairingId: invite.id, pairingSecret: qrPayload.pairingSecret, ...CLAIM_PARAMS });

        const status = await pairing.status(claimId);
        assert.equal(status.status, 'waiting_for_approval');
    });
});
