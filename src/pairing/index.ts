import { randomBytes } from 'node:crypto';
import { v4 as uuid } from 'uuid';
import * as QRCode from 'qrcode';
import { CollectionStore } from '../lib/store';
import { ApiError } from '../lib/errors';
import { sha256Hex } from '../security/tokens';
import type { PairingInvite, PairingClaim, PairingClaimStatus } from '../lib/types';
import type { UsersService } from '../users';
import type { RolesService } from '../roles';
import type { DevicesService } from '../devices';

export interface PairingConfig {
    publicUrl: string;
    instanceId: string;
    serverFingerprint: string;
    inviteTtlMinutes: number;
    requireAdminApproval: boolean;
}

export interface QrPayload {
    version: 1;
    serverUrl: string;
    pairingId: string;
    pairingSecret: string;
    expiresAt: string;
    serverFingerprint: string;
    instanceId: string;
}

export interface CreatedInvite {
    invite: PairingInvite;
    qrPayload: QrPayload;
    qrPngDataUrl: string;
}

export interface ClaimParams {
    pairingId: string;
    pairingSecret: string;
    deviceName: string;
    platform: string;
    appVersion: string;
    publicKey: string;
}

export class PairingService {
    constructor(
        private readonly invites: CollectionStore<PairingInvite>,
        private readonly claims: CollectionStore<PairingClaim>,
        private readonly users: UsersService,
        private readonly roles: RolesService,
        private readonly devices: DevicesService,
        private readonly config: PairingConfig,
    ) {}

    async createInvite(userId: string, roleId: string): Promise<CreatedInvite> {
        this.users.require(userId);
        this.roles.require(roleId);

        const secret = randomBytes(32).toString('base64url');
        const id = uuid();
        const expiresAt = Date.now() + this.config.inviteTtlMinutes * 60_000;

        const invite: PairingInvite = {
            id,
            secretHash: sha256Hex(secret),
            userId,
            roleId,
            expiresAt,
            used: false,
            createdAt: Date.now(),
        };
        await this.invites.put(invite);

        const qrPayload: QrPayload = {
            version: 1,
            serverUrl: this.config.publicUrl,
            pairingId: id,
            pairingSecret: secret,
            expiresAt: new Date(expiresAt).toISOString(),
            serverFingerprint: this.config.serverFingerprint,
            instanceId: this.config.instanceId,
        };
        const qrPngDataUrl = await QRCode.toDataURL(JSON.stringify(qrPayload));

        return { invite, qrPayload, qrPngDataUrl };
    }

    async claim(params: ClaimParams): Promise<{ claimId: string; status: PairingClaimStatus }> {
        const invite = this.invites.get(params.pairingId);
        if (!invite) {
            throw new ApiError('PAIRING_INVALID', 'unknown pairing id');
        }
        if (invite.used) {
            throw new ApiError('PAIRING_INVALID', 'pairing invite already used');
        }
        if (invite.expiresAt < Date.now()) {
            throw new ApiError('PAIRING_EXPIRED');
        }
        if (sha256Hex(params.pairingSecret) !== invite.secretHash) {
            throw new ApiError('PAIRING_INVALID', 'secret mismatch');
        }

        // single-use: mark consumed immediately, before any further processing
        await this.invites.put({ ...invite, used: true });

        const fingerprint = sha256Hex(params.publicKey);
        const device = await this.devices.register({
            userId: invite.userId,
            roleId: invite.roleId,
            name: params.deviceName,
            platform: params.platform,
            appVersion: params.appVersion,
            publicKey: params.publicKey,
            fingerprint,
        });

        let status: PairingClaimStatus = 'waiting_for_approval';
        if (!this.config.requireAdminApproval) {
            await this.devices.approve(device.id);
            status = 'approved';
        }

        const claim: PairingClaim = {
            id: uuid(),
            inviteId: invite.id,
            deviceName: params.deviceName,
            platform: params.platform,
            appVersion: params.appVersion,
            publicKey: params.publicKey,
            status,
            deviceId: device.id,
            tokensIssued: false,
            createdAt: Date.now(),
            expiresAt: Date.now() + this.config.inviteTtlMinutes * 60_000,
        };
        await this.claims.put(claim);

        return { claimId: claim.id, status: claim.status };
    }

    async status(claimId: string): Promise<PairingClaim> {
        const claim = this.claims.get(claimId);
        if (!claim) {
            throw new ApiError('NOT_FOUND', 'claim not found');
        }
        if (claim.status === 'waiting_for_approval' && claim.expiresAt < Date.now()) {
            const expired: PairingClaim = { ...claim, status: 'expired' };
            await this.claims.put(expired);
            return expired;
        }
        return claim;
    }

    /** Marks that the one-time token delivery for this claim has happened. */
    async markTokensIssued(claimId: string): Promise<void> {
        const claim = this.claims.get(claimId);
        if (!claim) {
            return;
        }
        await this.claims.put({ ...claim, tokensIssued: true });
    }

    listPendingClaims(): PairingClaim[] {
        return this.claims.find((c) => c.status === 'waiting_for_approval');
    }

    async approveClaim(claimId: string): Promise<PairingClaim> {
        const claim = this.claims.get(claimId);
        if (!claim) {
            throw new ApiError('NOT_FOUND', 'claim not found');
        }
        if (!claim.deviceId) {
            throw new ApiError('VALIDATION_ERROR', 'claim has no associated device');
        }
        await this.devices.approve(claim.deviceId);
        const updated: PairingClaim = { ...claim, status: 'approved' };
        await this.claims.put(updated);
        return updated;
    }

    async rejectClaim(claimId: string): Promise<PairingClaim> {
        const claim = this.claims.get(claimId);
        if (!claim) {
            throw new ApiError('NOT_FOUND', 'claim not found');
        }
        if (claim.deviceId) {
            await this.devices.reject(claim.deviceId);
        }
        const updated: PairingClaim = { ...claim, status: 'rejected' };
        await this.claims.put(updated);
        return updated;
    }
}
