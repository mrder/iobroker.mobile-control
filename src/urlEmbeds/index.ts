import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { UrlEmbed } from '../lib/types';

export interface ProxiedContent {
    buffer: Buffer;
    contentType: string;
    timestamp: number;
}

const FETCH_TIMEOUT_MS = 8_000;
const MAX_CONTENT_BYTES = 8 * 1024 * 1024;
const CACHE_TTL_MS = 5_000;

interface CacheEntry {
    content: ProxiedContent;
    fetchedAt: number;
}

/**
 * Admin-managed allowlist of external URLs a paired device may embed in a dashboard widget (a
 * camera-style screenshot endpoint, a small local device web UI, ...). This is the deliberately
 * scoped alternative to a fully generic URL proxy: the app only ever sees {id, name} via list(),
 * and every actual network access - both the proxied fetchContent() for image-style embeds and
 * resolve() for WebView-style embeds - happens by an id the admin already approved, never by a
 * client-supplied URL. Letting any paired device fetch/navigate to an arbitrary URL of its own
 * choosing would turn this adapter into an open SSRF relay into the LAN, which is exactly what
 * this allowlist is meant to prevent.
 *
 * fetchContent() uses the same cache + fallback-to-last-known-good pattern as CameraService (see
 * its own docs for the reasoning) - a momentary source hiccup should not flash a broken image.
 */
export class UrlEmbedsService {
    private readonly cache = new Map<string, CacheEntry>();

    constructor(
        private readonly adapter: ioBroker.Adapter,
        private readonly store: CollectionStore<UrlEmbed>,
        private readonly cacheTtlMs: number = CACHE_TTL_MS,
    ) {}

    list(): UrlEmbed[] {
        return this.store.list();
    }

    get(id: string): UrlEmbed | undefined {
        return this.store.get(id);
    }

    async create(data: { name: string; url: string }): Promise<UrlEmbed> {
        const name = UrlEmbedsService.validateName(data.name);
        const url = UrlEmbedsService.validateUrl(data.url);
        const embed: UrlEmbed = { id: uuid(), name, url, createdAt: Date.now() };
        await this.store.put(embed);
        return embed;
    }

    async update(id: string, patch: Partial<{ name: string; url: string }>): Promise<UrlEmbed> {
        const existing = this.store.get(id);
        if (!existing) {
            throw new ApiError('NOT_FOUND', `url embed ${id} not found`);
        }
        const updated: UrlEmbed = {
            ...existing,
            name: patch.name !== undefined ? UrlEmbedsService.validateName(patch.name) : existing.name,
            url: patch.url !== undefined ? UrlEmbedsService.validateUrl(patch.url) : existing.url,
        };
        await this.store.put(updated);
        this.cache.delete(id);
        return updated;
    }

    async delete(id: string): Promise<void> {
        await this.store.delete(id);
        this.cache.delete(id);
    }

    /** Resolves the real target URL for a single admin-approved id - used by WebView-style
     *  embeds, which need to navigate the LAN directly (a full page's own relative sub-resource
     *  requests can't realistically be rewritten through a single-resource proxy). The security
     *  property that matters is preserved regardless: the client picks an id, never a URL. */
    resolve(id: string): string {
        const embed = this.store.get(id);
        if (!embed) {
            throw new ApiError('NOT_FOUND', `url embed ${id} not found`);
        }
        return embed.url;
    }

    async fetchContent(id: string): Promise<ProxiedContent> {
        const embed = this.store.get(id);
        if (!embed) {
            throw new ApiError('NOT_FOUND', `url embed ${id} not found`);
        }

        const cached = this.cache.get(id);
        if (cached && Date.now() - cached.fetchedAt < this.cacheTtlMs) {
            return cached.content;
        }

        try {
            const fresh = await UrlEmbedsService.fetchFresh(embed.url);
            this.cache.set(id, { content: fresh, fetchedAt: Date.now() });
            return fresh;
        } catch (err) {
            if (cached) {
                this.adapter.log.debug(
                    `mobile-control: url embed content fetch failed for ${id} (${(err as Error).message}) - serving last known-good content instead`,
                );
                return cached.content;
            }
            throw err;
        }
    }

    private static validateName(raw: string): string {
        const name = raw.trim();
        if (!name) {
            throw new ApiError('VALIDATION_ERROR', 'name must not be empty');
        }
        return name;
    }

    private static validateUrl(raw: string): string {
        let parsed: URL;
        try {
            parsed = new URL(raw);
        } catch {
            throw new ApiError('VALIDATION_ERROR', 'url must be a valid absolute URL');
        }
        if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
            throw new ApiError('VALIDATION_ERROR', 'url must use http or https');
        }
        return parsed.toString();
    }

    private static async fetchFresh(url: string): Promise<ProxiedContent> {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
        try {
            const response = await fetch(url, { signal: controller.signal });
            if (!response.ok) {
                throw new ApiError('SERVER_UNAVAILABLE', `url embed source returned HTTP ${response.status}`);
            }
            const contentType = response.headers.get('content-type') ?? 'application/octet-stream';
            const arrayBuffer = await response.arrayBuffer();
            if (arrayBuffer.byteLength === 0 || arrayBuffer.byteLength > MAX_CONTENT_BYTES) {
                throw new ApiError('SERVER_UNAVAILABLE', 'url embed content is empty or too large');
            }
            return { buffer: Buffer.from(arrayBuffer), contentType, timestamp: Date.now() };
        } catch (err) {
            if (err instanceof ApiError) {
                throw err;
            }
            throw new ApiError('SERVER_UNAVAILABLE', `failed to fetch url embed content: ${(err as Error).message}`);
        } finally {
            clearTimeout(timer);
        }
    }
}
