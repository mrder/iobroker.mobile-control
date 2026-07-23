import { strict as assert } from 'node:assert';
import { CameraService } from '../src/camera';
import { ApiError } from '../src/lib/errors';
import { createFakeAdapter } from './helpers/fakeAdapter';

describe('CameraService', () => {
    it('decodes a data-URL snapshot state directly, with no network call', async () => {
        const adapter = createFakeAdapter();
        const jpegBytes = Buffer.from([0xff, 0xd8, 0xff, 0xd9]);
        await adapter.setForeignStateAsync('camera.0.front.data', {
            val: `data:image/jpeg;base64,${jpegBytes.toString('base64')}`,
            ack: true,
        });

        const camera = new CameraService(adapter);
        const snapshot = await camera.fetchSnapshot('camera.0.front.data');

        assert.equal(snapshot.contentType, 'image/jpeg');
        assert.ok(snapshot.buffer.equals(jpegBytes));
    });

    it('proxy-fetches an http(s) URL snapshot state and reports the source content-type', async () => {
        const adapter = createFakeAdapter();
        await adapter.setForeignStateAsync('camera.0.front.url', { val: 'http://192.168.1.50/snapshot.jpg', ack: true });

        const pngBytes = Buffer.from([0x89, 0x50, 0x4e, 0x47]);
        const originalFetch = global.fetch;
        let requestedUrl: string | undefined;
        global.fetch = (async (url: string) => {
            requestedUrl = url;
            return new Response(pngBytes, { status: 200, headers: { 'content-type': 'image/png' } });
        }) as typeof fetch;

        try {
            const camera = new CameraService(adapter);
            const snapshot = await camera.fetchSnapshot('camera.0.front.url');

            assert.equal(requestedUrl, 'http://192.168.1.50/snapshot.jpg');
            assert.equal(snapshot.contentType, 'image/png');
            assert.ok(snapshot.buffer.equals(pngBytes));
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('rejects with SERVER_UNAVAILABLE when the source URL responds with an error status', async () => {
        const adapter = createFakeAdapter();
        await adapter.setForeignStateAsync('camera.0.front.url', { val: 'http://192.168.1.50/snapshot.jpg', ack: true });

        const originalFetch = global.fetch;
        global.fetch = (async () => new Response(null, { status: 502 })) as typeof fetch;

        try {
            const camera = new CameraService(adapter);
            await assert.rejects(
                () => camera.fetchSnapshot('camera.0.front.url'),
                (err: unknown) => err instanceof ApiError && err.code === 'SERVER_UNAVAILABLE',
            );
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('rejects a state value that is neither a data URL nor an http(s) URL', async () => {
        const adapter = createFakeAdapter();
        await adapter.setForeignStateAsync('camera.0.front.raw', { val: 'not-an-image-or-url', ack: true });

        const camera = new CameraService(adapter);
        await assert.rejects(
            () => camera.fetchSnapshot('camera.0.front.raw'),
            (err: unknown) => err instanceof ApiError && err.code === 'SERVER_UNAVAILABLE',
        );
    });

    it('rejects when the state does not exist at all', async () => {
        const adapter = createFakeAdapter();
        const camera = new CameraService(adapter);
        await assert.rejects(
            () => camera.fetchSnapshot('camera.0.missing'),
            (err: unknown) => err instanceof ApiError && err.code === 'SERVER_UNAVAILABLE',
        );
    });

    it('serves a fresh fetch from cache on the next request, without hitting the source again', async () => {
        const adapter = createFakeAdapter();
        await adapter.setForeignStateAsync('camera.0.front.url', { val: 'http://192.168.1.50/snapshot.jpg', ack: true });

        const originalFetch = global.fetch;
        let fetchCount = 0;
        global.fetch = (async () => {
            fetchCount++;
            return new Response(Buffer.from([0x01]), { status: 200, headers: { 'content-type': 'image/jpeg' } });
        }) as typeof fetch;

        try {
            const camera = new CameraService(adapter);
            await camera.fetchSnapshot('camera.0.front.url');
            await camera.fetchSnapshot('camera.0.front.url');
            assert.equal(fetchCount, 1, 'the second call within the cache TTL must not hit the source again');
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('falls back to the last known-good snapshot instead of erroring when a fresh fetch fails', async () => {
        const adapter = createFakeAdapter();
        await adapter.setForeignStateAsync('camera.0.front.url', { val: 'http://192.168.1.50/snapshot.jpg', ack: true });

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
            const camera = new CameraService(adapter, 0); // TTL 0: every call re-fetches
            const first = await camera.fetchSnapshot('camera.0.front.url');
            assert.ok(first.buffer.equals(goodBytes));

            shouldFail = true;
            const second = await camera.fetchSnapshot('camera.0.front.url');
            assert.ok(second.buffer.equals(goodBytes), 'must serve the last known-good snapshot, not throw or return empty');
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('still rejects with SERVER_UNAVAILABLE when a fetch fails and there is no prior successful fetch to fall back to', async () => {
        const adapter = createFakeAdapter();
        await adapter.setForeignStateAsync('camera.0.front.url', { val: 'http://192.168.1.50/snapshot.jpg', ack: true });

        const originalFetch = global.fetch;
        global.fetch = (async () => new Response(null, { status: 502 })) as typeof fetch;

        try {
            const camera = new CameraService(adapter);
            await assert.rejects(
                () => camera.fetchSnapshot('camera.0.front.url'),
                (err: unknown) => err instanceof ApiError && err.code === 'SERVER_UNAVAILABLE',
            );
        } finally {
            global.fetch = originalFetch;
        }
    });
});
