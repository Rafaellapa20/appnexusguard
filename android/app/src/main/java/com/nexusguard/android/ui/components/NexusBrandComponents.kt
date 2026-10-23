package com.nexusguard.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme

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
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(NexusBadgeShape)
                            .clickable(role = Role.Button, onClick = onHistoryClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Histórico",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textDim
                        )
                    }
                }
                if (showSettingsButton) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
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
    enabled: Boolean = true
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color(0xFF0C0F16),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF0C0F16)
            )
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
