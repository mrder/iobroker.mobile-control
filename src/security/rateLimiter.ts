interface Bucket {
    count: number;
    windowStart: number;
}

const WINDOW_MS = 60_000;

/** Simple fixed-window rate limiter, one bucket per key (e.g. per device). */
export class RateLimiter {
    private readonly buckets = new Map<string, Bucket>();

    constructor(private readonly limitPerMinute: number) {}

    consume(key: string): boolean {
        const now = Date.now();
        const bucket = this.buckets.get(key);
        if (!bucket || now - bucket.windowStart >= WINDOW_MS) {
            this.buckets.set(key, { count: 1, windowStart: now });
            return true;
        }
        if (bucket.count >= this.limitPerMinute) {
            return false;
        }
        bucket.count += 1;
        return true;
    }
}
