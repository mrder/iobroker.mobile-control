/**
 * Versioned migrations for the adapter's own stored data (the CollectionStore-backed
 * "storage.*" states, not the ioBroker object tree itself). Each migration runs exactly
 * once, in ascending version order, tracked via an internal "meta.schemaVersion" state.
 *
 * There is nothing to migrate yet (this is the first shipped schema, version 1). Add future
 * migrations to the MIGRATIONS array below - do not renumber or remove past entries once
 * released, since installations may be sitting at any prior version.
 *
 * Example of what a future migration looks like:
 *
 *   {
 *     version: 2,
 *     description: 'rename Device.fingerprint to Device.publicKeyFingerprint',
 *     run: async (adapter) => {
 *       const state = await adapter.getStateAsync('storage.devices');
 *       const raw = typeof state?.val === 'string' ? JSON.parse(state.val) : {};
 *       for (const device of Object.values(raw) as Record<string, unknown>[]) {
 *         device.publicKeyFingerprint = device.fingerprint;
 *         delete device.fingerprint;
 *       }
 *       await adapter.setStateAsync('storage.devices', { val: JSON.stringify(raw), ack: true });
 *     },
 *   },
 */
export interface Migration {
    version: number;
    description: string;
    run: (adapter: ioBroker.Adapter) => Promise<void>;
}

export const CURRENT_SCHEMA_VERSION = 1;

const MIGRATIONS: Migration[] = [];

const SCHEMA_VERSION_STATE_ID = 'meta.schemaVersion';

/** Runs any migrations newer than the currently stored schema version, then updates it. */
export async function runMigrations(adapter: ioBroker.Adapter): Promise<void> {
    await adapter.setObjectNotExistsAsync(SCHEMA_VERSION_STATE_ID, {
        type: 'state',
        common: {
            name: 'Internal schema version (do not edit)',
            type: 'number',
            role: 'value',
            read: true,
            write: false,
            def: 0,
        },
        native: {},
    });

    const state = await adapter.getStateAsync(SCHEMA_VERSION_STATE_ID);
    let currentVersion = typeof state?.val === 'number' ? state.val : 0;

    const pending = MIGRATIONS.filter((migration) => migration.version > currentVersion).sort((a, b) => a.version - b.version);

    for (const migration of pending) {
        adapter.log.info(`mobile-control: running migration v${migration.version}: ${migration.description}`);
        await migration.run(adapter);
        currentVersion = migration.version;
        await adapter.setStateAsync(SCHEMA_VERSION_STATE_ID, { val: currentVersion, ack: true });
    }

    if (currentVersion < CURRENT_SCHEMA_VERSION) {
        // No migration bridges the gap to CURRENT_SCHEMA_VERSION (e.g. a fresh install) -
        // just record the current baseline, nothing to transform.
        await adapter.setStateAsync(SCHEMA_VERSION_STATE_ID, { val: CURRENT_SCHEMA_VERSION, ack: true });
    }
}
