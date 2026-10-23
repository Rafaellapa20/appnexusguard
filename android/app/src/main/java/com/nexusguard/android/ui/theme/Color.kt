package com.nexusguard.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class NexusColorTokens(
    val bg: Color,
    val bgRaised: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textDim: Color,
    val textMuted: Color,
    val accent: Color,
    val accent2: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    val isDark: Boolean
) {
    val primaryGradient: Brush
        get() = Brush.linearGradient(colors = listOf(accent, accent2))
}

val DarkNexusColors = NexusColorTokens(
    bg = Color(0xFF0C0F16),
    bgRaised = Color(0xFF11151E),
    surface = Color(0xFF161B26),
    surfaceAlt = Color(0xFF1C2230),
    border = Color(0xFF262E3E),
    borderStrong = Color(0xFF354057),
    text = Color(0xFFE8ECF4),
    textDim = Color(0xFF98A2B8),
    textMuted = Color(0xFF6A7488),
    accent = Color(0xFF7C5CF6),
    accent2 = Color(0xFF22D3EE),
    ok = Color(0xFF34D399),
    warn = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    isDark = true
)

val LightNexusColors = NexusColorTokens(
    bg = Color(0xFFF4F6FA),
    bgRaised = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEEF2F8),
    border = Color(0xFFD8E0EE),
    borderStrong = Color(0xFFB8C4DA),
    text = Color(0xFF0F172A),
    textDim = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    accent = Color(0xFF6D4AFF),
    accent2 = Color(0xFF0891B2),
    ok = Color(0xFF10B981),
    warn = Color(0xFFD97706),
    danger = Color(0xFFEF4444),
    isDark = false
)

val LocalNexusColors = staticCompositionLocalOf { DarkNexusColors }
