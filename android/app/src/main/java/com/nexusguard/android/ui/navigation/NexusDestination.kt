package com.nexusguard.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
        icon = Icons.Outlined.Home
    ),
    CLEAN(
        route = "clean",
        label = "Limpar",
        icon = Icons.Outlined.Delete
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
        icon = Icons.Outlined.Settings
    )
}

object NexusRoutes {
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ACTIVE_BOOST = "active_boost"
}
