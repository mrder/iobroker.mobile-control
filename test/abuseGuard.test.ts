import { strict as assert } from 'node:assert';
import { AbuseGuard } from '../src/security/abuseGuard';

describe('AbuseGuard', () => {
    it('is not blocked before the failure threshold is reached', () => {
        const guard = new AbuseGuard({ maxFailures: 3, windowMs: 60_000, blockMs: 60_000 });
        guard.recordFailure('1.2.3.4');
        guard.recordFailure('1.2.3.4');
        assert.equal(guard.isBlocked('1.2.3.4'), false);
    });

    it('blocks the key exactly once the threshold is crossed, and recordFailure returns true only that once', () => {
        const guard = new AbuseGuard({ maxFailures: 3, windowMs: 60_000, blockMs: 60_000 });
        assert.equal(guard.recordFailure('1.2.3.4'), false);
        assert.equal(guard.recordFailure('1.2.3.4'), false);
        assert.equal(guard.recordFailure('1.2.3.4'), true, 'the 3rd failure should trip the block');
        assert.equal(guard.isBlocked('1.2.3.4'), true);
        assert.equal(guard.recordFailure('1.2.3.4'), false, 'already blocked, must not report a fresh block again');
    });

    it('a block expires on its own once blockMs has elapsed', async () => {
        const guard = new AbuseGuard({ maxFailures: 1, windowMs: 60_000, blockMs: 20 });
        guard.recordFailure('1.2.3.4');
        assert.equal(guard.isBlocked('1.2.3.4'), true);
        await new Promise((resolve) => setTimeout(resolve, 30));
        assert.equal(guard.isBlocked('1.2.3.4'), false);
    });

    it('a success clears prior accumulated failures for that key', () => {
        const guard = new AbuseGuard({ maxFailures: 3, windowMs: 60_000, blockMs: 60_000 });
        guard.recordFailure('1.2.3.4');
        guard.recordFailure('1.2.3.4');
        guard.recordSuccess('1.2.3.4');
        guard.recordFailure('1.2.3.4');
        guard.recordFailure('1.2.3.4');
        assert.equal(guard.isBlocked('1.2.3.4'), false, 'the earlier 2 failures were cleared by the success');
    });

    it('different keys are tracked completely independently', () => {
        const guard = new AbuseGuard({ maxFailures: 2, windowMs: 60_000, blockMs: 60_000 });
        guard.recordFailure('1.2.3.4');
        guard.recordFailure('1.2.3.4');
        assert.equal(guard.isBlocked('1.2.3.4'), true);
        assert.equal(guard.isBlocked('5.6.7.8'), false);
    });

    it('failures outside the window do not accumulate toward the threshold', async () => {
        const guard = new AbuseGuard({ maxFailures: 2, windowMs: 20, blockMs: 60_000 });
        guard.recordFailure('1.2.3.4');
        await new Promise((resolve) => setTimeout(resolve, 30));
        guard.recordFailure('1.2.3.4'); // window has reset, this is treated as failure #1 again
        assert.equal(guard.isBlocked('1.2.3.4'), false);
    });

    it('getFailureCount reflects the live count and resets to 0 for an untracked or expired key', async () => {
        const guard = new AbuseGuard({ maxFailures: 10, windowMs: 20, blockMs: 60_000 });
        assert.equal(guard.getFailureCount('1.2.3.4'), 0);
        guard.recordFailure('1.2.3.4');
        guard.recordFailure('1.2.3.4');
        assert.equal(guard.getFailureCount('1.2.3.4'), 2);
        await new Promise((resolve) => setTimeout(resolve, 30));
        assert.equal(guard.getFailureCount('1.2.3.4'), 0, 'window expired, should not report stale failures');
    });

    it('snapshot reports every key still within its window or still blocked, most failures first, with the last reason', () => {
        const guard = new AbuseGuard({ maxFailures: 3, windowMs: 60_000, blockMs: 60_000 });
        guard.recordFailure('1.1.1.1', 'auth login');
        guard.recordFailure('2.2.2.2', 'pairing');
        guard.recordFailure('2.2.2.2', 'pairing');
        guard.recordFailure('2.2.2.2', 'pairing'); // trips the block for 2.2.2.2

        const snapshot = guard.snapshot();
        assert.equal(snapshot.length, 2);
        assert.equal(snapshot[0].key, '2.2.2.2', 'most failures first');
        assert.equal(snapshot[0].failures, 3);
        assert.equal(snapshot[0].blocked, true);
        assert.equal(snapshot[0].lastReason, 'pairing');
        assert.equal(snapshot[1].key, '1.1.1.1');
        assert.equal(snapshot[1].blocked, false);
        assert.equal(snapshot[1].lastReason, 'auth login');
    });

    it('snapshot prunes entries whose window has expired and are not blocked', async () => {
        const guard = new AbuseGuard({ maxFailures: 10, windowMs: 20, blockMs: 60_000 });
        guard.recordFailure('1.2.3.4');
        assert.equal(guard.snapshot().length, 1);
        await new Promise((resolve) => setTimeout(resolve, 30));
        assert.equal(guard.snapshot().length, 0);
    });
});
