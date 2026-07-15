import { randomBytes, createHash } from 'node:crypto';
import * as http from 'node:http';
import * as os from 'node:os';
import * as utils from '@iobroker/adapter-core';
import express from 'express';

import { CollectionStore } from './lib/store';
import type {
    AuditEvent,
    CommandRecord,
    Dashboard,
    Device,
    ExposureProfile,
    ExposureRule,
    PairingClaim,
    PairingInvite,
    PublicObjectMapping,
    Role,
    Session,
    User,
} from './lib/types';

import { RolesService } from './roles';
import { UsersService } from './users';
import { DevicesService } from './devices';
import { PairingService } from './pairing';
import { AuthService } from './auth';
import { SessionsService } from './sessions';
import { ExposureService } from './exposure';
import { ExposureProfilesService, type OwnerType } from './exposure/profiles';
import { AuthorizationService } from './authorization';
import { CatalogService } from './catalog';
import { DashboardsService } from './dashboards';
import { CommandsService } from './commands';
import { AuditService } from './audit';
import { RateLimiter } from './security/rateLimiter';
import { ReplayGuard } from './security/replayGuard';
import { RealtimeGateway } from './realtime';
import { createApiRouter } from './api/router';

interface AdapterNativeConfig {
    port: number;
    bindAddress: string;
    publicUrl: string;
    jwtSecret: string;
    accessTokenTtlMinutes: number;
    refreshTokenTtlDays: number;
    pairingTtlMinutes: number;
    requireAdminApproval: boolean;
    rateLimitPerMinute: number;
    localOnlyByDefault: boolean;
}

const STATUS_INTERVAL_MS = 15_000;

class MobileControlAdapter extends utils.Adapter {
    private httpServer?: http.Server;
    private realtimeGateway?: RealtimeGateway;
    private statusInterval?: NodeJS.Timeout;

    private rolesService!: RolesService;
    private usersService!: UsersService;
    private devicesService!: DevicesService;
    private pairingService!: PairingService;
    private authService!: AuthService;
    private sessionsService!: SessionsService;
    private exposureService!: ExposureService;
    private exposureProfilesService!: ExposureProfilesService;
    private authorizationService!: AuthorizationService;
    private catalogService!: CatalogService;
    private dashboardsService!: DashboardsService;
    private commandsService!: CommandsService;
    private auditService!: AuditService;

    public constructor(options: Partial<utils.AdapterOptions> = {}) {
        super({ ...options, name: 'mobile-control' });
        this.on('ready', this.onReady.bind(this));
        this.on('stateChange', this.onStateChange.bind(this));
        this.on('message', this.onMessage.bind(this));
        this.on('unload', this.onUnload.bind(this));
    }

