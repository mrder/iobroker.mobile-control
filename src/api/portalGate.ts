import type { Request, Response, NextFunction } from 'express';
import { sha256Hex, safeEqualHex } from '../security/tokens';
import type { AbuseGuard } from '../security/abuseGuard';

const REALM = 'mobile-control';

/**
 * A shared secret required on EVERY request to this server - literally everything, including
 * pairing/auth/app-download, sits behind this. Live-requested hardening (2026-07-30): without it,
 * an internet scanner or drive-by bot got a real (if harmless) response from every path, revealing
 * "there's an Express server here" and giving pairing/login/app-download something to poke at at
 * all. With this mounted first, an unauthenticated caller gets the exact same 401 for every path -
 * no way to distinguish a valid path from a typo, let alone reach any real logic.
 *
 * HTTP Basic Auth specifically (not a custom header) so a browser visiting e.g. /app gets a native
 * password prompt for free, no custom login page needed - the username is ignored, only the
 * password is checked against the configured portal key. The Android app sends the same header on
 * every request via a client-wide OkHttp interceptor (see RealtimeWebSocketClient/NetworkModule on
 * the app side), populated from the key embedded in the pairing QR payload.
 *
 * Reuses the same AbuseGuard instance that already watches pairing/auth failures, under its own
 * "portal_key" reason label - repeated wrong-key attempts from an IP get temporarily blocked
 * exactly like repeated wrong pairing secrets already do. This only ever helps against brute-force
 * guessing, not a real volumetric DDoS (that needs network-level mitigation, not an app-layer
 * check) - see the accompanying docs/SECURITY.md note.
 *
 * [exemptPaths] is deliberately tiny and exact-match only - today just GET /api/v1/portal-key
 * (see router.ts), the one-time bootstrap route a device paired BEFORE this gate existed uses to
 * learn the new portal key using its EXISTING, still-fully-required Bearer token + request
 * signature (requireAuth + requireSignature, unaffected by this exemption). Without this, rolling
 * out the gate would instantly lock out every already-paired device with no way back in short of
 * a full unpair/re-pair.
 */
export function createPortalGateMiddleware(
    getPortalKey: () => string,
    abuseGuard: AbuseGuard,
    adapter: ioBroker.Adapter,
    exemptPaths: readonly string[] = [],
) {
    return (req: Request, res: Response, next: NextFunction): void => {
        if (exemptPaths.includes(req.path)) {
            next();
            return;
        }

        const ip = req.ip ?? req.socket.remoteAddress ?? 'unknown';

        if (abuseGuard.isBlocked(ip)) {
            res.status(401).set('WWW-Authenticate', `Basic realm="${REALM}"`).end();
            return;
        }

        const provided = extractBasicAuthPassword(req.headers.authorization);
        const portalKey = getPortalKey();
        // Both sides hashed to a fixed-length digest first so the timing-safe comparison never
        // leaks the real key's length via how long the raw string comparison would otherwise take.
        if (provided !== null && safeEqualHex(sha256Hex(provided), sha256Hex(portalKey))) {
            abuseGuard.recordSuccess(ip);
            next();
            return;
        }

        const justBlocked = abuseGuard.recordFailure(ip, 'portal_key');
        if (justBlocked) {
            adapter.log.warn(`mobile-control: repeated invalid portal-key attempts from ${ip} - temporarily blocking`);
        }
        res.status(401).set('WWW-Authenticate', `Basic realm="${REALM}"`).end();
    };
}

function extractBasicAuthPassword(header: string | undefined): string | null {
    if (!header?.startsWith('Basic ')) {
        return null;
    }
    let decoded: string;
    try {
        decoded = Buffer.from(header.slice('Basic '.length), 'base64').toString('utf8');
    } catch {
        return null;
    }
    const colonIndex = decoded.indexOf(':');
    if (colonIndex === -1) {
        return null;
    }
    return decoded.slice(colonIndex + 1);
}
