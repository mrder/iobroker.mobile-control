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
    /** 'state' = an actual datapoint (leaf); 'container' = channel/device/folder/adapter grouping */
    kind: 'state' | 'container';
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

    /** Object types that count as a folder-like grouping rather than an actual datapoint. */
    private static readonly CONTAINER_TYPES = new Set(['channel', 'device', 'folder', 'adapter', 'instance']);

    /**
     * Admin UI object browser: real ioBroker state tree, minus the mandatory blocklist. Includes
     * both leaf states AND their container objects (channel/device/folder) so the admin tab can
     * render an actual folder hierarchy with real display names instead of only a flat list of
     * datapoints - a rule can then be granted on a container (scope 'channel', prefix-matched
     * against every state underneath it, see matchesScope) just as well as on a single state.
     */
    async browseObjectTree(): Promise<ObjectTreeEntry[]> {
        const objects = await this.adapter.getForeignObjectsAsync('*');
        const entries: ObjectTreeEntry[] = [];
        for (const [id, obj] of Object.entries(objects)) {
            if (isBlockedStateId(id) || !obj) {
                continue;
            }
            const isState = obj.type === 'state';
            const isContainer = ExposureService.CONTAINER_TYPES.has(obj.type);
            if (!isState && !isContainer) {
                continue;
            }
            const common = obj.common as ioBroker.StateCommon | undefined;
            const name = typeof common?.name === 'string' ? common.name : (common?.name?.en ?? id);
            entries.push({
                id,
                name,
                role: common?.role ?? '',
                type: isState ? ((common?.type as string) ?? 'mixed') : obj.type,
                unit: isState ? (common?.unit ?? null) : null,
                path: id.split('.'),
                kind: isState ? 'state' : 'container',
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
