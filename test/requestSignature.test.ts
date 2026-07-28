import { strict as assert } from 'node:assert';
import { generateKeyPairSync, sign as cryptoSign } from 'node:crypto';
import { verifyRequestSignature, buildSignedCanonicalString, SIGNATURE_MAX_CLOCK_SKEW_MS } from '../src/security/requestSignature';

function generateDeviceKeyPair() {
    const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
    const publicKeyBase64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
    return { publicKeyBase64, privateKey };
}

function sign(privateKey: unknown, canonical: string): string {
    return cryptoSign('sha256', Buffer.from(canonical, 'utf8'), privateKey as never).toString('base64');
}

describe('requestSignature', () => {
    it('verifies a correctly signed request', () => {
        const { publicKeyBase64, privateKey } = generateDeviceKeyPair();
        const input = {
            method: 'GET',
            path: '/api/v1/catalog',
            timestamp: String(Date.now()),
            nonce: 'abc123',
            bodyBytes: Buffer.alloc(0),
        };
        const signatureBase64 = sign(privateKey, buildSignedCanonicalString(input));

        assert.equal(verifyRequestSignature({ ...input, signatureBase64, devicePublicKeyBase64: publicKeyBase64 }), true);
    });

    it('rejects a signature from a different device key', () => {
        const { privateKey } = generateDeviceKeyPair();
        const { publicKeyBase64: otherDevicePublicKey } = generateDeviceKeyPair();
        const input = {
            method: 'POST',
            path: '/api/v1/commands',
            timestamp: String(Date.now()),
            nonce: 'nonce-1',
            bodyBytes: Buffer.from('{"objectId":"x"}'),
        };
        const signatureBase64 = sign(privateKey, buildSignedCanonicalString(input));

        assert.equal(verifyRequestSignature({ ...input, signatureBase64, devicePublicKeyBase64: otherDevicePublicKey }), false);
    });

    it('rejects a tampered body - the signature was computed over the original bytes', () => {
        const { publicKeyBase64, privateKey } = generateDeviceKeyPair();
        const input = {
            method: 'PUT',
            path: '/api/v1/dashboards/123',
            timestamp: String(Date.now()),
            nonce: 'nonce-2',
            bodyBytes: Buffer.from('{"name":"Original"}'),
        };
        const signatureBase64 = sign(privateKey, buildSignedCanonicalString(input));

        const tampered = { ...input, bodyBytes: Buffer.from('{"name":"Tampered"}'), signatureBase64, devicePublicKeyBase64: publicKeyBase64 };
        assert.equal(verifyRequestSignature(tampered), false);
    });

    it('rejects a signature for a different path - prevents replaying a signed request against another endpoint', () => {
        const { publicKeyBase64, privateKey } = generateDeviceKeyPair();
        const input = {
            method: 'GET',
            path: '/api/v1/catalog',
            timestamp: String(Date.now()),
            nonce: 'nonce-3',
            bodyBytes: Buffer.alloc(0),
        };
        const signatureBase64 = sign(privateKey, buildSignedCanonicalString(input));

        const redirected = { ...input, path: '/api/v1/dashboards', signatureBase64, devicePublicKeyBase64: publicKeyBase64 };
        assert.equal(verifyRequestSignature(redirected), false);
    });

    it('rejects a timestamp outside the clock-skew window, even with an otherwise-valid signature', () => {
        const { publicKeyBase64, privateKey } = generateDeviceKeyPair();
        const staleTimestamp = String(Date.now() - SIGNATURE_MAX_CLOCK_SKEW_MS - 60_000);
        const input = {
            method: 'GET',
            path: '/api/v1/catalog',
            timestamp: staleTimestamp,
            nonce: 'nonce-4',
            bodyBytes: Buffer.alloc(0),
        };
        const signatureBase64 = sign(privateKey, buildSignedCanonicalString(input));

        assert.equal(verifyRequestSignature({ ...input, signatureBase64, devicePublicKeyBase64: publicKeyBase64 }), false);
    });

    it('rejects a non-numeric timestamp instead of throwing', () => {
        const { publicKeyBase64 } = generateDeviceKeyPair();
        assert.equal(
            verifyRequestSignature({
                method: 'GET',
                path: '/api/v1/catalog',
                timestamp: 'not-a-number',
                nonce: 'nonce-5',
                bodyBytes: Buffer.alloc(0),
                signatureBase64: 'AAAA',
                devicePublicKeyBase64: publicKeyBase64,
            }),
            false,
        );
    });

    it('rejects garbage signature bytes instead of throwing', () => {
        const { publicKeyBase64 } = generateDeviceKeyPair();
        assert.equal(
            verifyRequestSignature({
                method: 'GET',
                path: '/api/v1/catalog',
                timestamp: String(Date.now()),
                nonce: 'nonce-6',
                bodyBytes: Buffer.alloc(0),
                signatureBase64: 'not-valid-base64-der!!',
                devicePublicKeyBase64: publicKeyBase64,
            }),
            false,
        );
    });
});
