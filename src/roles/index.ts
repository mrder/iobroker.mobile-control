import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { Role } from '../lib/types';

const BUILT_IN_ROLES: Array<Pick<Role, 'id' | 'name'>> = [
    { id: 'administrator', name: 'Administrator' },
    { id: 'operator', name: 'Bediener' },
    { id: 'viewer', name: 'Betrachter' },
    { id: 'guest', name: 'Gast' },
];

export class RolesService {
    constructor(private readonly store: CollectionStore<Role>) {}

    /** Seeds the four fixed roles from MASTERKONZEPT.md §8 if missing. Idempotent. */
    async ensureBuiltInRoles(): Promise<void> {
        for (const role of BUILT_IN_ROLES) {
            if (!this.store.has(role.id)) {
                await this.store.put({ ...role, builtIn: true, createdAt: Date.now() });
            }
        }
    }

    list(): Role[] {
        return this.store.list();
    }

    get(id: string): Role | undefined {
        return this.store.get(id);
    }

    require(id: string): Role {
        const role = this.store.get(id);
        if (!role) {
            throw new ApiError('NOT_FOUND', `role ${id} not found`);
        }
        return role;
    }

    async create(name: string): Promise<Role> {
        const role: Role = { id: uuid(), name, builtIn: false, createdAt: Date.now() };
        await this.store.put(role);
        return role;
    }

    async rename(id: string, name: string): Promise<Role> {
        const role = this.require(id);
        const updated: Role = { ...role, name };
        await this.store.put(updated);
        return updated;
    }

    async delete(id: string): Promise<void> {
        const role = this.store.get(id);
        if (!role) {
            return;
        }
        if (role.builtIn) {
            throw new ApiError('VALIDATION_ERROR', 'built-in roles cannot be deleted');
        }
        await this.store.delete(id);
    }
}
