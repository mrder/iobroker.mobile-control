package com.mobilecontrol.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun KeyGenerationScreen(
    viewModel: OnboardingViewModel,
    onClaimed: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.claimId) {
        if (state.claimId != null) onClaimed()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.onboarding_key_generation_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.onboarding_key_generation_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )

            OutlinedTextField(
                value = state.deviceName,
                onValueChange = viewModel::setDeviceName,
                label = { Text("Gerätename") },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.claimId == null,
            )

            if (state.claimId == null) {
                Button(onClick = { viewModel.generateKeyAndClaim() }, modifier = Modifier.padding(top = 24.dp)) {
                    Text("Schlüssel erzeugen & koppeln")
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }

            if (state.pairingError != null && state.claimId == null) {
                Text(state.pairingError.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
