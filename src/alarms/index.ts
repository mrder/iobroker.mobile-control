import { v4 as uuid } from 'uuid';
import { CollectionStore } from '../lib/store';
import type { AlarmEvent } from '../lib/types';

const MAX_ALARM_EVENTS = 500;

/**
 * Persists a history of "alarm went active" transitions for every ioBroker state whose role
 * marks it as an alarm (role.includes('alarm'), the same convention CatalogService already uses
 * for suggestedWidgets) - collected regardless of whether any client currently has that object
 * open. RealtimeGateway only forwards live updates to a connection for objects that connection
 * has explicitly subscribed to; a mobile client that was closed/backgrounded when an alarm fired
 * would otherwise never learn about it. This service subscribes to every alarm-role object
 * itself, so onStateChange fires and gets recorded no matter who (if anyone) is currently
 * watching, and a client can later ask "what happened since I was last here" via listSince().
 *
 * Authorization is intentionally NOT checked at collection time - same pattern as camera/history/
 * catalog, where collecting data isn't itself privileged, only serving it to a specific caller is
 * (checked by the caller against AuthorizationService/CatalogService.canRead at read time).
 *
 * Known MVP simplification: subscribes once at startup from the live ioBroker object tree. An
 * alarm-role object created afterwards (without an adapter restart) won't be picked up until the
 * next restart - acceptable for now, same class of staleness already documented for
 * CatalogService.currentVersion().
 */
export class AlarmEventsService {
    private readonly alarmStateIds = new Set<string>();

    constructor(
        private readonly adapter: ioBroker.Adapter,
        private readonly store: CollectionStore<AlarmEvent>,
    ) {}

    async subscribeToAlarmObjects(): Promise<void> {
        const objects = await this.adapter.getForeignObjectsAsync('*');
        for (const [id, obj] of Object.entries(objects)) {
            if (!obj || obj.type !== 'state') {
                continue;
            }
            const role = (obj.common as ioBroker.StateCommon | undefined)?.role ?? '';
            if (role.includes('alarm')) {
                this.alarmStateIds.add(id);
                await this.adapter.subscribeForeignStatesAsync(id);
            }
        }
    }

    /** Called from the adapter's shared onStateChange handler for every subscribed state - only
     *  acts on ids this service itself subscribed to via subscribeToAlarmObjects(), and only
     *  records a rising edge (value === true), not every re-publish of an already-active alarm. */
    async recordIfAlarm(stateId: string, state: ioBroker.State | null | undefined): Promise<void> {
        if (!this.alarmStateIds.has(stateId) || !state || state.val !== true) {
            return;
        }
        const event: AlarmEvent = { id: uuid(), stateId, value: true, timestamp: state.ts ?? Date.now() };
        await this.store.put(event);
        await this.store.trim(MAX_ALARM_EVENTS, (e) => e.timestamp);
    }

    listSince(sinceMs: number): AlarmEvent[] {
        return this.store
            .list()
            .filter((event) => event.timestamp > sinceMs)
            .sort((a, b) => a.timestamp - b.timestamp);
    }
}
