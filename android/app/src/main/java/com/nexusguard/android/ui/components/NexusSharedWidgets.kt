package com.nexusguard.android.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.LruCache
import android.util.Size as AndroidSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.ui.theme.JetBrainsMonoFamily
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache LRU partilhada de ~4 MB para ícones de apps convertidos em IO (fora da composição).
 */
private object AppIconBitmapCache {
    private const val MAX_BYTES = 4 * 1024 * 1024 // 4 MB
    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return (value.width * value.height * 4).coerceAtLeast(1024)
        }
    }

    fun get(packageName: String): ImageBitmap? = synchronized(cache) { cache.get(packageName) }
    fun put(packageName: String, bitmap: ImageBitmap) = synchronized(cache) { cache.put(packageName, bitmap) }
}

/**
 * Cache LRU partilhada para miniaturas de ficheiros (96x96) carregadas em IO.
 */
private object ThumbnailBitmapCache {
    private const val MAX_ITEMS = 120
    private val cache = LruCache<String, ImageBitmap>(MAX_ITEMS)

    fun get(uri: String): ImageBitmap? = synchronized(cache) { cache.get(uri) }
    fun put(uri: String, bitmap: ImageBitmap) = synchronized(cache) { cache.put(uri, bitmap) }
}

@Composable
fun AppIconOrMonogram(
    packageName: String,
    monogram: String,
    iconSizeDp: Int = 36
) {
    val context = LocalContext.current
    val colors = NexusTheme.colors

    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = AppIconBitmapCache.get(packageName),
        key1 = packageName
    ) {
        if (value == null && packageName.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                AppIconBitmapCache.get(packageName) ?: try {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    val bmp = drawableToBitmapInIo(drawable, 96)?.asImageBitmap()
                    if (bmp != null) {
                        AppIconBitmapCache.put(packageName, bmp)
                    }
                    bmp
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(iconSizeDp.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(iconSizeDp.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceAlt)
                .border(1.dp, colors.borderStrong, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram.take(2),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.accent2
            )
        }
    }
}

private fun drawableToBitmapInIo(drawable: Drawable, targetPx: Int): Bitmap? {
    return try {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.coerceIn(1, targetPx)
        val h = drawable.intrinsicHeight.coerceIn(1, targetPx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }
}

/**
 * Fase 4: Miniaturas na pré-visualização com contentResolver.loadThumbnail(uri, Size(96,96), null) (API 29+)
 * em produceState com Dispatchers.IO e LruCache partilhada. Abaixo da API 29 ou não-imagem, mostra o ícone do tipo.
 */
@Composable
fun FileThumbnailOrTypeBadge(
    contentUri: String,
    mimeType: String,
    displayName: String
) {
    val context = LocalContext.current
    val colors = NexusTheme.colors
    val isPreviewableMedia = mimeType.startsWith("image/") || mimeType.startsWith("video/")

    val thumbnail by produceState<ImageBitmap?>(
        initialValue = ThumbnailBitmapCache.get(contentUri),
        key1 = contentUri
    ) {
        if (value == null && isPreviewableMedia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            value = withContext(Dispatchers.IO) {
                ThumbnailBitmapCache.get(contentUri) ?: try {
                    val uri = Uri.parse(contentUri)
                    val bmp = context.contentResolver
                        .loadThumbnail(uri, AndroidSize(96, 96), null)
                        .asImageBitmap()
                    ThumbnailBitmapCache.put(contentUri, bmp)
                    bmp
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
        )
    } else {
        val badgeText = when {
            displayName.endsWith(".apk", ignoreCase = true) -> "APK"
            mimeType.startsWith("video/") -> "VID"
            mimeType.startsWith("image/") -> "IMG"
            mimeType.startsWith("audio/") -> "AUD"
            else -> "DOC"
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.bgRaised)
                .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                style = NexusMonoTextStyles.badge,
                color = colors.accent2
            )
        }
    }
}

/**
 * Fase 4: Gráfico térmico de 60 s num único Canvas:
 * - Linha com área suave
 * - Faixas de fundo Ok (<36 °C), Warn (36–42 °C) e Danger (>42 °C)
 * - Eixo mínimo em °C
 * - Se faltarem amostras, desenha só as que existem e escreve «a recolher amostras (n/30)».
 */
@Composable
fun Thermal60sCanvasChart(
    tempHistory: ImmutableList<Float>,
    modifier: Modifier = Modifier
) {
    val colors = NexusTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontSize = 12.sp,
        color = colors.textMuted
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
        ) {
            val minTemp = 20f
            val maxTemp = 50f
            val range = maxTemp - minTemp
            val leftAxisWidth = 34.dp.toPx()
            val chartWidth = (size.width - leftAxisWidth).coerceAtLeast(1f)
            val chartHeight = size.height

            fun tempToY(tempC: Float): Float {
                val clamped = tempC.coerceIn(minTemp, maxTemp)
                val normalized = (clamped - minTemp) / range
                return chartHeight * (1f - normalized)
            }

            val y42 = tempToY(42f)
            val y36 = tempToY(36f)

            // Faixa Danger (> 42 °C)
            drawRect(
                color = colors.danger.copy(alpha = 0.08f),
                topLeft = Offset(leftAxisWidth, 0f),
                size = Size(chartWidth, y42)
            )
            // Faixa Warn (36–42 °C)
            drawRect(
                color = colors.warn.copy(alpha = 0.08f),
                topLeft = Offset(leftAxisWidth, y42),
                size = Size(chartWidth, (y36 - y42).coerceAtLeast(0f))
            )
            // Faixa Ok (< 36 °C)
            drawRect(
                color = colors.ok.copy(alpha = 0.06f),
                topLeft = Offset(leftAxisWidth, y36),
                size = Size(chartWidth, (chartHeight - y36).coerceAtLeast(0f))
            )

            // Linhas guia tracejadas / subtis em 36 °C e 42 °C
            drawLine(
                color = colors.warn.copy(alpha = 0.30f),
                start = Offset(leftAxisWidth, y36),
                end = Offset(size.width, y36),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = colors.danger.copy(alpha = 0.30f),
                start = Offset(leftAxisWidth, y42),
                end = Offset(size.width, y42),
                strokeWidth = 1.dp.toPx()
            )

            // Eixo mínimo em °C
            drawText(
                textMeasurer = textMeasurer,
                text = "42°",
                style = axisStyle,
                topLeft = Offset(0f, (y42 - 6.dp.toPx()).coerceAtLeast(0f))
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "36°",
                style = axisStyle,
                topLeft = Offset(0f, (y36 - 6.dp.toPx()).coerceAtLeast(0f))
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "25°",
                style = axisStyle,
                topLeft = Offset(0f, (tempToY(25f) - 6.dp.toPx()).coerceAtMost(chartHeight - 12.dp.toPx()))
            )

            val samples = tempHistory.takeLast(30)
            if (samples.isNotEmpty()) {
                val maxSlots = 29f
                val linePath = Path()
                val areaPath = Path()

                samples.forEachIndexed { idx, tempC ->
                    val x = if (samples.size == 1) {
                        leftAxisWidth + chartWidth / 2f
                    } else {
                        leftAxisWidth + (idx.toFloat() / maxSlots) * chartWidth
                    }
                    val y = tempToY(tempC)
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        areaPath.moveTo(x, chartHeight)
                        areaPath.lineTo(x, y)
                    } else {
                        val prevX = leftAxisWidth + ((idx - 1).toFloat() / maxSlots) * chartWidth
                        val prevY = tempToY(samples[idx - 1])
                        val ctrlX = (prevX + x) / 2f
                        linePath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
                        areaPath.cubicTo(ctrlX, prevY, ctrlX, y, x, y)
                    }
                    if (idx == samples.lastIndex) {
                        areaPath.lineTo(x, chartHeight)
                        areaPath.close()
                    }
                }

                val lastTemp = samples.last()
                val strokeColor = when {
                    lastTemp >= 42f -> colors.danger
                    lastTemp >= 36f -> colors.warn
                    else -> colors.ok
                }

                if (samples.size >= 2) {
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                strokeColor.copy(alpha = 0.32f),
                                strokeColor.copy(alpha = 0.02f)
                            )
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = strokeColor,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    val singleX = leftAxisWidth + 6.dp.toPx()
                    val singleY = tempToY(samples.first())
                    drawCircle(
                        color = strokeColor,
                        radius = 4.dp.toPx(),
                        center = Offset(singleX, singleY)
                    )
                }
            }
        }

        if (tempHistory.size < 30) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "a recolher amostras (${tempHistory.size}/30)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
    }
}

