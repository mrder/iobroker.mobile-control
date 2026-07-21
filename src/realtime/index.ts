import type { Server as HttpServer } from 'node:http';
import { WebSocket, WebSocketServer } from 'ws';
import type { AuthService } from '../auth';
import type { SessionsService } from '../sessions';
import type { DevicesService } from '../devices';
import type { CatalogService } from '../catalog';
import type { CommandsService, CommandResultEvent } from '../commands';
import type { AuthContext } from '../authorization';

const HEARTBEAT_INTERVAL_MS = 30_000;
const AUTH_TIMEOUT_MS = 5_000;

interface Subscription {
    stateId: string;
    publicId: string;
}

interface Connection {
    ws: WebSocket;
    ctx: AuthContext | null;
    sessionId: string | null;
    subscriptions: Map<string, Subscription>; // keyed by publicId
    authenticated: boolean;
    alive: boolean;
}

/**
 * WebSocket gateway (BACKEND-KONZEPT.md §8). The app only ever subscribes to public
 * object UUIDs, never raw ioBroker state ids - resolution + authorization goes through
 * CatalogService.resolveAuthorized on every subscribe, same as the REST layer.
 */
export class RealtimeGateway {
    private readonly wss: WebSocketServer;
    private readonly connections = new Set<Connection>();
    /** internal ioBroker stateId -> connections currently subscribed to it */
    private readonly stateSubscribers = new Map<string, Set<Connection>>();
    private readonly heartbeatTimer: NodeJS.Timeout;

    constructor(
        server: HttpServer,
        private readonly adapter: ioBroker.Adapter,
        private readonly authService: AuthService,
        private readonly sessions: SessionsService,
        private readonly devices: DevicesService,
        private readonly catalog: CatalogService,
        private readonly commands: CommandsService,
    ) {
        this.wss = new WebSocketServer({ server, path: '/ws/v1' });
        // The ws library forwards the underlying http.Server's 'error' event onto this
        // WebSocketServer instance (see ws/lib/websocket-server.js) - Node's EventEmitter throws
        // synchronously, as a genuine uncaught exception, if an 'error' event has zero listeners.
        // Without this handler, ANY listen() error (e.g. EADDRINUSE) crashes the process before
        // main.ts's own listen-retry error handling on `server` ever gets a chance to run, since
        // this listener was registered first (in construction order) and a throw here aborts the
        // rest of the underlying EventEmitter's listener queue. Real handling stays in main.ts's
        // own `server.once('error', ...)` - this only has to exist to stop the crash.
        this.wss.on('error', (err: Error) => this.adapter.log.debug(`mobile-control: WebSocketServer reported: ${err.message}`));
        this.wss.on('connection', (ws) => this.handleConnection(ws));
        this.commands.on('commandResult', (event: CommandResultEvent) => this.publishCommandResult(event));
        this.heartbeatTimer = setInterval(() => this.heartbeatTick(), HEARTBEAT_INTERVAL_MS);
    }

    private handleConnection(ws: WebSocket): void {
        const connection: Connection = {
            ws,
            ctx: null,
            sessionId: null,
            subscriptions: new Map(),
            authenticated: false,
            alive: true,
        };
        this.connections.add(connection);

        const authTimer = setTimeout(() => {
            if (!connection.authenticated) {
                this.send(connection, { type: 'error', code: 'AUTH_REQUIRED' });
                ws.close();
            }
        }, AUTH_TIMEOUT_MS);

        ws.on('pong', () => {
            connection.alive = true;
        });

        ws.on('message', (raw: Buffer) => {
            void this.handleMessage(connection, raw.toString());
        });

        ws.on('close', () => {
            clearTimeout(authTimer);
            this.cleanupConnection(connection);
        });

        ws.on('error', () => {
            this.cleanupConnection(connection);
        });
    }

    private async handleMessage(connection: Connection, raw: string): Promise<void> {
        let msg: any;
        try {
            msg = JSON.parse(raw);
        } catch {
            this.send(connection, { type: 'error', code: 'VALIDATION_ERROR', message: 'invalid JSON' });
            return;
        }

        if (msg.type === 'auth') {
            await this.handleAuth(connection, msg.accessToken);
            return;
        }

        if (!connection.authenticated || !connection.ctx) {
            this.send(connection, { type: 'error', code: 'AUTH_REQUIRED' });
            return;
        }

        if (msg.type === 'subscribe' && Array.isArray(msg.objectIds)) {
            await this.subscribe(connection, msg.objectIds);
        } else if (msg.type === 'unsubscribe' && Array.isArray(msg.objectIds)) {
            await this.unsubscribe(connection, msg.objectIds);
        } else if (msg.type === 'ping') {
            this.send(connection, { type: 'heartbeat' });
        }
    }

    private async handleAuth(connection: Connection, accessToken: unknown): Promise<void> {
        if (typeof accessToken !== 'string') {
            this.send(connection, { type: 'error', code: 'AUTH_REQUIRED' });
            connection.ws.close();
            return;
        }
        try {
            const payload = this.authService.verifyAccessToken(accessToken);
            const session = this.sessions.requireActive(payload.sessionId);
            const device = this.devices.require(payload.deviceId);
            if (!this.devices.isUsable(device)) {
                throw new Error('device not usable');
            }

            connection.ctx = { userId: payload.sub, deviceId: payload.deviceId, roleId: payload.roleId };
            connection.sessionId = session.id;
            connection.authenticated = true;
            this.send(connection, { type: 'auth_ok' });
        } catch {
            this.send(connection, { type: 'error', code: 'AUTH_REQUIRED' });
            connection.ws.close();
        }
    }

