export type RoleId = 'administrator' | 'operator' | 'viewer' | 'guest' | string;

export interface Role {
    id: RoleId;
    name: string;
    builtIn: boolean;
    createdAt: number;
}

export interface User {
    id: string;
    name: string;
    roleId: RoleId;
    disabled: boolean;
    createdAt: number;
}

export type DeviceStatus = 'pending' | 'approved' | 'rejected' | 'revoked';

export interface Device {
    id: string;
    userId: string;
    roleId: RoleId | null;
    name: string;
    platform: string;
    appVersion: string;
    /** SPKI-encoded EC P-256 public key, base64 */
    publicKey: string;
    fingerprint: string;
    status: DeviceStatus;
    createdAt: number;
    lastSeenAt: number | null;
    lastIp: string | null;
}

export type PairingClaimStatus = 'waiting_for_approval' | 'approved' | 'rejected' | 'expired';

export interface PairingInvite {
    id: string;
    /** sha256 hex of the single-use secret, never store the secret itself */
    secretHash: string;
    userId: string;
    roleId: RoleId;
    expiresAt: number;
    used: boolean;
    createdAt: number;
}

export interface PairingClaim {
    id: string;
    inviteId: string;
    deviceName: string;
    platform: string;
    appVersion: string;
    publicKey: string;
    status: PairingClaimStatus;
    deviceId: string | null;
    /** tokens can only be collected once via the status endpoint (refresh tokens are never stored in plaintext) */
    tokensIssued: boolean;
    createdAt: number;
    expiresAt: number;
}

export interface Session {
    id: string;
    userId: string;
    deviceId: string;
    roleId: RoleId;
    tokenFamily: string;
    /** sha256 hex of the current valid refresh token */
    refreshTokenHash: string;
    /** sha256 hex of the immediately-preceding refresh token, kept one generation back to detect reuse */
    previousRefreshTokenHash: string | null;
    refreshGeneration: number;
    createdAt: number;
    lastActivityAt: number;
    expiresAt: number;
    revoked: boolean;
    lastIp: string | null;
    userAgent: string | null;
}

export type ExposureScope = 'adapter' | 'device' | 'channel' | 'state' | 'group' | 'alias' | 'pattern';

export type ConfirmPolicy = 'NONE' | 'DIALOG' | 'BIOMETRIC' | 'REAUTHENTICATE' | 'LOCAL_NETWORK_ONLY' | 'BLOCKED_ON_MOBILE';

export interface ExposureRule {
    id: string;
    scope: ExposureScope;
    /** ioBroker state id, id-prefix, or glob pattern depending on scope */
    target: string;
    /** exactly one of these three should be set; role-only rules apply to everyone with that role */
    roleId: RoleId | null;
    userId: string | null;
    deviceId: string | null;
    /** explicit deny always wins regardless of read/write */
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

/**
 * Admin-managed allowlist entry for embedding an external URL (a device's local web UI, a
 * screenshot/snapshot endpoint that isn't backed by any ioBroker state) into a dashboard widget.
 * Deliberately NOT a generic proxy: a paired device only ever learns {id, name} via the list
 * endpoint and can only ever fetch/resolve a URL by an id it already knows from that list - never
 * by supplying a URL itself. See src/urlEmbeds/index.ts.
 */
export interface UrlEmbed {
    id: string;
    name: string;
    url: string;
    createdAt: number;
}

/**
 * Who may see/use a given UrlEmbed - same role/user/device ownership and deny-wins priority as
 * ExposureRule (see AuthorizationService.resolve), deliberately without the read/write/history/
 * min/max/... fields that don't apply to a URL embed: it's either visible to a device or it
 * isn't, there's no separate "write" concept for an embedded URL. Same default-deny behavior as
 * object exposure - an embed with zero access rules is invisible to everyone until granted.
 */
export interface UrlEmbedAccessRule {
    id: string;
    urlEmbedId: string;
    roleId: RoleId | null;
    userId: string | null;
    deviceId: string | null;
    deny: boolean;
    createdAt: number;
}

/**
 * A persisted "alarm went active" transition for an ioBroker state whose role marks it as an
 * alarm (same role.includes('alarm') convention CatalogService uses for suggestedWidgets).
 * Collected regardless of whether any client currently has that object open (unlike
 * RealtimeGateway's per-connection dynamic subscriptions), so a device that reconnects after
 * being offline/backgrounded can catch up on what it missed via GET /alarm-events?since=. See
 * src/alarms/index.ts. stateId is the real ioBroker id - authorization is checked at read time.
 */
export interface AlarmEvent {
    id: string;
    stateId: string;
    value: boolean;
    timestamp: number;
}

export interface PublicObjectMapping {
    /** the public UUID, used as CollectionStore id */
    id: string;
    stateId: string;
    createdAt: number;
}

/** An ExposureRule without an owner (roleId/userId/deviceId) - filled in when a profile is applied. */
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
    valueType: 'number' | 'string' | 'boolean' | 'json' | 'mixed';
    unit: string | null;
    read: boolean;
    write: boolean;
    history: boolean;
    min: number | null;
    max: number | null;
    step: number | null;
    allowedValues: (string | number | boolean)[] | null;
    localOnly: boolean;
    confirmPolicy: ConfirmPolicy;
    suggestedWidgets: string[];
}

export interface WidgetLayoutEntry {
    id: string;
    objectId: string | null;
    type: string;
    title: string;
    x: number;
    y: number;
    w: number;
    h: number;
    config: Record<string, string>;
}

export type SizeClass = 'compact' | 'medium' | 'expanded';

export interface DashboardLayout {
    sizeClass: SizeClass;
    columns: number;
    widgets: WidgetLayoutEntry[];
}

export interface Dashboard {
    id: string;
    userId: string;
    name: string;
    revision: number;
    layouts: DashboardLayout[];
    isStartDashboard: boolean;
    createdAt: number;
    updatedAt: number;
}

export type CommandStatus = 'accepted' | 'executed' | 'confirmed' | 'timeout' | 'rejected' | 'blocked';

export interface CommandRecord {
    id: string;
    objectId: string;
    stateId: string;
    deviceId: string;
    userId: string;
    value: unknown;
    status: CommandStatus;
    reason: string | null;
    createdAt: number;
    updatedAt: number;
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