    private async onReady(): Promise<void> {
        const config = this.config as unknown as AdapterNativeConfig;

        let jwtSecret = config.jwtSecret;
        if (!jwtSecret) {
            jwtSecret = randomBytes(48).toString('base64url');
            await this.extendForeignObjectAsync(`system.adapter.${this.namespace}`, { native: { jwtSecret } });
            this.log.info('mobile-control: generated a new JWT signing secret (stored in adapter config)');
        }

        // NOTE (documented simplification): this MVP does not terminate TLS itself - it expects
        // a VPN or reverse proxy in front of it for remote access (see docs/MASTERKONZEPT.md §4).
        // serverFingerprint therefore is NOT a real certificate pin yet; it is a stable per-adapter
        // identity value so the app can at least detect "this is the server I originally paired
        // with". Once deployed behind HTTPS, replace this with the actual certificate SPKI pin.
        const serverFingerprint = `sha256/${createHash('sha256').update(jwtSecret).digest('base64')}`;
        const publicUrl = config.publicUrl || `http://${os.hostname()}:${config.port}`;
        if (!config.publicUrl) {
            this.log.warn(
                'mobile-control: no publicUrl configured - falling back to a guessed local address. Set "publicUrl" in the instance settings for real deployments.',
            );
        }

        const usersStore = new CollectionStore<User>(this, 'users');
        const rolesStore = new CollectionStore<Role>(this, 'roles');
        const devicesStore = new CollectionStore<Device>(this, 'devices');
        const invitesStore = new CollectionStore<PairingInvite>(this, 'pairingInvites');
        const claimsStore = new CollectionStore<PairingClaim>(this, 'pairingClaims');
        const sessionsStore = new CollectionStore<Session>(this, 'sessions');
        const exposureStore = new CollectionStore<ExposureRule>(this, 'exposureRules');
        const exposureProfilesStore = new CollectionStore<ExposureProfile>(this, 'exposureProfiles');
        const mappingsStore = new CollectionStore<PublicObjectMapping>(this, 'objectMappings');
        const dashboardsStore = new CollectionStore<Dashboard>(this, 'dashboards');
        const commandsStore = new CollectionStore<CommandRecord>(this, 'commands');
        const auditStore = new CollectionStore<AuditEvent>(this, 'auditEvents');

        await Promise.all([
            usersStore.init(),
            rolesStore.init(),
            devicesStore.init(),
            invitesStore.init(),
            claimsStore.init(),
            sessionsStore.init(),
            exposureStore.init(),
            exposureProfilesStore.init(),
            mappingsStore.init(),
            dashboardsStore.init(),
            commandsStore.init(),
            auditStore.init(),
        ]);

        this.rolesService = new RolesService(rolesStore);
        await this.rolesService.ensureBuiltInRoles();

        this.usersService = new UsersService(usersStore, this.rolesService);
        this.devicesService = new DevicesService(devicesStore);
        this.auditService = new AuditService(auditStore);
        this.authService = new AuthService(this.devicesService, jwtSecret, config.accessTokenTtlMinutes);
        this.sessionsService = new SessionsService(sessionsStore);
        this.pairingService = new PairingService(invitesStore, claimsStore, this.usersService, this.rolesService, this.devicesService, {
            publicUrl,
            instanceId: this.namespace,
            serverFingerprint,
            inviteTtlMinutes: config.pairingTtlMinutes,
            requireAdminApproval: config.requireAdminApproval,
        });
        this.exposureService = new ExposureService(this, exposureStore);
        this.exposureProfilesService = new ExposureProfilesService(exposureProfilesStore, this.exposureService);
        this.authorizationService = new AuthorizationService(this.exposureService);
        this.catalogService = new CatalogService(this.exposureService, this.authorizationService, mappingsStore);
        this.dashboardsService = new DashboardsService(dashboardsStore, mappingsStore, this.authorizationService, this.usersService);

        const rateLimiter = new RateLimiter(config.rateLimitPerMinute);
        const replayGuard = new ReplayGuard();
        this.commandsService = new CommandsService(this, commandsStore, this.catalogService, this.auditService, rateLimiter, replayGuard);

        await this.startHttpServer(config);

        this.subscribeStates('control.*');
        await this.setStateAsync('info.apiStatus', { val: 'running', ack: true });
        await this.setStateAsync('info.websocketStatus', { val: 'running', ack: true });

        this.statusInterval = setInterval(() => void this.updateStatusStates(), STATUS_INTERVAL_MS);
        void this.updateStatusStates();
    }

    private async startHttpServer(config: AdapterNativeConfig): Promise<void> {
        const app = express();
        app.use(express.json({ limit: '256kb' }));
        app.use(
            '/api/v1',
            createApiRouter({
                adapter: this,
                auth: this.authService,
                sessions: this.sessionsService,
                devices: this.devicesService,
                users: this.usersService,
                pairing: this.pairingService,
                catalog: this.catalogService,
                dashboards: this.dashboardsService,
                commands: this.commandsService,
                audit: this.auditService,
                refreshTokenTtlDays: config.refreshTokenTtlDays,
            }),
        );

        const server = http.createServer(app);
        this.httpServer = server;
        this.realtimeGateway = new RealtimeGateway(
            server,
            this,
            this.authService,
            this.sessionsService,
            this.devicesService,
            this.catalogService,
            this.commandsService,
        );

        await new Promise<void>((resolve, reject) => {
            server.once('error', reject);
            server.listen(config.port, config.bindAddress, () => {
                this.log.info(`mobile-control: REST/WebSocket API listening on ${config.bindAddress}:${config.port}`);
                resolve();
            });
        });
    }

    private onStateChange(id: string, state: ioBroker.State | null | undefined): void {
        if (id === `${this.namespace}.control.revokeAllSessions` && state && !state.ack && state.val) {
            void this.handleRevokeAllSessions();
            return;
        }
        if (id.startsWith(`${this.namespace}.`)) {
            return; // control/info states are not part of the exposed catalog
        }

        void this.commandsService?.handleForeignStateChange(id, state);
        this.realtimeGateway?.publishStateChange(id, state);
    }

