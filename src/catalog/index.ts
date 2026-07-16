import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { CatalogObject, PublicObjectMapping } from '../lib/types';
import type { ExposureService } from '../exposure';
import type { AuthContext, AuthorizationService, EffectivePermission } from '../authorization';

function hashString(input: string): number {
    let hash = 2166136261;
    for (let i = 0; i < input.length; i++) {
        hash ^= input.charCodeAt(i);
        hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
}

function mapValueType(type: string): CatalogObject['valueType'] {
    return type === 'number' || type === 'string' || type === 'boolean' ? type : 'mixed';
}

function suggestWidgets(role: string, valueType: CatalogObject['valueType']): string[] {
    if (role.includes('alarm')) {
        return ['alarm', 'status'];
    }
    if (role.includes('temperature')) {
        return ['temperature', 'value', 'chart'];
    }
    if (role.includes('humidity')) {
        return ['humidity', 'value'];
    }
    if (role.startsWith('switch') || role.startsWith('button')) {
        return ['switch'];
    }
    if (role.startsWith('level.blind') || role.startsWith('level.shutter')) {
        return ['shutter', 'slider'];
    }
    if (role.startsWith('level')) {
        return ['slider', 'value'];
    }
    if (valueType === 'boolean') {
        return ['status', 'switch'];
    }
    if (valueType === 'number') {
        return ['value', 'chart'];
    }
    return ['text'];
}

export interface EffectiveCatalog {
    version: number;
    objects: CatalogObject[];
}

export class CatalogService {
    constructor(
        private readonly exposure: ExposureService,
        private readonly authorization: AuthorizationService,
        private readonly mappings: CollectionStore<PublicObjectMapping>,
    ) {}

    async getOrCreateMapping(stateId: string): Promise<PublicObjectMapping> {
        const existing = this.mappings.findOne((m) => m.stateId === stateId);
        if (existing) {
            return existing;
        }
        const mapping: PublicObjectMapping = { id: uuid(), stateId, createdAt: Date.now() };
        await this.mappings.put(mapping);
        return mapping;
    }

    /** Central authorization checkpoint reused by the states/commands/realtime endpoints. */
    resolveAuthorized(publicId: string, ctx: AuthContext, need: 'read' | 'write'): { stateId: string; permission: EffectivePermission } {
        const mapping = this.mappings.get(publicId);
        if (!mapping) {
            throw new ApiError('OBJECT_NOT_FOUND');
        }
        const permission = this.authorization.resolve(mapping.stateId, ctx);
        if (need === 'read' && !permission.read) {
            throw new ApiError('READ_FORBIDDEN');
        }
        if (need === 'write' && !permission.write) {
            throw new ApiError('WRITE_FORBIDDEN');
        }
        return { stateId: mapping.stateId, permission };
    }

    /**
     * Cheap version check (no ioBroker object tree read) for GET /catalog?version= delta support.
     * NOTE: only reflects exposure-rule changes, not the live ioBroker object tree - an object
     * disappearing from ioBroker without any rule edit won't change the version. Acceptable for
     * MVP: newly-appeared objects are invisible until a rule is created for them anyway (deny by
     * default), which DOES change the version; only "an exposed object vanished on its own" can
     * go briefly stale in a client that skips refetching on an unchanged version.
     */
    currentVersion(): number {
        return hashString(JSON.stringify(this.exposure.list()));
    }

    async effectiveCatalog(ctx: AuthContext): Promise<EffectiveCatalog> {
        const entries = await this.exposure.browseObjectTree();
        const version = this.currentVersion();

        const objects: CatalogObject[] = [];
        for (const entry of entries) {
            const permission = this.authorization.resolve(entry.id, ctx);
            if (!permission.read) {
                continue;
            }
            const mapping = await this.getOrCreateMapping(entry.id);
            const valueType = mapValueType(entry.type);
            objects.push({
                id: mapping.id,
                name: permission.displayName ?? entry.name,
                path: entry.path.slice(0, -1),
                role: entry.role,
                valueType,
                unit: entry.unit,
                read: permission.read,
                write: permission.write,
                history: permission.history,
                min: permission.min,
                max: permission.max,
                step: permission.step,
                allowedValues: permission.allowedValues,
                localOnly: permission.localOnly,
                confirmPolicy: permission.confirmPolicy,
                suggestedWidgets: permission.suggestedWidgets ?? suggestWidgets(entry.role, valueType),
            });
        }
        return { version, objects };
    }
}
