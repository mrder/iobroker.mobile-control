import { randomBytes } from 'node:crypto';
import { ApiError } from '../lib/errors';
import type { AuthContext } from '../authorization';
import type { UrlEmbedsService } from '../urlEmbeds';

const TOKEN_TTL_MS = 10 * 60_000;

interface TunnelSession {
    urlEmbedId: string;
    ctx: AuthContext;
    expiresAt: number;
}

/**
 * Short-lived, narrowly-scoped credential for the "Tunnel" mode of the Web-Seite widget (see
 * WEB_VIEW's local-proxy-in-the-app design, android-app WebTunnelProxy) - deliberately a
 * SEPARATE credential from the JWT bearer token used for the rest of the API, per the live
 * request for "einen zusätzlichen separaten API-Key". A tunnel token authorizes forwarding
 * requests to exactly ONE admin-approved UrlEmbed's origin, for a limited time, for the exact
 * (user, device, role) it was issued to - never broader API access, even if it leaked somewhere
 * downstream (a proxy log, a crash report, ...).
 *
 * In-memory only (same pattern as ReplayGuard/AbuseGuard) - a tunnel session doesn't need to
 * survive an adapter restart, and access is re-checked via UrlEmbedsService.canAccess() on every
 * single proxied request (resolve()), not just at issuance - revoking the underlying
 * UrlEmbedAccessRule mid-session cuts the tunnel off immediately instead of waiting for the
 * token to expire.
 */
export class TunnelService {
    private readonly sessions = new Map<string, TunnelSession>();

    constructor(private readonly urlEmbeds: UrlEmbedsService) {}

    /** Mints a fresh token scoped to [urlEmbedId] for the requesting device. Throws NOT_FOUND for
     *  an unknown embed, READ_FORBIDDEN if this role/user/device has no access rule granting it -
     *  same checks GET /url-embeds itself already enforces, just re-applied here at issuance. */
    issue(urlEmbedId: string, ctx: AuthContext): { token: string; expiresAt: number } {
        if (!this.urlEmbeds.get(urlEmbedId)) {
            throw new ApiError('NOT_FOUND', `url embed ${urlEmbedId} not found`);
        }
        if (!this.urlEmbeds.canAccess(urlEmbedId, ctx)) {
            throw new ApiError('READ_FORBIDDEN');
        }
        this.evictExpired();
        // At most one live token per (device, embed) - a fresh WebView session simply replaces
        // whatever session it had before rather than accumulating stale ones.
        for (const [token, session] of this.sessions) {
            if (session.urlEmbedId === urlEmbedId && session.ctx.deviceId === ctx.deviceId) {
                this.sessions.delete(token);
            }
        }
        const token = randomBytes(32).toString('base64url');
        const expiresAt = Date.now() + TOKEN_TTL_MS;
        this.sessions.set(token, { urlEmbedId, ctx, expiresAt });
        return { token, expiresAt };
    }

    /** Resolves a tunnel token to the real target URL it's allowed to reach - null if the token
     *  is unknown, expired, or access has since been revoked (re-checked every call). */
    resolve(token: string): { urlEmbedId: string; targetUrl: string } | null {
        this.evictExpired();
        const session = this.sessions.get(token);
        if (!session) {
            return null;
        }
        if (!this.urlEmbeds.canAccess(session.urlEmbedId, session.ctx)) {
            this.sessions.delete(token);
            return null;
        }
        try {
            return { urlEmbedId: session.urlEmbedId, targetUrl: this.urlEmbeds.resolve(session.urlEmbedId) };
        } catch {
            this.sessions.delete(token);
            return null;
        }
    }

    /** Invalidates every active tunnel session for a device - called when a device is unpaired
     *  or its session revoked, so a tunnel doesn't keep working past that point. */
    revokeAllForDevice(deviceId: string): void {
        for (const [token, session] of this.sessions) {
            if (session.ctx.deviceId === deviceId) {
                this.sessions.delete(token);
            }
        }
    }

    private evictExpired(): void {
        const now = Date.now();
        for (const [token, session] of this.sessions) {
            if (session.expiresAt < now) {
                this.sessions.delete(token);
            }
        }
    }
}
