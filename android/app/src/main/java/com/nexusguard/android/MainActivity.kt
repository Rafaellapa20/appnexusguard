package com.nexusguard.android

import android.content.Intent
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

    companion object {
        const val EXTRA_START_DEEP_CLEAN = "extra_start_deep_clean"
        const val EXTRA_START_HIBERNATION = "extra_start_hibernation"
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        handleQuickCleanIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleQuickCleanIntent(intent)
    }

    private fun handleQuickCleanIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_DEEP_CLEAN, false) == true) {
            intent.removeExtra(EXTRA_START_DEEP_CLEAN)
            mainViewModel.requestOpenDeepCleanFromShortcut()
        }
        if (intent?.getBooleanExtra(EXTRA_START_HIBERNATION, false) == true) {
            intent.removeExtra(EXTRA_START_HIBERNATION)
            mainViewModel.requestOpenHibernationFromShortcut()
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshAllRealData()
    }
}
