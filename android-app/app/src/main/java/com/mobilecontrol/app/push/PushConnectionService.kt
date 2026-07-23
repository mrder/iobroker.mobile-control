package com.mobilecontrol.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mobilecontrol.app.R
import com.mobilecontrol.app.domain.repository.StateRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the app's shared WebSocket connection (see RealtimeWebSocketClient/StateRepository) alive
 * even while no screen is visible, so alarm updates for objects nothing currently displays still
 * arrive live (see AlarmMonitor) - opt-in via Settings ("Live-Benachrichtigungen"), since holding
 * a connection open costs more battery than the app's normal on-screen-only behavior. Android
 * requires an ongoing, low-priority notification for any foreground service - that notification
 * IS the visible acknowledgment that this is running, there is no hidden background connection.
 *
 * Deliberately never force-disconnects the shared WebSocket client itself in onDestroy(): other
 * parts of the app (dashboard/object browser screens) manage their own connect()/disconnect()
 * calls independently, and this service has no way to know if one of them still wants the
 * connection up. Stopping this service just removes the "stay connected even with no screen
 * open" guarantee; Android will naturally reclaim the process (and with it the socket) once the
 * app is fully backgrounded and no longer foreground-protected.
 */
@AndroidEntryPoint
class PushConnectionService : Service() {

    @Inject lateinit var stateRepository: StateRepository
    @Inject lateinit var alarmMonitor: AlarmMonitor

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SystemNotifierImpl.ONGOING_CHANNEL_ID,
                getString(R.string.push_ongoing_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply { description = getString(R.string.push_ongoing_channel_description) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, SystemNotifierImpl.ONGOING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.push_ongoing_notification_title))
            .setContentText(getString(R.string.push_ongoing_notification_body))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        stateRepository.connect()
        alarmMonitor.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        alarmMonitor.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ONGOING_NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PushConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PushConnectionService::class.java))
        }
    }
}
