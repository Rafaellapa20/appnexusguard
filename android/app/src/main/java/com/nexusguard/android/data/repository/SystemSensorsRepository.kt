package com.nexusguard.android.data.repository

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.domain.HealthScoreCalculator
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.SystemHealthInputs
import com.nexusguard.android.domain.ThermalLevelPt
import com.nexusguard.android.domain.ThermalStatusMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class LiveBatteryMetrics(
    val levelPercent: Int,
    val temperatureCelsius: Float?,
    val healthLabelPt: String,
    val isHealthy: Boolean,
    val voltageMv: Int,
    val isCharging: Boolean,
    val cycleCountText: String,
    val cycleCountReason: String,
    val statusLineText: String,
    val detailLineText: String
)

data class LiveThermalMetrics(
    val level: ThermalLevelPt,
    val headroomValue: Float?,
    val headroomText: String
)

data class LiveStorageMetrics(
    val freeBytes: Long,
    val totalBytes: Long,
    val usedBytes: Long,
    val appsAndSystemBytes: Long,
    val photosBytes: Long,
    val videosBytes: Long,
    val otherMediaBytes: Long
)

data class InstalledAppUsageInfo(
    val packageName: String,
    val appName: String,
    val monogram: String,
    val isGame: Boolean,
    val lastTimeUsedMillis: Long,
    val foregroundTimeMillis: Long,
    val daysSinceUse: Int,
    val totalSizeBytes: Long,
    val isUnused30Days: Boolean
)

