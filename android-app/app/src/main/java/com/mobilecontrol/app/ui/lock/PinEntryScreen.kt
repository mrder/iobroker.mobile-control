package com.mobilecontrol.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.mobilecontrol.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PinMode { SETUP, VERIFY }

/**
 * Renders both PIN setup (asks twice, confirms match) and PIN verification (unlock gate).
 * Successful verification/setup unlocks [AppLockManager] inside the view model; [onUnlocked] is
 * called from here as the direct navigation trigger so this screen doesn't depend on a separate
 * observer elsewhere in the tree.
 */
@Composable
fun PinEntryScreen(
    mode: PinMode,
    viewModel: LockViewModel,
    onUnlocked: () -> Unit,
) {
    val wrongPin by viewModel.wrongPin.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val lockoutUntil by viewModel.lockoutUntil.collectAsState()
    var pin by remember { mutableStateOf("") }
    var confirmStage by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var setupComplete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()

    // Ticks once a second while a lockout is active, purely to drive the countdown text below -
    // the actual lockout enforcement lives in LockViewModel.verifyPin (persisted, checked fresh
    // on every attempt), this is display-only.
    var remainingLockoutMs by remember { mutableStateOf(0L) }
    LaunchedEffect(lockoutUntil) {
        while (true) {
            val remaining = lockoutUntil - System.currentTimeMillis()
            remainingLockoutMs = remaining.coerceAtLeast(0L)
            if (remaining <= 0L) break
            delay(1_000L)
        }
    }
    val isLockedOut = mode == PinMode.VERIFY && remainingLockoutMs > 0L

    // Try biometrics automatically once when verifying, if the user has opted in - but never
    // while locked out (a biometric bypass would defeat the whole point of the PIN lockout).
    LaunchedEffect(mode, isLockedOut) {
        if (mode == PinMode.VERIFY && !isLockedOut && biometricEnabled && activity != null) {
            if (BiometricPromptHelper.authenticate(activity, "App entsperren")) {
                viewModel.onBiometricSuccess()
                onUnlocked()
            }
        }
    }

    LaunchedEffect(setupComplete) {
        if (setupComplete) onUnlocked()
    }

    var biometricRetryTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(biometricRetryTrigger) {
        if (biometricRetryTrigger > 0 && !isLockedOut && activity != null) {
            if (BiometricPromptHelper.authenticate(activity, "App entsperren")) {
                viewModel.onBiometricSuccess()
                onUnlocked()
            }
        }
    }

    fun submitVerify(submittedPin: String) {
        coroutineScope.launch {
            // Only the actual, awaited result unlocks - see LockViewModel.verifyPin's own doc for
            // the race this replaced (a wrong PIN briefly let the user through).
            if (viewModel.verifyPin(submittedPin)) {
                onUnlocked()
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(
                    if (mode == PinMode.SETUP) R.string.lock_pin_setup_title else R.string.lock_pin_title,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (mode == PinMode.SETUP && confirmStage) {
                Text("PIN bestätigen", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }

            if (isLockedOut) {
                val totalSeconds = (remainingLockoutMs / 1000L).toInt()
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                Text(
                    text = "Zu viele Fehlversuche. Gesperrt für noch %d:%02d Minuten.".format(minutes, seconds),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                return@Column
            }

            PinDots(length = pin.length, modifier = Modifier.padding(vertical = 24.dp))

            if (wrongPin) {
                Text(stringResource(R.string.lock_wrong_pin), color = MaterialTheme.colorScheme.error)
            }

            NumericKeypad(
                onDigit = { digit ->
                    if (pin.length < MAX_PIN_LENGTH) pin += digit
                    if (pin.length == MAX_PIN_LENGTH) {
                        when (mode) {
                            PinMode.VERIFY -> {
                                val submitted = pin
                                pin = ""
                                submitVerify(submitted)
                            }
                            PinMode.SETUP -> {
                                if (!confirmStage) {
                                    firstPin = pin
                                    pin = ""
                                    confirmStage = true
                                } else if (pin == firstPin) {
                                    viewModel.setupPin(pin)
                                    setupComplete = true
                                } else {
                                    pin = ""
                                    confirmStage = false
                                    firstPin = ""
                                }
                            }
                        }
                    }
                },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            )

            if (mode == PinMode.VERIFY && biometricEnabled && activity != null) {
                OutlinedButton(
                    onClick = { biometricRetryTrigger++ },
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.lock_use_biometric))
                }
            }
        }
    }
}

@Composable
private fun PinDots(length: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        repeat(MAX_PIN_LENGTH) { index ->
            val filled = index < length
            Text(
                text = if (filled) "●" else "○",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun NumericKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column {
        rows.forEach { row ->
            Row {
                row.forEach { key ->
                    when (key) {
                        "" -> Text("", modifier = Modifier.size(64.dp).padding(4.dp))
                        "⌫" -> Button(onClick = onBackspace, modifier = Modifier.size(64.dp).padding(4.dp)) { Text(key) }
                        else -> Button(onClick = { onDigit(key) }, modifier = Modifier.size(64.dp).padding(4.dp)) { Text(key) }
                    }
                }
            }
        }
    }
}

private const val MAX_PIN_LENGTH = 6