    private async handleRevokeAllSessions(): Promise<void> {
        await this.sessionsService.revokeAll();
        for (const device of this.devicesService.list()) {
            this.realtimeGateway?.notifyDeviceRevoked(device.id);
        }
        await this.auditService.log({ action: 'sessions.revoke_all', result: 'success' });
        await this.setStateAsync('control.revokeAllSessions', { val: false, ack: true });
    }

    private async updateStatusStates(): Promise<void> {
        const activeSessions = this.sessionsService?.listActive().length ?? 0;
        const connectedDevices = this.realtimeGateway?.connectedDeviceCount ?? 0;
        await this.setStateAsync('info.connection', { val: connectedDevices > 0, ack: true });
        await this.setStateAsync('info.activeDevices', { val: connectedDevices, ack: true });
        await this.setStateAsync('info.activeSessions', { val: activeSessions, ack: true });

        // TODO: derive a real security posture (e.g. warn if remote access is enabled without
        // a reverse proxy / VPN in front of it). Fixed at "ok" for the MVP.
        await this.setStateAsync('info.securityStatus', { val: 'ok', ack: true });
    }

    private onMessage(obj: ioBroker.Message): void {
        void this.handleMessage(obj);
    }

    private async handleMessage(obj: ioBroker.Message): Promise<void> {
        const respond = (result: unknown): void => {
            if (obj.callback) {
                this.sendTo(obj.from, obj.command, result, obj.callback);
            }
        };
        const respondError = (err: unknown): void => {
            const message = err instanceof Error ? err.message : String(err);
            respond({ error: message });
        };

        const body = (obj.message ?? {}) as Record<string, unknown>;

        try {
            switch (obj.command) {
                case 'listUsers':
                    respond(this.usersService.list());
                    break;
                case 'createUser':
                    respond(await this.usersService.create(String(body.name), String(body.roleId)));
                    break;
                case 'renameUser':
                    respond(await this.usersService.rename(String(body.id), String(body.name)));
                    break;
                case 'setUserRole':
                    respond(await this.usersService.setRole(String(body.id), String(body.roleId)));
                    break;
                case 'setUserDisabled':
                    respond(await this.usersService.setDisabled(String(body.id), Boolean(body.disabled)));
                    break;
                case 'deleteUser':
                    await this.usersService.delete(String(body.id));
                    respond({ ok: true });
                    break;

                case 'listRoles':
                    respond(this.rolesService.list());
                    break;
                case 'createRole':
                    respond(await this.rolesService.create(String(body.name)));
                    break;
                case 'renameRole':
                    respond(await this.rolesService.rename(String(body.id), String(body.name)));
                    break;
                case 'deleteRole':
                    await this.rolesService.delete(String(body.id));
                    respond({ ok: true });
                    break;

                case 'listDevices':
                    respond(this.devicesService.list());
                    break;
                case 'approveDevice':
                    respond(await this.devicesService.approve(String(body.id)));
                    break;
                case 'rejectDevice':
                    respond(await this.devicesService.reject(String(body.id)));
                    break;
                case 'revokeDevice': {
                    const device = await this.devicesService.revoke(String(body.id));
                    await this.sessionsService.revokeAllForDevice(device.id);
                    this.realtimeGateway?.notifyDeviceRevoked(device.id);
                    await this.auditService.log({ action: 'device.revoked', actorDeviceId: device.id, result: 'success' });
                    respond(device);
                    break;
                }

                case 'createPairingInvite': {
                    const created = await this.pairingService.createInvite(String(body.userId), String(body.roleId));
                    await this.auditService.log({ action: 'pairing.invite_created', actorUserId: String(body.userId), result: 'success' });
                    respond(created);
                    break;
                }
                case 'listPendingClaims':
                    respond(this.pairingService.listPendingClaims());
                    break;
                case 'approveClaim': {
                    const claim = await this.pairingService.approveClaim(String(body.claimId));
                    await this.auditService.log({ action: 'pairing.claim_approved', actorDeviceId: claim.deviceId, result: 'success' });
                    respond(claim);
                    break;
                }
                case 'rejectClaim': {
                    const claim = await this.pairingService.rejectClaim(String(body.claimId));
                    await this.auditService.log({ action: 'pairing.claim_rejected', actorDeviceId: claim.deviceId, result: 'success' });
                    respond(claim);
                    break;
                }

                case 'browseObjectTree':
                    respond(await this.exposureService.browseObjectTree());
                    break;
                // BACKEND-KONZEPT.md §4 "effektive Vorschau": lets an admin see exactly what a
                // given user (optionally narrowed to one of their devices) would receive from
                // GET /api/v1/catalog, without needing that user's own session/token.
                case 'previewCatalog': {
                    const previewUserId = String(body.userId);
                    const previewUser = this.usersService.require(previewUserId);
                    const previewDeviceId = body.deviceId ? String(body.deviceId) : '';
                    const previewDevice = previewDeviceId ? this.devicesService.get(previewDeviceId) : undefined;
                    const previewRoleId = previewDevice?.roleId ?? previewUser.roleId;
                    respond(
                        await this.catalogService.effectiveCatalog({
                            userId: previewUserId,
                            deviceId: previewDeviceId,
                            roleId: previewRoleId,
                        }),
                    );
                    break;
                }
                case 'listExposureRules':
                    respond(this.exposureService.list());
                    break;
                // Admin messages come from an already-authenticated ioBroker admin session
                // (a stronger trust boundary than the app API), so the rule body is trusted
                // here rather than re-validated field by field.
                case 'createExposureRule':
                    respond(await this.exposureService.create(body as unknown as Omit<ExposureRule, 'id' | 'createdAt'>));
                    break;
                case 'updateExposureRule':
                    respond(
                        await this.exposureService.update(
                            String(body.id),
                            body as unknown as Partial<Omit<ExposureRule, 'id' | 'createdAt'>>,
                        ),
                    );
                    break;
                case 'deleteExposureRule':
                    await this.exposureService.delete(String(body.id));
                    respond({ ok: true });
                    break;

                case 'listExposureProfiles':
                    respond(this.exposureProfilesService.list());
                    break;
                case 'createExposureProfileFromOwner':
                    respond(
                        await this.exposureProfilesService.createFromOwner(
                            String(body.name),
                            body.description ? String(body.description) : null,
                            body.ownerType as OwnerType,
                            String(body.ownerId),
                        ),
                    );
                    break;
                case 'renameExposureProfile':
                    respond(
                        await this.exposureProfilesService.rename(
                            String(body.id),
                            String(body.name),
                            body.description ? String(body.description) : null,
                        ),
                    );
                    break;
                case 'deleteExposureProfile':
                    await this.exposureProfilesService.delete(String(body.id));
                    respond({ ok: true });
                    break;
                case 'applyExposureProfile': {
                    const applied = await this.exposureProfilesService.applyTo(
                        String(body.profileId),
                        body.ownerType as OwnerType,
                        String(body.ownerId),
                    );
                    await this.auditService.log({
                        action: 'exposure.profile_applied',
                        result: 'success',
                        detail: `profile=${String(body.profileId)} owner=${String(body.ownerType)}:${String(body.ownerId)} rules=${applied.length}`,
                    });
                    respond(applied);
                    break;
                }

                case 'listSessions':
                    respond(this.sessionsService.list());
                    break;
                case 'revokeSession':
                    await this.sessionsService.revoke(String(body.id));
                    this.realtimeGateway?.notifySessionRevoked(String(body.id));
                    await this.auditService.log({ action: 'session.revoked', sessionId: String(body.id), result: 'success' });
                    respond({ ok: true });
                    break;
                case 'revokeAllSessions':
                    await this.handleRevokeAllSessions();
                    respond({ ok: true });
                    break;

                case 'listAudit':
                    respond(this.auditService.list(typeof body.limit === 'number' ? body.limit : undefined));
                    break;

                case 'getOverview':
                    respond({
                        users: this.usersService.list().length,
                        devices: this.devicesService.list().length,
                        pendingClaims: this.pairingService.listPendingClaims().length,
                        activeSessions: this.sessionsService.listActive().length,
                        connectedDevices: this.realtimeGateway?.connectedDeviceCount ?? 0,
                    });
                    break;

                default:
                    if (obj.callback) {
                        respond({ error: `unknown command: ${obj.command}` });
                    }
            }
        } catch (err) {
            respondError(err);
        }
    }

    private onUnload(callback: () => void): void {
        try {
            if (this.statusInterval) {
                clearInterval(this.statusInterval);
            }
            this.realtimeGateway?.close();
            this.httpServer?.close();
            callback();
        } catch {
            callback();
        }
    }
}

if (require.main !== module) {
    module.exports = (options: Partial<utils.AdapterOptions> | undefined) => new MobileControlAdapter(options);
} else {
    (() => new MobileControlAdapter())();
}
