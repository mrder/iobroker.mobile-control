import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { ExposureRule } from '../lib/types';

export interface ObjectTreeEntry {
    id: string;
    name: string;
    role: string;
    type: string;
    unit: string | null;
    path: string[];
}

/** system.* is mandatory per BACKEND-KONZEPT.md §3; the rest are common secret/config namespaces. */
const BLOCKED_PREFIXES = ['system.', 'authentication.'];

export function isBlockedStateId(id: string): boolean {
    return BLOCKED_PREFIXES.some((prefix) => id.startsWith(prefix));
}

function matchesScope(rule: ExposureRule, stateId: string): boolean {
    switch (rule.scope) {
        case 'state':
        case 'alias':
            return stateId === rule.target;
        case 'channel':
        case 'device':
        case 'adapter':
            return stateId === rule.target || stateId.startsWith(`${rule.target}.`);
        case 'group':
            try {
                const ids = JSON.parse(rule.target) as string[];
                return ids.includes(stateId);
            } catch {
                return false;
            }
        case 'pattern':
            return globToRegExp(rule.target).test(stateId);
        default:
            return false;
    }
}

/** Minimal glob support (only "*" as wildcard) - enough for id-prefix style exposure patterns. */
function globToRegExp(pattern: string): RegExp {
    const escaped = pattern.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*');
    return new RegExp(`^${escaped}$`);
}

export class ExposureService {
    constructor(
        private readonly adapter: ioBroker.Adapter,
        private readonly store: CollectionStore<ExposureRule>,
    ) {}

    /** Admin UI object browser: real ioBroker state tree, minus the mandatory blocklist. */
    async browseObjectTree(): Promise<ObjectTreeEntry[]> {
        const objects = await this.adapter.getForeignObjectsAsync('*', 'state');
        const entries: ObjectTreeEntry[] = [];
        for (const [id, obj] of Object.entries(objects)) {
            if (isBlockedStateId(id) || !obj) {
                continue;
            }
            const common = obj.common as ioBroker.StateCommon | undefined;
            const name = typeof common?.name === 'string' ? common.name : (common?.name?.en ?? id);
            entries.push({
                id,
                name,
                role: common?.role ?? '',
                type: (common?.type as string) ?? 'mixed',
                unit: common?.unit ?? null,
                path: id.split('.'),
            });
        }
        return entries.sort((a, b) => a.id.localeCompare(b.id));
    }

    list(): ExposureRule[] {
        return this.store.list();
    }

    get(id: string): ExposureRule | undefined {
        return this.store.get(id);
    }

    async create(data: Omit<ExposureRule, 'id' | 'createdAt'>): Promise<ExposureRule> {
        this.validateSingleOwner(data);
        const rule: ExposureRule = { ...data, id: uuid(), createdAt: Date.now() };
        await this.store.put(rule);
        return rule;
    }

    async update(id: string, patch: Partial<Omit<ExposureRule, 'id' | 'createdAt'>>): Promise<ExposureRule> {
        const existing = this.store.get(id);
        if (!existing) {
            throw new ApiError('NOT_FOUND', `exposure rule ${id} not found`);
        }
        const updated: ExposureRule = { ...existing, ...patch };
        this.validateSingleOwner(updated);
        await this.store.put(updated);
        return updated;
    }

    async delete(id: string): Promise<void> {
        await this.store.delete(id);
    }

    /** All rules (of any scope) whose target matches this concrete state id. */
    matchingRules(stateId: string): ExposureRule[] {
        if (isBlockedStateId(stateId)) {
            return [];
        }
        return this.store.find((rule) => matchesScope(rule, stateId));
    }

    private validateSingleOwner(rule: Pick<ExposureRule, 'roleId' | 'userId' | 'deviceId'>): void {
        const ownerCount = [rule.roleId, rule.userId, rule.deviceId].filter((v) => v !== null && v !== undefined).length;
        if (ownerCount !== 1) {
            throw new ApiError('VALIDATION_ERROR', 'an exposure rule must apply to exactly one of role, user or device');
        }
    }
}
