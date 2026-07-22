import { Router, type Request, type Response } from 'express';
import { ApiError } from '../lib/errors';
import {
    sendError,
    createAuthMiddleware,
    createRateLimitMiddleware,
    createAbuseGuardMiddleware,
    type AuthenticatedRequest,
} from './middleware';
import type { AuthService } from '../auth';
import type { SessionsService } from '../sessions';
import type { DevicesService } from '../devices';
import type { UsersService } from '../users';
import type { PairingService } from '../pairing';
import type { CatalogService } from '../catalog';
import type { DashboardsService } from '../dashboards';
import type { CommandsService } from '../commands';
import type { AuditService } from '../audit';
import type { HistoryService } from '../history';
import type { CameraService } from '../camera';
import type { RateLimiter } from '../security/rateLimiter';
import type { AbuseGuard } from '../security/abuseGuard';
import type { DashboardLayout } from '../lib/types';

export interface ApiServices {
    adapter: ioBroker.Adapter;
    auth: AuthService;
    sessions: SessionsService;
    devices: DevicesService;
    users: UsersService;
    pairing: PairingService;
    catalog: CatalogService;
    dashboards: DashboardsService;
    commands: CommandsService;
    audit: AuditService;
    history: HistoryService;
    camera: CameraService;
    refreshTokenTtlDays: number;
    authRateLimiter: RateLimiter;
    abuseGuard: AbuseGuard;
}

/** Records a failed attempt and, the moment it crosses the threshold and triggers a new
 *  temporary block, logs a warning - visible in the adapter's normal log, not just buried in
 *  the audit log (which recorded every attempt already, but never actively surfaced a pattern). */
function recordAbuseFailure(services: ApiServices, ip: string | null, reason: string): void {
    const key = ip ?? 'unknown';
    const justBlocked = services.abuseGuard.recordFailure(key);
    if (justBlocked) {
        services.adapter.log.warn(
            `mobile-control: repeated failed ${reason} attempts from ${key} - temporarily blocking this IP`,
        );
    }
}

async function issueSessionAndTokens(
    services: ApiServices,
    deviceId: string,
    userId: string,
    roleId: string,
    ip: string | null,
    userAgent: string | null,
) {
    const { session, refreshToken } = await services.sessions.create({
        userId,
        deviceId,
        roleId,
        ttlDays: services.refreshTokenTtlDays,
        ip,
        userAgent,
    });
    const access = services.auth.issueAccessToken({ sub: userId, deviceId, roleId, sessionId: session.id });
    return { accessToken: access.token, refreshToken, expiresIn: access.expiresIn, sessionId: session.id };
}

