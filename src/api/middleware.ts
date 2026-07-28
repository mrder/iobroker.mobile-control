import type { Request, Response, NextFunction } from 'express';
import { ApiError } from '../lib/errors';
import type { AuthService } from '../auth';
import type { SessionsService } from '../sessions';
import type { DevicesService } from '../devices';
import type { AuthContext } from '../authorization';
import type { RateLimiter } from '../security/rateLimiter';
import type { AbuseGuard } from '../security/abuseGuard';
import type { ReplayGuard } from '../security/replayGuard';
import { verifyRequestSignature } from '../security/requestSignature';
import { isPrivateIp } from './localNetwork';

export interface AuthenticatedRequest extends Request {
    ctx?: AuthContext;
    sessionId?: string;
    isLocalNetwork?: boolean;
    /** Raw request body bytes, captured by express.json()'s verify hook in main.ts - needed
     *  because signature verification must hash exactly what the client sent, not a re-serialized
     *  version of the parsed body (key order/whitespace would differ and break every signature). */
    rawBody?: Buffer;
}

const SIGNATURE_TIMESTAMP_HEADER = 'x-signature-timestamp';
const SIGNATURE_NONCE_HEADER = 'x-signature-nonce';
const SIGNATURE_HEADER = 'x-signature';

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

/**
 * Requires a per-request signature from the paired device's Keystore key on top of the bearer
 * token - closes the gap where a stolen/leaked access token alone would be enough to replay a
 * request via curl. Must run AFTER createAuthMiddleware (needs req.ctx.deviceId already set).
 *
 * The signed payload is method + path + timestamp + nonce + body hash (see
 * buildSignedCanonicalString) - path is anchored at "/api/v1" specifically because a reverse
 * proxy in front of the adapter may add or strip its own prefix (see DEPLOYMENT.md), so
 * req.originalUrl (which always starts at "/api/v1" - that's this router's own mount point,
 * unaffected by whatever prefix a proxy added/removed before the request got here) is the one
 * value both this adapter and the app can agree on regardless of deployment topology.
 */
export function createSignatureMiddleware(devices: DevicesService, replayGuard: ReplayGuard) {
    return (req: AuthenticatedRequest, res: Response, next: NextFunction): void => {
        const deviceId = req.ctx?.deviceId;
        if (!deviceId) {
            // Defensive only - createAuthMiddleware must always run first in every route chain.
            sendError(res, new ApiError('AUTH_REQUIRED'));
            return;
        }

        const timestamp = req.header(SIGNATURE_TIMESTAMP_HEADER);
        const nonce = req.header(SIGNATURE_NONCE_HEADER);
        const signature = req.header(SIGNATURE_HEADER);
        if (!timestamp || !nonce || !signature) {
            sendError(res, new ApiError('SIGNATURE_INVALID', 'missing signature headers'));
            return;
        }

        try {
            const device = devices.require(deviceId);
            const path = req.originalUrl.split('?')[0];
            const valid = verifyRequestSignature({
                method: req.method,
                path,
                timestamp,
                nonce,
                bodyBytes: req.rawBody ?? Buffer.alloc(0),
                signatureBase64: signature,
                devicePublicKeyBase64: device.publicKey,
            });
            if (!valid) {
                sendError(res, new ApiError('SIGNATURE_INVALID'));
                return;
            }
            // Only consumed once the signature itself checks out, so a garbage/forged signature
            // attempt can't burn a nonce a legitimate request might still want to use.
            if (!replayGuard.checkAndRemember(`sig:${deviceId}:${nonce}`)) {
                sendError(res, new ApiError('REPLAY_DETECTED'));
                return;
            }
            next();
        } catch (err) {
            sendError(res, err instanceof ApiError ? err : new ApiError('SIGNATURE_INVALID'));
        }
    };
}
