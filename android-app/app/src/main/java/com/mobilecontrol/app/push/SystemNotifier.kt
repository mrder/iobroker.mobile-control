package com.mobilecontrol.app.push

/** Thin, testable wrapper around actually posting a system (status-bar) notification - kept
 *  separate from [AlarmMonitor] so the "which alarms should be shown" decision logic can be unit
 *  tested with a fake, without touching Android's NotificationManager. */
interface SystemNotifier {
    fun notifyAlarm(objectId: String, title: String, body: String, timestampMs: Long)
}
