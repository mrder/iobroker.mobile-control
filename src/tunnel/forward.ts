import { ApiError } from '../lib/errors';

// Generous on purpose: a device UI's own live-data connection (long-polling, e.g. socket.io's
// fallback transport when a real WebSocket upgrade isn't possible - this tunnel doesn't relay
// WebSocket frames, see docs/TODO.md) legitimately holds a request open for tens of seconds
// waiting for the next event, with nothing wrong happening. The original 10s value cut those off
// mid-wait, which the page's own client saw as a dropped connection and kept reconnecting -
// confirmed live. Still bounded, not infinite, so a genuinely hung target can't leak a request.
const FETCH_TIMEOUT_MS = 45_000;
const MAX_RESPONSE_BYTES = 20 * 1024 * 1024;

/** Deliberately narrow allowlists in both directions - the tunnel forwards enough for a typical
 *  device web UI to function (styling, scripts, forms, cookies, redirects), not an unfiltered
 *  header pass-through that could leak something unexpected either way. */
const REQUEST_HEADER_ALLOWLIST = [
    'accept',
    'accept-language',
    'content-type',
    'cookie',
    'referer',
    'user-agent',
    'x-requested-with',
    'origin',
];
const RESPONSE_HEADER_ALLOWLIST = ['content-type', 'cache-control', 'location', 'expires', 'last-modified', 'etag'];

export interface ForwardResult {
    status: number;
    headers: Record<string, string>;
    setCookies: string[];
    body: Buffer;
}

/**
 * Forwards one HTTP request to [targetOrigin] + [path] and returns the raw response - the actual
 * transport hop of the tunnel (see TunnelService for the token/authorization side). [path] comes
 * from the client (the Android-side local proxy relaying what the WebView asked for), so the
 * critical invariant is: it can only ever resolve to a URL still inside targetOrigin. new URL()
 * would otherwise happily resolve an absolute URL in [path] to somewhere else entirely (ignoring
 * the base) - the explicit origin comparison below is what actually blocks that, not the URL
 * constructor's base-resolution behavior.
 */
export async function forwardTunnelRequest(params: {
    targetOrigin: string;
    path: string;
    method: string;
    headers: Record<string, string | string[] | undefined>;
    body: Buffer | null;
}): Promise<ForwardResult> {
    const origin = new URL(params.targetOrigin);
    let target: URL;
    try {
        target = new URL(params.path, origin);
    } catch {
        throw new ApiError('VALIDATION_ERROR', 'invalid tunnel path');
    }
    if (target.protocol !== origin.protocol || target.host !== origin.host) {
        throw new ApiError('VALIDATION_ERROR', 'tunnel path must stay within the approved origin');
    }

    const forwardHeaders: Record<string, string> = {};
    for (const name of REQUEST_HEADER_ALLOWLIST) {
        const value = params.headers[name];
        if (typeof value === 'string') {
            forwardHeaders[name] = value;
        }
    }

    const method = params.method.toUpperCase();
    const hasBody = !!params.body && params.body.length > 0 && method !== 'GET' && method !== 'HEAD';

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
    try {
        const response = await fetch(target.toString(), {
            method,
            headers: forwardHeaders,
            body: hasBody ? (params.body as Buffer) : undefined,
            // The WebView is what should follow a redirect (it re-navigates and the next request
            // goes through the tunnel again the same way) - resolving it silently here would hide
            // the real final URL from the page/WebView.
            redirect: 'manual',
            signal: controller.signal,
        });
        const arrayBuffer = await response.arrayBuffer();
        if (arrayBuffer.byteLength > MAX_RESPONSE_BYTES) {
            throw new ApiError('SERVER_UNAVAILABLE', 'tunnel response exceeded the size limit');
        }

        const headers: Record<string, string> = {};
        for (const name of RESPONSE_HEADER_ALLOWLIST) {
            const value = response.headers.get(name);
            if (value !== null) {
                headers[name] = value;
            }
        }
        const setCookies =
            typeof response.headers.getSetCookie === 'function'
                ? response.headers.getSetCookie()
                : (response.headers.get('set-cookie')?.split(/,(?=[^;]+?=)/).map((c) => c.trim()) ?? []);

        return { status: response.status, headers, setCookies, body: Buffer.from(arrayBuffer) };
    } catch (err) {
        if (err instanceof ApiError) {
            throw err;
        }
        throw new ApiError('SERVER_UNAVAILABLE', `tunnel request failed: ${(err as Error).message}`);
    } finally {
        clearTimeout(timer);
    }
}