    private async subscribe(connection: Connection, objectIds: unknown[]): Promise<void> {
        if (!connection.ctx) {
            return;
        }
        for (const raw of objectIds) {
            if (typeof raw !== 'string') {
                continue;
            }
            let stateId: string;
            try {
                ({ stateId } = this.catalog.resolveAuthorized(raw, connection.ctx, 'read'));
            } catch {
                continue; // silently skip objects this device/user can't read
            }

            connection.subscriptions.set(raw, { stateId, publicId: raw });

            let subscribers = this.stateSubscribers.get(stateId);
            if (!subscribers) {
                subscribers = new Set();
                this.stateSubscribers.set(stateId, subscribers);
                await this.adapter.subscribeForeignStatesAsync(stateId);
            }
            subscribers.add(connection);

            const state = await this.adapter.getForeignStateAsync(stateId);
            if (state) {
                this.send(connection, {
                    type: 'state_update',
                    objectId: raw,
                    value: state.val,
                    timestamp: new Date(state.ts).toISOString(),
                    lastChange: new Date(state.lc).toISOString(),
                    ack: state.ack,
                });
            }
        }
    }

    private async unsubscribe(connection: Connection, objectIds: unknown[]): Promise<void> {
        for (const raw of objectIds) {
            if (typeof raw !== 'string') {
                continue;
            }
            const sub = connection.subscriptions.get(raw);
            if (!sub) {
                continue;
            }
            connection.subscriptions.delete(raw);
            await this.releaseSubscription(connection, sub.stateId);
        }
    }

    private async releaseSubscription(connection: Connection, stateId: string): Promise<void> {
        const subscribers = this.stateSubscribers.get(stateId);
        if (!subscribers) {
            return;
        }
        subscribers.delete(connection);
        if (subscribers.size === 0) {
            this.stateSubscribers.delete(stateId);
            await this.adapter.unsubscribeForeignStatesAsync(stateId);
        }
    }

    /** Wired from main.ts's adapter.on('stateChange', ...). */
    publishStateChange(stateId: string, state: ioBroker.State | null | undefined): void {
        if (!state) {
            return;
        }
        const subscribers = this.stateSubscribers.get(stateId);
        if (!subscribers) {
            return;
        }
        for (const connection of subscribers) {
            const sub = [...connection.subscriptions.values()].find((s) => s.stateId === stateId);
            if (!sub) {
                continue;
            }
            this.send(connection, {
                type: 'state_update',
                objectId: sub.publicId,
                value: state.val,
                timestamp: new Date(state.ts).toISOString(),
                lastChange: new Date(state.lc).toISOString(),
                ack: state.ack,
            });
        }
    }

    private publishCommandResult(event: CommandResultEvent): void {
        for (const connection of this.connections) {
            if (connection.ctx?.deviceId === event.deviceId) {
                this.send(connection, { type: 'command_result', commandId: event.commandId, status: event.status });
            }
        }
    }

    notifySessionRevoked(sessionId: string): void {
        for (const connection of this.connections) {
            if (connection.sessionId === sessionId) {
                this.send(connection, { type: 'session_revoked' });
                connection.ws.close();
            }
        }
    }

    notifyDeviceRevoked(deviceId: string): void {
        for (const connection of this.connections) {
            if (connection.ctx?.deviceId === deviceId) {
                this.send(connection, { type: 'session_revoked', reason: 'device_revoked' });
                connection.ws.close();
            }
        }
    }

    notifyPermissionsChanged(userId: string): void {
        for (const connection of this.connections) {
            if (connection.ctx?.userId === userId) {
                this.send(connection, { type: 'permissions_changed' });
            }
        }
    }

    /**
     * Also doubles as a periodic re-authorization sweep: the initial WS handshake only checks
     * session validity once (see handleAuth). Revocation is normally pushed proactively via
     * notifySessionRevoked/notifyDeviceRevoked, but plain session *expiry* (session.expiresAt
     * elapsing under a long-lived connection, e.g. a phone that never drops the socket for days)
     * had no equivalent check - a stale-but-still-open connection would otherwise keep receiving
     * live updates past its session's natural TTL. Piggybacking on the heartbeat interval that
     * already iterates every connection avoids a second timer for this.
     */
    private heartbeatTick(): void {
        for (const connection of this.connections) {
            if (!connection.alive) {
                connection.ws.terminate();
                this.cleanupConnection(connection);
                continue;
            }
            if (connection.sessionId && !this.isSessionStillActive(connection.sessionId)) {
                this.send(connection, { type: 'session_revoked' });
                connection.ws.close();
                this.cleanupConnection(connection);
                continue;
            }
            connection.alive = false;
            connection.ws.ping();
            this.send(connection, { type: 'heartbeat' });
        }
    }

    private isSessionStillActive(sessionId: string): boolean {
        try {
            this.sessions.requireActive(sessionId);
            return true;
        } catch {
            return false;
        }
    }

    private cleanupConnection(connection: Connection): void {
        this.connections.delete(connection);
        for (const sub of connection.subscriptions.values()) {
            void this.releaseSubscription(connection, sub.stateId);
        }
    }

    private send(connection: Connection, payload: Record<string, unknown>): void {
        if (connection.ws.readyState === WebSocket.OPEN) {
            connection.ws.send(JSON.stringify(payload));
        }
    }

    close(): void {
        clearInterval(this.heartbeatTimer);
        for (const connection of this.connections) {
            connection.ws.close();
        }
        this.wss.close();
    }

    get connectedDeviceCount(): number {
        const ids = new Set<string>();
        for (const connection of this.connections) {
            if (connection.authenticated && connection.ctx) {
                ids.add(connection.ctx.deviceId);
            }
        }
        return ids.size;
    }

    get connectionCount(): number {
        return this.connections.size;
    }
}
