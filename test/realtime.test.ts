import { strict as assert } from 'node:assert';
import http from 'node:http';
import { EventEmitter } from 'node:events';
import { RealtimeGateway } from '../src/realtime';
import { createFakeAdapter } from './helpers/fakeAdapter';

describe('RealtimeGateway', () => {
    it('does not crash the process when the underlying http.Server hits a real listen error (e.g. EADDRINUSE)', async () => {
        // Regression test for a real bug found during the first live install: the `ws` package
        // forwards the underlying http.Server's 'error' event onto the WebSocketServer instance
        // (see ws/lib/websocket-server.js addListeners()). Node's EventEmitter throws synchronously,
        // as a genuine uncaught exception, when an 'error' event has zero listeners - so without
        // RealtimeGateway registering one, ANY listen() error crashed the whole adapter process
        // before it ever reached main.ts's own error handling on the same server. Occupying a real
        // port with a first server and pointing a second, RealtimeGateway-wrapped server at the
        // same port reproduces the exact failure mode end-to-end.
        const blocker = http.createServer();
        await new Promise<void>((resolve) => blocker.listen(0, '127.0.0.1', resolve));
        const address = blocker.address();
        if (address === null || typeof address === 'string') {
            throw new Error('failed to determine the blocking server port');
        }
        const port = address.port;

        const server = http.createServer();
        const adapter = createFakeAdapter();
        const gateway = new RealtimeGateway(
            server,
            adapter,
            undefined as unknown as import('../src/auth').AuthService,
            undefined as unknown as import('../src/sessions').SessionsService,
            undefined as unknown as import('../src/devices').DevicesService,
            undefined as unknown as import('../src/catalog').CatalogService,
            new EventEmitter() as unknown as import('../src/commands').CommandsService,
        );

        try {
            const listenError = await new Promise<Error>((resolve) => {
                server.once('error', resolve);
                server.listen(port, '127.0.0.1');
            });

            assert.equal((listenError as NodeJS.ErrnoException).code, 'EADDRINUSE');
        } finally {
            gateway.close();
            await new Promise<void>((resolve) => blocker.close(() => resolve()));
        }
    });
});
