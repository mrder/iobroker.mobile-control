package com.mobilecontrol.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mobilecontrol.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemNotifier {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                context.getString(R.string.push_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.push_alarm_channel_description) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun notifyAlarm(objectId: String, title: String, body: String, timestampMs: Long) {
        // Defense in depth: the toggle in Settings is gated on this permission already being
        // granted, but it can be revoked afterwards in system settings without the app knowing -
        // this avoids a SecurityException crash if that happens (POST_NOTIFICATIONS, API 33+).
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setWhen(timestampMs)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(objectId.hashCode(), notification)
    }

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_events"
        const val ONGOING_CHANNEL_ID = "push_connection"
    }
}
