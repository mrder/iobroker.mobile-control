import { randomBytes, createHash, timingSafeEqual } from 'node:crypto';

export function generateSecret(bytes = 32): string {
    return randomBytes(bytes).toString('base64url');
}

export function sha256Hex(input: string): string {
    return createHash('sha256').update(input).digest('hex');
}

/** Constant-time comparison of two hex digests to avoid timing side-channels. */
export function safeEqualHex(a: string, b: string): boolean {
    const bufA = Buffer.from(a, 'hex');
    const bufB = Buffer.from(b, 'hex');
    if (bufA.length !== bufB.length) {
        return false;
    }
    return timingSafeEqual(bufA, bufB);
}

export function newId(): string {
    return randomBytes(16).toString('hex');
}
