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
 * (e.g. "KFONWI" on an Amazon Fire tablet, not "Fire Tablet") - the device's own Bluetooth name
 * (Settings.Secure "bluetooth_name") is usually what the user actually set and recognizes, since
 * it's shown in their own Bluetooth/WiFi Direct settings. Reading it via Settings.Secure needs no
 * special permission, unlike BluetoothAdapter.getDefaultAdapter()?.name, which requires
 * BLUETOOTH_CONNECT from API 31 onward for the same value. Still only a suggestion - the user can
 * freely edit it before pairing (see OnboardingViewModel.setDeviceName).
 */
@Singleton
class DeviceNameProvider @Inject constructor(@ApplicationContext private val context: Context) {

    fun suggestedName(): String {
        val bluetoothName = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        if (!bluetoothName.isNullOrBlank()) {
            return bluetoothName
        }
        return Build.MODEL ?: "Android-Gerät"
    }
}
