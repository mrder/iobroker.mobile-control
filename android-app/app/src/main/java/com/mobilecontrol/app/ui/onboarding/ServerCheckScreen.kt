package com.mobilecontrol.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mobilecontrol.app.R
import com.mobilecontrol.app.data.crypto.FingerprintCheckResult

@Composable
fun ServerCheckScreen(
    viewModel: OnboardingViewModel,
    onContinue: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.qrPayload) {
        if (state.qrPayload != null && state.fingerprintResult == null) {
            viewModel.checkServer()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.onboarding_server_check_title), style = MaterialTheme.typography.headlineSmall)
            Text(state.qrPayload?.serverUrl.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

            when {
                state.fingerprintChecking -> CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))

                state.fingerprintResult is FingerprintCheckResult.Match -> {
                    Text("Server-Zertifikat bestätigt.", modifier = Modifier.padding(top = 16.dp))
                    Button(onClick = onContinue, modifier = Modifier.padding(top = 24.dp)) { Text("Weiter") }
                }

                state.fingerprintResult is FingerprintCheckResult.Mismatch -> {
                    Text(
                        stringResource(R.string.onboarding_server_check_fingerprint_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Button(
                        onClick = {
                            viewModel.acceptFingerprintMismatch()
                            onContinue()
                        },
                        modifier = Modifier.padding(top = 24.dp),
                    ) { Text("Trotzdem fortfahren") }
                }

                state.fingerprintResult is FingerprintCheckResult.Failure -> {
                    Text(
                        "Server nicht erreichbar: ${(state.fingerprintResult as FingerprintCheckResult.Failure).message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    OutlinedButton(onClick = { viewModel.checkServer() }, modifier = Modifier.padding(top = 24.dp)) {
                        Text(stringResource(R.string.common_retry))
                    }
                }

                else -> Unit
            }
        }
    }
}
