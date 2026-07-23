import { ApiError } from '../lib/errors';

export interface Snapshot {
    buffer: Buffer;
    contentType: string;
    timestamp: number;
}

const FETCH_TIMEOUT_MS = 8_000;
const MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
/** How long a freshly-fetched snapshot is served without hitting the source again - matches a
 *  typical dashboard auto-refresh cadence, so a camera widget polling every few seconds doesn't
 *  hammer the actual camera/its adapter on every single request. */
const CACHE_TTL_MS = 5_000;
const DATA_URL_RE = /^data:(image\/[a-zA-Z0-9.+-]+);base64,(.+)$/;

interface CacheEntry {
    snapshot: Snapshot;
    fetchedAt: number;
}

/**
 * Reads a camera snapshot from a regular ioBroker state. There is no single standard across
 * camera adapters (ioBroker.camera, rtsp-camera-ffmpeg, foscam, ...) for how a snapshot is
 * exposed, so this supports the two patterns actually seen in the wild:
 *  1. the state holds a data URL ("data:image/jpeg;base64,...") - decoded directly, no network call.
 *  2. the state holds a plain http(s) URL to a snapshot file/endpoint - fetched server-side and
 *     proxied through this adapter's own auth boundary, so the app never needs direct network
 *     access to the camera or the adapter that serves it (see docs/DEPLOYMENT.md).
 * Any other value shape is reported as SERVER_UNAVAILABLE rather than guessed at.
 *
 * Caches the last successfully-fetched snapshot per state: a request within CACHE_TTL_MS of the
 * last fetch is served straight from the cache (no network call at all), and - more importantly -
 * if a *fresh* fetch fails (source down, timeout, bad/empty response), the last known-good cached
 * snapshot is served instead of an error, so the app never flashes a broken image for a
 * momentary hiccup. This does NOT detect a genuinely broken frame the source itself returns
 * successfully (e.g. an all-black/all-white placeholder JPEG with a normal HTTP 200) - that would
 * need real image decoding/pixel analysis, out of scope here; this only covers "didn't load at
 * all" via the fallback-to-last-known-good behavior.
 */
export class CameraService {
    private readonly cache = new Map<string, CacheEntry>();

    constructor(
        private readonly adapter: ioBroker.Adapter,
        private readonly cacheTtlMs: number = CACHE_TTL_MS,
    ) {}

    async fetchSnapshot(stateId: string): Promise<Snapshot> {
        const cached = this.cache.get(stateId);
        if (cached && Date.now() - cached.fetchedAt < this.cacheTtlMs) {
            return cached.snapshot;
        }

        try {
            const fresh = await this.fetchFresh(stateId);
            this.cache.set(stateId, { snapshot: fresh, fetchedAt: Date.now() });
            return fresh;
        } catch (err) {
            if (cached) {
                this.adapter.log.debug(
                    `mobile-control: camera snapshot fetch failed for ${stateId} (${(err as Error).message}) - serving last known-good snapshot instead`,
                );
                return cached.snapshot;
            }
            throw err;
        }
    }

    private async fetchFresh(stateId: string): Promise<Snapshot> {
        const state = await this.adapter.getForeignStateAsync(stateId);
        if (!state || typeof state.val !== 'string' || state.val.length === 0) {
            throw new ApiError('SERVER_UNAVAILABLE', 'camera state has no snapshot value');
        }

        const dataUrlMatch = DATA_URL_RE.exec(state.val);
        if (dataUrlMatch) {
            const buffer = Buffer.from(dataUrlMatch[2], 'base64');
            if (buffer.length === 0 || buffer.length > MAX_SNAPSHOT_BYTES) {
                throw new ApiError('SERVER_UNAVAILABLE', 'camera snapshot is empty or too large');
            }
            return { buffer, contentType: dataUrlMatch[1], timestamp: state.ts };
        }

        if (state.val.startsWith('http://') || state.val.startsWith('https://')) {
            return this.fetchFromUrl(state.val, state.ts);
        }

        throw new ApiError('SERVER_UNAVAILABLE', 'camera state value is neither a data URL nor an http(s) URL');
    }

    private async fetchFromUrl(url: string, timestamp: number): Promise<Snapshot> {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
        try {
            const response = await fetch(url, { signal: controller.signal });
            if (!response.ok) {
                throw new ApiError('SERVER_UNAVAILABLE', `camera snapshot source returned HTTP ${response.status}`);
            }
            const contentType = response.headers.get('content-type') ?? 'image/jpeg';
            const arrayBuffer = await response.arrayBuffer();
            if (arrayBuffer.byteLength === 0 || arrayBuffer.byteLength > MAX_SNAPSHOT_BYTES) {
                throw new ApiError('SERVER_UNAVAILABLE', 'camera snapshot is empty or too large');
            }
            return { buffer: Buffer.from(arrayBuffer), contentType, timestamp };
        } catch (err) {
            if (err instanceof ApiError) {
                throw err;
            }
            throw new ApiError('SERVER_UNAVAILABLE', `failed to fetch camera snapshot: ${(err as Error).message}`);
        } finally {
            clearTimeout(timer);
        }
    }
}
