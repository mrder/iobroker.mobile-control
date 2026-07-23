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
    UrlEmbed,
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
import { HistoryService } from './history';
import { CameraService } from './camera';
import { UrlEmbedsService } from './urlEmbeds';
import { RateLimiter } from './security/rateLimiter';
import { AbuseGuard } from './security/abuseGuard';
import { ReplayGuard } from './security/replayGuard';
import { RealtimeGateway } from './realtime';
import { createApiRouter } from './api/router';
import { runMigrations } from './migrations';

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
    /** Per-IP limit for the unauthenticated auth/pairing endpoints (brute-force protection). */
    authRateLimitPerMinute: number;
    /** Failed auth/pairing attempts from one IP within 5 minutes before it's temporarily blocked. */
    abuseBlockThreshold: number;
    /** How long an IP stays blocked once it crosses abuseBlockThreshold. */
    abuseBlockMinutes: number;
    localOnlyByDefault: boolean;
    /** e.g. "history.0", "sql.0" - empty disables the /api/v1/history endpoint entirely */
    historyInstance: string;
}

const STATUS_INTERVAL_MS = 15_000;
/** How recently a device must have made an authenticated request to still count as "connected"
 *  for info.connection - see DevicesService.hasRecentlyActiveDevice's own docs for why this isn't
 *  just "does a WebSocket happen to be open right now". */
const RECENT_ACTIVITY_WINDOW_MS = 5 * 60_000;

/** Non-internal IPv4 addresses of this host - shown in the admin tab so the user knows exactly
 * what to point a reverse proxy or VPN config at (see docs/DEPLOYMENT.md). */
