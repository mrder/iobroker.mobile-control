import assert from 'node:assert/strict';
import http from 'node:http';
import express from 'express';
import { createPortalGateMiddleware } from '../src/api/portalGate';
import { AbuseGuard } from '../src/security/abuseGuard';

function fakeAdapter(): ioBroker.Adapter {
    return { log: { warn: () => undefined } } as unknown as ioBroker.Adapter;
}

async function withTestServer(
    portalKey: string,
    abuseGuard: AbuseGuard,
    exemptPaths: string[],
    run: (baseUrl: string) => Promise<void>,
): Promise<void> {
    const app = express();
    app.use(createPortalGateMiddleware(() => portalKey, abuseGuard, fakeAdapter(), exemptPaths));
    app.get('/anything', (_req, res) => res.json({ ok: true }));
    app.get('/api/v1/portal-key', (_req, res) => res.json({ portalKey }));

    const server = http.createServer(app);
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    if (address === null || typeof address === 'string') {
        throw new Error('failed to bind test server');
    }
    try {
        await run(`http://127.0.0.1:${address.port}`);
    } finally {
        await new Promise<void>((resolve) => server.close(() => resolve()));
    }
}

function basicAuthHeader(password: string, username = 'device'): string {
    return 'Basic ' + Buffer.from(`${username}:${password}`).toString('base64');
}

describe('createPortalGateMiddleware', () => {
    it('rejects a request with no Authorization header at all, for any path', async () => {
        await withTestServer('correct-key', new AbuseGuard({ maxFailures: 10, windowMs: 60_000, blockMs: 60_000 }), [], async (baseUrl) => {
            const res = await fetch(`${baseUrl}/anything`);
            assert.equal(res.status, 401);
            assert.equal(res.headers.get('www-authenticate'), 'Basic realm="mobile-control"');

            const missingToo = await fetch(`${baseUrl}/this/path/does/not/exist`);
            assert.equal(missingToo.status, 401, 'a nonexistent path gets the exact same 401 as a real one');
        });
    });

    it('rejects a request with the wrong password', async () => {
        await withTestServer('correct-key', new AbuseGuard({ maxFailures: 10, windowMs: 60_000, blockMs: 60_000 }), [], async (baseUrl) => {
            const res = await fetch(`${baseUrl}/anything`, { headers: { authorization: basicAuthHeader('wrong-key') } });
            assert.equal(res.status, 401);
        });
    });

    it('allows a request with the correct password, regardless of the (ignored) username', async () => {
        await withTestServer('correct-key', new AbuseGuard({ maxFailures: 10, windowMs: 60_000, blockMs: 60_000 }), [], async (baseUrl) => {
            const res = await fetch(`${baseUrl}/anything`, { headers: { authorization: basicAuthHeader('correct-key', 'whoever') } });
            assert.equal(res.status, 200);
        });
    });

    it('lets an exempt path through with no Authorization header at all', async () => {
        await withTestServer('correct-key', new AbuseGuard({ maxFailures: 10, windowMs: 60_000, blockMs: 60_000 }), ['/api/v1/portal-key'], async (baseUrl) => {
            const res = await fetch(`${baseUrl}/api/v1/portal-key`);
            assert.equal(res.status, 200);

            // a DIFFERENT path is still fully gated
            const other = await fetch(`${baseUrl}/anything`);
            assert.equal(other.status, 401);
        });
    });

    it('blocks an IP after repeated wrong-key attempts, via the provided AbuseGuard', async () => {
        const guard = new AbuseGuard({ maxFailures: 2, windowMs: 60_000, blockMs: 60_000 });
        await withTestServer('correct-key', guard, [], async (baseUrl) => {
            await fetch(`${baseUrl}/anything`, { headers: { authorization: basicAuthHeader('wrong-1') } });
            await fetch(`${baseUrl}/anything`, { headers: { authorization: basicAuthHeader('wrong-2') } });

            // now blocked outright, even with the CORRECT key
            const res = await fetch(`${baseUrl}/anything`, { headers: { authorization: basicAuthHeader('correct-key') } });
            assert.equal(res.status, 401);
        });
    });
});
