import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { AuditService } from '../src/audit';
import type { AuditEvent } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

async function setup() {
    const adapter = createFakeAdapter();
    const store = new CollectionStore<AuditEvent>(adapter, 'auditEvents');
    await store.init();
    const service = new AuditService(store);
    return { adapter, store, service };
}

describe('AuditService', () => {
    it('logs an event with redacted long token-like strings in the detail', async () => {
        const { service } = await setup();
        await service.log({
            action: 'auth.login',
            result: 'success',
            detail: `token=${'a'.repeat(40)}`,
            ip: '192.168.1.10',
        });

        const [event] = service.list();
        assert.equal(event.action, 'auth.login');
        assert.equal(event.result, 'success');
        assert.equal(event.ip, '192.168.1.10');
        assert.equal(event.detail, 'token=[redacted]');
    });

    it('list() returns newest first and respects the limit', async () => {
        // Writes directly to the store with explicit, distinct timestamps rather than looping
        // service.log() - a tight loop's Date.now() calls can land in the same millisecond,
        // which would make the ordering assertion below flaky (sort is stable, so same-
        // timestamp entries keep insertion order rather than reflecting "newest first").
        const { service, store } = await setup();
        const now = Date.now();
        for (let i = 0; i < 5; i++) {
            await store.put({
                id: `event-${i}`,
                timestamp: now + i,
                action: `action-${i}`,
                actorUserId: null,
                actorDeviceId: null,
                sessionId: null,
                objectId: null,
                result: 'success',
                detail: null,
                ip: null,
            });
        }

        const limited = service.list(2);
        assert.equal(limited.length, 2);
        assert.equal(limited[0].action, 'action-4');
        assert.equal(limited[1].action, 'action-3');
    });

    it('clearAll removes every event and reports how many were removed', async () => {
        const { service } = await setup();
        await service.log({ action: 'a', result: 'success' });
        await service.log({ action: 'b', result: 'failure' });

        const removed = await service.clearAll();

        assert.equal(removed, 2);
        assert.deepEqual(service.list(), []);
    });

    it('clearOlderThan keeps only events within the last N days', async () => {
        const { service, store } = await setup();
        const now = Date.now();
        const dayMs = 24 * 60 * 60 * 1000;
        // Write directly to the store to control exact timestamps (service.log() always stamps "now").
        await store.put({ id: 'old', timestamp: now - 10 * dayMs, action: 'old', actorUserId: null, actorDeviceId: null, sessionId: null, objectId: null, result: 'success', detail: null, ip: null });
        await store.put({ id: 'recent', timestamp: now - 1 * dayMs, action: 'recent', actorUserId: null, actorDeviceId: null, sessionId: null, objectId: null, result: 'success', detail: null, ip: null });

        const removed = await service.clearOlderThan(7);

        assert.equal(removed, 1);
        assert.deepEqual(service.list().map((e) => e.id), ['recent']);
    });

    it('clearOlderThan with a large window keeps everything', async () => {
        const { service } = await setup();
        await service.log({ action: 'a', result: 'success' });
        await service.log({ action: 'b', result: 'success' });

        const removed = await service.clearOlderThan(365);

        assert.equal(removed, 0);
        assert.equal(service.list().length, 2);
    });
});

describe('CollectionStore.retain', () => {
    it('keeps only items matching the predicate and reports how many were removed', async () => {
        const adapter = createFakeAdapter();
        const store = new CollectionStore<{ id: string; keep: boolean }>(adapter, 'retainTest');
        await store.init();
        await store.putMany([
            { id: 'a', keep: true },
            { id: 'b', keep: false },
            { id: 'c', keep: true },
        ]);

        const removed = await store.retain((item) => item.keep);

        assert.equal(removed, 1);
        assert.deepEqual(
            store.list().map((i) => i.id).sort(),
            ['a', 'c'],
        );
    });

    it('persists the retained set so a fresh load reflects it', async () => {
        const adapter = createFakeAdapter();
        const store = new CollectionStore<{ id: string }>(adapter, 'retainPersist');
        await store.init();
        await store.putMany([{ id: 'a' }, { id: 'b' }]);
        await store.retain((item) => item.id === 'a');

        const reloaded = new CollectionStore<{ id: string }>(adapter, 'retainPersist');
        await reloaded.init();

        assert.deepEqual(reloaded.list().map((i) => i.id), ['a']);
    });
});