export function createApiRouter(services: ApiServices): Router {
    const router = Router();
    const requireAuth = createAuthMiddleware(services.auth, services.sessions, services.devices);
    const rateLimitByIp = createRateLimitMiddleware(services.authRateLimiter);
    const blockIfAbusive = createAbuseGuardMiddleware(services.abuseGuard);

    // ---- Server info ------------------------------------------------------
    // Intentionally unauthenticated: the value itself isn't a secret, it's the same
    // serverFingerprint already embedded in every QR pairing invite. Exists purely so the app can
    // re-fetch it live over its own connection during onboarding and compare it against the QR
    // code's copy (see ServerFingerprintChecker.kt on the Android side) - this backend doesn't
    // terminate TLS itself, so there's no real certificate to pin against instead.
    router.get('/server/info', rateLimitByIp, (_req: Request, res: Response) => {
        res.json({ fingerprint: services.pairing.getServerFingerprint() });
    });

    // ---- Pairing --------------------------------------------------------
    router.post('/pairing/claim', blockIfAbusive, rateLimitByIp, async (req: Request, res: Response) => {
        try {
            const { pairingId, pairingSecret, deviceName, platform, appVersion, publicKey } = req.body ?? {};
            if (!pairingId || !pairingSecret || !deviceName || !platform || !appVersion || !publicKey) {
                throw new ApiError('VALIDATION_ERROR', 'missing required fields');
            }
            const result = await services.pairing.claim({ pairingId, pairingSecret, deviceName, platform, appVersion, publicKey });
            services.abuseGuard.recordSuccess(req.ip ?? 'unknown');
            await services.audit.log({ action: 'pairing.claim', result: 'success', ip: req.ip ?? null, detail: pairingId });
            res.json(result);
        } catch (err) {
            recordAbuseFailure(services, req.ip ?? null, 'pairing');
            await services.audit.log({
                action: 'pairing.claim',
                result: 'failure',
                ip: req.ip ?? null,
                detail: err instanceof Error ? err.message : String(err),
            });
            sendError(res, err);
        }
    });

    // Trust boundary note: initial trust for a paired device is the single-use, short-lived
    // pairing secret (delivered via the scanned QR code) - not yet a proven private-key
    // signature. That proof (challenge-response, /auth/challenge + /auth/login) is required
    // for every subsequent login once the device is approved. This matches MASTERKONZEPT.md §5.
    router.get('/pairing/status/:claimId', async (req: Request, res: Response) => {
        try {
            const claim = await services.pairing.status(req.params.claimId);
            if (claim.status !== 'approved' || !claim.deviceId) {
                res.json({ status: claim.status });
                return;
            }
            if (claim.tokensIssued) {
                res.json({ status: 'approved' });
                return;
            }
            const device = services.devices.require(claim.deviceId);
            const tokens = await issueSessionAndTokens(
                services,
                device.id,
                device.userId,
                device.roleId ?? 'viewer',
                req.ip ?? null,
                (req.headers['user-agent'] as string) ?? null,
            );
            await services.pairing.markTokensIssued(claim.id);
            await services.audit.log({
                action: 'pairing.tokens_issued',
                actorUserId: device.userId,
                actorDeviceId: device.id,
                sessionId: tokens.sessionId,
                result: 'success',
                ip: req.ip ?? null,
            });
            res.json({
                status: 'approved',
                deviceId: device.id,
                accessToken: tokens.accessToken,
                refreshToken: tokens.refreshToken,
                expiresIn: tokens.expiresIn,
            });
        } catch (err) {
            sendError(res, err);
        }
    });

    // ---- Auth -------------------------------------------------------------
    // Challenge success isn't cryptographic proof of anything (just that a deviceId exists and
    // is usable), so unlike claim/login/refresh below it deliberately does NOT call
    // abuseGuard.recordSuccess() - that would let device-id enumeration reset the failure
    // counter for free, defeating the point of tracking it at all.
    router.post('/auth/challenge', blockIfAbusive, rateLimitByIp, async (req: Request, res: Response) => {
        try {
            const { deviceId } = req.body ?? {};
            if (!deviceId) {
                throw new ApiError('VALIDATION_ERROR', 'deviceId required');
            }
            res.json(services.auth.createChallenge(deviceId));
        } catch (err) {
            recordAbuseFailure(services, req.ip ?? null, 'auth challenge');
            sendError(res, err);
        }
    });

    router.post('/auth/login', blockIfAbusive, rateLimitByIp, async (req: Request, res: Response) => {
        try {
            const { deviceId, challengeId, signature } = req.body ?? {};
            if (!deviceId || !challengeId || !signature) {
                throw new ApiError('VALIDATION_ERROR', 'deviceId, challengeId and signature required');
            }
            const device = services.auth.verifyLogin(deviceId, challengeId, signature);
            const user = services.users.require(device.userId);
            const tokens = await issueSessionAndTokens(
                services,
                device.id,
                device.userId,
                device.roleId ?? user.roleId,
                req.ip ?? null,
                (req.headers['user-agent'] as string) ?? null,
            );
            services.abuseGuard.recordSuccess(req.ip ?? 'unknown');
            await services.audit.log({
                action: 'auth.login',
                actorUserId: user.id,
                actorDeviceId: device.id,
                sessionId: tokens.sessionId,
                result: 'success',
                ip: req.ip ?? null,
            });
            res.json({
                accessToken: tokens.accessToken,
                refreshToken: tokens.refreshToken,
                expiresIn: tokens.expiresIn,
                user: { id: user.id, name: user.name },
            });
        } catch (err) {
            recordAbuseFailure(services, req.ip ?? null, 'auth login');
            await services.audit.log({
                action: 'auth.login',
                result: 'failure',
                ip: req.ip ?? null,
                detail: err instanceof Error ? err.message : String(err),
            });
            sendError(res, err);
        }
    });

    router.post('/auth/refresh', blockIfAbusive, rateLimitByIp, async (req: Request, res: Response) => {
        try {
            const { deviceId, refreshToken } = req.body ?? {};
            if (!deviceId || !refreshToken) {
                throw new ApiError('VALIDATION_ERROR', 'deviceId and refreshToken required');
            }
            const device = services.devices.require(deviceId);
            if (!services.devices.isUsable(device)) {
                throw new ApiError('DEVICE_REVOKED');
            }
            const session = services.sessions.findByRefreshToken(deviceId, refreshToken);
            if (!session) {
                throw new ApiError('SESSION_REVOKED', 'unknown refresh token');
            }
            const rotated = await services.sessions.rotate(session.id, refreshToken);
            services.abuseGuard.recordSuccess(req.ip ?? 'unknown');
            const access = services.auth.issueAccessToken({
                sub: rotated.session.userId,
                deviceId,
                roleId: rotated.session.roleId,
                sessionId: rotated.session.id,
            });
            await services.audit.log({
                action: 'auth.refresh',
                actorUserId: rotated.session.userId,
                actorDeviceId: deviceId,
                sessionId: rotated.session.id,
                result: 'success',
                ip: req.ip ?? null,
            });
            res.json({ accessToken: access.token, refreshToken: rotated.refreshToken, expiresIn: access.expiresIn });
        } catch (err) {
            recordAbuseFailure(services, req.ip ?? null, 'auth refresh');
            await services.audit.log({
                action: 'auth.refresh',
                result: 'failure',
                ip: req.ip ?? null,
                detail: err instanceof Error ? err.message : String(err),
            });
            sendError(res, err);
        }
    });

    // ---- Catalog / States --------------------------------------------------
    router.get('/catalog', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            // Delta support: if the client already has this exact version cached, skip the full
            // (relatively expensive) object-tree walk and just confirm nothing changed.
            const clientVersion = typeof req.query.version === 'string' ? Number(req.query.version) : NaN;
            if (!Number.isNaN(clientVersion) && clientVersion === services.catalog.currentVersion()) {
                res.json({ version: clientVersion, unchanged: true });
                return;
            }
            const catalog = await services.catalog.effectiveCatalog(req.ctx!);
            res.json(catalog);
        } catch (err) {
            sendError(res, err);
        }
    });

    router.get('/states', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            const idsParam = typeof req.query.ids === 'string' ? req.query.ids : '';
            const ids = idsParam
                .split(',')
                .map((s) => s.trim())
                .filter(Boolean);

            const states: Record<string, { value: unknown; timestamp: string; lastChange: string; ack: boolean } | null> = {};
            for (const id of ids) {
                try {
                    const { stateId } = services.catalog.resolveAuthorized(id, req.ctx!, 'read');
                    const state = await services.adapter.getForeignStateAsync(stateId);
                    states[id] = state
                        ? {
                              value: state.val,
                              timestamp: new Date(state.ts).toISOString(),
                              lastChange: new Date(state.lc).toISOString(),
                              ack: state.ack,
                          }
                        : null;
                } catch {
                    states[id] = null;
                }
            }
            res.json({ states });
        } catch (err) {
            sendError(res, err);
        }
    });

    router.get('/history', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            const id = typeof req.query.id === 'string' ? req.query.id : '';
            if (!id) {
                throw new ApiError('VALIDATION_ERROR', 'id required');
            }
            const { stateId, permission } = services.catalog.resolveAuthorized(id, req.ctx!, 'read');
            if (!permission.history) {
                throw new ApiError('READ_FORBIDDEN', 'history is not enabled for this object');
            }

            const from = typeof req.query.from === 'string' ? Date.parse(req.query.from) : NaN;
            const to = typeof req.query.to === 'string' ? Date.parse(req.query.to) : NaN;
            const limit = typeof req.query.limit === 'string' ? parseInt(req.query.limit, 10) : undefined;

            const entries = await services.history.query(stateId, {
                from: Number.isNaN(from) ? null : from,
                to: Number.isNaN(to) ? null : to,
                limit: limit && !Number.isNaN(limit) ? limit : 500,
            });

            res.json({
                entries: entries.map((e) => ({ value: e.value, timestamp: new Date(e.timestamp).toISOString() })),
            });
        } catch (err) {
            sendError(res, err);
        }
    });

    // ---- Camera --------------------------------------------------------------
    // Proxied through this adapter (not a direct link to the camera/its adapter) so the app only
    // ever needs to reach mobile-control's own auth boundary - see CameraService for why.
    router.get('/objects/:id/snapshot', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            const { stateId } = services.catalog.resolveAuthorized(req.params.id, req.ctx!, 'read');
            const snapshot = await services.camera.fetchSnapshot(stateId);
            res.set('Cache-Control', 'no-store');
            res.set('X-Snapshot-Timestamp', new Date(snapshot.timestamp).toISOString());
            res.type(snapshot.contentType).send(snapshot.buffer);
        } catch (err) {
            sendError(res, err);
        }
    });

    // ---- Commands -----------------------------------------------------------
    router.post('/commands', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            const { commandId, objectId, value, timestamp, nonce, confirmed } = req.body ?? {};
            if (!commandId || !objectId || timestamp === undefined || !nonce) {
                throw new ApiError('VALIDATION_ERROR', 'commandId, objectId, timestamp and nonce required');
            }
            const record = await services.commands.execute(
                { ...req.ctx!, ip: req.ip ?? null, isLocalNetwork: req.isLocalNetwork ?? false },
                { commandId, objectId, value, timestamp, nonce, confirmed },
            );
            res.json({ status: record.status === 'accepted' || record.status === 'executed' ? 'accepted' : record.status });
        } catch (err) {
            sendError(res, err);
        }
    });

    // ---- Dashboards -----------------------------------------------------------
    router.get('/dashboards', requireAuth, (req: AuthenticatedRequest, res: Response) => {
        res.json({ dashboards: services.dashboards.listForUser(req.ctx!.userId) });
    });

    router.post('/dashboards', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            const { name } = req.body ?? {};
            if (!name) {
                throw new ApiError('VALIDATION_ERROR', 'name required');
            }
            const dashboard = await services.dashboards.create(req.ctx!.userId, name);
            res.status(201).json(dashboard);
        } catch (err) {
            sendError(res, err);
        }
    });

    router.put('/dashboards/:id', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            const { name, layouts, isStartDashboard, revision } = req.body ?? {};
            if (typeof revision !== 'number') {
                throw new ApiError('VALIDATION_ERROR', 'revision required for optimistic concurrency');
            }
            const dashboard = await services.dashboards.update(
                req.ctx!.userId,
                req.params.id,
                { name, layouts: layouts as DashboardLayout[] | undefined, isStartDashboard },
                revision,
            );
            await services.audit.log({
                action: 'dashboard.updated',
                actorUserId: req.ctx!.userId,
                actorDeviceId: req.ctx!.deviceId,
                result: 'success',
            });
            res.json(dashboard);
        } catch (err) {
            sendError(res, err);
        }
    });

    router.delete('/dashboards/:id', requireAuth, async (req: AuthenticatedRequest, res: Response) => {
        try {
            await services.dashboards.delete(req.ctx!.userId, req.params.id);
            res.status(204).send();
        } catch (err) {
            sendError(res, err);
        }
    });

    return router;
}
