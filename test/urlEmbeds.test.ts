import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { UrlEmbedsService } from '../src/urlEmbeds';
import { ApiError } from '../src/lib/errors';
import type { UrlEmbed } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

async function setup(cacheTtlMs?: number) {
    const adapter = createFakeAdapter();
    const store = new CollectionStore<UrlEmbed>(adapter, 'urlEmbeds');
    await store.init();
    const service = cacheTtlMs === undefined ? new UrlEmbedsService(adapter, store) : new UrlEmbedsService(adapter, store, cacheTtlMs);
    return { adapter, store, service };
}

describe('UrlEmbedsService', () => {
    it('creates an entry and lists it back', async () => {
        const { service } = await setup();
        const created = await service.create({ name: 'Shelly Wohnzimmer', url: 'http://192.168.1.40/status' });

        assert.equal(created.name, 'Shelly Wohnzimmer');
        assert.equal(created.url, 'http://192.168.1.40/status');
        assert.deepEqual(service.list(), [created]);
    });

    it('rejects a non-http(s) url', async () => {
        const { service } = await setup();
        await assert.rejects(
            () => service.create({ name: 'Bad', url: 'javascript:alert(1)' }),
            (err: unknown) => err instanceof ApiError && err.code === 'VALIDATION_ERROR',
        );
    });

    it('rejects a malformed url', async () => {
        const { service } = await setup();
        await assert.rejects(
            () => service.create({ name: 'Bad', url: 'not-a-url' }),
            (err: unknown) => err instanceof ApiError && err.code === 'VALIDATION_ERROR',
        );
    });

    it('rejects an empty name', async () => {
        const { service } = await setup();
        await assert.rejects(
            () => service.create({ name: '   ', url: 'http://192.168.1.40/status' }),
            (err: unknown) => err instanceof ApiError && err.code === 'VALIDATION_ERROR',
        );
    });

    it('updates name and url independently', async () => {
        const { service } = await setup();
        const created = await service.create({ name: 'Original', url: 'http://192.168.1.40/a' });

        const renamed = await service.update(created.id, { name: 'Renamed' });
        assert.equal(renamed.name, 'Renamed');
        assert.equal(renamed.url, 'http://192.168.1.40/a');

        const reUrled = await service.update(created.id, { url: 'http://192.168.1.40/b' });
        assert.equal(reUrled.name, 'Renamed');
        assert.equal(reUrled.url, 'http://192.168.1.40/b');
    });

    it('update rejects an unknown id', async () => {
        const { service } = await setup();
        await assert.rejects(
            () => service.update('does-not-exist', { name: 'X' }),
            (err: unknown) => err instanceof ApiError && err.code === 'NOT_FOUND',
        );
    });

    it('delete removes the entry', async () => {
        const { service } = await setup();
        const created = await service.create({ name: 'Original', url: 'http://192.168.1.40/a' });
        await service.delete(created.id);
        assert.deepEqual(service.list(), []);
    });

    it('resolve returns the real url for a known id and throws NOT_FOUND otherwise', async () => {
        const { service } = await setup();
        const created = await service.create({ name: 'Original', url: 'http://192.168.1.40/a' });

        assert.equal(service.resolve(created.id), 'http://192.168.1.40/a');
        assert.throws(
            () => service.resolve('does-not-exist'),
            (err: unknown) => err instanceof ApiError && err.code === 'NOT_FOUND',
        );
    });

    it('fetchContent proxies the entry url and reports the source content-type', async () => {
        const { service } = await setup();
        const created = await service.create({ name: 'Snapshot', url: 'http://192.168.1.50/snapshot.jpg' });

        const pngBytes = Buffer.from([0x89, 0x50, 0x4e, 0x47]);
        const originalFetch = global.fetch;
        let requestedUrl: string | undefined;
        global.fetch = (async (url: string) => {
            requestedUrl = url;
            return new Response(pngBytes, { status: 200, headers: { 'content-type': 'image/png' } });
        }) as typeof fetch;

        try {
            const content = await service.fetchContent(created.id);
            assert.equal(requestedUrl, 'http://192.168.1.50/snapshot.jpg');
            assert.equal(content.contentType, 'image/png');
            assert.ok(content.buffer.equals(pngBytes));
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('fetchContent throws NOT_FOUND for an unknown id', async () => {
        const { service } = await setup();
        await assert.rejects(
            () => service.fetchContent('does-not-exist'),
            (err: unknown) => err instanceof ApiError && err.code === 'NOT_FOUND',
        );
    });

    it('fetchContent serves a fresh fetch from cache on the next request, without hitting the source again', async () => {
        const { service } = await setup();
        const created = await service.create({ name: 'Snapshot', url: 'http://192.168.1.50/snapshot.jpg' });

        const originalFetch = global.fetch;
        let fetchCount = 0;
        global.fetch = (async () => {
            fetchCount++;
            return new Response(Buffer.from([0x01]), { status: 200, headers: { 'content-type': 'image/jpeg' } });
        }) as typeof fetch;

        try {
            await service.fetchContent(created.id);
            await service.fetchContent(created.id);
            assert.equal(fetchCount, 1, 'the second call within the cache TTL must not hit the source again');
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('fetchContent falls back to the last known-good content instead of erroring when a fresh fetch fails', async () => {
        const { service } = await setup(0); // TTL 0: every call re-fetches
        const created = await service.create({ name: 'Snapshot', url: 'http://192.168.1.50/snapshot.jpg' });

        const goodBytes = Buffer.from([0xaa, 0xbb]);
        const originalFetch = global.fetch;
        let shouldFail = false;
        global.fetch = (async () => {
            if (shouldFail) {
                return new Response(null, { status: 502 });
            }
            return new Response(goodBytes, { status: 200, headers: { 'content-type': 'image/jpeg' } });
        }) as typeof fetch;

        try {
            const first = await service.fetchContent(created.id);
            assert.ok(first.buffer.equals(goodBytes));

            shouldFail = true;
            const second = await service.fetchContent(created.id);
            assert.ok(second.buffer.equals(goodBytes), 'must serve the last known-good content, not throw or return empty');
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('still rejects with SERVER_UNAVAILABLE when a fetch fails and there is no prior successful fetch to fall back to', async () => {
        const { service } = await setup();
        const created = await service.create({ name: 'Snapshot', url: 'http://192.168.1.50/snapshot.jpg' });

        const originalFetch = global.fetch;
        global.fetch = (async () => new Response(null, { status: 502 })) as typeof fetch;

        try {
            await assert.rejects(
                () => service.fetchContent(created.id),
                (err: unknown) => err instanceof ApiError && err.code === 'SERVER_UNAVAILABLE',
            );
        } finally {
            global.fetch = originalFetch;
        }
    });
});
