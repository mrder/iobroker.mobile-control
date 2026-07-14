import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { Dashboard, DashboardLayout, PublicObjectMapping } from '../lib/types';
import type { AuthorizationService } from '../authorization';
import type { UsersService } from '../users';

export interface DashboardPatch {
    name?: string;
    layouts?: DashboardLayout[];
    isStartDashboard?: boolean;
}

export class DashboardsService {
    constructor(
        private readonly store: CollectionStore<Dashboard>,
        private readonly mappings: CollectionStore<PublicObjectMapping>,
        private readonly authorization: AuthorizationService,
        private readonly users: UsersService,
    ) {}

    listForUser(userId: string): Dashboard[] {
        return this.store.find((d) => d.userId === userId);
    }

    get(userId: string, id: string): Dashboard {
        const dashboard = this.store.get(id);
        if (!dashboard || dashboard.userId !== userId) {
            throw new ApiError('NOT_FOUND', `dashboard ${id} not found`);
        }
        return dashboard;
    }

    async create(userId: string, name: string): Promise<Dashboard> {
        const dashboard: Dashboard = {
            id: uuid(),
            userId,
            name,
            revision: 1,
            layouts: [{ sizeClass: 'compact', columns: 4, widgets: [] }],
            isStartDashboard: this.listForUser(userId).length === 0,
            createdAt: Date.now(),
            updatedAt: Date.now(),
        };
        await this.store.put(dashboard);
        return dashboard;
    }

    async update(userId: string, id: string, patch: DashboardPatch, expectedRevision: number): Promise<Dashboard> {
        const existing = this.get(userId, id);
        if (existing.revision !== expectedRevision) {
            throw new ApiError('REVISION_CONFLICT', `dashboard ${id} was modified concurrently`);
        }

        const updated: Dashboard = {
            ...existing,
            ...patch,
            revision: existing.revision + 1,
            updatedAt: Date.now(),
        };

        if (patch.layouts) {
            this.validateWidgetReferences(userId, updated);
        }

        await this.store.put(updated);
        return updated;
    }

    async delete(userId: string, id: string): Promise<void> {
        this.get(userId, id); // throws NOT_FOUND if missing/not owner
        await this.store.delete(id);
    }

    /** BACKEND-KONZEPT.md §9: every referenced object UUID must still be visible to the owning user. */
    private validateWidgetReferences(userId: string, dashboard: Dashboard): void {
        const user = this.users.require(userId);
        for (const layout of dashboard.layouts) {
            for (const widget of layout.widgets) {
                if (!widget.objectId) {
                    continue;
                }
                const mapping = this.mappings.get(widget.objectId);
                if (!mapping) {
                    throw new ApiError('OBJECT_NOT_FOUND', `widget references unknown object ${widget.objectId}`);
                }
                const permission = this.authorization.resolve(mapping.stateId, {
                    userId,
                    roleId: user.roleId,
                    deviceId: '',
                });
                if (!permission.read) {
                    throw new ApiError('OBJECT_NOT_FOUND', `object ${widget.objectId} is not visible to this user`);
                }
            }
        }
    }
}
