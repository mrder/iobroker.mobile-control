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
    return type === 'number' || type === 'string' || type === 'boolean' || type === 'json' ? type : 'mixed';
}

function suggestWidgets(role: string, valueType: CatalogObject['valueType']): string[] {
    if (role.includes('alarm')) {
        return ['alarm', 'status'];
    }
    if (role.includes('camera')) {
        return ['camera'];
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
    /** Display name for every folder id that has at least one visible object beneath it (dot-
     *  joined path prefix -> ioBroker common.name), so a client can label "zigbee.0.00124b..."
     *  as e.g. "SNZB-03 Bewegungsmelder Briefkasten" instead of the bare id segment. Deliberately
     *  NOT the full container list from browseObjectTree(): only folders that actually have a
     *  visible descendant here are included, so a folder with zero exposed content never leaks
     *  its name to a client with no access to anything inside it. */
    folderNames: Record<string, string>;
}

export class CatalogService {
    constructor(
        private readonly adapter: ioBroker.Adapter,
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

    /** Authorization check by the real ioBroker id rather than a public UUID - used where the
     *  caller already has the internal stateId (e.g. AlarmEventsService's persisted events),
     *  unlike resolveAuthorized() which starts from a client-facing public mapping id. */
    canRead(stateId: string, ctx: AuthContext): boolean {
        return this.authorization.canRead(stateId, ctx);
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

        const containerNames = new Map<string, string>();
        for (const entry of entries) {
            if (entry.kind === 'container') {
                containerNames.set(entry.id, entry.name);
            }
        }

        const objects: CatalogObject[] = [];
        for (const entry of entries) {
            // Same isolation principle as ExposureService.browseObjectTree(): one entry whose
            // permission resolution or mapping throws must not wipe every other category out of
            // the catalog for this client - skip and log it instead.
            try {
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
            } catch (err) {
                this.adapter.log.warn(`mobile-control: skipping "${entry.id}" while building the catalog: ${(err as Error).message}`);
            }
        }

        const folderNames: Record<string, string> = {};
        for (const obj of objects) {
            let prefix = '';
            for (const segment of obj.path) {
                prefix = prefix ? `${prefix}.${segment}` : segment;
                if (!(prefix in folderNames)) {
                    const name = containerNames.get(prefix);
                    if (name) {
                        folderNames[prefix] = name;
                    }
                }
            }
        }

        return { version, objects, folderNames };
    }
}
