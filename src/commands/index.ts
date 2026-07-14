import { EventEmitter } from 'node:events';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import type { CommandRecord } from '../lib/types';
import type { CatalogService } from '../catalog';
import type { AuthContext, EffectivePermission } from '../authorization';
import type { AuditService } from '../audit';
import type { RateLimiter } from '../security/rateLimiter';
import type { ReplayGuard } from '../security/replayGuard';

export interface CommandRequest {
    commandId: string;
    objectId: string;
    value: unknown;
    timestamp: string;
    nonce: string;
    /** set by the app after the user completed a DIALOG/BIOMETRIC/REAUTHENTICATE confirmation */
    confirmed?: boolean;
}

export interface CommandExecutionContext extends AuthContext {
    ip: string | null;
    isLocalNetwork: boolean;
}

export interface CommandResultEvent {
    deviceId: string;
    commandId: string;
    status: CommandRecord['status'];
}

const CONFIRMATION_TIMEOUT_MS = 10_000;

interface PendingConfirmation {
    commandId: string;
    timer: NodeJS.Timeout;
}

function validateValue(
    value: unknown,
    expectedType: string | undefined,
    permission: Pick<EffectivePermission, 'min' | 'max' | 'step' | 'allowedValues'>,
): { ok: true } | { ok: false; reason: string } {
    if (expectedType === 'boolean' && typeof value !== 'boolean') {
        return { ok: false, reason: 'expected boolean value' };
    }
    if (expectedType === 'number' && typeof value !== 'number') {
        return { ok: false, reason: 'expected numeric value' };
    }
    if (expectedType === 'string' && typeof value !== 'string') {
        return { ok: false, reason: 'expected string value' };
    }

    if (typeof value === 'number') {
        if (permission.min !== null && value < permission.min) {
            return { ok: false, reason: `value below minimum ${permission.min}` };
        }
        if (permission.max !== null && value > permission.max) {
            return { ok: false, reason: `value above maximum ${permission.max}` };
        }
        if (permission.step !== null && permission.step > 0 && permission.min !== null) {
            const stepsFromMin = (value - permission.min) / permission.step;
            if (Math.abs(stepsFromMin - Math.round(stepsFromMin)) > 1e-6) {
                return { ok: false, reason: `value does not match step ${permission.step}` };
            }
        }
    }

    if (permission.allowedValues && !permission.allowedValues.includes(value as string | number | boolean)) {
        return { ok: false, reason: 'value not in allowed set' };
    }

    return { ok: true };
}

/**
 * Actuator command pipeline (BACKEND-KONZEPT.md §10 / MASTERKONZEPT.md §16).
 * Emits "commandResult" events ({deviceId, commandId, status}) for the realtime
 * gateway to push over WebSocket - kept decoupled via EventEmitter instead of a
 * direct dependency on the WebSocket layer.
 */
export class CommandsService extends EventEmitter {
    private readonly pendingByStateId = new Map<string, PendingConfirmation>();
    private readonly subscribedStateIds = new Set<string>();

    constructor(
        private readonly adapter: ioBroker.Adapter,
        private readonly store: CollectionStore<CommandRecord>,
        private readonly catalog: CatalogService,
        private readonly audit: AuditService,
        private readonly rateLimiter: RateLimiter,
        private readonly replayGuard: ReplayGuard,
    ) {
        super();
    }

