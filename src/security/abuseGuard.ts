interface AbuseBucket {
    failures: number;
    windowStart: number;
    blockedUntil: number | null;
}

export interface AbuseGuardConfig {
    /** Failures within `windowMs` before a temporary block kicks in. */
    maxFailures: number;
    /** How long a burst of failures is counted together. */
    windowMs: number;
    /** How long a key stays blocked once it crosses `maxFailures`. */
    blockMs: number;
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

    /** Records a failed attempt. Returns true exactly once, the moment this call causes a new
     *  block to start - callers use that to log a warning without spamming on every retry. */
    recordFailure(key: string): boolean {
        const now = Date.now();
        let bucket = this.buckets.get(key);
        if (!bucket || now - bucket.windowStart >= this.config.windowMs) {
            bucket = { failures: 0, windowStart: now, blockedUntil: null };
            this.buckets.set(key, bucket);
        }
        // Deliberately increments and checks the threshold on every call, including the first
        // failure in a brand new window - a maxFailures of 1 must still block immediately.
        bucket.failures += 1;
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
}
