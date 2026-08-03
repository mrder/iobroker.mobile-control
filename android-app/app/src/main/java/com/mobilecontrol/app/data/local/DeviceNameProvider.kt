package com.mobilecontrol.app.data.local

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suggests a friendly default device name for the onboarding "device name" field. Build.MODEL is
 * frequently an internal hardware/marketing codename rather than something a user recognizes
 * (e.g. "KFONWI" on an Amazon Fire tablet, not "Fire Tablet") - the device's own name
 * (Settings.Global.DEVICE_NAME, the same value shown under "About phone"/Bluetooth/WiFi Direct)
 * is usually what the user actually set and recognizes.
 *
 * Live-crash found (2026-07-31) on a Samsung Galaxy S21 / Android 13+: this used to read the
 * Settings.Secure key "bluetooth_name" directly, which threw a SecurityException ("only readable
 * to apps with targetSdkVersion <= 31") - that key was never actually a stable public API, it just
 * happened to work unrestricted on the older Fire HD 8 test tablet (Android 9), which doesn't
 * enforce the API-31+ gate. Settings.Global.DEVICE_NAME is the documented, unrestricted equivalent.
 * Wrapped in try/catch regardless, since this reads OS/OEM state outside our control and a naming
 * suggestion should never be able to crash onboarding.
 */
@Singleton
class DeviceNameProvider @Inject constructor(@ApplicationContext private val context: Context) {

    fun suggestedName(): String {
        val deviceName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        if (!deviceName.isNullOrBlank()) {
            return deviceName
        }
        return Build.MODEL ?: "Android-Gerät"
    }
}
