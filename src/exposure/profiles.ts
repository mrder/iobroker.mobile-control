import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { ExposureProfile, ExposureRule, ExposureRuleTemplate } from '../lib/types';
import type { ExposureService } from './index';

export type OwnerType = 'role' | 'user' | 'device';

function toOwnerFields(ownerType: OwnerType, ownerId: string): Pick<ExposureRule, 'roleId' | 'userId' | 'deviceId'> {
    return {
        roleId: ownerType === 'role' ? ownerId : null,
        userId: ownerType === 'user' ? ownerId : null,
        deviceId: ownerType === 'device' ? ownerId : null,
    };
}

function toTemplate(rule: ExposureRule): ExposureRuleTemplate {
    return {
        scope: rule.scope,
        target: rule.target,
        deny: rule.deny,
        read: rule.read,
        write: rule.write,
        history: rule.history,
        min: rule.min,
        max: rule.max,
        step: rule.step,
        allowedValues: rule.allowedValues,
        localOnly: rule.localOnly,
        confirmPolicy: rule.confirmPolicy,
        displayName: rule.displayName,
        suggestedWidgets: rule.suggestedWidgets,
    };
}

/**
 * Reusable, named bundles of exposure rules (BACKEND-KONZEPT.md §12 "Freigabeprofile").
 * A profile itself grants nothing - applying it to a role/user/device materializes concrete
 * ExposureRule rows owned by that target, going through the normal ExposureService so the
 * usual single-owner validation and authorization priority rules apply unchanged.
 */
export class ExposureProfilesService {
    constructor(
        private readonly store: CollectionStore<ExposureProfile>,
        private readonly exposure: ExposureService,
    ) {}

    list(): ExposureProfile[] {
        return this.store.list();
    }

    get(id: string): ExposureProfile | undefined {
        return this.store.get(id);
    }

    require(id: string): ExposureProfile {
        const profile = this.store.get(id);
        if (!profile) {
            throw new ApiError('NOT_FOUND', `exposure profile ${id} not found`);
        }
        return profile;
    }

    async create(name: string, description: string | null, rules: ExposureRuleTemplate[]): Promise<ExposureProfile> {
        const profile: ExposureProfile = { id: uuid(), name, description, rules, createdAt: Date.now() };
        await this.store.put(profile);
        return profile;
    }

    /** Snapshots every current rule owned by (ownerType, ownerId) into a brand new profile. */
    async createFromOwner(name: string, description: string | null, ownerType: OwnerType, ownerId: string): Promise<ExposureProfile> {
        const owned = this.exposure.list().filter((rule) => {
            if (ownerType === 'role') return rule.roleId === ownerId;
            if (ownerType === 'user') return rule.userId === ownerId;
            return rule.deviceId === ownerId;
        });
        return this.create(name, description, owned.map(toTemplate));
    }

    async rename(id: string, name: string, description: string | null): Promise<ExposureProfile> {
        const profile = this.require(id);
        const updated: ExposureProfile = { ...profile, name, description };
        await this.store.put(updated);
        return updated;
    }

    async delete(id: string): Promise<void> {
        await this.store.delete(id);
    }

    /** Materializes every rule template in the profile as a new ExposureRule owned by the given target. */
    async applyTo(profileId: string, ownerType: OwnerType, ownerId: string): Promise<ExposureRule[]> {
        const profile = this.require(profileId);
        const owner = toOwnerFields(ownerType, ownerId);
        const created: ExposureRule[] = [];
        for (const template of profile.rules) {
            created.push(await this.exposure.create({ ...template, ...owner }));
        }
        return created;
    }
}
