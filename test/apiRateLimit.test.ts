import assert from 'node:assert/strict';
import http from 'node:http';
import express from 'express';
import { createRateLimitMiddleware } from '../src/api/middleware';
import { RateLimiter } from '../src/security/rateLimiter';

async function withTestServer(limitPerMinute: number, run: (baseUrl: string) => Promise<void>): Promise<void> {
    const app = express();
    app.use(createRateLimitMiddleware(new RateLimiter(limitPerMinute)));
    app.post('/probe', (_req, res) => res.json({ ok: true }));

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

describe('createRateLimitMiddleware (IP-based, guards unauthenticated auth/pairing endpoints)', () => {
    it('allows requests up to the configured per-minute limit', async () => {
        await withTestServer(3, async (baseUrl) => {
            for (let i = 0; i < 3; i++) {
                const res = await fetch(`${baseUrl}/probe`, { method: 'POST' });
                assert.equal(res.status, 200);
            }
        });
    });

    it('rejects the request once the limit is exceeded with 429 RATE_LIMITED', async () => {
        await withTestServer(2, async (baseUrl) => {
            await fetch(`${baseUrl}/probe`, { method: 'POST' });
            await fetch(`${baseUrl}/probe`, { method: 'POST' });
            const blocked = await fetch(`${baseUrl}/probe`, { method: 'POST' });
            assert.equal(blocked.status, 429);
            const body = (await blocked.json()) as { error: string };
            assert.equal(body.error, 'RATE_LIMITED');
        });
    });
});
