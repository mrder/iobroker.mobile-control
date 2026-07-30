import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import express from 'express';
import { createAppDistributionRouter } from '../src/api/appDistributionRouter';
import { RateLimiter } from '../src/security/rateLimiter';

function fakeAdapter(): ioBroker.Adapter {
    return { log: { warn: () => undefined, error: () => undefined } } as unknown as ioBroker.Adapter;
}

async function withTestServer(
    apkPath: string | undefined,
    run: (baseUrl: string) => Promise<void>,
): Promise<void> {
    const app = express();
    app.use(
        createAppDistributionRouter({
            adapter: fakeAdapter(),
            publicUrl: 'https://smart.example.test',
            downloadRateLimiter: new RateLimiter(30),
            apkPath,
        }),
    );

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

describe('createAppDistributionRouter (unauthenticated APK install page/download)', () => {
    it('GET /app/download 404s when no APK is bundled with this installation', async () => {
        const missingPath = path.join(os.tmpdir(), `mobile-control-test-missing-${Date.now()}.apk`);
        await withTestServer(missingPath, async (baseUrl) => {
            const res = await fetch(`${baseUrl}/app/download`);
            assert.equal(res.status, 404);
        });
    });

    it('GET /app 503s with a clear message when no APK is bundled', async () => {
        const missingPath = path.join(os.tmpdir(), `mobile-control-test-missing-${Date.now()}.apk`);
        await withTestServer(missingPath, async (baseUrl) => {
            const res = await fetch(`${baseUrl}/app`);
            assert.equal(res.status, 503);
            const body = await res.text();
            assert.match(body, /nicht verfügbar/);
        });
    });

    it('GET /app/download streams the bundled file with an .apk filename once one exists', async () => {
        const apkPath = path.join(os.tmpdir(), `mobile-control-test-${Date.now()}.apk`);
        fs.writeFileSync(apkPath, Buffer.from('fake apk bytes'));
        try {
            await withTestServer(apkPath, async (baseUrl) => {
                const res = await fetch(`${baseUrl}/app/download`);
                assert.equal(res.status, 200);
                assert.match(res.headers.get('content-disposition') ?? '', /\.apk"?$/);
                const body = Buffer.from(await res.arrayBuffer());
                assert.equal(body.toString(), 'fake apk bytes');
            });
        } finally {
            fs.rmSync(apkPath, { force: true });
        }
    });

    it('GET /app renders an install page with a QR code and the public download URL once an APK exists', async () => {
        const apkPath = path.join(os.tmpdir(), `mobile-control-test-${Date.now()}.apk`);
        fs.writeFileSync(apkPath, Buffer.from('fake apk bytes'));
        try {
            await withTestServer(apkPath, async (baseUrl) => {
                const res = await fetch(`${baseUrl}/app`);
                assert.equal(res.status, 200);
                const html = await res.text();
                assert.match(html, /<img src="data:image\/png;base64,/);
                assert.match(html, /https:\/\/smart\.example\.test\/app\/download/);
            });
        } finally {
            fs.rmSync(apkPath, { force: true });
        }
    });

    it('rate-limits repeated requests from the same caller', async () => {
        const missingPath = path.join(os.tmpdir(), `mobile-control-test-missing-${Date.now()}.apk`);
        const app = express();
        app.use(
            createAppDistributionRouter({
                adapter: fakeAdapter(),
                publicUrl: 'https://smart.example.test',
                downloadRateLimiter: new RateLimiter(2),
                apkPath: missingPath,
            }),
        );
        const server = http.createServer(app);
        await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
        const address = server.address();
        if (address === null || typeof address === 'string') {
            throw new Error('failed to bind test server');
        }
        const baseUrl = `http://127.0.0.1:${address.port}`;
        try {
            await fetch(`${baseUrl}/app/download`);
            await fetch(`${baseUrl}/app/download`);
            const blocked = await fetch(`${baseUrl}/app/download`);
            assert.equal(blocked.status, 429);
        } finally {
            await new Promise<void>((resolve) => server.close(() => resolve()));
        }
    });
});
