/* eslint-disable @typescript-eslint/no-var-requires, no-console */
/**
 * Real integration smoke test: instantiates the ACTUAL compiled adapter class (not a fake
 * service wired up by hand, like the mocha unit tests) against @iobroker/testing's mock
 * ioBroker environment, runs its real onReady(), then drives a full pairing -> approval ->
 * token issuance -> authenticated REST call flow through the REAL Express/HTTP server it
 * starts.
 *
 * This runs as a standalone script via `ts-node` (see package.json "test:integration"), NOT
 * through mocha: mocha decides per-file whether to load a .ts spec via Node's native ESM
 * loader or ts-node's CommonJS require() hook based on whether the file contains
 * import/export syntax, and under its ESM path `require.cache` is unavailable (confirmed by
 * hand) - which this script fundamentally depends on to swap out @iobroker/adapter-core
 * before requiring src/main. Running it as a plain ts-node script sidesteps that entirely.
 */
const assert = require('node:assert').strict;
const { generateKeyPairSync, sign: cryptoSign } = require('node:crypto');
const http = require('node:http');
const { MockDatabase } = require('@iobroker/testing');
const { mockAdapterCore } = require('@iobroker/testing/build/tests/unit/mocks/mockAdapterCore');

const TEST_PORT = 18099;
const BASE_URL = `http://127.0.0.1:${TEST_PORT}`;
const ADAPTER_CORE_PATH = require.resolve('@iobroker/adapter-core');

interface MockAdapterLike {
    config: Record<string, unknown>;
    namespace: string;
    readyHandler?: () => Promise<void> | void;
    messageHandler?: (obj: Record<string, unknown>) => void;
    unloadHandler?: (callback: () => void) => void;
    sendTo: (...args: unknown[]) => void;
}

let passed = 0;

async function step(name: string, fn: () => Promise<void>): Promise<void> {
    try {
        await fn();
        passed++;
        console.log(`  ok - ${name}`);
    } catch (err) {
        console.error(`  FAIL - ${name}`);
        throw err;
    }
}

