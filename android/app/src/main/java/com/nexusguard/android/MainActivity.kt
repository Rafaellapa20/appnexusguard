package com.nexusguard.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexusguard.android.ui.MainViewModel
import com.nexusguard.android.ui.NexusGuardMainScreen
import com.nexusguard.android.ui.theme.NexusTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            !mainViewModel.preferencesState.value.isLoaded
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0C0F16")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#11151E"))
        )

        setContent {
            val prefs by mainViewModel.preferencesState.collectAsStateWithLifecycle()
            NexusTheme(darkTheme = prefs.darkThemeEnabled) {
                if (prefs.isLoaded) {
                    NexusGuardMainScreen(viewModel = mainViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshAllRealData()
    }
}
