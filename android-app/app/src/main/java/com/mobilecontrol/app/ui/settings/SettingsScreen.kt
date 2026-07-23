package com.mobilecontrol.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilecontrol.app.R
import com.mobilecontrol.app.domain.model.ThemeMode
import com.mobilecontrol.app.ui.theme.SecureScreen
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onLoggedOut: () -> Unit,
) {
    SecureScreen()
    val state by viewModel.uiState.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setPushNotificationsEnabled(true)
    }
    fun onTogglePushNotifications(requested: Boolean) {
        val needsPermission = requested &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setPushNotificationsEnabled(requested)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Text(
                    stringResource(R.string.settings_server_info),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(state.deviceProfile?.serverUrl ?: "—") },
                    supportingContent = { Text("Fingerprint: ${state.deviceProfile?.serverFingerprint ?: "—"}") },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_last_connection)) },
                    supportingContent = {
                        Text(
                            state.lastConnectionAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "—",
                        )
                    },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("App-Sperre") },
                    trailingContent = {
                        Switch(checked = state.appLockEnabled, onCheckedChange = viewModel::setAppLockEnabled)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.lock_use_biometric)) },
                    trailingContent = {
                        Switch(checked = state.biometricEnabled, onCheckedChange = viewModel::setBiometricEnabled)
                    },
                )
            }
            item { HorizontalDivider() }
            item {
                Text(
                    stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                ThemeModeSelector(
                    selected = state.themeMode,
                    onSelect = viewModel::setThemeMode,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_push_notifications)) },
                    supportingContent = { Text(stringResource(R.string.settings_push_notifications_description)) },
                    trailingContent = {
                        Switch(checked = state.pushNotificationsEnabled, onCheckedChange = ::onTogglePushNotifications)
                    },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingContent = { TextButton(onClick = { viewModel.clearCache() }) { Text(stringResource(R.string.settings_clear_cache)) } },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_logs)) },
                    trailingContent = { TextButton(onClick = { showLogs = true }) { Text("Anzeigen") } },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.settings_app_version)) }, supportingContent = { Text(viewModel.appVersion) })
            }
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.settings_api_version)) }, supportingContent = { Text(viewModel.apiVersion) })
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_logout), color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    trailingContent = {
                        TextButton(onClick = { showLogoutConfirm = true }) { Text(stringResource(R.string.settings_logout)) }
                    },
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout(onLoggedOut)
                }) { Text(stringResource(R.string.settings_logout)) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text(stringResource(R.string.settings_logs)) },
            text = {
                Column {
                    state.logs.takeLast(30).reversed().forEach { entry ->
                        Text("[${entry.level}] ${entry.message}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.logs.isEmpty()) Text("Keine Einträge")
                }
            },
            confirmButton = { TextButton(onClick = { showLogs = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

private val THEME_MODE_LABELS: Map<ThemeMode, Int> = mapOf(
    ThemeMode.SYSTEM to R.string.settings_theme_system,
    ThemeMode.LIGHT to R.string.settings_theme_light,
    ThemeMode.DARK to R.string.settings_theme_dark,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
            ) {
                Text(stringResource(THEME_MODE_LABELS.getValue(mode)))
            }
        }
    }
}