/**
 * Fase 4: Gráfico de bateria 24 h desenhado num único Canvas (sem criar dezenas de Box).
 */
@Composable
fun Battery24hCanvasChart(
    samples: ImmutableList<BatterySampleEntity>,
    modifier: Modifier = Modifier
) {
    val colors = NexusTheme.colors
    val displaySamples = samples.takeLast(24)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            if (displaySamples.isEmpty()) return@Canvas
            val maxSlots = 24
            val spacingPx = 4.dp.toPx()
            val totalSpacing = spacingPx * (maxSlots - 1)
            val barWidth = ((size.width - totalSpacing) / maxSlots).coerceAtLeast(4f)

            displaySamples.forEachIndexed { index, item ->
                val fraction = (item.levelPercent / 100f).coerceIn(0.08f, 1f)
                val barHeight = size.height * fraction
                val x = index * (barWidth + spacingPx)
                val y = size.height - barHeight
                if (item.isCharging) {
                    // Zona de carga colorida ao fundo
                    drawRoundRect(
                        color = colors.ok.copy(alpha = 0.16f),
                        topLeft = Offset((x - spacingPx / 2f).coerceAtLeast(0f), 0f),
                        size = Size(barWidth + spacingPx, size.height),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                val barColor = when {
                    item.isCharging -> colors.ok
                    item.levelPercent < 20 -> colors.danger
                    else -> colors.accent
                }
                drawRoundRect(
                    color = barColor.copy(alpha = 0.88f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }

        if (displaySamples.size < 24) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "a recolher amostras (${displaySamples.size}/24)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
    }
}

@Composable
fun NexusToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = colors.text
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.text,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textDim,
                uncheckedTrackColor = colors.surfaceAlt,
                uncheckedBorderColor = colors.borderStrong
            )
        )
    }
}

object NexusSystemIntents {
    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
            }
        }
    }

    fun openDndPolicySettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
        }
    }

    fun openManageAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                }
            }
        }
    }

    fun openSystemClearCacheAction(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.startActivity(Intent(StorageManager.ACTION_CLEAR_APP_CACHE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
            }
        }
    }

    fun openAppDetailsSettings(context: Context, packageName: String) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }

    fun requestUninstallPackage(context: Context, packageName: String) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }

    fun openWriteSettingsPermission(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }

    fun openBatterySaverSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            openOemBatteryProtectionSettings(context)
        }
    }

    fun openDisplaySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }

    fun openOemBatteryProtectionSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }
}
