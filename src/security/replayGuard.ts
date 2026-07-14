interface Entry {
    expiresAt: number;
}

/** Tracks recently-seen (device, nonce) pairs in memory to reject replayed commands. */
export class ReplayGuard {
    private readonly seen = new Map<string, Entry>();

    constructor(private readonly ttlMs = 5 * 60_000) {}

    /** Returns true if this key was not seen before (and remembers it); false if it's a replay. */
    checkAndRemember(key: string): boolean {
        this.evictExpired();
        if (this.seen.has(key)) {
            return false;
        }
        this.seen.set(key, { expiresAt: Date.now() + this.ttlMs });
        return true;
    }

    private evictExpired(): void {
        const now = Date.now();
        for (const [key, entry] of this.seen) {
            if (entry.expiresAt < now) {
                this.seen.delete(key);
            }
        }
    }
}