data class RealSystemSnapshot(
    val healthScore: Int = 100,
    val healthLabel: String = "A ler sensores...",
    val battery: LiveBatteryMetrics = LiveBatteryMetrics(
        levelPercent = 0,
        temperatureCelsius = null,
        healthLabelPt = "A ler...",
        isHealthy = true,
        voltageMv = 0,
        isCharging = false,
        cycleCountText = "—",
        cycleCountReason = "",
        statusLineText = "A ler estado da bateria...",
        detailLineText = ""
    ),
    val thermal: LiveThermalMetrics = LiveThermalMetrics(
        level = ThermalLevelPt.NORMAL,
        headroomValue = null,
        headroomText = "A ler..."
    ),
    val storage: LiveStorageMetrics = LiveStorageMetrics(0, 0, 0, 0, 0, 0, 0),
    val availableRamBytes: Long = 0L,
    val totalRamBytes: Long = 0L,
    val hasUsageStatsPermission: Boolean = false,
    val installedApps: List<InstalledAppUsageInfo> = emptyList(),
    val unusedAppsCount: Int = 0,
    val highBackgroundAppsCount: Int = 0,
    val tempHistory60s: List<Float> = emptyList(),
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

@Singleton
class SystemSensorsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NexusGuardDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tempWindow = ArrayDeque<Float>(30)
    private var lastSampleSavedMillis = 0L

    val liveSnapshot: StateFlow<RealSystemSnapshot> = flow {
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                readCurrentSnapshot()
            }
            emit(snapshot)
            delay(2_000L) // Atualiza sensores e gráfico de 60s a cada 2 segundos
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RealSystemSnapshot()
    )

    fun observeBatterySamples24h(): Flow<List<BatterySampleEntity>> = dao.observeRecentBatterySamples()

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun readCurrentSnapshot(): RealSystemSnapshot = withContext(Dispatchers.IO) {
        val battery = readBatteryMetrics()
        val thermal = readThermalMetrics()
        val storage = readStorageMetrics()
        val (availRam, totalRam) = readMemoryMetrics()
        val usageGranted = hasUsageStatsPermission()
        val apps = readInstalledAppsUsage(usageGranted)
        val unusedApps = apps.filter { it.isUnused30Days }
        val activeBgApps = apps.filter { it.foregroundTimeMillis > 15 * 60_000L }.take(10)

        // Atualiza janela deslizante de 60 segundos (30 amostras x 2s)
        battery.temperatureCelsius?.let { temp ->
            synchronized(tempWindow) {
                if (tempWindow.size >= 30) {
                    tempWindow.removeFirst()
                }
                tempWindow.addLast(temp)
            }

            val now = System.currentTimeMillis()
            if (now - lastSampleSavedMillis >= 60_000L) {
                lastSampleSavedMillis = now
                scope.launch(Dispatchers.IO) {
                    dao.insertBatterySample(
                        BatterySampleEntity(
                            timestampEpochMillis = now,
                            levelPercent = battery.levelPercent,
                            temperatureCelsius = temp,
                            isCharging = battery.isCharging
                        )
                    )
                    dao.pruneOldBatterySamples(now - 24L * 3600_000L)
                }
            }
        }

        val score = HealthScoreCalculator.calculateScore(
            SystemHealthInputs(
                freeStorageBytes = storage.freeBytes,
                totalStorageBytes = storage.totalBytes,
                thermalLevel = thermal.level,
                batteryTempCelsius = battery.temperatureCelsius,
                isBatteryHealthy = battery.isHealthy,
                unusedAppsOver30Days = unusedApps.size,
                availableMemoryBytes = availRam,
                totalMemoryBytes = totalRam
            )
        )

        val tempList = synchronized(tempWindow) { tempWindow.toList() }

        RealSystemSnapshot(
            healthScore = score,
            healthLabel = HealthScoreCalculator.statusLabelPt(score),
            battery = battery,
            thermal = thermal,
            storage = storage,
            availableRamBytes = availRam,
            totalRamBytes = totalRam,
            hasUsageStatsPermission = usageGranted,
            installedApps = apps,
            unusedAppsCount = unusedApps.size,
            highBackgroundAppsCount = activeBgApps.size,
            tempHistory60s = tempList,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    private fun readBatteryMetrics(): LiveBatteryMetrics {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val levelPercent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val rawTempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempCelsius = if (rawTempTenths > 0) rawTempTenths / 10f else null

        val healthInt = intent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

        val (healthLabel, isHealthy) = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Boa" to true
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobreaquecida" to false
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degradada" to false
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensão" to false
            BatteryManager.BATTERY_HEALTH_COLD -> "Fria" to true
            else -> "Desconhecida" to true
        }

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        // Ciclos de bateria (API 34+ EXTRA_CYCLE_COUNT ou sysfs se acessível; caso contrário explica porquê)
        val rawCycles = if (Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
        } else {
            readSysfsCycleCount()
        }
        val (cycleText, cycleReason) = if (rawCycles > 0) {
            rawCycles.toString() to "Medido pelo Android"
        } else if (Build.VERSION.SDK_INT < 34) {
            "N/D" to "Requer Android 14+ (API 34)"
        } else {
            "N/D" to "Não reportado pelo fabricante"
        }

        // Corrente e estimativa real
        val currentNowUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeCounterUah = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentMa = if (currentNowUa != Int.MIN_VALUE && currentNowUa != 0) {
            val raw = abs(currentNowUa)
            if (raw > 10_000) raw / 1000 else raw
        } else {
            null
        }

        val statusLine: String
        val detailLine: String
        if (isCharging) {
            val chargeTimeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                batteryManager.computeChargeTimeRemaining()
            } else {
                -1L
            }
            statusLine = if (chargeTimeMs > 60_000L) {
                val mins = (chargeTimeMs / 60_000L).toInt()
                val h = mins / 60
                val m = mins % 60
                if (h > 0) "A carregar · ~${h} h ${m} min até 100%" else "A carregar · ~${m} min até 100%"
            } else {
                "Em carregamento"
            }
            detailLine = buildString {
                append("Tensão ${voltageMv} mV")
                if (currentMa != null) append(" · Corrente ${currentMa} mA")
            }
        } else {
            if (currentMa != null && currentMa > 20 && chargeCounterUah > 10_000) {
                val remainingMah = chargeCounterUah / 1000f
                val hoursFloat = remainingMah / currentMa.toFloat()
                if (hoursFloat in 0.1f..72f) {
                    val totalMins = (hoursFloat * 60f).toInt()
                    val h = totalMins / 60
                    val m = totalMins % 60
                    statusLine = "~${h} h ${m} min restantes"
                    detailLine = "medido ao consumo atual (${currentMa} mA · ${voltageMv} mV)"
                } else {
                    statusLine = "Em descarga (${currentMa} mA)"
                    detailLine = "Tensão medida: ${voltageMv} mV · Saúde: $healthLabel"
                }
            } else {
                statusLine = "Em utilização na bateria"
                detailLine = if (voltageMv > 0) {
                    "Tensão medida: ${voltageMv} mV · Saúde: $healthLabel"
                } else {
                    "Saúde reportada: $healthLabel"
                }
            }
        }

        return LiveBatteryMetrics(
            levelPercent = levelPercent,
            temperatureCelsius = tempCelsius,
            healthLabelPt = healthLabel,
            isHealthy = isHealthy,
            voltageMv = voltageMv,
            isCharging = isCharging,
            cycleCountText = cycleText,
            cycleCountReason = cycleReason,
            statusLineText = statusLine,
            detailLineText = detailLine
        )
    }

    private fun readSysfsCycleCount(): Int {
        return try {
            val file = File("/sys/class/power_supply/battery/cycle_count")
            if (file.canRead()) {
                file.readText().trim().toIntOrNull() ?: -1
            } else {
                -1
            }
        } catch (_: Exception) {
            -1
        }
    }

    private fun readThermalMetrics(): LiveThermalMetrics {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThermalStatusMapper.fromPowerManagerStatus(powerManager.currentThermalStatus)
        } else {
            ThermalLevelPt.INDISPONIVEL
        }

        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val raw = powerManager.getThermalHeadroom(10)
                if (!raw.isNaN() && raw > 0f) raw else null
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val headroomText = when {
            headroom != null -> String.format(Locale("pt", "PT"), "margem %.2f", headroom)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "margem N/D (API < 30)"
            else -> "margem N/D (sensor HAL)"
        }

        return LiveThermalMetrics(
            level = level,
            headroomValue = headroom,
            headroomText = headroomText
        )
    }

    private fun readStorageMetrics(): LiveStorageMetrics {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val total = statFs.totalBytes
        val free = statFs.availableBytes
        val used = (total - free).coerceAtLeast(0L)

        val photos = sumMediaStoreColumn(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videos = sumMediaStoreColumn(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val audio = sumMediaStoreColumn(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        val appsAndSystem = (used - photos - videos - audio).coerceAtLeast(0L)

        return LiveStorageMetrics(
            freeBytes = free,
            totalBytes = total,
            usedBytes = used,
            appsAndSystemBytes = appsAndSystem,
            photosBytes = photos,
            videosBytes = videos,
            otherMediaBytes = audio
        )
    }

    private fun sumMediaStoreColumn(uri: android.net.Uri): Long {
        var sum = 0L
        try {
            val projection = arrayOf(MediaStore.MediaColumns.SIZE)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    sum += cursor.getLong(sizeCol).coerceAtLeast(0L)
                }
            }
        } catch (_: Exception) {
            // Sem permissão de media ainda concedida; devolve 0L honestamente
        }
        return sum
    }

    private fun readMemoryMetrics(): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem to info.totalMem
    }

    private fun readInstalledAppsUsage(hasUsagePermission: Boolean): List<InstalledAppUsageInfo> {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30L * 24L * 3600_000L)
        val oneYearAgo = now - (365L * 24L * 3600_000L)

        val usageMap = if (hasUsagePermission) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            usm.queryAndAggregateUsageStats(oneYearAgo, now)
        } else {
            emptyMap()
        }

        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val userHandle = Process.myUserHandle()

        val knownGamePackages = setOf(
            "com.miHoYo.GenshinImpact",
            "com.tencent.ig",
            "com.supercell.clashroyale",
            "com.supercell.brawlstars",
            "com.dts.freefireth",
            "com.ea.gp.fifamobile",
            "com.roblox.client",
            "com.mojang.minecraftpe",
            "com.activision.callofduty.shooter",
            "com.king.candycrushsaga"
        )

        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }

        return apps.mapNotNull { appInfo ->
            val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
            if (!isUserApp && launchIntent == null) return@mapNotNull null
            if (appInfo.packageName == context.packageName) return@mapNotNull null

            val label = try {
                pm.getApplicationLabel(appInfo).toString().ifBlank { appInfo.packageName }
            } catch (_: Exception) {
                appInfo.packageName
            }

            val words = label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val monogram = when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
                label.length >= 2 -> label.take(2).uppercase()
                else -> label.uppercase()
            }

            val isGame = (appInfo.category == ApplicationInfo.CATEGORY_GAME) ||
                ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) ||
                (appInfo.packageName in knownGamePackages)

            val stats = usageMap[appInfo.packageName]
            val lastUsed = stats?.lastTimeUsed ?: 0L
            val fgTime = stats?.totalTimeInForeground ?: 0L

            val installTime = try {
                pm.getPackageInfo(appInfo.packageName, 0).firstInstallTime
            } catch (_: Exception) {
                now
            }

            val referenceTime = if (lastUsed > 0L) lastUsed else installTime
            val daysSinceUse = ((now - referenceTime).coerceAtLeast(0L) / (24L * 3600_000L)).toInt()
            val isUnused30Days = hasUsagePermission && referenceTime < thirtyDaysAgo

            val sizeBytes = if (hasUsagePermission && storageStatsManager != null) {
                try {
                    val st = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        appInfo.packageName,
                        userHandle
                    )
                    st.appBytes + st.dataBytes + st.cacheBytes
                } catch (_: Exception) {
                    appInfo.sourceDir?.let { File(it).length() } ?: 0L
                }
            } else {
                appInfo.sourceDir?.let { File(it).length() } ?: 0L
            }

            InstalledAppUsageInfo(
                packageName = appInfo.packageName,
                appName = label,
                monogram = monogram,
                isGame = isGame,
                lastTimeUsedMillis = lastUsed,
                foregroundTimeMillis = fgTime,
                daysSinceUse = daysSinceUse,
                totalSizeBytes = sizeBytes,
                isUnused30Days = isUnused30Days
            )
        }.sortedWith(
            compareByDescending<InstalledAppUsageInfo> { it.totalSizeBytes }
                .thenBy { it.appName }
        )
    }

    /**
     * Para processos em fundo permitidos pelo Android (apenas no flavor 'full' com KILL_BACKGROUND_PROCESSES)
     * e grava a ação real no Histórico Room.
     */
    suspend fun stopPermittedBackgroundApps(recordInHistory: Boolean = true): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.IS_FULL_FLAVOR) return@withContext 0
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val snapshot = readCurrentSnapshot()
        val candidates = snapshot.installedApps
            .filter { it.packageName != context.packageName }
            .take(12)

        var stoppedCount = 0
        for (app in candidates) {
            try {
                am.killBackgroundProcesses(app.packageName)
                stoppedCount++
            } catch (_: Exception) {
                // Ignora se bloqueado pelo sistema
            }
        }

        if (recordInHistory && stoppedCount > 0) {
            dao.insertHistory(
                HistoryEntity(
                    actionType = "BACKGROUND_STOP",
                    title = "Apps em fundo paradas ($stoppedCount apps)",
                    details = "killBackgroundProcesses executado nas apps em segundo plano permitidas pelo Android.",
                    timestampEpochMillis = System.currentTimeMillis(),
                    bytesFreed = 0L,
                    canRestore = false
                )
            )
        }
        stoppedCount
    }
}
