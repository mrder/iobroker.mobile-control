package com.mobilecontrol.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.mobilecontrol.app.domain.model.PairingStatus

@Composable
fun PairingWaitScreen(
    viewModel: OnboardingViewModel,
    onApproved: () -> Unit,
    onRestart: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.pairingStatus, state.isPolling) {
        if (state.pairingStatus == PairingStatus.APPROVED && !state.isPolling) {
            onApproved()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                state.pairingStatus == PairingStatus.REJECTED -> {
                    Text(stringResource(R.string.onboarding_pairing_rejected), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRestart, modifier = Modifier.padding(top = 24.dp)) { Text(stringResource(R.string.common_retry)) }
                }

                state.pairingStatus == PairingStatus.EXPIRED -> {
                    Text(stringResource(R.string.onboarding_pairing_expired), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRestart, modifier = Modifier.padding(top = 24.dp)) { Text(stringResource(R.string.common_retry)) }
                }

                state.pairingError == "timeout" -> {
                    Text(stringResource(R.string.onboarding_pairing_timeout), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRestart, modifier = Modifier.padding(top = 24.dp)) { Text(stringResource(R.string.common_retry)) }
                }

                else -> {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.onboarding_pairing_wait_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Text(
                        stringResource(R.string.onboarding_pairing_wait_body),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
