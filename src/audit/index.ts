import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import type { AuditEvent } from '../lib/types';

export interface AuditLogParams {
    action: string;
    actorUserId?: string | null;
    actorDeviceId?: string | null;
    sessionId?: string | null;
    objectId?: string | null;
    result: 'success' | 'failure';
    detail?: string | null;
    ip?: string | null;
}

const MAX_AUDIT_EVENTS = 5000;

/** Heuristic redaction: long opaque token-like strings are blanked before persisting. */
function redact(detail: string | null): string | null {
    if (!detail) {
        return detail;
    }
    return detail.replace(/[A-Za-z0-9_-]{32,}/g, '[redacted]');
}

export class AuditService {
    constructor(private readonly store: CollectionStore<AuditEvent>) {}

    async log(params: AuditLogParams): Promise<void> {
        const event: AuditEvent = {
            id: uuid(),
            timestamp: Date.now(),
            action: params.action,
            actorUserId: params.actorUserId ?? null,
            actorDeviceId: params.actorDeviceId ?? null,
            sessionId: params.sessionId ?? null,
            objectId: params.objectId ?? null,
            result: params.result,
            detail: redact(params.detail ?? null),
            ip: params.ip ?? null,
        };
        await this.store.put(event);
        await this.store.trim(MAX_AUDIT_EVENTS, (e) => e.timestamp);
    }

    list(limit = 200): AuditEvent[] {
        return this.store
            .list()
            .sort((a, b) => b.timestamp - a.timestamp)
            .slice(0, limit);
    }

    /** Wipes every event. Returns how many were removed - callers are expected to log a fresh
     *  "audit log cleared" event immediately afterwards, so the clear itself stays traceable. */
    async clearAll(): Promise<number> {
        return this.store.retain(() => false);
    }

    /** Keeps only events from the last `days` days, evicting everything older. */
    async clearOlderThan(days: number): Promise<number> {
        const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
        return this.store.retain((event) => event.timestamp >= cutoff);
    }
}
