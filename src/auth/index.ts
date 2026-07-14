import { randomBytes, createPublicKey, verify as cryptoVerify } from 'node:crypto';
import { v4 as uuid } from 'uuid';
import jwt from 'jsonwebtoken';
import { ApiError } from '../lib/errors';
import type { DevicesService } from '../devices';
import type { Device } from '../lib/types';

interface Challenge {
    id: string;
    deviceId: string;
    nonce: Buffer;
    expiresAt: number;
}

export interface AccessTokenPayload {
    sub: string; // userId
    deviceId: string;
    roleId: string;
    sessionId: string;
}

const CHALLENGE_TTL_MS = 60_000;

export class AuthService {
    private readonly challenges = new Map<string, Challenge>();

    constructor(
        private readonly devices: DevicesService,
        private readonly jwtSecret: string,
        private readonly accessTokenTtlMinutes: number,
    ) {}

    createChallenge(deviceId: string): { challengeId: string; nonce: string; expiresAt: string } {
        const device = this.devices.require(deviceId);
        if (!this.devices.isUsable(device)) {
            throw new ApiError('DEVICE_REVOKED');
        }

        this.evictExpired();

        const nonce = randomBytes(32);
        const challengeId = uuid();
        const expiresAt = Date.now() + CHALLENGE_TTL_MS;
        this.challenges.set(challengeId, { id: challengeId, deviceId, nonce, expiresAt });

        return { challengeId, nonce: nonce.toString('base64'), expiresAt: new Date(expiresAt).toISOString() };
    }

    /**
     * Verifies the signed challenge and returns the device it belongs to.
     * Signature is expected as DER-encoded ECDSA/SHA-256 (Node's crypto.verify default,
     * matching Android's `Signature.getInstance("SHA256withECDSA")` output format 1:1).
     */
    verifyLogin(deviceId: string, challengeId: string, signatureBase64: string): Device {
        const challenge = this.challenges.get(challengeId);
        // one-time use: consume immediately regardless of outcome to prevent replay
        this.challenges.delete(challengeId);

        if (!challenge) {
            throw new ApiError('CHALLENGE_INVALID', 'unknown or already used challenge');
        }
        if (challenge.deviceId !== deviceId) {
            throw new ApiError('CHALLENGE_INVALID', 'device mismatch');
        }
        if (challenge.expiresAt < Date.now()) {
            throw new ApiError('CHALLENGE_INVALID', 'challenge expired');
        }

        const device = this.devices.require(deviceId);
        if (!this.devices.isUsable(device)) {
            throw new ApiError('DEVICE_REVOKED');
        }

        let signatureValid: boolean;
        try {
            const publicKeyObject = createPublicKey({
                key: Buffer.from(device.publicKey, 'base64'),
                format: 'der',
                type: 'spki',
            });
            const signature = Buffer.from(signatureBase64, 'base64');
            signatureValid = cryptoVerify('sha256', challenge.nonce, publicKeyObject, signature);
        } catch {
            signatureValid = false;
        }

        if (!signatureValid) {
            throw new ApiError('SIGNATURE_INVALID');
        }

        return device;
    }

    issueAccessToken(payload: AccessTokenPayload): { token: string; expiresIn: number } {
        const expiresIn = this.accessTokenTtlMinutes * 60;
        const token = jwt.sign(payload, this.jwtSecret, { expiresIn });
        return { token, expiresIn };
    }

    verifyAccessToken(token: string): AccessTokenPayload {
        try {
            return jwt.verify(token, this.jwtSecret) as AccessTokenPayload & jwt.JwtPayload;
        } catch {
            throw new ApiError('TOKEN_EXPIRED');
        }
    }

    private evictExpired(): void {
        const now = Date.now();
        for (const [id, challenge] of this.challenges) {
            if (challenge.expiresAt < now) {
                this.challenges.delete(id);
            }
        }
    }
}