    async execute(ctx: CommandExecutionContext, request: CommandRequest): Promise<CommandRecord> {
        const existing = this.store.get(request.commandId);
        if (existing) {
            return existing; // idempotent: client retried the same commandId
        }

        if (!this.rateLimiter.consume(ctx.deviceId)) {
            await this.logRejection(ctx, request.objectId, 'RATE_LIMITED');
            throw new ApiError('RATE_LIMITED');
        }

        if (!this.replayGuard.checkAndRemember(`${ctx.deviceId}:${request.nonce}`)) {
            await this.logRejection(ctx, request.objectId, 'REPLAY_DETECTED');
            throw new ApiError('REPLAY_DETECTED');
        }

        let stateId: string;
        let permission: EffectivePermission;
        try {
            ({ stateId, permission } = this.catalog.resolveAuthorized(request.objectId, ctx, 'write'));
        } catch (err) {
            await this.logRejection(ctx, request.objectId, err instanceof ApiError ? err.code : 'WRITE_FORBIDDEN');
            throw err;
        }

        if (permission.confirmPolicy === 'BLOCKED_ON_MOBILE') {
            await this.logRejection(ctx, request.objectId, 'WRITE_FORBIDDEN');
            throw new ApiError('WRITE_FORBIDDEN', 'blocked by confirmation policy');
        }
        if (permission.localOnly && !ctx.isLocalNetwork) {
            await this.logRejection(ctx, request.objectId, 'LOCAL_ONLY');
            throw new ApiError('LOCAL_ONLY');
        }
        const needsExplicitConfirmation =
            permission.confirmPolicy === 'DIALOG' || permission.confirmPolicy === 'BIOMETRIC' || permission.confirmPolicy === 'REAUTHENTICATE';
        if (needsExplicitConfirmation && request.confirmed !== true) {
            await this.logRejection(ctx, request.objectId, 'CONFIRMATION_REQUIRED');
            throw new ApiError('CONFIRMATION_REQUIRED');
        }

        const object = await this.adapter.getForeignObjectAsync(stateId);
        const expectedType = (object?.common as ioBroker.StateCommon | undefined)?.type;
        const validation = validateValue(request.value, expectedType, permission);
        if (!validation.ok) {
            await this.logRejection(ctx, request.objectId, 'VALUE_INVALID', validation.reason);
            throw new ApiError('VALUE_INVALID', validation.reason);
        }

        const record: CommandRecord = {
            id: request.commandId,
            objectId: request.objectId,
            stateId,
            deviceId: ctx.deviceId,
            userId: ctx.userId,
            value: request.value,
            status: 'accepted',
            reason: null,
            createdAt: Date.now(),
            updatedAt: Date.now(),
        };
        await this.store.put(record);
        await this.audit.log({
            action: 'command.accepted',
            actorUserId: ctx.userId,
            actorDeviceId: ctx.deviceId,
            objectId: request.objectId,
            result: 'success',
            ip: ctx.ip,
        });

        try {
            await this.adapter.setForeignStateAsync(stateId, { val: request.value as ioBroker.StateValue, ack: false });
        } catch (err) {
            const failed: CommandRecord = { ...record, status: 'rejected', reason: (err as Error).message, updatedAt: Date.now() };
            await this.store.put(failed);
            await this.audit.log({
                action: 'command.failed',
                actorUserId: ctx.userId,
                actorDeviceId: ctx.deviceId,
                objectId: request.objectId,
                result: 'failure',
                detail: (err as Error).message,
                ip: ctx.ip,
            });
            this.emit('commandResult', { deviceId: ctx.deviceId, commandId: failed.id, status: 'rejected' } satisfies CommandResultEvent);
            throw new ApiError('SERVER_UNAVAILABLE', 'failed to write state');
        }

        const executedRecord: CommandRecord = { ...record, status: 'executed', updatedAt: Date.now() };
        await this.store.put(executedRecord);
        this.emit('commandResult', { deviceId: ctx.deviceId, commandId: executedRecord.id, status: 'executed' } satisfies CommandResultEvent);

        void this.awaitConfirmation(stateId, executedRecord.id);

        return executedRecord;
    }

    /** Wired from main.ts's adapter.on('stateChange', ...) for every subscribed state. */
    async handleForeignStateChange(stateId: string, state: ioBroker.State | null | undefined): Promise<void> {
        if (!state?.ack) {
            return;
        }
        const pending = this.pendingByStateId.get(stateId);
        if (!pending) {
            return;
        }
        clearTimeout(pending.timer);
        this.pendingByStateId.delete(stateId);

        const record = this.store.get(pending.commandId);
        if (!record || record.status !== 'executed') {
            return;
        }
        const confirmed: CommandRecord = { ...record, status: 'confirmed', updatedAt: Date.now() };
        await this.store.put(confirmed);
        await this.audit.log({
            action: 'command.confirmed',
            actorUserId: record.userId,
            actorDeviceId: record.deviceId,
            objectId: record.objectId,
            result: 'success',
        });
        this.emit('commandResult', { deviceId: record.deviceId, commandId: record.id, status: 'confirmed' } satisfies CommandResultEvent);
    }

    /**
     * Only one pending confirmation is tracked per stateId at a time - a second command to
     * the same actuator before the first confirms will replace the pending wait (documented
     * MVP simplification; acceptable since actuator writes are rarely pipelined per-state).
     */
    private async awaitConfirmation(stateId: string, commandId: string): Promise<void> {
        if (!this.subscribedStateIds.has(stateId)) {
            try {
                await this.adapter.subscribeForeignStatesAsync(stateId);
                this.subscribedStateIds.add(stateId);
            } catch (err) {
                this.adapter.log.warn(`mobile-control: failed to subscribe to ${stateId} for command confirmation: ${(err as Error).message}`);
            }
        }

        const previous = this.pendingByStateId.get(stateId);
        if (previous) {
            clearTimeout(previous.timer);
        }
        const timer = setTimeout(() => {
            void this.timeoutCommand(stateId);
        }, CONFIRMATION_TIMEOUT_MS);
        this.pendingByStateId.set(stateId, { commandId, timer });
    }

    private async timeoutCommand(stateId: string): Promise<void> {
        const pending = this.pendingByStateId.get(stateId);
        if (!pending) {
            return;
        }
        this.pendingByStateId.delete(stateId);

        const record = this.store.get(pending.commandId);
        if (!record || record.status !== 'executed') {
            return;
        }
        const timedOut: CommandRecord = { ...record, status: 'timeout', updatedAt: Date.now() };
        await this.store.put(timedOut);
        await this.audit.log({
            action: 'command.timeout',
            actorUserId: record.userId,
            actorDeviceId: record.deviceId,
            objectId: record.objectId,
            result: 'failure',
        });
        this.emit('commandResult', { deviceId: record.deviceId, commandId: record.id, status: 'timeout' } satisfies CommandResultEvent);
    }

    private async logRejection(ctx: CommandExecutionContext, objectId: string, code: string, detail?: string): Promise<void> {
        await this.audit.log({
            action: 'command.rejected',
            actorUserId: ctx.userId,
            actorDeviceId: ctx.deviceId,
            objectId,
            result: 'failure',
            detail: detail ? `${code}: ${detail}` : code,
            ip: ctx.ip,
        });
    }
}
