package com.mobilecontrol.app.ui.theme

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Marks the current screen as sensitive (pairing, settings/tokens): sets FLAG_SECURE so it is
 * blacked out in the recent-apps task switcher and cannot be screenshotted, clearing it again once
 * the screen leaves composition. Route-based detection at the Activity level doesn't work here
 * because Settings lives inside a nested bottom-nav NavHost the top-level NavController never sees,
 * so each sensitive screen opts in directly instead.
 */
@Composable
fun SecureScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
