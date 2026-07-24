import { Router, type Request, type Response } from 'express';
import express from 'express';
import { ApiError } from '../lib/errors';
import { sendError, createAuthMiddleware, type AuthenticatedRequest } from './middleware';
import { forwardTunnelRequest } from '../tunnel/forward';
import type { AuthService } from '../auth';
import type { SessionsService } from '../sessions';
import type { DevicesService } from '../devices';
import type { TunnelService } from '../tunnel';
import type { AuditService } from '../audit';
import type { RateLimiter } from '../security/rateLimiter';

export interface TunnelRouterServices {
    adapter: ioBroker.Adapter;
    auth: AuthService;
    sessions: SessionsService;
    devices: DevicesService;
    tunnel: TunnelService;
    audit: AuditService;
    /** Per-device request budget for /tunnel/proxy - deliberately separate from and far more
     *  generous than the general API rate limiter: a single page load easily bursts dozens of
     *  sub-resource requests (HTML, CSS, JS, images) within a second or two. */
    tunnelRateLimiter: RateLimiter;
}

const TUNNEL_TOKEN_HEADER = 'x-tunnel-token';
const TUNNEL_PATH_HEADER = 'x-tunnel-path';

/**
 * Mounted at /api/v1 BEFORE the global express.json() body parser (see main.ts) so /tunnel/proxy
 * gets the request body as untouched raw bytes regardless of content-type - a JSON-parse-then-
 * reserialize round trip would silently corrupt/reformat whatever the tunneled page actually
 * sent. Kept as its own router (not part of the main createApiRouter) specifically because of
 * that different body-parsing need.
 *
 * See src/tunnel/index.ts (TunnelService) for the token model and src/tunnel/forward.ts for the
 * actual forwarding + origin-allowlist enforcement.
 */
export function createTunnelRouter(services: TunnelRouterServices): Router {
    const router = Router();
    const requireAuth = createAuthMiddleware(services.auth, services.sessions, services.devices);

    router.post('/tunnel-token/:embedId', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            const { token, expiresAt } = services.tunnel.issue(req.params.embedId, req.ctx!);
            await services.audit.log({
                action: 'tunnel.token_issued',
                actorUserId: req.ctx!.userId,
                actorDeviceId: req.ctx!.deviceId,
                objectId: req.params.embedId,
                result: 'success',
            });
            res.json({ token, expiresAt });
        } catch (err) {
            sendError(res, err);
        }
    });

    // Deliberately NOT behind requireAuth (the JWT bearer flow) - authorized purely by the
    // short-lived, single-purpose tunnel token from the route above (the "separate API key").
    // express.raw({ type: () => true }) matches every content-type so req.body is always the
    // exact bytes the client sent, never parsed/reserialized.
    router.all('/tunnel/proxy', express.raw({ type: () => true, limit: '20mb' }), async (req: Request, res: Response) => {
        try {
            const token = req.header(TUNNEL_TOKEN_HEADER);
            const path = req.header(TUNNEL_PATH_HEADER);
            if (!token || !path) {
                throw new ApiError('VALIDATION_ERROR', `${TUNNEL_TOKEN_HEADER} and ${TUNNEL_PATH_HEADER} headers are required`);
            }
            const session = services.tunnel.resolve(token);
            if (!session) {
                throw new ApiError('AUTH_REQUIRED', 'tunnel token is invalid, expired, or access was revoked');
            }
            const key = `tunnel:${session.urlEmbedId}`;
            if (!services.tunnelRateLimiter.consume(key)) {
                throw new ApiError('RATE_LIMITED');
            }

            const targetOrigin = new URL(session.targetUrl);
            const result = await forwardTunnelRequest({
                targetOrigin: `${targetOrigin.protocol}//${targetOrigin.host}`,
                path,
                method: req.method,
                headers: req.headers,
                body: Buffer.isBuffer(req.body) ? req.body : null,
            });

            res.status(result.status);
            for (const [name, value] of Object.entries(result.headers)) {
                res.set(name, value);
            }
            for (const cookie of result.setCookies) {
                res.append('Set-Cookie', cookie);
            }
            res.send(result.body);
        } catch (err) {
            sendError(res, err);
        }
    });

    return router;
}
