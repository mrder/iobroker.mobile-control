export interface Role {
    id: string;
    name: string;
    builtIn: boolean;
    createdAt: number;
}

export interface User {
    id: string;
    name: string;
    roleId: string;
    disabled: boolean;
    createdAt: number;
}

export type DeviceStatus = 'pending' | 'approved' | 'rejected' | 'revoked';

export interface Device {
    id: string;
    userId: string;
    roleId: string | null;
    name: string;
    platform: string;
    appVersion: string;
    fingerprint: string;
    status: DeviceStatus;
    createdAt: number;
    lastSeenAt: number | null;
    lastIp: string | null;
}

export interface PairingClaim {
    id: string;
    inviteId: string;
    deviceName: string;
    platform: string;
    appVersion: string;
    status: 'waiting_for_approval' | 'approved' | 'rejected' | 'expired';
    deviceId: string | null;
    createdAt: number;
}

export interface CreatedInvite {
    invite: { id: string; userId: string; roleId: string; expiresAt: number };
    qrPayload: Record<string, unknown>;
    qrPngDataUrl: string;
}

export type ConfirmPolicy = 'NONE' | 'DIALOG' | 'BIOMETRIC' | 'REAUTHENTICATE' | 'LOCAL_NETWORK_ONLY' | 'BLOCKED_ON_MOBILE';

export interface ExposureRule {
    id: string;
    scope: 'adapter' | 'device' | 'channel' | 'state' | 'group' | 'alias' | 'pattern';
    target: string;
    roleId: string | null;
    userId: string | null;
    deviceId: string | null;
    deny: boolean;
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
    createdAt: number;
}

export interface ObjectTreeEntry {
    id: string;
    name: string;
    role: string;
    type: string;
    unit: string | null;
    path: string[];
    kind: 'state' | 'container';
}

export interface Session {
    id: string;
    userId: string;
    deviceId: string;
    roleId: string;
    createdAt: number;
    lastActivityAt: number;
    expiresAt: number;
    revoked: boolean;
    lastIp: string | null;
}

export interface AuditEvent {
    id: string;
    timestamp: number;
    action: string;
    actorUserId: string | null;
    actorDeviceId: string | null;
    sessionId: string | null;
    objectId: string | null;
    result: 'success' | 'failure';
    detail: string | null;
    ip: string | null;
}

export interface Overview {
    users: number;
    devices: number;
    pendingClaims: number;
    activeSessions: number;
    connectedDevices: number;
}

export interface ConnectionInfo {
    port: number;
    bindAddress: string;
    publicUrl: string;
    localAddresses: string[];
}

export interface UrlEmbed {
    id: string;
    name: string;
    url: string;
    createdAt: number;
}

export interface AbuseSnapshotEntry {
    key: string;
    failures: number;
    blocked: boolean;
    blockedUntil: number | null;
    windowStart: number;
    lastReason: string | null;
}

export type ExposureRuleTemplate = Omit<ExposureRule, 'id' | 'roleId' | 'userId' | 'deviceId' | 'createdAt'>;

export interface ExposureProfile {
    id: string;
    name: string;
    description: string | null;
    rules: ExposureRuleTemplate[];
    createdAt: number;
}

export interface CatalogObject {
    id: string;
    name: string;
    path: string[];
    role: string;
    valueType: string;
    unit: string | null;
    read: boolean;
    write: boolean;
    history: boolean;
    suggestedWidgets: string[];
}

export interface EffectiveCatalog {
    version: number;
    objects: CatalogObject[];
}
