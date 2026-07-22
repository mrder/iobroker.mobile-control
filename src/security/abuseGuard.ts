interface AbuseBucket {
    failures: number;
    windowStart: number;
    blockedUntil: number | null;
    lastReason: string | null;
}

export interface AbuseGuardConfig {
    /** Failures within `windowMs` before a temporary block kicks in. */
    maxFailures: number;
    /** How long a burst of failures is counted together. */
    windowMs: number;
    /** How long a key stays blocked once it crosses `maxFailures`. */
    blockMs: number;
}

export interface AbuseSnapshotEntry {
    key: string;
    failures: number;
    blocked: boolean;
    blockedUntil: number | null;
    windowStart: number;
    lastReason: string | null;
}

/**
 * Tracks repeated *failures* (wrong pairing secret, bad signature, unknown device, ...) per key
 * (normally the client IP) - distinct from RateLimiter, which throttles raw request *volume*
 * regardless of outcome. A burst of failures within the window triggers a temporary block, so
 * an attacker can't just stay just under the per-minute rate limit forever. Every failure/success
 * is already written to the audit log by the callers of this class; this only adds the part that
 * was missing: something that actually reacts to a sustained pattern instead of only recording it.
 */
export class AbuseGuard {
    private readonly buckets = new Map<string, AbuseBucket>();

    constructor(private readonly config: AbuseGuardConfig) {}

    isBlocked(key: string): boolean {
        const bucket = this.buckets.get(key);
        if (!bucket?.blockedUntil) {
            return false;
        }
        if (Date.now() >= bucket.blockedUntil) {
            this.buckets.delete(key);
            return false;
        }
        return true;
    }

    /** Records a failed attempt. `reason` is a short label (e.g. "pairing", "auth login") kept
     *  purely for visibility (see snapshot()) - it does not affect the counting itself. Returns
     *  true exactly once, the moment this call causes a new block to start - callers use that to
     *  log a warning without spamming on every retry. */
    recordFailure(key: string, reason?: string): boolean {
        const now = Date.now();
        let bucket = this.buckets.get(key);
        if (!bucket || now - bucket.windowStart >= this.config.windowMs) {
            bucket = { failures: 0, windowStart: now, blockedUntil: null, lastReason: null };
            this.buckets.set(key, bucket);
        }
        // Deliberately increments and checks the threshold on every call, including the first
        // failure in a brand new window - a maxFailures of 1 must still block immediately.
        bucket.failures += 1;
        if (reason) {
            bucket.lastReason = reason;
        }
        if (bucket.failures >= this.config.maxFailures && !bucket.blockedUntil) {
            bucket.blockedUntil = now + this.config.blockMs;
            return true;
        }
        return false;
    }

    /** A successful attempt clears any accumulated failure history for this key. */
    recordSuccess(key: string): void {
        this.buckets.delete(key);
    }

    /** Current failure count for a key within its active window (0 if untracked or the window
     *  has since expired) - used to build a detailed "10/10 attempts" log message. */
    getFailureCount(key: string): number {
        const bucket = this.buckets.get(key);
        if (!bucket || Date.now() - bucket.windowStart >= this.config.windowMs) {
            return 0;
        }
        return bucket.failures;
    }

    /** Every key currently worth showing an admin (still within its failure window, or still
     *  blocked), most failures first. Opportunistically prunes stale entries (window expired,
     *  not blocked) while iterating, so this also bounds the map's long-term memory use without
     *  needing a separate cleanup timer. */
    snapshot(): AbuseSnapshotEntry[] {
        const now = Date.now();
        const entries: AbuseSnapshotEntry[] = [];
        for (const [key, bucket] of this.buckets) {
            const blocked = bucket.blockedUntil !== null && bucket.blockedUntil > now;
            const windowActive = now - bucket.windowStart < this.config.windowMs;
            if (!blocked && !windowActive) {
                this.buckets.delete(key);
                continue;
            }
            entries.push({
                key,
                failures: bucket.failures,
                blocked,
                blockedUntil: bucket.blockedUntil,
                windowStart: bucket.windowStart,
                lastReason: bucket.lastReason,
            });
        }
        return entries.sort((a, b) => b.failures - a.failures);
    }
}
