import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { SessionsService } from '../src/sessions';
import { ApiError } from '../src/lib/errors';
import type { Session } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

async function setup(): Promise<SessionsService> {
    const adapter = createFakeAdapter();
    const store = new CollectionStore<Session>(adapter, 'sessions');
    await store.init();
    return new SessionsService(store);
}

describe('SessionsService refresh token rotation', () => {
    it('rotates the refresh token and invalidates the previous one for normal use', async () => {
        const sessions = await setup();
        const { session, refreshToken } = await sessions.create({
            userId: 'u1',
            deviceId: 'd1',
            roleId: 'viewer',
            ttlDays: 30,
            ip: null,
            userAgent: null,
        });

        const rotated = await sessions.rotate(session.id, refreshToken);
        assert.notEqual(rotated.refreshToken, refreshToken);
        assert.equal(rotated.session.refreshGeneration, 1);
    });

    it('detects reuse of an already-rotated-away token and locks the token family', async () => {
        const sessions = await setup();
        const { session, refreshToken: firstToken } = await sessions.create({
            userId: 'u1',
            deviceId: 'd1',
            roleId: 'viewer',
            ttlDays: 30,
            ip: null,
            userAgent: null,
        });

        const rotated = await sessions.rotate(session.id, firstToken);

        // an attacker (or a race) replays the OLD, already-rotated-away token
        await assert.rejects(
            () => sessions.rotate(session.id, firstToken),
            (err: unknown) => err instanceof ApiError && err.code === 'SESSION_REVOKED',
        );

        // the legitimate holder of the NEW token is now also locked out - the whole family was revoked
        await assert.rejects(
            () => sessions.rotate(session.id, rotated.refreshToken),
            (err: unknown) => err instanceof ApiError && err.code === 'SESSION_REVOKED',
        );
    });

    it('rejects an entirely unrecognized refresh token without revoking anything', async () => {
        const sessions = await setup();
        const { session } = await sessions.create({
            userId: 'u1',
            deviceId: 'd1',
            roleId: 'viewer',
            ttlDays: 30,
            ip: null,
            userAgent: null,
        });

        await assert.rejects(() => sessions.rotate(session.id, 'not-a-real-token'), (err: unknown) => err instanceof ApiError);
        assert.equal(sessions.get(session.id)?.revoked, false);
    });

    it('findByRefreshToken locates a session by its current OR previous-generation token', async () => {
        const sessions = await setup();
        const { session, refreshToken: firstToken } = await sessions.create({
            userId: 'u1',
            deviceId: 'd1',
            roleId: 'viewer',
            ttlDays: 30,
            ip: null,
            userAgent: null,
        });

        assert.equal(sessions.findByRefreshToken('d1', firstToken)?.id, session.id);

        const rotated = await sessions.rotate(session.id, firstToken);
        assert.equal(sessions.findByRefreshToken('d1', rotated.refreshToken)?.id, session.id);
        assert.equal(sessions.findByRefreshToken('d1', 'garbage'), undefined);
    });

    it('revokeAllForUser immediately blocks all future rotation for that user', async () => {
        const sessions = await setup();
        const { session, refreshToken } = await sessions.create({
            userId: 'u1',
            deviceId: 'd1',
            ttlDays: 30,
            roleId: 'viewer',
            ip: null,
            userAgent: null,
        });

        await sessions.revokeAllForUser('u1');

        await assert.rejects(() => sessions.rotate(session.id, refreshToken), (err: unknown) => err instanceof ApiError && err.code === 'SESSION_REVOKED');
    });
});
