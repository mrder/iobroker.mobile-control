import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { Device, DeviceStatus } from '../lib/types';

export type NewDevice = Pick<Device, 'userId' | 'roleId' | 'name' | 'platform' | 'appVersion' | 'publicKey' | 'fingerprint'>;

export class DevicesService {
    constructor(private readonly store: CollectionStore<Device>) {}

    list(): Device[] {
        return this.store.list();
    }

    get(id: string): Device | undefined {
        return this.store.get(id);
    }

    require(id: string): Device {
        const device = this.store.get(id);
        if (!device) {
            throw new ApiError('NOT_FOUND', `device ${id} not found`);
        }
        return device;
    }

    listForUser(userId: string): Device[] {
        return this.store.find((d) => d.userId === userId);
    }

    listPending(): Device[] {
        return this.store.find((d) => d.status === 'pending');
    }

    async register(data: NewDevice): Promise<Device> {
        const device: Device = {
            ...data,
            id: uuid(),
            status: 'pending',
            createdAt: Date.now(),
            lastSeenAt: null,
            lastIp: null,
        };
        await this.store.put(device);
        return device;
    }

    async setStatus(id: string, status: DeviceStatus): Promise<Device> {
        const device = this.require(id);
        const updated: Device = { ...device, status };
        await this.store.put(updated);
        return updated;
    }

    async approve(id: string): Promise<Device> {
        return this.setStatus(id, 'approved');
    }

    async reject(id: string): Promise<Device> {
        return this.setStatus(id, 'rejected');
    }

    /** Immediate, unconditional access removal (SECURITY.md: revocation takes effect instantly). */
    async revoke(id: string): Promise<Device> {
        return this.setStatus(id, 'revoked');
    }

    isUsable(device: Device): boolean {
        return device.status === 'approved';
    }

    async touch(id: string, ip: string | null): Promise<void> {
        const device = this.store.get(id);
        if (!device) {
            return;
        }
        await this.store.put({ ...device, lastSeenAt: Date.now(), lastIp: ip });
    }
}
