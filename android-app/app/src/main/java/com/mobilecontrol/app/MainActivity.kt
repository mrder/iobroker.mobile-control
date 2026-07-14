package com.mobilecontrol.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobilecontrol.app.ui.navigation.AppNavGraph
import com.mobilecontrol.app.ui.navigation.Routes
import com.mobilecontrol.app.ui.theme.MobileControlTheme
import dagger.hilt.android.AndroidEntryPoint

// Settings is intentionally not listed here: it lives inside Start's nested bottom-nav NavHost,
// which the top-level NavController never sees as a distinct route. SettingsScreen instead calls
// ui.theme.SecureScreen() itself to set FLAG_SECURE. Onboarding/pairing screens ARE top-level
// routes, so route-prefix matching works fine for them.
private val SECURE_SCREEN_PREFIXES = listOf(Routes.ONBOARDING_GRAPH)

// FragmentActivity (not plain ComponentActivity) is required here: androidx.biometric.BiometricPrompt
// hosts its dialog via a headless Fragment internally, and PinEntryScreen casts LocalContext.current
// to FragmentActivity to invoke it.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MobileControlTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()

                LaunchedEffect(backStackEntry) {
                    val route = backStackEntry?.destination?.route.orEmpty()
                    val sensitive = SECURE_SCREEN_PREFIXES.any { route.startsWith(it) }
                    if (sensitive) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                AppNavGraph(navController = navController)
            }
        }
    }
}
