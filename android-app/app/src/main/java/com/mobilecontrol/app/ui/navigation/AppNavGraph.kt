package com.mobilecontrol.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobilecontrol.app.data.remote.RevocationReason
import com.mobilecontrol.app.ui.dashboards.DashboardEditorScreen
import com.mobilecontrol.app.ui.lock.LockViewModel
import com.mobilecontrol.app.ui.lock.PinEntryScreen
import com.mobilecontrol.app.ui.lock.PinMode
import com.mobilecontrol.app.ui.lock.RevokedScreen
import com.mobilecontrol.app.ui.onboarding.KeyGenerationScreen
import com.mobilecontrol.app.ui.onboarding.OnboardingViewModel
import com.mobilecontrol.app.ui.onboarding.PairingWaitScreen
import com.mobilecontrol.app.ui.onboarding.QrScanScreen
import com.mobilecontrol.app.ui.onboarding.ServerCheckScreen
import com.mobilecontrol.app.ui.onboarding.WelcomeScreen
import com.mobilecontrol.app.ui.start.StartScreen

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    val rootViewModel: AppRootViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        rootViewModel.revocationNotifier.events.collect { _: RevocationReason ->
            rootViewModel.handleRevocation()
            navController.navigate(Routes.REVOKED) {
                popUpTo(0)
            }
        }
    }

    val isLocked by rootViewModel.isLocked.collectAsState()

    var resolvedStart by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        resolvedStart = when (rootViewModel.resolveStartDestination()) {
            StartDestination.ONBOARDING -> Routes.WELCOME
            StartDestination.LOCK_VERIFY -> Routes.LOCK
            StartDestination.LOCK_SETUP -> Routes.PIN_SETUP
            StartDestination.START -> {
                rootViewModel.connectRealtime()
                Routes.START
            }
        }
    }

    // Re-lock takes effect immediately (e.g. after backgrounding the app) by bouncing any screen
    // outside onboarding/lock/revoked back to the PIN gate. Unlocking always re-enters at START,
    // which loses e.g. a dashboard editor's back-stack depth - an accepted MVP simplification.
    LaunchedEffect(isLocked, resolvedStart) {
        if (isLocked && resolvedStart != null) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            val unprotected = currentRoute == null ||
                currentRoute.startsWith(Routes.ONBOARDING_GRAPH) ||
                currentRoute == Routes.LOCK ||
                currentRoute == Routes.REVOKED
            if (!unprotected) {
                navController.navigate(Routes.LOCK) { popUpTo(0) }
            }
        }
    }

    val start = resolvedStart
    if (start == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onStartScan = { navController.navigate(Routes.QR_SCAN) })
        }
        composable(Routes.QR_SCAN) {
            val onboardingViewModel = onboardingViewModel(navController)
            QrScanScreen(onCodeScanned = { raw ->
                onboardingViewModel.onQrCodeScanned(raw)
                navController.navigate(Routes.SERVER_CHECK)
            })
        }
        composable(Routes.SERVER_CHECK) {
            val onboardingViewModel = onboardingViewModel(navController)
            ServerCheckScreen(
                viewModel = onboardingViewModel,
                onContinue = { navController.navigate(Routes.KEY_GENERATION) },
            )
        }
        composable(Routes.KEY_GENERATION) {
            val onboardingViewModel = onboardingViewModel(navController)
            KeyGenerationScreen(
                viewModel = onboardingViewModel,
                onClaimed = { navController.navigate(Routes.PAIRING_WAIT) },
            )
        }
        composable(Routes.PAIRING_WAIT) {
            val onboardingViewModel = onboardingViewModel(navController)
            PairingWaitScreen(
                viewModel = onboardingViewModel,
                onApproved = {
                    navController.navigate(Routes.PIN_SETUP) { popUpTo(Routes.WELCOME) { inclusive = true } }
                },
                onRestart = {
                    onboardingViewModel.retryAfterRejectionOrTimeout()
                    navController.navigate(Routes.WELCOME) { popUpTo(Routes.WELCOME) { inclusive = true } }
                },
            )
        }
        composable(Routes.PIN_SETUP) {
            val lockViewModel: LockViewModel = hiltViewModel()
            PinEntryScreen(
                mode = PinMode.SETUP,
                viewModel = lockViewModel,
                onUnlocked = {
                    rootViewModel.connectRealtime()
                    navController.navigate(Routes.START) { popUpTo(0) }
                },
            )
        }
        composable(Routes.LOCK) {
            val lockViewModel: LockViewModel = hiltViewModel()
            PinEntryScreen(
                mode = PinMode.VERIFY,
                viewModel = lockViewModel,
                onUnlocked = {
                    rootViewModel.connectRealtime()
                    navController.navigate(Routes.START) { popUpTo(0) }
                },
            )
        }
        composable(Routes.REVOKED) {
            RevokedScreen(onRestartPairing = {
                navController.navigate(Routes.WELCOME) { popUpTo(0) }
            })
        }
        composable(Routes.START) {
            StartScreen(
                onOpenDashboard = { id -> navController.navigate(Routes.dashboardEditor(id)) },
                onLoggedOut = { navController.navigate(Routes.WELCOME) { popUpTo(0) } },
            )
        }
        composable(
            Routes.DASHBOARD_EDITOR,
            arguments = listOf(navArgument(Routes.DASHBOARD_EDITOR_ARG) { type = NavType.StringType }),
        ) {
            DashboardEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun onboardingViewModel(navController: NavController): OnboardingViewModel {
    val parentEntry = remember { navController.getBackStackEntry(Routes.WELCOME) }
    return hiltViewModel(parentEntry)
}
