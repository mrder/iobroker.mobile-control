import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { UrlEmbedsService } from '../src/urlEmbeds';
import { TunnelService } from '../src/tunnel';
import { forwardTunnelRequest } from '../src/tunnel/forward';
import { ApiError } from '../src/lib/errors';
import type { UrlEmbed, UrlEmbedAccessRule } from '../src/lib/types';
import type { AuthContext } from '../src/authorization';
import { createFakeAdapter } from './helpers/fakeAdapter';

async function setup() {
    const adapter = createFakeAdapter();
    const store = new CollectionStore<UrlEmbed>(adapter, 'urlEmbeds');
    const accessStore = new CollectionStore<UrlEmbedAccessRule>(adapter, 'urlEmbedAccessRules');
    await Promise.all([store.init(), accessStore.init()]);
    const urlEmbeds = new UrlEmbedsService(adapter, store, accessStore);
    const tunnel = new TunnelService(urlEmbeds);
    return { urlEmbeds, tunnel };
}

const ctx: AuthContext = { userId: 'user-1', deviceId: 'device-1', roleId: 'viewer' };

describe('TunnelService', () => {
    it('issue() rejects an unknown embed with NOT_FOUND', async () => {
        const { tunnel } = await setup();
        assert.throws(
            () => tunnel.issue('does-not-exist', ctx),
            (err: unknown) => err instanceof ApiError && err.code === 'NOT_FOUND',
        );
    });

    it('issue() rejects when the device has no access rule granting the embed', async () => {
        const { tunnel, urlEmbeds } = await setup();
        const embed = await urlEmbeds.create({ name: 'Shelly', url: 'http://192.168.1.40/status' });
        assert.throws(
            () => tunnel.issue(embed.id, ctx),
            (err: unknown) => err instanceof ApiError && err.code === 'READ_FORBIDDEN',
        );
    });

    it('a token issued for a granted embed resolves to its real target URL', async () => {
        const { tunnel, urlEmbeds } = await setup();
        const embed = await urlEmbeds.create({ name: 'Shelly', url: 'http://192.168.1.40/status' });
        await urlEmbeds.createAccessRule({ urlEmbedId: embed.id, roleId: 'viewer', userId: null, deviceId: null, deny: false });

        const { token, expiresAt } = tunnel.issue(embed.id, ctx);
        assert.ok(token.length > 20);
        assert.ok(expiresAt > Date.now());

        const resolved = tunnel.resolve(token);
        assert.equal(resolved?.urlEmbedId, embed.id);
        assert.equal(resolved?.targetUrl, 'http://192.168.1.40/status');
    });

    it('resolve() returns null for an unknown token', async () => {
        const { tunnel } = await setup();
        assert.equal(tunnel.resolve('not-a-real-token'), null);
    });

    it('resolve() re-checks access on every call - revoking mid-session cuts the tunnel off immediately', async () => {
        const { tunnel, urlEmbeds } = await setup();
        const embed = await urlEmbeds.create({ name: 'Shelly', url: 'http://192.168.1.40/status' });
        const rule = await urlEmbeds.createAccessRule({
            urlEmbedId: embed.id,
            roleId: 'viewer',
            userId: null,
            deviceId: null,
            deny: false,
        });
        const { token } = tunnel.issue(embed.id, ctx);
        assert.ok(tunnel.resolve(token));

        await urlEmbeds.deleteAccessRule(rule.id);

        assert.equal(tunnel.resolve(token), null);
    });

    it('issuing a second token for the same (device, embed) invalidates the first', async () => {
        const { tunnel, urlEmbeds } = await setup();
        const embed = await urlEmbeds.create({ name: 'Shelly', url: 'http://192.168.1.40/status' });
        await urlEmbeds.createAccessRule({ urlEmbedId: embed.id, roleId: 'viewer', userId: null, deviceId: null, deny: false });

        const first = tunnel.issue(embed.id, ctx);
        const second = tunnel.issue(embed.id, ctx);

        assert.notEqual(first.token, second.token);
        assert.equal(tunnel.resolve(first.token), null, 'the first token must no longer resolve');
        assert.ok(tunnel.resolve(second.token));
    });

    it('revokeAllForDevice invalidates every token issued to that device, not other devices', async () => {
        const { tunnel, urlEmbeds } = await setup();
        const embedA = await urlEmbeds.create({ name: 'A', url: 'http://192.168.1.40/a' });
        const embedB = await urlEmbeds.create({ name: 'B', url: 'http://192.168.1.41/b' });
        await urlEmbeds.createAccessRule({ urlEmbedId: embedA.id, roleId: 'viewer', userId: null, deviceId: null, deny: false });
        await urlEmbeds.createAccessRule({ urlEmbedId: embedB.id, roleId: 'viewer', userId: null, deviceId: null, deny: false });

        const tokenA = tunnel.issue(embedA.id, ctx).token;
        const otherDeviceCtx: AuthContext = { ...ctx, deviceId: 'device-2' };
        const tokenB = tunnel.issue(embedB.id, otherDeviceCtx).token;

        tunnel.revokeAllForDevice('device-1');

        assert.equal(tunnel.resolve(tokenA), null);
        assert.ok(tunnel.resolve(tokenB), 'a different device\'s token must be unaffected');
    });
});

