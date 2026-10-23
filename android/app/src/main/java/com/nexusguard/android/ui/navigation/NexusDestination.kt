package com.nexusguard.android.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.nexusguard.android.ui.theme.NexusIcons

enum class NexusTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(
        route = "home",
        label = "Início",
        icon = NexusIcons.Home
    ),
    CLEAN(
        route = "clean",
        label = "Limpar",
        icon = NexusIcons.Delete
    ),
    APPS(
        route = "apps",
        label = "Apps",
        icon = NexusIcons.AppsGrid
    ),
    GAMES(
        route = "games",
        label = "Jogos",
        icon = NexusIcons.SportsEsports
    ),
    BATTERY(
        route = "battery",
        label = "Bateria",
        icon = NexusIcons.BatteryChargingFull
    ),
    SYSTEM(
        route = "system",
        label = "Sistema",
        icon = NexusIcons.Settings
    )
}

object NexusRoutes {
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ACTIVE_BOOST = "active_boost"
    const val HIBERNATION = "hibernation"
    const val MONTHLY_REPORT = "monthly_report"
}
