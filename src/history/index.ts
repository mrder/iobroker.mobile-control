import { ApiError } from '../lib/errors';

export interface HistoryQueryOptions {
    from: number | null;
    to: number | null;
    limit: number;
}

export interface HistoryEntry {
    value: unknown;
    timestamp: number;
}

interface RawHistoryReply {
    result?: Array<{ val?: unknown; ts?: number }>;
    error?: string;
}

const QUERY_TIMEOUT_MS = 8_000;
const DEFAULT_LIMIT = 500;
const MAX_LIMIT = 2000;

/**
 * Bridges to a configured ioBroker history-storing adapter instance (e.g. "history.0", "sql.0",
 * "influxdb.0") via the de-facto standard `sendTo(instance, 'getHistory', {id, options}, cb)`
 * convention those adapters implement. This is best-effort: the exact reply shape has minor
 * variations across history adapter implementations, so both a `{result: [...]}` envelope and a
 * bare array are accepted. If no instance is configured, history is simply unavailable rather
 * than erroring - most installs won't have one set up.
 */
export class HistoryService {
    constructor(
        private readonly adapter: ioBroker.Adapter,
        private readonly historyInstance: string,
    ) {}

    get isConfigured(): boolean {
        return this.historyInstance.trim().length > 0;
    }

    async query(stateId: string, options: HistoryQueryOptions): Promise<HistoryEntry[]> {
        if (!this.isConfigured) {
            throw new ApiError('SERVER_UNAVAILABLE', 'no history instance configured');
        }

        const limit = Math.min(Math.max(options.limit || DEFAULT_LIMIT, 1), MAX_LIMIT);

        const reply = await new Promise<RawHistoryReply | unknown>((resolve, reject) => {
            const timer = setTimeout(() => reject(new ApiError('COMMAND_TIMEOUT', 'history instance did not respond')), QUERY_TIMEOUT_MS);
            this.adapter.sendTo(
                this.historyInstance,
                'getHistory',
                {
                    id: stateId,
                    options: {
                        start: options.from ?? undefined,
                        end: options.to ?? undefined,
                        count: limit,
                        aggregate: 'none',
                    },
                },
                (response: unknown) => {
                    clearTimeout(timer);
                    resolve(response);
                },
            );
        });

        const rows = Array.isArray(reply) ? reply : (reply as RawHistoryReply)?.result;
        if (!Array.isArray(rows)) {
            const error = !Array.isArray(reply) ? (reply as RawHistoryReply)?.error : undefined;
            if (error) {
                throw new ApiError('SERVER_UNAVAILABLE', `history instance error: ${error}`);
            }
            return [];
        }

        return rows
            .filter((row) => typeof row.ts === 'number')
            .map((row) => ({ value: row.val ?? null, timestamp: row.ts as number }));
    }
}
