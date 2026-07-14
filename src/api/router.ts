import { Router, type Request, type Response } from 'express';
import { ApiError } from '../lib/errors';
import { sendError, createAuthMiddleware, type AuthenticatedRequest } from './middleware';
import type { AuthService } from '../auth';
import type { SessionsService } from '../sessions';
import type { DevicesService } from '../devices';
import type { UsersService } from '../users';
import type { PairingService } from '../pairing';
import type { CatalogService } from '../catalog';
import type { DashboardsService } from '../dashboards';
import type { CommandsService } from '../commands';
import type { AuditService } from '../audit';
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
    refreshTokenTtlDays: number;
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

    // ---- Pairing --------------------------------------------------------
    router.post('/pairing/claim', async (req: Request, res: Response) => {
        try {
            const { pairingId, pairingSecret, deviceName, platform, appVersion, publicKey } = req.body ?? {};
            if (!pairingId || !pairingSecret || !deviceName || !platform || !appVersion || !publicKey) {
                throw new ApiError('VALIDATION_ERROR', 'missing required fields');
            }
            const result = await services.pairing.claim({ pairingId, pairingSecret, deviceName, platform, appVersion, publicKey });
            await services.audit.log({ action: 'pairing.claim', result: 'success', ip: req.ip ?? null, detail: pairingId });
            res.json(result);
        } catch (err) {
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
    router.post('/auth/challenge', async (req: Request, res: Response) => {
        try {
            const { deviceId } = req.body ?? {};
            if (!deviceId) {
                throw new ApiError('VALIDATION_ERROR', 'deviceId required');
            }
            res.json(services.auth.createChallenge(deviceId));
        } catch (err) {
            sendError(res, err);
        }
    });

    router.post('/auth/login', async (req: Request, res: Response) => {
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
            await services.audit.log({
                action: 'auth.login',
                result: 'failure',
                ip: req.ip ?? null,
                detail: err instanceof Error ? err.message : String(err),
            });
            sendError(res, err);
        }
    });

    router.post('/auth/refresh', async (req: Request, res: Response) => {
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
