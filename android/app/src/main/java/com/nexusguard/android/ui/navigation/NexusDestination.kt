package com.nexusguard.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

enum class NexusTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(
        route = "home",
        label = "Início",
        icon = Icons.Outlined.Home
    ),
    CLEAN(
        route = "clean",
        label = "Limpar",
        icon = Icons.Outlined.DeleteOutline
    ),
    GAMES(
        route = "games",
        label = "Jogos",
        icon = Icons.Outlined.SportsEsports
    ),
    BATTERY(
        route = "battery",
        label = "Bateria",
        icon = Icons.Outlined.BatteryChargingFull
    ),
    SYSTEM(
        route = "system",
        label = "Sistema",
        icon = Icons.Outlined.Settings
    )
}

object NexusRoutes {
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ACTIVE_BOOST = "active_boost"
}
