import { randomBytes } from 'node:crypto';
import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import { safeEqualHex, sha256Hex } from '../security/tokens';
import type { AuditService } from '../audit';
import type { Session } from '../lib/types';

export interface NewSessionParams {
    userId: string;
    deviceId: string;
    roleId: string;
    ttlDays: number;
    ip: string | null;
    userAgent: string | null;
}

export interface IssuedRefreshToken {
    session: Session;
    refreshToken: string;
}

export class SessionsService {
    constructor(
        private readonly store: CollectionStore<Session>,
        private readonly audit: AuditService,
    ) {}

    list(): Session[] {
        return this.store.list();
    }

    get(id: string): Session | undefined {
        return this.store.get(id);
    }

    listForUser(userId: string): Session[] {
        return this.store.find((s) => s.userId === userId);
    }

    listForDevice(deviceId: string): Session[] {
        return this.store.find((s) => s.deviceId === deviceId);
    }

    listActive(): Session[] {
        const now = Date.now();
        return this.store.find((s) => !s.revoked && s.expiresAt > now);
    }

    async create(params: NewSessionParams): Promise<IssuedRefreshToken> {
        const refreshToken = randomBytes(32).toString('base64url');
        const session: Session = {
            id: uuid(),
            userId: params.userId,
            deviceId: params.deviceId,
            roleId: params.roleId,
            tokenFamily: uuid(),
            refreshTokenHash: sha256Hex(refreshToken),
            previousRefreshTokenHash: null,
            refreshGeneration: 0,
            createdAt: Date.now(),
            lastActivityAt: Date.now(),
            expiresAt: Date.now() + params.ttlDays * 86_400_000,
            revoked: false,
            lastIp: params.ip,
            userAgent: params.userAgent,
        };
        await this.store.put(session);
        return { session, refreshToken };
    }

    /** Finds the session a presented refresh token belongs to, by current OR previous generation hash. */
    findByRefreshToken(deviceId: string, presentedRefreshToken: string): Session | undefined {
        const hash = sha256Hex(presentedRefreshToken);
        return this.store.findOne(
            (s) =>
                s.deviceId === deviceId &&
                !s.revoked &&
                (safeEqualHex(s.refreshTokenHash, hash) || (s.previousRefreshTokenHash !== null && safeEqualHex(s.previousRefreshTokenHash, hash))),
        );
    }

    /**
     * Rotates the refresh token. If the presented token matches the *previous* generation's
     * hash (i.e. an already-rotated-away token is replayed), the entire token family is
     * revoked immediately per SECURITY.md's reuse-detection requirement.
     */
    async rotate(sessionId: string, presentedRefreshToken: string): Promise<IssuedRefreshToken> {
        const session = this.store.get(sessionId);
        if (!session) {
            throw new ApiError('SESSION_REVOKED', 'unknown session');
        }
        if (session.revoked) {
            throw new ApiError('SESSION_REVOKED');
        }
        if (session.expiresAt < Date.now()) {
            throw new ApiError('SESSION_REVOKED', 'session expired');
        }

        const presentedHash = sha256Hex(presentedRefreshToken);

        if (session.previousRefreshTokenHash !== null && safeEqualHex(presentedHash, session.previousRefreshTokenHash)) {
            await this.revokeFamily(session.tokenFamily);
            await this.audit.log({
                action: 'security.refresh_token_reuse',
                actorUserId: session.userId,
                actorDeviceId: session.deviceId,
                sessionId: session.id,
                result: 'failure',
                detail: `token family ${session.tokenFamily} revoked`,
                ip: session.lastIp,
            });
            throw new ApiError('SESSION_REVOKED', 'refresh token reuse detected');
        }
        if (!safeEqualHex(presentedHash, session.refreshTokenHash)) {
            throw new ApiError('SESSION_REVOKED', 'refresh token not recognized');
        }

        const refreshToken = randomBytes(32).toString('base64url');
        const updated: Session = {
            ...session,
            previousRefreshTokenHash: session.refreshTokenHash,
            refreshTokenHash: sha256Hex(refreshToken),
            refreshGeneration: session.refreshGeneration + 1,
            lastActivityAt: Date.now(),
        };
        await this.store.put(updated);
        return { session: updated, refreshToken };
    }

    async touch(sessionId: string, ip: string | null): Promise<void> {
        const session = this.store.get(sessionId);
        if (!session) {
            return;
        }
        await this.store.put({ ...session, lastActivityAt: Date.now(), lastIp: ip ?? session.lastIp });
    }

    requireActive(sessionId: string): Session {
        const session = this.store.get(sessionId);
        if (!session || session.revoked || session.expiresAt < Date.now()) {
            throw new ApiError('SESSION_REVOKED');
        }
        return session;
    }

    async revoke(sessionId: string): Promise<void> {
        const session = this.store.get(sessionId);
        if (!session) {
            return;
        }
        await this.store.put({ ...session, revoked: true });
    }

    async revokeFamily(tokenFamily: string): Promise<void> {
        for (const session of this.store.find((s) => s.tokenFamily === tokenFamily)) {
            await this.store.put({ ...session, revoked: true });
        }
    }

    async revokeAllForUser(userId: string): Promise<void> {
        for (const session of this.store.find((s) => s.userId === userId)) {
            await this.store.put({ ...session, revoked: true });
        }
    }

    async revokeAllForDevice(deviceId: string): Promise<void> {
        for (const session of this.store.find((s) => s.deviceId === deviceId)) {
            await this.store.put({ ...session, revoked: true });
        }
    }

    async revokeAll(): Promise<void> {
        for (const session of this.store.list()) {
            await this.store.put({ ...session, revoked: true });
        }
    }
}
