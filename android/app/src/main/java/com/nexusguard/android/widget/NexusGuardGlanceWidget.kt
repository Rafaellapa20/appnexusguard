package com.nexusguard.android.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nexusguard.android.MainActivity
import com.nexusguard.android.domain.AllUnlockedEntitlements
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.PremiumFeature
import kotlin.math.abs

class NexusGuardWidgetReceiver2x2 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NexusGuardGlanceWidget()
}

class NexusGuardWidgetReceiver4x2 : GlanceAppWidgetReceiver() {
    private val entitlements = AllUnlockedEntitlements()
    val isWidget4x2Enabled: Boolean
        get() = entitlements.isPremiumFeatureEnabled(PremiumFeature.WIDGET_4X2)
    override val glanceAppWidget: GlanceAppWidget = NexusGuardGlanceWidget()
}

class NexusGuardGlanceWidget : GlanceAppWidget() {

    companion object {
        val SMALL_2X2 = DpSize(110.dp, 110.dp)
        val WIDE_4X2 = DpSize(250.dp, 110.dp)
        val KEY_START_DEEP_CLEAN = ActionParameters.Key<Boolean>(MainActivity.EXTRA_START_DEEP_CLEAN)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_2X2, WIDE_4X2)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val freeBytes = statFs.availableBytes
        val totalBytes = statFs.totalBytes
        val freeText = NexusFormatters.formatBytes(freeBytes)
        val totalText = NexusFormatters.formatBytes(totalBytes)

        val battIntent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (battIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val pct = if (level >= 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }
        val status = battIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val remainingSubtitle = if (isCharging) {
            val chargeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) bm.computeChargeTimeRemaining() else -1L
            if (chargeMs > 60_000L) {
                val mins = (chargeMs / 60_000L).toInt()
                "A carregar (~${mins / 60}h ${mins % 60}m)"
            } else {
                "A carregar"
            }
        } else {
            val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val counterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val currentMa = if (currentUa != Int.MIN_VALUE && currentUa != 0) {
                val r = abs(currentUa)
                if (r > 10_000) r / 1000 else r
            } else 0
            if (currentMa > 20 && counterUah > 10_000) {
                val hrs = (counterUah / 1000f) / currentMa.toFloat()
                if (hrs in 0.1f..72f) {
                    val m = (hrs * 60f).toInt()
                    "~${m / 60}h ${m % 60}m restantes"
                } else {
                    "Em bateria"
                }
            } else {
                "Em bateria"
            }
        }

        provideContent {
            val size = LocalSize.current
            val isWide = size.width >= 200.dp
            NexusWidgetContent(
                isWide = isWide,
                freeText = freeText,
                totalText = totalText,
                batteryPercent = pct,
                batterySubtitle = remainingSubtitle
            )
        }
    }

    @Composable
    private fun NexusWidgetContent(
        isWide: Boolean,
        freeText: String,
        totalText: String,
        batteryPercent: Int,
        batterySubtitle: String
    ) {
        val bgProvider = ColorProvider(Color(0xFF0C0F16))
        val surfaceProvider = ColorProvider(Color(0xFF161B26))
        val textProvider = ColorProvider(Color(0xFFE8ECF4))
        val dimProvider = ColorProvider(Color(0xFF98A2B8))
        val accentProvider = ColorProvider(Color(0xFF7C5CF6))
        val accent2Provider = ColorProvider(Color(0xFF22D3EE))

        val launchDeepCleanAction = actionStartActivity<MainActivity>(
            parameters = actionParametersOf(KEY_START_DEEP_CLEAN to true)
        )

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgProvider)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(launchDeepCleanAction)
        ) {
            if (isWide) {
                // Layout 4×2
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nexus ",
                            style = TextStyle(color = textProvider, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Guard",
                            style = TextStyle(color = accent2Provider, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .background(surfaceProvider)
                                .cornerRadius(10.dp)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Armazenamento livre",
                                style = TextStyle(color = dimProvider, fontSize = 11.sp)
                            )
                            Text(
                                text = freeText,
                                style = TextStyle(color = textProvider, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "de $totalText",
                                style = TextStyle(color = dimProvider, fontSize = 10.sp)
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .background(surfaceProvider)
                                .cornerRadius(10.dp)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Bateria",
                                style = TextStyle(color = dimProvider, fontSize = 11.sp)
                            )
                            Text(
                                text = "$batteryPercent%",
                                style = TextStyle(color = textProvider, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = batterySubtitle,
                                style = TextStyle(color = dimProvider, fontSize = 10.sp)
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Box(
                            modifier = GlanceModifier
                                .background(accentProvider)
                                .cornerRadius(10.dp)
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                                .clickable(launchDeepCleanAction),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Analisar",
                                style = TextStyle(color = textProvider, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            } else {
                // Layout 2×2
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = "Livre: $freeText",
                        style = TextStyle(color = textProvider, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "Bateria $batteryPercent% · $batterySubtitle",
                        style = TextStyle(color = dimProvider, fontSize = 11.sp)
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(accentProvider)
                            .cornerRadius(10.dp)
                            .padding(vertical = 8.dp)
                            .clickable(launchDeepCleanAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Analisar",
                            style = TextStyle(color = textProvider, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

object NexusWidgetUpdater {
    suspend fun updateAllWidgets(context: Context) {
        try {
            NexusGuardGlanceWidget().updateAll(context)
        } catch (_: Exception) {
        }
    }
}