function getLocalAddresses(): string[] {
    const addresses: string[] = [];
    const interfaces = os.networkInterfaces();
    for (const entries of Object.values(interfaces)) {
        for (const iface of entries ?? []) {
            if (iface.family === 'IPv4' && !iface.internal) {
                addresses.push(iface.address);
            }
        }
    }
    return addresses;
}

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
    private historyService!: HistoryService;
    private cameraService!: CameraService;
    private urlEmbedsService!: UrlEmbedsService;
    private abuseGuard!: AbuseGuard;

    public constructor(options: Partial<utils.AdapterOptions> = {}) {
        super({ ...options, name: 'mobile-control' });
        // Logs any error escaping onReady() through our own logger, with its full stack, before
        // letting it propagate - js-controller's own top-level handler only ever logs a bare
        // "UNCAUGHT_EXCEPTION", which made an unrelated real bug (see RealtimeGateway's wss 'error'
        // listener) much harder to diagnose during the first live install than it needed to be.
        this.on('ready', async () => {
            try {
                await this.onReady();
            } catch (err) {
                this.log.error(`mobile-control: onReady() failed: ${(err as Error).message}\n${(err as Error).stack}`);
                throw err;
            }
        });
        this.on('stateChange', this.onStateChange.bind(this));
        this.on('message', this.onMessage.bind(this));
        this.on('unload', this.onUnload.bind(this));
    }

    private async onReady(): Promise<void> {
        const config = this.config as unknown as AdapterNativeConfig;

        await runMigrations(this);

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
        const urlEmbedsStore = new CollectionStore<UrlEmbed>(this, 'urlEmbeds');

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
            urlEmbedsStore.init(),
        ]);

        this.rolesService = new RolesService(rolesStore);
        await this.rolesService.ensureBuiltInRoles();

        this.usersService = new UsersService(usersStore, this.rolesService);
        this.devicesService = new DevicesService(devicesStore);
        this.auditService = new AuditService(auditStore);
        this.authService = new AuthService(this.devicesService, jwtSecret, config.accessTokenTtlMinutes);
        this.sessionsService = new SessionsService(sessionsStore, this.auditService);
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
        this.historyService = new HistoryService(this, config.historyInstance ?? '');
        this.cameraService = new CameraService(this);
        this.urlEmbedsService = new UrlEmbedsService(this, urlEmbedsStore);

        await this.startHttpServer(config);

        this.subscribeStates('control.*');
        await this.setStateAsync('info.apiStatus', { val: 'running', ack: true });
        await this.setStateAsync('info.websocketStatus', { val: 'running', ack: true });

        this.statusInterval = setInterval(() => this.updateStatusStatesSafely(), STATUS_INTERVAL_MS);
        this.updateStatusStatesSafely();
    }

    /** Fire-and-forget wrapper - a transient state-write failure here must never crash the adapter. */
    private updateStatusStatesSafely(): void {
        this.updateStatusStates().catch((err: unknown) => this.log.error(`mobile-control: updateStatusStates failed: ${(err as Error).message}`));
    }

    private async startHttpServer(config: AdapterNativeConfig): Promise<void> {
        // Falls back to sane defaults if missing (e.g. an instance configured before this setting
        // existed) rather than silently disabling blocking (0/undefined would make the
        // ">= maxFailures" check in AbuseGuard never trip). Kept as an instance field (not just a
        // local here) so the 'listAbuseState' admin message handler can read its live snapshot.
        this.abuseGuard = new AbuseGuard({
            maxFailures: config.abuseBlockThreshold || 10,
            windowMs: 5 * 60_000,
            blockMs: (config.abuseBlockMinutes || 30) * 60_000,
        });

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
                history: this.historyService,
                camera: this.cameraService,
                urlEmbeds: this.urlEmbedsService,
                refreshTokenTtlDays: config.refreshTokenTtlDays,
                authRateLimiter: new RateLimiter(config.authRateLimitPerMinute),
                abuseGuard: this.abuseGuard,
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

        await this.listenWithRetry(server, config);
    }

    private tryListen(server: http.Server, port: number, bindAddress: string): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            const onError = (err: Error): void => {
                server.removeListener('listening', onListening);
                reject(err);
            };
            const onListening = (): void => {
                server.removeListener('error', onError);
                resolve();
            };
            server.once('error', onError);
            server.once('listening', onListening);
            server.listen(port, bindAddress);
        });
    }

    /**
     * Retries a few times on EADDRINUSE before giving up - a restarting adapter instance can
     * briefly race the just-exited previous process for the same port. If the port is still taken
     * after the retries and no device has ever paired yet (see scanForFreePort), tries a small
     * range of nearby ports instead. Any other listen error, or EADDRINUSE with no free port found
     * (or once a device exists), produces one clear, actionable log line (instead of the previous
     * behavior: an unhandled rejection out of onReady() that js-controller only ever surfaced as a
     * generic "UNCAUGHT_EXCEPTION" with a raw Node stack trace) and then a clean termination via
     * this.terminate() rather than a crash.
     */
    private async listenWithRetry(server: http.Server, config: AdapterNativeConfig, attempt = 1): Promise<void> {
        const MAX_ATTEMPTS = 3;
        const RETRY_DELAY_MS = 1000;
        try {
            await this.tryListen(server, config.port, config.bindAddress);
            this.log.info(`mobile-control: REST/WebSocket API listening on ${config.bindAddress}:${config.port}`);
        } catch (err) {
            const code = (err as NodeJS.ErrnoException).code;
            this.log.debug(`mobile-control: listen attempt ${attempt} failed: code=${String(code)}, message=${(err as Error).message}`);
            if (code === 'EADDRINUSE' && attempt < MAX_ATTEMPTS) {
                this.log.warn(
                    `mobile-control: port ${config.port} is still in use (attempt ${attempt}/${MAX_ATTEMPTS}), retrying in ${RETRY_DELAY_MS}ms...`,
                );
                await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
                return this.listenWithRetry(server, config, attempt + 1);
            }
            if (code === 'EADDRINUSE' && this.devicesService.list().length === 0) {
                return this.scanForFreePort(server, config);
            }
            if (code === 'EADDRINUSE') {
                this.terminate(
                    `Port ${config.port} on ${config.bindAddress} is already in use by another process. ` +
                        `Set a different port in the "mobile-control" instance settings, or free port ${config.port} first ` +
                        `(e.g. "sudo lsof -i :${config.port}" / "sudo netstat -tlnp | grep ${config.port}" to find what's using it).`,
                    11,
                );
            }
            this.terminate(`Failed to start the REST/WebSocket server: ${(err as Error).message}`, 11);
        }
    }

    /**
     * Only reached when no device has ever paired yet (this.devicesService.list() is empty) - it is
     * safe to silently move to a different port here because nothing yet depends on the originally
     * configured one (no firewall rule, no reverse proxy, no paired device). Once a real device
     * exists this path is never taken again - listenWithRetry instead fails loudly on a busy port,
     * so a port an existing setup already relies on never changes silently underneath it.
     */
    private async scanForFreePort(server: http.Server, config: AdapterNativeConfig): Promise<void> {
        const PORT_SCAN_RANGE = 20;
        for (let candidate = config.port + 1; candidate <= config.port + PORT_SCAN_RANGE; candidate++) {
            try {
                await this.tryListen(server, candidate, config.bindAddress);
                await this.extendForeignObjectAsync(`system.adapter.${this.namespace}`, { native: { port: candidate } });
                this.log.warn(
                    `mobile-control: port ${config.port} was already in use, and no device has paired yet, so this ` +
                        `first-run automatically picked port ${candidate} instead and saved it to the instance settings. ` +
                        `Set "Port" explicitly there if you need a specific one (e.g. for a reverse proxy) - this fallback ` +
                        `only ever runs before the first device pairing.`,
                );
                return;
            } catch (err) {
                if ((err as NodeJS.ErrnoException).code !== 'EADDRINUSE') {
                    this.terminate(`Failed to start the REST/WebSocket server: ${(err as Error).message}`, 11);
                }
            }
        }
        this.terminate(
            `Port ${config.port} on ${config.bindAddress} is already in use, and no free port was found in the range ` +
                `${config.port + 1}-${config.port + PORT_SCAN_RANGE} either. Set a different port in the "mobile-control" ` +
                `instance settings.`,
            11,
        );
    }

    private onStateChange(id: string, state: ioBroker.State | null | undefined): void {
        if (id === `${this.namespace}.control.revokeAllSessions` && state && !state.ack && state.val) {
            this.handleRevokeAllSessions().catch((err: unknown) =>
                this.log.error(`mobile-control: handleRevokeAllSessions failed: ${(err as Error).message}`),
            );
            return;
        }
        if (id.startsWith(`${this.namespace}.`)) {
            return; // control/info states are not part of the exposed catalog
        }

        this.commandsService
            ?.handleForeignStateChange(id, state)
            .catch((err: unknown) => this.log.error(`mobile-control: handleForeignStateChange failed: ${(err as Error).message}`));
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
        const recentlyActive = this.devicesService?.hasRecentlyActiveDevice(RECENT_ACTIVITY_WINDOW_MS) ?? false;
        await this.setStateAsync('info.connection', { val: connectedDevices > 0 || recentlyActive, ack: true });
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
                case 'createExposureRule': {
                    const created = await this.exposureService.create(body as unknown as Omit<ExposureRule, 'id' | 'createdAt'>);
                    await this.auditService.log({
                        action: 'exposure.rule_created',
                        result: 'success',
                        detail: `rule=${created.id} scope=${created.scope}:${created.target}`,
                    });
                    respond(created);
                    break;
                }
                case 'updateExposureRule': {
                    const updated = await this.exposureService.update(
                        String(body.id),
                        body as unknown as Partial<Omit<ExposureRule, 'id' | 'createdAt'>>,
                    );
                    await this.auditService.log({
                        action: 'exposure.rule_updated',
                        result: 'success',
                        detail: `rule=${updated.id} scope=${updated.scope}:${updated.target}`,
                    });
                    respond(updated);
                    break;
                }
                case 'deleteExposureRule': {
                    const ruleId = String(body.id);
                    await this.exposureService.delete(ruleId);
                    await this.auditService.log({
                        action: 'exposure.rule_deleted',
                        result: 'success',
                        detail: `rule=${ruleId}`,
                    });
                    respond({ ok: true });
                    break;
                }

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

                // Purely informational for the admin - shows exactly what a reverse proxy or VPN
                // config needs to point at. The adapter never sets up networking on its own (see
                // docs/DEPLOYMENT.md); this just surfaces the current settings + host addresses.
                case 'getConnectionInfo': {
                    const netConfig = this.config as unknown as AdapterNativeConfig;
                    respond({
                        port: netConfig.port,
                        bindAddress: netConfig.bindAddress,
                        publicUrl: netConfig.publicUrl,
                        localAddresses: getLocalAddresses(),
                    });
                    break;
                }

                // Live visibility into AbuseGuard, requested after a live Q&A about pairing
                // security: "which IP tried what, how many times" - the log warning in
                // router.ts only fires once per new block, this is the full current picture.
                case 'listAbuseState':
                    respond(this.abuseGuard?.snapshot() ?? []);
                    break;

                case 'listUrlEmbeds':
                    respond(this.urlEmbedsService.list());
                    break;
                // Same trust boundary note as the exposure rule messages above: this comes from
                // an already-authenticated ioBroker admin session, so the url is trusted here
                // (beyond the basic http(s)-absolute-URL sanity check UrlEmbedsService itself
                // does) rather than re-validated against e.g. an SSRF target blocklist.
                case 'createUrlEmbed': {
                    const created = await this.urlEmbedsService.create({ name: String(body.name), url: String(body.url) });
                    await this.auditService.log({ action: 'urlEmbed.created', result: 'success', detail: `embed=${created.id}` });
                    respond(created);
                    break;
                }
                case 'updateUrlEmbed': {
                    const updated = await this.urlEmbedsService.update(String(body.id), {
                        name: body.name !== undefined ? String(body.name) : undefined,
                        url: body.url !== undefined ? String(body.url) : undefined,
                    });
                    await this.auditService.log({ action: 'urlEmbed.updated', result: 'success', detail: `embed=${updated.id}` });
                    respond(updated);
                    break;
                }
                case 'deleteUrlEmbed': {
                    const embedId = String(body.id);
                    await this.urlEmbedsService.delete(embedId);
                    await this.auditService.log({ action: 'urlEmbed.deleted', result: 'success', detail: `embed=${embedId}` });
                    respond({ ok: true });
                    break;
                }

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
