import type { Request, Response, NextFunction } from 'express';
import { ApiError } from '../lib/errors';
import type { AuthService } from '../auth';
import type { SessionsService } from '../sessions';
import type { DevicesService } from '../devices';
import type { AuthContext } from '../authorization';
import type { RateLimiter } from '../security/rateLimiter';
import type { AbuseGuard } from '../security/abuseGuard';
import { isPrivateIp } from './localNetwork';

export interface AuthenticatedRequest extends Request {
    ctx?: AuthContext;
    sessionId?: string;
    isLocalNetwork?: boolean;
}

export function sendError(res: Response, err: unknown): void {
    if (err instanceof ApiError) {
        res.status(err.status).json(err.toBody());
        return;
    }
    const message = err instanceof Error ? err.message : 'internal error';
    res.status(500).json({ error: 'SERVER_UNAVAILABLE', message });
}

/**
 * Guards unauthenticated, brute-forceable endpoints (auth challenge/login/refresh, pairing claim)
 * by client IP - these run before any device/session identity is established, so IP is the only
 * key available. Distinct from the per-device RateLimiter guarding /commands.
 */
export function createRateLimitMiddleware(rateLimiter: RateLimiter) {
    return (req: Request, res: Response, next: NextFunction): void => {
        const key = req.ip ?? req.socket.remoteAddress ?? 'unknown';
        if (!rateLimiter.consume(key)) {
            sendError(res, new ApiError('RATE_LIMITED'));
            return;
        }
        next();
    };
}

/**
 * Rejects requests from an IP currently under a temporary block from AbuseGuard, before they
 * even reach the route handler (and before RateLimiter's per-minute counter, so a blocked IP
 * doesn't get to "wait out" the block by staying under the raw rate limit). Route handlers are
 * responsible for calling guard.recordFailure()/recordSuccess() themselves, since only they know
 * whether a given request actually succeeded (e.g. right vs. wrong pairing secret) - this
 * middleware only enforces blocks that already exist.
 */
export function createAbuseGuardMiddleware(guard: AbuseGuard) {
    return (req: Request, res: Response, next: NextFunction): void => {
        const key = req.ip ?? req.socket.remoteAddress ?? 'unknown';
        if (guard.isBlocked(key)) {
            sendError(res, new ApiError('RATE_LIMITED', 'temporarily blocked after repeated failed attempts'));
            return;
        }
        next();
    };
}

export function createAuthMiddleware(auth: AuthService, sessions: SessionsService, devices: DevicesService) {
    return (req: AuthenticatedRequest, res: Response, next: NextFunction): void => {
        req.isLocalNetwork = isPrivateIp(req.ip ?? req.socket.remoteAddress ?? '');

        const header = req.headers.authorization;
        if (!header || !header.startsWith('Bearer ')) {
            sendError(res, new ApiError('AUTH_REQUIRED'));
            return;
        }

        try {
            const payload = auth.verifyAccessToken(header.slice('Bearer '.length));
            const session = sessions.requireActive(payload.sessionId);
            const device = devices.require(payload.deviceId);
            if (!devices.isUsable(device)) {
                throw new ApiError('DEVICE_REVOKED');
            }
            req.ctx = { userId: payload.sub, deviceId: payload.deviceId, roleId: payload.roleId };
            req.sessionId = session.id;
            // Best-effort bookkeeping (last-seen timestamp) - never let a transient storage error
            // here fail the request or, worse, crash the process via an unhandled rejection.
            devices.touch(device.id, req.ip ?? null).catch(() => undefined);
            sessions.touch(session.id, req.ip ?? null).catch(() => undefined);
            next();
        } catch (err) {
            sendError(res, err instanceof ApiError ? err : new ApiError('AUTH_REQUIRED'));
        }
    };
}
