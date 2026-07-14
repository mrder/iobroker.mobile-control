import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { User } from '../lib/types';
import type { RolesService } from '../roles';

export class UsersService {
    constructor(
        private readonly store: CollectionStore<User>,
        private readonly roles: RolesService,
    ) {}

    list(): User[] {
        return this.store.list();
    }

    get(id: string): User | undefined {
        return this.store.get(id);
    }

    require(id: string): User {
        const user = this.store.get(id);
        if (!user) {
            throw new ApiError('NOT_FOUND', `user ${id} not found`);
        }
        return user;
    }

    async create(name: string, roleId: string): Promise<User> {
        this.roles.require(roleId);
        const user: User = { id: uuid(), name, roleId, disabled: false, createdAt: Date.now() };
        await this.store.put(user);
        return user;
    }

    async rename(id: string, name: string): Promise<User> {
        const user = this.require(id);
        const updated: User = { ...user, name };
        await this.store.put(updated);
        return updated;
    }

    async setRole(id: string, roleId: string): Promise<User> {
        this.roles.require(roleId);
        const user = this.require(id);
        const updated: User = { ...user, roleId };
        await this.store.put(updated);
        return updated;
    }

    async setDisabled(id: string, disabled: boolean): Promise<User> {
        const user = this.require(id);
        const updated: User = { ...user, disabled };
        await this.store.put(updated);
        return updated;
    }

    async delete(id: string): Promise<void> {
        await this.store.delete(id);
    }
}