async function main(): Promise<void> {
    let adapter!: MockAdapterLike;
    const pendingAdminCalls = new Map<string, (result: unknown) => void>();

    async function callAdmin<T>(command: string, message: Record<string, unknown> = {}): Promise<T> {
        return new Promise<T>((resolve) => {
            pendingAdminCalls.set(command, resolve as (result: unknown) => void);
            adapter.messageHandler!({ command, message, from: 'system.adapter.test.0', callback: { id: 1, message: {} } });
        });
    }

    const db = new MockDatabase();
    const mocked = mockAdapterCore(db, {
        onAdapterCreated: (created: MockAdapterLike) => {
            adapter = created;
            adapter.config = {
                port: TEST_PORT,
                bindAddress: '127.0.0.1',
                publicUrl: BASE_URL,
                jwtSecret: 'integration-test-secret',
                accessTokenTtlMinutes: 10,
                refreshTokenTtlDays: 30,
                pairingTtlMinutes: 10,
                requireAdminApproval: true,
                rateLimitPerMinute: 60,
                authRateLimitPerMinute: 60,
                abuseBlockThreshold: 3,
                abuseBlockMinutes: 30,
                localOnlyByDefault: false,
                historyInstance: 'history.0',
            };
            adapter.namespace = 'mobile-control.0';
            // sendTo() is overloaded for two different real ioBroker usages, both exercised here:
            //  1. adapter replies to an admin-tab message: sendTo(from, command, result) with no
            //     function callback - real ioBroker routes this back through the message bus via
            //     obj.callback, which the mock doesn't implement, so we resolve callAdmin() here.
            //  2. adapter queries ANOTHER instance directly (HistoryService -> history.0):
            //     sendTo(target, command, message, callbackFn) - a real function to call back.
            adapter.sendTo = (target: unknown, command: unknown, messageOrResult: unknown, callback?: unknown) => {
                if (typeof callback === 'function') {
                    if (target === 'history.0' && command === 'getHistory') {
                        (callback as (reply: unknown) => void)({
                            result: [
                                { val: 21.5, ts: Date.now() - 60_000 },
                                { val: 22, ts: Date.now() },
                            ],
                        });
                    } else {
                        (callback as (reply: unknown) => void)(undefined);
                    }
                    return;
                }
                const resolver = pendingAdminCalls.get(command as string);
                if (resolver) {
                    pendingAdminCalls.delete(command as string);
                    resolver(messageOrResult);
                }
            };
        },
    });

    require.cache[ADAPTER_CORE_PATH] = { id: ADAPTER_CORE_PATH, filename: ADAPTER_CORE_PATH, loaded: true, exports: mocked } as unknown as NodeJS.Module;

    const factory = require('../../src/main');
    factory({});
    await adapter.readyHandler!();
    console.log('adapter onReady() completed, HTTP server listening on', BASE_URL);

    await step('unauthenticated catalog request is rejected over the real HTTP server', async () => {
        const res = await fetch(`${BASE_URL}/api/v1/catalog`);
        assert.equal(res.status, 401);
        const body = (await res.json()) as { error: string };
        assert.equal(body.error, 'AUTH_REQUIRED');
    });

    await step('GET /api/v1/server/info is reachable without auth and returns a fingerprint', async () => {
        const res = await fetch(`${BASE_URL}/api/v1/server/info`);
        assert.equal(res.status, 200);
        const body = (await res.json()) as { fingerprint: string };
        assert.equal(typeof body.fingerprint, 'string');
        assert.ok(body.fingerprint.length > 0);
    });

    let firstDeviceStatus!: { status: string; deviceId: string; accessToken: string; refreshToken: string };

    await step('full pairing -> approval -> token flow issues working tokens', async () => {
        const user = await callAdmin<{ id: string }>('createUser', { name: 'Integration Test User', roleId: 'viewer' });
        assert.ok(user.id);

        const invite = await callAdmin<{
            invite: { id: string };
            qrPayload: { pairingId: string; pairingSecret: string; serverFingerprint: string };
        }>('createPairingInvite', { userId: user.id, roleId: 'viewer' });
        assert.equal(invite.qrPayload.pairingId, invite.invite.id);

        const serverInfoRes = await fetch(`${BASE_URL}/api/v1/server/info`);
        const serverInfo = (await serverInfoRes.json()) as { fingerprint: string };
        assert.equal(
            serverInfo.fingerprint,
            invite.qrPayload.serverFingerprint,
            'GET /api/v1/server/info must return exactly the fingerprint embedded in QR invites, so the app can verify a scanned QR against it',
        );

        const { publicKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
        const publicKeyBase64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');

        const claimRes = await fetch(`${BASE_URL}/api/v1/pairing/claim`, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({
                pairingId: invite.qrPayload.pairingId,
                pairingSecret: invite.qrPayload.pairingSecret,
                deviceName: 'Integration Test Phone',
                platform: 'android',
                appVersion: '1.0',
                publicKey: publicKeyBase64,
            }),
        });
        assert.equal(claimRes.status, 200);
        const claim = (await claimRes.json()) as { status: string; claimId: string };
        assert.equal(claim.status, 'waiting_for_approval');

        const pendingClaims = await callAdmin<Array<{ id: string; deviceId: string }>>('listPendingClaims');
        assert.ok(pendingClaims.some((c) => c.id === claim.claimId));

        await callAdmin('approveClaim', { claimId: claim.claimId });

        const statusRes = await fetch(`${BASE_URL}/api/v1/pairing/status/${claim.claimId}`);
        assert.equal(statusRes.status, 200);
        const status = (await statusRes.json()) as typeof firstDeviceStatus & { expiresIn: number };
        assert.equal(status.status, 'approved');
        assert.ok(status.accessToken);
        assert.ok(status.refreshToken);
        firstDeviceStatus = status;

        const secondStatusRes = await fetch(`${BASE_URL}/api/v1/pairing/status/${claim.claimId}`);
        const secondStatus = (await secondStatusRes.json()) as { status: string; accessToken?: string };
        assert.equal(secondStatus.status, 'approved');
        assert.equal(secondStatus.accessToken, undefined, 'tokens must only be delivered once');
    });

    await step('the issued access token authenticates a real catalog request', async () => {
        const res = await fetch(`${BASE_URL}/api/v1/catalog`, {
            headers: { authorization: `Bearer ${firstDeviceStatus.accessToken}` },
        });
        assert.equal(res.status, 200);
        const catalog = (await res.json()) as { version: number; objects: unknown[] };
        assert.equal(Array.isArray(catalog.objects), true);

        // delta support: passing the same version back must short-circuit to "unchanged"
        const deltaRes = await fetch(`${BASE_URL}/api/v1/catalog?version=${catalog.version}`, {
            headers: { authorization: `Bearer ${firstDeviceStatus.accessToken}` },
        });
        assert.equal(deltaRes.status, 200);
        const delta = (await deltaRes.json()) as { version: number; unchanged?: boolean; objects?: unknown[] };
        assert.equal(delta.unchanged, true);
        assert.equal(delta.objects, undefined);

        // a stale/wrong version must fall back to the full catalog
        const staleRes = await fetch(`${BASE_URL}/api/v1/catalog?version=999999999`, {
            headers: { authorization: `Bearer ${firstDeviceStatus.accessToken}` },
        });
        const stale = (await staleRes.json()) as { objects?: unknown[] };
        assert.ok(Array.isArray(stale.objects));
    });

    await step('refresh token rotates over real HTTP and detects reuse of the old one', async () => {
        const refreshRes = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ deviceId: firstDeviceStatus.deviceId, refreshToken: firstDeviceStatus.refreshToken }),
        });
        assert.equal(refreshRes.status, 200);
        const rotated = (await refreshRes.json()) as { refreshToken: string };
        assert.notEqual(rotated.refreshToken, firstDeviceStatus.refreshToken);

        const reuseRes = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ deviceId: firstDeviceStatus.deviceId, refreshToken: firstDeviceStatus.refreshToken }),
        });
        assert.equal(reuseRes.status, 401);
    });

    await step('challenge-response login works end to end with a real EC P-256 signature', async () => {
        const user = await callAdmin<{ id: string }>('createUser', { name: 'Second Device Owner', roleId: 'viewer' });
        const invite = await callAdmin<{ qrPayload: { pairingId: string; pairingSecret: string } }>('createPairingInvite', {
            userId: user.id,
            roleId: 'viewer',
        });

        const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
        const publicKeyBase64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');

        const claimRes = await fetch(`${BASE_URL}/api/v1/pairing/claim`, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({
                pairingId: invite.qrPayload.pairingId,
                pairingSecret: invite.qrPayload.pairingSecret,
                deviceName: 'Second Device',
                platform: 'android',
                appVersion: '1.0',
                publicKey: publicKeyBase64,
            }),
        });
        const claim = (await claimRes.json()) as { claimId: string };

        const pendingClaims = await callAdmin<Array<{ id: string; deviceId: string }>>('listPendingClaims');
        const pendingClaim = pendingClaims.find((c) => c.id === claim.claimId)!;
        await callAdmin('approveClaim', { claimId: claim.claimId });
        await fetch(`${BASE_URL}/api/v1/pairing/status/${claim.claimId}`); // consume the pairing-issued tokens

        const challengeRes = await fetch(`${BASE_URL}/api/v1/auth/challenge`, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ deviceId: pendingClaim.deviceId }),
        });
        assert.equal(challengeRes.status, 200);
        const challenge = (await challengeRes.json()) as { challengeId: string; nonce: string };

        const signature = cryptoSign('sha256', Buffer.from(challenge.nonce, 'base64'), privateKey).toString('base64');

        const loginRes = await fetch(`${BASE_URL}/api/v1/auth/login`, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ deviceId: pendingClaim.deviceId, challengeId: challenge.challengeId, signature }),
        });
        assert.equal(loginRes.status, 200);
        const login = (await loginRes.json()) as { accessToken: string; user: { name: string } };
        assert.equal(login.user.name, 'Second Device Owner');
        assert.ok(login.accessToken);
    });

    await step('history endpoint requires auth and 404s an unmapped object id', async () => {
        const unauthedRes = await fetch(`${BASE_URL}/api/v1/history?id=whatever`);
        assert.equal(unauthedRes.status, 401);

        const authedRes = await fetch(`${BASE_URL}/api/v1/history?id=not-a-real-mapped-uuid`, {
            headers: { authorization: `Bearer ${firstDeviceStatus.accessToken}` },
        });
        // the original access token was already rotated away by the refresh-token step above,
        // so this also doubles as a check that a stale (but well-formed) JWT is rejected.
        assert.equal(authedRes.status, 401);
    });

    await step('a reverse proxy on loopback can correctly forward the real client IP via X-Forwarded-For', async () => {
        // Simulates the real-world setup this was fixed for: an nginx/Caddy reverse proxy (often
        // its own Docker container) sitting in front of the adapter. The test itself always
        // connects via loopback (127.0.0.1) either way, which is exactly the "trusted local
        // proxy hop" case app.set('trust proxy', ...) is meant to honor - so a forwarded header
        // from here should be believed, while an attacker connecting directly from the public
        // internet (not exercised here, but see the trust-proxy comment in main.ts) would not be.
        const user = await callAdmin<{ id: string }>('createUser', { name: 'Trust Proxy Test User', roleId: 'viewer' });
        const invite = await callAdmin<{ qrPayload: { pairingId: string; pairingSecret: string } }>('createPairingInvite', {
            userId: user.id,
            roleId: 'viewer',
        });
        const { publicKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
        const publicKeyBase64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');

        const spoofedClientIp = '203.0.113.42';
        const claimRes = await fetch(`${BASE_URL}/api/v1/pairing/claim`, {
            method: 'POST',
            headers: { 'content-type': 'application/json', 'x-forwarded-for': spoofedClientIp },
            body: JSON.stringify({
                pairingId: invite.qrPayload.pairingId,
                pairingSecret: invite.qrPayload.pairingSecret,
                deviceName: 'Trust Proxy Test Device',
                platform: 'android',
                appVersion: '1.0',
                publicKey: publicKeyBase64,
            }),
        });
        assert.equal(claimRes.status, 200);

        const auditEvents = await callAdmin<Array<{ action: string; ip: string | null }>>('listAudit', { limit: 500 });
        const claimEvent = auditEvents.find((e) => e.action === 'pairing.claim' && e.ip === spoofedClientIp);
        assert.ok(
            claimEvent,
            'expected the audit log to record the X-Forwarded-For client IP (via the loopback-trusted proxy hop), not the raw socket address',
        );
    });

    await step('the tunnel proxy forwards to an admin-approved target via a separate short-lived token, and rejects an invalid one', async () => {
        // A tiny real HTTP server standing in for a local device web UI - the tunnel is supposed
        // to reach exactly this over a real HTTP hop, never anything the client itself names.
        const targetServer = http.createServer((req: { url: string }, res: { writeHead: (code: number, headers?: Record<string, string>) => void; end: (body?: string) => void }) => {
            if (req.url === '/relay/0?turn=on') {
                res.writeHead(200, { 'content-type': 'text/plain' });
                res.end('switched on');
            } else {
                res.writeHead(404);
                res.end();
            }
        });
        await new Promise<void>((resolve) => targetServer.listen(0, '127.0.0.1', resolve));
        const targetPort = targetServer.address().port;

        try {
            const user = await callAdmin<{ id: string }>('createUser', { name: 'Tunnel Test User', roleId: 'viewer' });
            const invite = await callAdmin<{ qrPayload: { pairingId: string; pairingSecret: string } }>('createPairingInvite', {
                userId: user.id,
                roleId: 'viewer',
            });
            const { publicKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
            const publicKeyBase64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
            const claimRes = await fetch(`${BASE_URL}/api/v1/pairing/claim`, {
                method: 'POST',
                headers: { 'content-type': 'application/json' },
                body: JSON.stringify({
                    pairingId: invite.qrPayload.pairingId,
                    pairingSecret: invite.qrPayload.pairingSecret,
                    deviceName: 'Tunnel Test Device',
                    platform: 'android',
                    appVersion: '1.0',
                    publicKey: publicKeyBase64,
                }),
            });
            const claim = (await claimRes.json()) as { claimId: string };
            await callAdmin('approveClaim', { claimId: claim.claimId });
            const statusRes = await fetch(`${BASE_URL}/api/v1/pairing/status/${claim.claimId}`);
            const status = (await statusRes.json()) as { accessToken: string };

            const embed = await callAdmin<{ id: string }>('createUrlEmbed', {
                name: 'Tunnel Test Target',
                url: `http://127.0.0.1:${targetPort}/relay/0`,
            });
            await callAdmin('createUrlEmbedAccessRule', {
                urlEmbedId: embed.id,
                roleId: 'viewer',
                userId: null,
                deviceId: null,
                deny: false,
            });

            const tokenRes = await fetch(`${BASE_URL}/api/v1/tunnel-token/${embed.id}`, {
                method: 'POST',
                headers: { authorization: `Bearer ${status.accessToken}` },
            });
            assert.equal(tokenRes.status, 200);
            const { token } = (await tokenRes.json()) as { token: string };

            const proxied = await fetch(`${BASE_URL}/api/v1/tunnel/proxy`, {
                headers: { 'x-tunnel-token': token, 'x-tunnel-path': '/relay/0?turn=on' },
            });
            assert.equal(proxied.status, 200);
            assert.equal(await proxied.text(), 'switched on');

            const rejected = await fetch(`${BASE_URL}/api/v1/tunnel/proxy`, {
                headers: { 'x-tunnel-token': 'not-a-real-token', 'x-tunnel-path': '/relay/0?turn=on' },
            });
            assert.equal(rejected.status, 401);
        } finally {
            await new Promise<void>((resolve) => targetServer.close(() => resolve()));
        }
    });

    await step('deleteDevice removes the device and cleans up its exposure/url-embed access rules, but leaves other devices alone', async () => {
        const user = await callAdmin<{ id: string }>('createUser', { name: 'Delete Test User', roleId: 'viewer' });
        const invite = await callAdmin<{ qrPayload: { pairingId: string; pairingSecret: string } }>('createPairingInvite', {
            userId: user.id,
            roleId: 'viewer',
        });
        const { publicKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
        const publicKeyBase64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
        const claimRes = await fetch(`${BASE_URL}/api/v1/pairing/claim`, {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({
                pairingId: invite.qrPayload.pairingId,
                pairingSecret: invite.qrPayload.pairingSecret,
                deviceName: 'Delete Test Device',
                platform: 'android',
                appVersion: '1.0',
                publicKey: publicKeyBase64,
            }),
        });
        const claim = (await claimRes.json()) as { claimId: string };
        await callAdmin('approveClaim', { claimId: claim.claimId });
        const statusRes = await fetch(`${BASE_URL}/api/v1/pairing/status/${claim.claimId}`);
        const status = (await statusRes.json()) as { deviceId: string };
        const deviceId = status.deviceId;

        await callAdmin('createExposureRule', {
            scope: 'state',
            target: 'zigbee.0.some.object',
            deviceId,
            userId: null,
            roleId: null,
            read: true,
            write: false,
            history: false,
            deny: false,
            min: null,
            max: null,
            step: null,
            allowedValues: null,
            localOnly: false,
            confirmPolicy: 'NONE',
            displayName: null,
        });
        const embed = await callAdmin<{ id: string }>('createUrlEmbed', { name: 'Delete Test Embed', url: 'http://127.0.0.1:1/x' });
        await callAdmin('createUrlEmbedAccessRule', {
            urlEmbedId: embed.id,
            roleId: null,
            userId: null,
            deviceId,
            deny: false,
        });

        const devicesBefore = await callAdmin<Array<{ id: string }>>('listDevices');
        assert.ok(devicesBefore.some((d) => d.id === deviceId));
        const exposureRulesBefore = await callAdmin<Array<{ deviceId: string | null }>>('listExposureRules');
        assert.ok(exposureRulesBefore.some((r) => r.deviceId === deviceId));

        await callAdmin('deleteDevice', { id: deviceId });

        const devicesAfter = await callAdmin<Array<{ id: string }>>('listDevices');
        assert.ok(!devicesAfter.some((d) => d.id === deviceId), 'deleted device must no longer be listed');
        assert.ok(devicesAfter.some((d) => d.id === firstDeviceStatus.deviceId), 'other devices must be unaffected by the delete');

        const exposureRulesAfter = await callAdmin<Array<{ deviceId: string | null }>>('listExposureRules');
        assert.ok(!exposureRulesAfter.some((r) => r.deviceId === deviceId), 'orphaned exposure rules for the deleted device must be cleaned up');

        // deleting again must be a harmless no-op, not an error
        await callAdmin('deleteDevice', { id: deviceId });
    });

    // Must be the LAST step: AbuseGuard blocks by IP for abuseBlockMinutes (30 here), and every
    // request in this whole script comes from the same loopback IP - tripping the block earlier
    // would break every legitimate call in the steps above it.
    await step('repeated failed /auth/challenge attempts trigger a temporary block (AbuseGuard)', async () => {
        const attempt = () =>
            fetch(`${BASE_URL}/api/v1/auth/challenge`, {
                method: 'POST',
                headers: { 'content-type': 'application/json' },
                body: JSON.stringify({ deviceId: 'no-such-device' }),
            });

        // abuseBlockThreshold is configured to 3 above - the first 3 failures should each fail
        // as a normal "device not found", not yet be blocked.
        for (let i = 0; i < 3; i++) {
            const res = await attempt();
            assert.equal(res.status, 404, `attempt ${i + 1} should be a normal not-found, not a block`);
        }

        // The 4th request should now be rejected by AbuseGuard itself before even reaching the
        // route handler - still a 4xx, but for a different reason (RATE_LIMITED, not NOT_FOUND).
        const blockedRes = await attempt();
        assert.equal(blockedRes.status, 429);
        const blockedBody = (await blockedRes.json()) as { error: string };
        assert.equal(blockedBody.error, 'RATE_LIMITED');

        // The admin tab's 'listAbuseState' message must show this exact block, for the live
        // visibility panel in OverviewTab.tsx.
        const state = await callAdmin<{ key: string; failures: number; blocked: boolean; lastReason: string | null }[]>(
            'listAbuseState',
        );
        const ownEntry = state.find((e) => e.failures >= 3);
        assert.ok(ownEntry, 'expected a blocked entry for this test run\'s IP in listAbuseState');
        assert.equal(ownEntry!.blocked, true);
        assert.equal(ownEntry!.lastReason, 'auth challenge');
    });

    await new Promise<void>((resolve) => adapter.unloadHandler!(resolve));
    console.log(`\n${passed} steps passed. INTEGRATION_TEST_OK`);
}

const safety = setTimeout(() => {
    console.error('Integration test timed out');
    process.exit(1);
}, 20000);

main()
    .then(() => {
        clearTimeout(safety);
        process.exit(0);
    })
    .catch((err) => {
        clearTimeout(safety);
        console.error('INTEGRATION TEST FAILED:', err);
        process.exit(1);
    });
