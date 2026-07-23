import { strict as assert } from 'node:assert';
import { CollectionStore } from '../src/lib/store';
import { AlarmEventsService } from '../src/alarms';
import type { AlarmEvent } from '../src/lib/types';
import { createFakeAdapter } from './helpers/fakeAdapter';

const SMOKE_ALARM_STATE_ID = 'zigbee.0.kitchen.smoke_alarm';
const TEMPERATURE_STATE_ID = 'zigbee.0.living_room.temperature';

async function setup() {
    const adapter = createFakeAdapter({
        getForeignObjectsAsync: async () => ({
            [SMOKE_ALARM_STATE_ID]: {
                type: 'state',
                common: { name: 'Rauchmelder Küche', role: 'sensor.alarm.fire', type: 'boolean' },
                native: {},
            },
            [TEMPERATURE_STATE_ID]: {
                type: 'state',
                common: { name: 'Wohnzimmer Temperatur', role: 'value.temperature', type: 'number' },
                native: {},
            },
        }),
    });
    const store = new CollectionStore<AlarmEvent>(adapter, 'alarmEvents');
    await store.init();
    const service = new AlarmEventsService(adapter, store);
    return { service };
}

describe('AlarmEventsService', () => {
    it('records a rising edge (val === true) for a state it subscribed to as alarm-role', async () => {
        const { service } = await setup();
        await service.subscribeToAlarmObjects();

        await service.recordIfAlarm(SMOKE_ALARM_STATE_ID, { val: true, ack: true, ts: 1000 } as ioBroker.State);

        const events = service.listSince(0);
        assert.equal(events.length, 1);
        assert.equal(events[0].stateId, SMOKE_ALARM_STATE_ID);
        assert.equal(events[0].value, true);
        assert.equal(events[0].timestamp, 1000);
    });

    it('ignores a state change for an object that is not alarm-role, even with the same value shape', async () => {
        const { service } = await setup();
        await service.subscribeToAlarmObjects();

        await service.recordIfAlarm(TEMPERATURE_STATE_ID, { val: true, ack: true, ts: 1000 } as ioBroker.State);

        assert.equal(service.listSince(0).length, 0);
    });

    it('ignores a falling edge (val === false) - only "went active" transitions are recorded', async () => {
        const { service } = await setup();
        await service.subscribeToAlarmObjects();

        await service.recordIfAlarm(SMOKE_ALARM_STATE_ID, { val: false, ack: true, ts: 1000 } as ioBroker.State);

        assert.equal(service.listSince(0).length, 0);
    });

    it('ignores alarm-role state changes before subscribeToAlarmObjects() has run', async () => {
        const { service } = await setup();
        await service.recordIfAlarm(SMOKE_ALARM_STATE_ID, { val: true, ack: true, ts: 1000 } as ioBroker.State);
        assert.equal(service.listSince(0).length, 0);
    });

    it('listSince only returns events strictly after the given timestamp, sorted ascending', async () => {
        const { service } = await setup();
        await service.subscribeToAlarmObjects();

        await service.recordIfAlarm(SMOKE_ALARM_STATE_ID, { val: true, ack: true, ts: 1000 } as ioBroker.State);
        await service.recordIfAlarm(SMOKE_ALARM_STATE_ID, { val: false, ack: true, ts: 1500 } as ioBroker.State); // falling edge, not recorded
        await service.recordIfAlarm(SMOKE_ALARM_STATE_ID, { val: true, ack: true, ts: 2000 } as ioBroker.State);

        const sinceFirst = service.listSince(1000);
        assert.equal(sinceFirst.length, 1);
        assert.equal(sinceFirst[0].timestamp, 2000);

        const sinceStart = service.listSince(0);
        assert.deepEqual(
            sinceStart.map((e) => e.timestamp),
            [1000, 2000],
        );
    });
});
