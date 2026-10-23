package com.nexusguard.android.ui.components

import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.R
import com.nexusguard.android.ui.theme.NexusBadgeShape
import com.nexusguard.android.ui.theme.NexusButtonShape
import com.nexusguard.android.ui.theme.NexusCardShape
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme

/**
 * Fase 6: Respeita Settings.Global.ANIMATOR_DURATION_SCALE em todas as animações (0 quando desligadas).
 */
@Composable
fun rememberScaledAnimationDuration(baseDurationMs: Int): Int {
    val context = LocalContext.current
    return remember(context, baseDurationMs) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        if (scale <= 0f) 0 else (baseDurationMs * scale).toInt().coerceAtLeast(1)
    }
}

@Composable
fun GaugeShieldLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    withContainer: Boolean = true
) {
    val colors = NexusTheme.colors
    if (withContainer) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_gauge_shield),
                contentDescription = "Logótipo Nexus Guard",
                modifier = Modifier.size(size * 0.68f)
            )
        }
    } else {
        Image(
            painter = painterResource(id = R.drawable.ic_gauge_shield),
            contentDescription = "Logótipo Nexus Guard",
            modifier = modifier.size(size)
        )
    }
}

@Composable
fun NexusBrandWordmark(
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 22
) {
    val colors = NexusTheme.colors
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSizeSp.sp
                )
            ) {
                append("Nexus ")
            }
            withStyle(
                SpanStyle(
                    color = colors.accent2,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSizeSp.sp
                )
            ) {
                append("Guard")
            }
        },
        modifier = modifier
    )
}

@Composable
fun NexusTopBar(
    title: String?,
    showBrandHeader: Boolean = false,
    showHistoryAction: Boolean = false,
    showSettingsButton: Boolean = false,
    onHistoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val colors = NexusTheme.colors
    Surface(
        color = colors.bg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showBrandHeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GaugeShieldLogo(size = 34.dp, withContainer = true)
                    Spacer(modifier = Modifier.width(10.dp))
                    NexusBrandWordmark(fontSizeSp = 22)
                }
            } else {
                Text(
                    text = title ?: "",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.text
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showHistoryAction) {
                    NexusTextButton(
                        text = "Histórico",
                        color = colors.textDim,
                        onClick = onHistoryClick
                    )
                }
                if (showSettingsButton) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = NexusIcons.Settings,
                            contentDescription = "Abrir Definições",
                            tint = colors.textDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NexusFlavorPill(modifier: Modifier = Modifier) {
    val colors = NexusTheme.colors
    val label = if (BuildConfig.IS_FULL_FLAVOR) "FULL" else "PLAY"
    Box(
        modifier = modifier
            .clip(NexusBadgeShape)
            .background(colors.surfaceAlt)
            .border(1.dp, colors.borderStrong, NexusBadgeShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = NexusMonoTextStyles.badge,
            color = colors.accent2
        )
    }
}

@Composable
fun NexusPrimaryGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    contentSlot: (@Composable () -> Unit)? = null
) {
    val colors = NexusTheme.colors
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .clip(NexusButtonShape)
            .background(
                brush = colors.primaryGradient,
                alpha = if (enabled) 1f else 0.5f
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        if (contentSlot != null) {
            contentSlot()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onAccent
                )
            }
        }
    }
}

/**
 * Fase 6: Botão de texto acessível com alvo de toque mínimo de 48 dp, Role.Button e indicação visual.
 */
@Composable
fun NexusTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NexusTheme.colors.accent2
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(NexusBadgeShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

/**
 * Fase 6: Chip clicável com alvo de toque mínimo de 48 dp, Role.Button e indicação visual.
 */
@Composable
fun NexusChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NexusTheme.colors.accent2
) {
    val colors = NexusTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(NexusButtonShape)
            .background(colors.surfaceAlt)
            .border(1.dp, color.copy(alpha = 0.45f), NexusButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

/**
 * Fase 6: Checkbox da limpeza com área de toque de 48 dp e cor onAccent do NexusTheme.
 */
@Composable
fun NexusCheckbox48dp(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NexusTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Checkbox,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) colors.accent2 else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (checked) colors.accent2 else colors.borderStrong,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = NexusIcons.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun NexusCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = NexusTheme.colors.surface,
    borderColor: Color = NexusTheme.colors.border,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(NexusCardShape)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = cardModifier,
        shape = NexusCardShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
