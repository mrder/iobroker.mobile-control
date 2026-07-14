/**
 * Generic collection persistence for adapter runtime data (users, devices, sessions,
 * dashboards, audit events, ...). Structured data is kept as JSON inside a single
 * ioBroker state per collection under "storage.<collection>". This is deliberately
 * NOT a filesystem/db approach: the ioBroker object/state database lives outside the
 * adapter's own npm folder, so data survives adapter updates without extra path APIs
 * or native dependencies (important for Raspberry Pi installs).
 */
export class CollectionStore<T extends { id: string }> {
    private readonly cache = new Map<string, T>();
    private readonly stateId: string;
    private loaded = false;

    constructor(
        private readonly adapter: ioBroker.Adapter,
        private readonly collection: string,
    ) {
        this.stateId = `storage.${collection}`;
    }

    async init(): Promise<void> {
        await this.adapter.setObjectNotExistsAsync(this.stateId, {
            type: 'state',
            common: {
                name: `Internal storage: ${this.collection}`,
                type: 'string',
                role: 'json',
                read: true,
                write: false,
                def: '{}',
            },
            native: {},
        });

        const state = await this.adapter.getStateAsync(this.stateId);
        const raw = state?.val;
        if (typeof raw === 'string' && raw.length > 0) {
            try {
                const parsed = JSON.parse(raw) as Record<string, T>;
                for (const [id, value] of Object.entries(parsed)) {
                    this.cache.set(id, value);
                }
            } catch (err) {
                this.adapter.log.error(
                    `mobile-control: failed to parse storage.${this.collection}, starting empty: ${(err as Error).message}`,
                );
            }
        }
        this.loaded = true;
    }

    private ensureLoaded(): void {
        if (!this.loaded) {
            throw new Error(`CollectionStore "${this.collection}" used before init()`);
        }
    }

    list(): T[] {
        this.ensureLoaded();
        return Array.from(this.cache.values());
    }

    get(id: string): T | undefined {
        this.ensureLoaded();
        return this.cache.get(id);
    }

    find(predicate: (item: T) => boolean): T[] {
        return this.list().filter(predicate);
    }

    findOne(predicate: (item: T) => boolean): T | undefined {
        return this.list().find(predicate);
    }

    has(id: string): boolean {
        this.ensureLoaded();
        return this.cache.has(id);
    }

    async put(item: T): Promise<void> {
        this.ensureLoaded();
        this.cache.set(item.id, item);
        await this.persist();
    }

    async putMany(items: T[]): Promise<void> {
        this.ensureLoaded();
        for (const item of items) {
            this.cache.set(item.id, item);
        }
        await this.persist();
    }

    async delete(id: string): Promise<boolean> {
        this.ensureLoaded();
        const existed = this.cache.delete(id);
        if (existed) {
            await this.persist();
        }
        return existed;
    }

    /** Keeps only the newest `max` entries according to `sortKey` (descending), evicting the rest. */
    async trim(max: number, sortKey: (item: T) => number): Promise<void> {
        this.ensureLoaded();
        if (this.cache.size <= max) {
            return;
        }
        const sorted = this.list().sort((a, b) => sortKey(b) - sortKey(a));
        this.cache.clear();
        for (const item of sorted.slice(0, max)) {
            this.cache.set(item.id, item);
        }
        await this.persist();
    }

    private async persist(): Promise<void> {
        const obj: Record<string, T> = {};
        for (const [id, value] of this.cache) {
            obj[id] = value;
        }
        await this.adapter.setStateAsync(this.stateId, { val: JSON.stringify(obj), ack: true });
    }
}
