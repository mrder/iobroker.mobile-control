/** Minimal in-memory stand-in for ioBroker.Adapter, covering only what the services under test call. */
export function createFakeAdapter(overrides: Record<string, unknown> = {}): ioBroker.Adapter {
    const states = new Map<string, ioBroker.State>();
    const objects = new Map<string, ioBroker.Object>();

    const toState = (val: unknown, ack: boolean): ioBroker.State =>
        ({ val, ack, ts: Date.now(), lc: Date.now(), from: 'test', q: 0 }) as ioBroker.State;

    const base = {
        namespace: 'mobile-control.0',
        log: {
            info: () => undefined,
            warn: () => undefined,
            error: () => undefined,
            debug: () => undefined,
            silly: () => undefined,
        },
        setObjectNotExistsAsync: async (id: string, obj: ioBroker.Object) => {
            if (!objects.has(id)) {
                objects.set(id, obj);
            }
            return undefined;
        },
        getStateAsync: async (id: string) => states.get(id) ?? null,
        setStateAsync: async (id: string, state: { val: unknown; ack: boolean }) => {
            states.set(id, toState(state.val, state.ack));
            return undefined;
        },
        getForeignObjectsAsync: async () => ({}) as Record<string, ioBroker.Object>,
        getForeignObjectAsync: async () => null,
        getForeignStateAsync: async (id: string) => states.get(id) ?? null,
        setForeignStateAsync: async (id: string, state: { val: unknown; ack: boolean }) => {
            states.set(id, toState(state.val, state.ack));
            return { id };
        },
        subscribeForeignStatesAsync: async () => undefined,
        unsubscribeForeignStatesAsync: async () => undefined,
        extendForeignObjectAsync: async () => undefined,
    };

    return Object.assign(base, overrides) as unknown as ioBroker.Adapter;
}
