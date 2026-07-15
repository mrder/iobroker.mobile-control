package com.mobilecontrol.app.ui.widgets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.mobilecontrol.app.ui.lock.BiometricPromptHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gates a write command behind ObjectCatalogItem.confirmPolicy before it is sent with
 * confirmed=true. Shared by every writable widget type (Switch, Taster, Slider, Rollladen,
 * Thermostat) so the confirm-policy handling only exists in one place.
 *
 * Simplifications for the MVP (see android-app/README.md "Bewusste Vereinfachungen"):
 * - REAUTHENTICATE is treated like DIALOG - a real step-up re-login flow is future work.
 * - LOCAL_NETWORK_ONLY is also treated like DIALOG here; enforcing "must be on the local
 *   network" is a server-side concern (the server can reject/require it), the client only adds
 *   the same confirmation step as DIALOG rather than trying to detect network origin itself.
 * - BLOCKED_ON_MOBILE is NOT handled by this gate - callers (WidgetHost) are expected to disable
 *   the widget entirely before a command could ever be triggered for that policy.
 */
@Composable
fun rememberConfirmationGate(): ConfirmationGateState {
    val activity = LocalContext.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var dialogAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (dialogAction != null) {
        AlertDialog(
            onDismissRequest = { dialogAction = null },
            title = { Text("Aktion bestätigen?") },
            confirmButton = {
                TextButton(onClick = {
                    val action = dialogAction
                    dialogAction = null
                    action?.invoke()
                }) { Text("Ja") }
            },
            dismissButton = {
                TextButton(onClick = { dialogAction = null }) { Text("Abbrechen") }
            },
        )
    }

    return remember(activity, scope) {
        ConfirmationGateState(
            activity = activity,
            scope = scope,
            showDialog = { onConfirmed -> dialogAction = onConfirmed },
        )
    }
}

class ConfirmationGateState internal constructor(
    private val activity: FragmentActivity?,
    private val scope: CoroutineScope,
    private val showDialog: (onConfirmed: () -> Unit) -> Unit,
) {
    /**
     * Runs [onConfirmed] once [confirmPolicy] is satisfied. For NONE (or any unrecognized value)
     * this calls through immediately; the caller decides what `confirmed` flag to send based on
     * the same policy (NONE -> confirmed=false, everything else -> confirmed=true).
     */
    fun request(confirmPolicy: String, onConfirmed: () -> Unit) {
        when (confirmPolicy) {
            "NONE" -> onConfirmed()
            "DIALOG", "REAUTHENTICATE", "LOCAL_NETWORK_ONLY" -> showDialog(onConfirmed)
            "BIOMETRIC" -> {
                val host = activity
                if (host == null) {
                    // No FragmentActivity available to host the biometric prompt (shouldn't happen
                    // in practice since MainActivity is a FragmentActivity) - fall back to a
                    // dialog rather than silently skipping confirmation.
                    showDialog(onConfirmed)
                    return
                }
                scope.launch {
                    if (BiometricPromptHelper.authenticate(host, "Aktion bestätigen")) {
                        onConfirmed()
                    }
                }
            }
            else -> onConfirmed()
        }
    }
}
