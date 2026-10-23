package com.nexusguard.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun NexusTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Cores dinâmicas sempre desligadas ("a marca manda")
    val tokens = if (darkTheme) DarkNexusColors else LightNexusColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.text,
            secondary = tokens.accent2,
            onSecondary = tokens.bg,
            tertiary = tokens.ok,
            background = tokens.bg,
            onBackground = tokens.text,
            surface = tokens.surface,
            onSurface = tokens.text,
            surfaceVariant = tokens.surfaceAlt,
            onSurfaceVariant = tokens.textDim,
            outline = tokens.border,
            outlineVariant = tokens.borderStrong,
            error = tokens.danger
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.bgRaised,
            secondary = tokens.accent2,
            onSecondary = tokens.bgRaised,
            tertiary = tokens.ok,
            background = tokens.bg,
            onBackground = tokens.text,
            surface = tokens.surface,
            onSurface = tokens.text,
            surfaceVariant = tokens.surfaceAlt,
            onSurfaceVariant = tokens.textDim,
            outline = tokens.border,
            outlineVariant = tokens.borderStrong,
            error = tokens.danger
        )
    }

    CompositionLocalProvider(LocalNexusColors provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexusTypography,
            shapes = NexusShapes,
            content = content
        )
    }
}

object NexusTheme {
    val colors: NexusColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalNexusColors.current
}
