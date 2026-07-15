import { strict as assert } from 'node:assert';
import { HistoryService } from '../src/history';
import { ApiError } from '../src/lib/errors';
import { createFakeAdapter } from './helpers/fakeAdapter';

describe('HistoryService', () => {
    it('is not configured when no history instance is set', () => {
        const adapter = createFakeAdapter();
        const history = new HistoryService(adapter, '');
        assert.equal(history.isConfigured, false);
    });

    it('rejects a query when no history instance is configured', async () => {
        const adapter = createFakeAdapter();
        const history = new HistoryService(adapter, '');
        await assert.rejects(
            () => history.query('some.state', { from: null, to: null, limit: 100 }),
            (err: unknown) => err instanceof ApiError && err.code === 'SERVER_UNAVAILABLE',
        );
    });

    it('maps a {result: [...]} reply from the history adapter to timestamped entries', async () => {
        const adapter = createFakeAdapter({
            sendTo: (_instance: string, _command: string, _message: unknown, callback: (reply: unknown) => void) => {
                callback({ result: [{ val: 21.5, ts: 1000 }, { val: 22, ts: 2000 }] });
            },
        });
        const history = new HistoryService(adapter, 'history.0');
        const entries = await history.query('some.state', { from: null, to: null, limit: 100 });
        assert.deepEqual(entries, [
            { value: 21.5, timestamp: 1000 },
            { value: 22, timestamp: 2000 },
        ]);
    });

    it('also accepts a bare array reply', async () => {
        const adapter = createFakeAdapter({
            sendTo: (_instance: string, _command: string, _message: unknown, callback: (reply: unknown) => void) => {
                callback([{ val: true, ts: 5000 }]);
            },
        });
        const history = new HistoryService(adapter, 'history.0');
        const entries = await history.query('some.state', { from: null, to: null, limit: 100 });
        assert.deepEqual(entries, [{ value: true, timestamp: 5000 }]);
    });

    it('surfaces an error reported by the history adapter', async () => {
        const adapter = createFakeAdapter({
            sendTo: (_instance: string, _command: string, _message: unknown, callback: (reply: unknown) => void) => {
                callback({ error: 'no such id' });
            },
        });
        const history = new HistoryService(adapter, 'history.0');
        await assert.rejects(
            () => history.query('some.state', { from: null, to: null, limit: 100 }),
            (err: unknown) => err instanceof ApiError && err.code === 'SERVER_UNAVAILABLE',
        );
    });

    it('times out if the history instance never replies', async () => {
        const adapter = createFakeAdapter({
            sendTo: () => {
                // never calls back
            },
        });
        const history = new HistoryService(adapter, 'history.0');
        await assert.rejects(
            () => history.query('some.state', { from: null, to: null, limit: 100 }),
            (err: unknown) => err instanceof ApiError && err.code === 'COMMAND_TIMEOUT',
        );
    }).timeout(10_000);
});
