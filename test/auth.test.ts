import { strict as assert } from 'node:assert';
import { generateKeyPairSync, sign as cryptoSign } from 'node:crypto';
import { CollectionStore } from '../src/lib/store';
import { DevicesService } from '../src/devices';
import { AuthService } from '../src/auth';
import { ApiError } from '../src/lib/errors';
import type { Device } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

function generateDeviceKeyPair() {
    const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
    const publicKeyBase64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
    return { publicKeyBase64, privateKey };
}

async function setup() {
    const adapter = createFakeAdapter();
    const store = new CollectionStore<Device>(adapter, 'devices');
    await store.init();
    const devices = new DevicesService(store);
    const auth = new AuthService(devices, 'test-jwt-secret', 10);
    return { devices, auth };
}

describe('AuthService challenge-response (EC P-256)', () => {
    it('accepts a correctly signed challenge from an approved device', async () => {
        const { devices, auth } = await setup();
        const { publicKeyBase64, privateKey } = generateDeviceKeyPair();
        const device = await devices.register({
            userId: 'u1',
            roleId: 'viewer',
            name: 'Test Phone',
            platform: 'android',
            appVersion: '1.0',
            publicKey: publicKeyBase64,
            fingerprint: 'fp',
        });
        await devices.approve(device.id);

        const { challengeId, nonce } = auth.createChallenge(device.id);
        const signature = cryptoSign('sha256', Buffer.from(nonce, 'base64'), privateKey);

        const verified = auth.verifyLogin(device.id, challengeId, signature.toString('base64'));
        assert.equal(verified.id, device.id);
    });

    it('rejects a signature made with the wrong private key', async () => {
        const { devices, auth } = await setup();
        const { publicKeyBase64 } = generateDeviceKeyPair();
        const { privateKey: wrongKey } = generateDeviceKeyPair();
        const device = await devices.register({
            userId: 'u1',
            roleId: 'viewer',
            name: 'Test Phone',
            platform: 'android',
            appVersion: '1.0',
            publicKey: publicKeyBase64,
            fingerprint: 'fp',
        });
        await devices.approve(device.id);

        const { challengeId, nonce } = auth.createChallenge(device.id);
        const badSignature = cryptoSign('sha256', Buffer.from(nonce, 'base64'), wrongKey);

        assert.throws(() => auth.verifyLogin(device.id, challengeId, badSignature.toString('base64')), (err: unknown) => {
            return err instanceof ApiError && err.code === 'SIGNATURE_INVALID';
        });
    });

    it('rejects reuse of an already-consumed challenge (replay protection)', async () => {
        const { devices, auth } = await setup();
        const { publicKeyBase64, privateKey } = generateDeviceKeyPair();
        const device = await devices.register({
            userId: 'u1',
            roleId: 'viewer',
            name: 'Test Phone',
            platform: 'android',
            appVersion: '1.0',
            publicKey: publicKeyBase64,
            fingerprint: 'fp',
        });
        await devices.approve(device.id);

        const { challengeId, nonce } = auth.createChallenge(device.id);
        const signature = cryptoSign('sha256', Buffer.from(nonce, 'base64'), privateKey).toString('base64');

        auth.verifyLogin(device.id, challengeId, signature);
        assert.throws(() => auth.verifyLogin(device.id, challengeId, signature), (err: unknown) => {
            return err instanceof ApiError && err.code === 'CHALLENGE_INVALID';
        });
    });

    it('refuses to issue a challenge for a device that is not approved', async () => {
        const { devices, auth } = await setup();
        const { publicKeyBase64 } = generateDeviceKeyPair();
        const device = await devices.register({
            userId: 'u1',
            roleId: 'viewer',
            name: 'Test Phone',
            platform: 'android',
            appVersion: '1.0',
            publicKey: publicKeyBase64,
            fingerprint: 'fp',
        });
        // still 'pending' - never approved

        assert.throws(() => auth.createChallenge(device.id), (err: unknown) => {
            return err instanceof ApiError && err.code === 'DEVICE_REVOKED';
        });
    });

    it('rejects login once a device has been revoked, even with a valid signature', async () => {
        const { devices, auth } = await setup();
        const { publicKeyBase64, privateKey } = generateDeviceKeyPair();
        const device = await devices.register({
            userId: 'u1',
            roleId: 'viewer',
            name: 'Test Phone',
            platform: 'android',
            appVersion: '1.0',
            publicKey: publicKeyBase64,
            fingerprint: 'fp',
        });
        await devices.approve(device.id);
        const { challengeId, nonce } = auth.createChallenge(device.id);
        const signature = cryptoSign('sha256', Buffer.from(nonce, 'base64'), privateKey).toString('base64');

        await devices.revoke(device.id);

        assert.throws(() => auth.verifyLogin(device.id, challengeId, signature), (err: unknown) => {
            return err instanceof ApiError && err.code === 'DEVICE_REVOKED';
        });
    });

    it('issues a JWT access token that verifyAccessToken can decode', () => {
        return setup().then(({ auth }) => {
            const { token, expiresIn } = auth.issueAccessToken({ sub: 'u1', deviceId: 'd1', roleId: 'viewer', sessionId: 's1' });
            assert.equal(expiresIn, 600);
            const payload = auth.verifyAccessToken(token);
            assert.equal(payload.sub, 'u1');
            assert.equal(payload.deviceId, 'd1');
        });
    });
});