describe('forwardTunnelRequest', () => {
    it('forwards the method, an allowlisted request header and the body to the resolved origin', async () => {
        const originalFetch = global.fetch;
        let seenUrl = '';
        let seenMethod = '';
        let seenHeaders: Record<string, string> = {};
        let seenBody: unknown;
        global.fetch = (async (url: string, init?: RequestInit) => {
            seenUrl = url;
            seenMethod = init?.method ?? 'GET';
            seenHeaders = (init?.headers as Record<string, string>) ?? {};
            seenBody = init?.body;
            return new Response('hello', { status: 200, headers: { 'content-type': 'text/plain' } });
        }) as typeof fetch;

        try {
            const result = await forwardTunnelRequest({
                targetOrigin: 'http://192.168.1.40:8097',
                path: '/relay/0?turn=on',
                method: 'post',
                headers: { 'content-type': 'application/json', 'x-not-allowlisted': 'nope' },
                body: Buffer.from('{"on":true}'),
            });

            assert.equal(seenUrl, 'http://192.168.1.40:8097/relay/0?turn=on');
            assert.equal(seenMethod, 'POST');
            assert.equal(seenHeaders['content-type'], 'application/json');
            assert.equal(seenHeaders['x-not-allowlisted'], undefined, 'headers outside the allowlist must not be forwarded');
            assert.equal(Buffer.from(seenBody as ArrayBuffer).toString(), '{"on":true}');
            assert.equal(result.status, 200);
            assert.equal(result.body.toString(), 'hello');
            assert.equal(result.headers['content-type'], 'text/plain');
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('never sends a body for GET/HEAD even if one was provided', async () => {
        const originalFetch = global.fetch;
        let sawBody = true;
        global.fetch = (async (_url: string, init?: RequestInit) => {
            sawBody = init?.body !== undefined;
            return new Response(null, { status: 204 });
        }) as typeof fetch;

        try {
            await forwardTunnelRequest({
                targetOrigin: 'http://192.168.1.40',
                path: '/x',
                method: 'GET',
                headers: {},
                body: Buffer.from('should be ignored'),
            });
            assert.equal(sawBody, false);
        } finally {
            global.fetch = originalFetch;
        }
    });

    it('rejects a path that resolves outside the approved origin (anti-SSRF)', async () => {
        await assert.rejects(
            () =>
                forwardTunnelRequest({
                    targetOrigin: 'http://192.168.1.40',
                    path: 'http://evil.example.com/steal',
                    method: 'GET',
                    headers: {},
                    body: null,
                }),
            (err: unknown) => err instanceof ApiError && err.code === 'VALIDATION_ERROR',
        );
    });

    it('rejects a path escaping via a different port on the same host (still a different origin)', async () => {
        await assert.rejects(
            () =>
                forwardTunnelRequest({
                    targetOrigin: 'http://192.168.1.40:80',
                    path: 'http://192.168.1.40:9999/x',
                    method: 'GET',
                    headers: {},
                    body: null,
                }),
            (err: unknown) => err instanceof ApiError && err.code === 'VALIDATION_ERROR',
        );
    });

    it('surfaces a fetch failure as SERVER_UNAVAILABLE', async () => {
        const originalFetch = global.fetch;
        global.fetch = (async () => {
            throw new Error('connection refused');
        }) as typeof fetch;

        try {
            await assert.rejects(
                () =>
                    forwardTunnelRequest({
                        targetOrigin: 'http://192.168.1.40',
                        path: '/x',
                        method: 'GET',
                        headers: {},
                        body: null,
                    }),
                (err: unknown) => err instanceof ApiError && err.code === 'SERVER_UNAVAILABLE',
            );
        } finally {
            global.fetch = originalFetch;
        }
    });
});
