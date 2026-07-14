import type { Request, Response, NextFunction } from 'express';
import { ApiError } from '../lib/errors';
import type { AuthService } from '../auth';
import type { SessionsService } from '../sessions';
import type { DevicesService } from '../devices';
import type { AuthContext } from '../authorization';
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
            void devices.touch(device.id, req.ip ?? null);
            void sessions.touch(session.id, req.ip ?? null);
            next();
        } catch (err) {
            sendError(res, err instanceof ApiError ? err : new ApiError('AUTH_REQUIRED'));
        }
    };
}
