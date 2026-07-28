import { createPublicKey, createHash, verify as cryptoVerify } from 'node:crypto';

/** How far a request's timestamp may drift from the server's clock before it's rejected outright,
 *  independent of the nonce/replay check below - keeps the acceptance window short regardless of
 *  how long the replay cache happens to retain entries. */
export const SIGNATURE_MAX_CLOCK_SKEW_MS = 5 * 60_000;

export interface SignedRequestInput {
    method: string;
    /** The request path starting at (and including) "/api/v1" - deliberately NOT the full public
     *  URL path, since a reverse proxy may add or strip its own prefix (see DEPLOYMENT.md's nginx
     *  example, which strips "/mobile-control") before the request reaches this adapter. Both
     *  sides anchor on "/api/v1" specifically because that's the one path segment guaranteed to be
     *  stable: it's how this router mounts itself internally, regardless of any external prefix. */
    path: string;
    /** Epoch milliseconds, as a decimal string (matches the X-Signature-Timestamp header verbatim). */
    timestamp: string;
    nonce: string;
    bodyBytes: Buffer;
    signatureBase64: string;
    /** SPKI-encoded EC P-256 public key, base64 - same format as Device.publicKey. */
    devicePublicKeyBase64: string;
}

/** Deterministic byte-for-byte reconstruction of what the client actually signed - both sides
 *  build this identically, so a re-serialized body or normalized path would silently break every
 *  signature. Order matters and is not configurable. */
export function buildSignedCanonicalString(input: Pick<SignedRequestInput, 'method' | 'path' | 'timestamp' | 'nonce' | 'bodyBytes'>): string {
    const bodyHash = createHash('sha256').update(input.bodyBytes).digest('hex');
    return `${input.method.toUpperCase()}\n${input.path}\n${input.timestamp}\n${input.nonce}\n${bodyHash}`;
}

/**
 * Verifies a per-request signature from a paired device. DER-encoded ECDSA/SHA-256, same
 * convention already established for the login challenge signature (see AuthService.verifyLogin) -
 * Node's crypto.verify default format matches Android's Signature.getInstance("SHA256withECDSA")
 * output 1:1.
 */
export function verifyRequestSignature(input: SignedRequestInput): boolean {
    const timestampMs = Number(input.timestamp);
    if (!Number.isFinite(timestampMs)) {
        return false;
    }
    if (Math.abs(Date.now() - timestampMs) > SIGNATURE_MAX_CLOCK_SKEW_MS) {
        return false;
    }

    const canonical = buildSignedCanonicalString(input);

    try {
        const publicKeyObject = createPublicKey({
            key: Buffer.from(input.devicePublicKeyBase64, 'base64'),
            format: 'der',
            type: 'spki',
        });
        const signature = Buffer.from(input.signatureBase64, 'base64');
        return cryptoVerify('sha256', Buffer.from(canonical, 'utf8'), publicKeyObject, signature);
    } catch {
        return false;
    }
}
