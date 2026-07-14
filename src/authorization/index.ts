import type { ConfirmPolicy, ExposureRule } from '../lib/types';
import type { ExposureService } from '../exposure';

export interface AuthContext {
    userId: string;
    deviceId: string;
    roleId: string;
}

export interface EffectivePermission {
    read: boolean;
    write: boolean;
    history: boolean;
    min: number | null;
    max: number | null;
    step: number | null;
    allowedValues: (string | number | boolean)[] | null;
    localOnly: boolean;
    confirmPolicy: ConfirmPolicy;
    displayName: string | null;
    suggestedWidgets: string[] | null;
}

const DENY_PERMISSION: EffectivePermission = {
    read: false,
    write: false,
    history: false,
    min: null,
    max: null,
    step: null,
    allowedValues: null,
    localOnly: false,
    confirmPolicy: 'NONE',
    displayName: null,
    suggestedWidgets: null,
};

function toPermission(rule: ExposureRule): EffectivePermission {
    return {
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
 * Priority per MASTERKONZEPT.md §8:
 *   explizites Verbot > explizite Gerätefreigabe > explizite Benutzerfreigabe > Rollenfreigabe > Standardverbot
 * An explicit deny at ANY level (device/user/role) wins outright, before grant priority is considered.
 */
export class AuthorizationService {
    constructor(private readonly exposure: ExposureService) {}

    resolve(stateId: string, ctx: AuthContext): EffectivePermission {
        const rules = this.exposure.matchingRules(stateId).filter(
            (rule) =>
                rule.deviceId === ctx.deviceId ||
                (rule.userId === ctx.userId && !rule.deviceId) ||
                (rule.roleId === ctx.roleId && !rule.userId && !rule.deviceId),
        );

        if (rules.some((rule) => rule.deny)) {
            return DENY_PERMISSION;
        }

        const deviceRule = rules.find((rule) => rule.deviceId === ctx.deviceId);
        const userRule = rules.find((rule) => rule.userId === ctx.userId && !rule.deviceId);
        const roleRule = rules.find((rule) => rule.roleId === ctx.roleId && !rule.userId && !rule.deviceId);

        const chosen = deviceRule ?? userRule ?? roleRule;
        return chosen ? toPermission(chosen) : DENY_PERMISSION;
    }

    canRead(stateId: string, ctx: AuthContext): boolean {
        return this.resolve(stateId, ctx).read;
    }

    canWrite(stateId: string, ctx: AuthContext): boolean {
        return this.resolve(stateId, ctx).write;
    }
}
