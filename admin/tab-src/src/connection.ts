import { Connection } from '@iobroker/adapter-react-v5';

function getInstanceNumber(): number {
    const params = new URLSearchParams(window.location.search);
    const instance = params.get('instance');
    return instance ? parseInt(instance, 10) : 0;
}

export const instanceNumber = getInstanceNumber();
export const instanceId = `mobile-control.${instanceNumber}`;

let connectionPromise: Promise<Connection> | null = null;

/**
 * Wraps @iobroker/adapter-react-v5's Connection (the standard socket.io wrapper every
 * modern custom ioBroker admin tab uses to talk to its adapter instance). Resolves once
 * connected, or rejects after a timeout so the UI can show a clear error instead of
 * hanging silently if the admin socket never becomes ready.
 */
export function getConnection(): Promise<Connection> {
    if (!connectionPromise) {
        connectionPromise = new Promise((resolve, reject) => {
            const timeout = setTimeout(() => reject(new Error('Timeout beim Verbindungsaufbau zum Adapter')), 15_000);
            let conn: Connection;
            try {
                conn = new Connection({
                    name: 'mobile-control-tab',
                    doNotLoadAllObjects: true,
                    onReady: () => {
                        clearTimeout(timeout);
                        resolve(conn);
                    },
                    onError: (err: unknown) => {
                        // eslint-disable-next-line no-console
                        console.error('mobile-control admin tab: connection error', err);
                    },
                } as ConstructorParameters<typeof Connection>[0]);
            } catch (err) {
                clearTimeout(timeout);
                reject(err);
            }
        });
    }
    return connectionPromise;
}

export async function callAdapter<T = unknown>(command: string, message?: Record<string, unknown>): Promise<T> {
    const conn = await getConnection();
    const result = await conn.sendTo(instanceId, command, message ?? {});
    if (result && typeof result === 'object' && 'error' in (result as Record<string, unknown>)) {
        const err = (result as Record<string, unknown>).error;
        throw new Error(typeof err === 'string' ? err : 'Unbekannter Adapterfehler');
    }
    return result as T;
}
