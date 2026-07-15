import { strict as assert } from 'node:assert';
import { runMigrations, CURRENT_SCHEMA_VERSION } from '../src/migrations';
import { createFakeAdapter } from './helpers/fakeAdapter';

describe('runMigrations', () => {
    it('sets the schema version state to the current baseline on a fresh install', async () => {
        const adapter = createFakeAdapter();
        await runMigrations(adapter);
        const state = await adapter.getStateAsync('meta.schemaVersion');
        assert.equal(state?.val, CURRENT_SCHEMA_VERSION);
    });

    it('is idempotent - running it again does not throw or regress the stored version', async () => {
        const adapter = createFakeAdapter();
        await runMigrations(adapter);
        await runMigrations(adapter);
        const state = await adapter.getStateAsync('meta.schemaVersion');
        assert.equal(state?.val, CURRENT_SCHEMA_VERSION);
    });
});
