package com.nexusguard.android.data.repository

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
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
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.domain.HealthScoreCalculator
import com.nexusguard.android.domain.SystemHealthInputs
import com.nexusguard.android.domain.ThermalLevelPt
import com.nexusguard.android.domain.ThermalStatusMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Immutable
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

@Immutable
data class LiveThermalMetrics(
    val level: ThermalLevelPt,
    val headroomValue: Float?,
    val headroomText: String
)

@Immutable
data class LiveStorageMetrics(
    val freeBytes: Long,
    val totalBytes: Long,
    val usedBytes: Long,
    val appsAndSystemBytes: Long,
    val photosBytes: Long,
    val videosBytes: Long,
    val otherMediaBytes: Long,
    val externalStatsAvailable: Boolean = false
)

@Immutable
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

@Immutable
data class RealSystemSnapshot(
    val isLoading: Boolean = false,
    val healthScore: Int = 0,
    val healthLabel: String = "A ler sensores...",
    val battery: LiveBatteryMetrics = LiveBatteryMetrics(
        levelPercent = 0,
        temperatureCelsius = null,
        healthLabelPt = "A ler...",
        isHealthy = true,
        voltageMv = 0,
        isCharging = false,
        cycleCountText = "N/D",
        cycleCountReason = "A ler...",
        statusLineText = "A ler estado da bateria...",
        detailLineText = ""
    ),
    val thermal: LiveThermalMetrics = LiveThermalMetrics(
        level = ThermalLevelPt.NORMAL,
        headroomValue = null,
        headroomText = "A ler..."
    ),
    val storage: LiveStorageMetrics = LiveStorageMetrics(0, 0, 0, 0, 0, 0, 0, false),
    val availableRamBytes: Long = 0L,
    val totalRamBytes: Long = 0L,
    val hasUsageStatsPermission: Boolean = false,
    val installedApps: ImmutableList<InstalledAppUsageInfo> = persistentListOf(),
    val unusedAppsCount: Int = 0,
    val highBackgroundAppsCount: Int = 0,
    val tempHistory60s: ImmutableList<Float> = persistentListOf(),
    val lastUpdatedMillis: Long = 0L
)

private data class SlowAppsState(
    val hasUsagePermission: Boolean = false,
    val apps: ImmutableList<InstalledAppUsageInfo> = persistentListOf()
)

@Singleton
class SystemSensorsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: NexusGuardDao
) {
    companion object {
        private const val SLOW_REFRESH_COOLDOWN_MS = 5 * 60_000L // 5 minutos
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tempWindow = ArrayDeque<Float>(30)
    private var lastSampleSavedMillis = 0L
    private var lastSlowRefreshMillis = 0L

    // Cache em memória dos tamanhos das apps com chave "packageName:lastUpdateTime"
    private val appSizeCache = ConcurrentHashMap<String, Long>()

    private val _storageState = MutableStateFlow(LiveStorageMetrics(0, 0, 0, 0, 0, 0, 0, false))
    val storageFlow: StateFlow<LiveStorageMetrics> = _storageState

    private val _appsState = MutableStateFlow(SlowAppsState())
    private val appsFlow: StateFlow<SlowAppsState> = _appsState

    private val _tempHistoryState = MutableStateFlow<ImmutableList<Float>>(persistentListOf())

    /**
     * Fluxo rápido 1: Bateria via BroadcastReceiver(ACTION_BATTERY_CHANGED), emite só quando muda.
     */
    val batteryFlow: Flow<LiveBatteryMetrics> = callbackFlow {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = ContextCompat.registerReceiver(
            context,
            null,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        trySend(parseBatteryIntent(initialIntent))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val metrics = parseBatteryIntent(intent)
                trySend(metrics)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /**
     * Fluxo rápido 2: Estado térmico via PowerManager.addThermalStatusListener (API 29+)
     * e getThermalHeadroom a cada 2 s apenas enquanto houver subscritores.
     * Também regista a amostra real de temperatura de 2 em 2 s na janela de 60 s (máx. 30 amostras).
     */
    val thermalFlow: Flow<LiveThermalMetrics> = callbackFlow {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        var currentLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThermalStatusMapper.fromPowerManagerStatus(powerManager.currentThermalStatus)
        } else {
            ThermalLevelPt.INDISPONIVEL
        }

        val listener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { status ->
                currentLevel = ThermalStatusMapper.fromPowerManagerStatus(status)
                trySend(buildThermalMetrics(powerManager, currentLevel))
            }
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && listener != null) {
            try {
                powerManager.addThermalStatusListener(Executor { it.run() }, listener)
            } catch (_: Exception) {
            }
        }

        val headroomJob = launch(Dispatchers.IO) {
            while (true) {
                trySend(buildThermalMetrics(powerManager, currentLevel))
                recordCurrentBatteryTempSample()
                delay(2_000L)
            }
        }

        awaitClose {
            headroomJob.cancel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && listener != null) {
                try {
                    powerManager.removeThermalStatusListener(listener)
                } catch (_: Exception) {
                }
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /**
     * Fluxo rápido 3: Memória RAM (ActivityManager.MemoryInfo) a cada 2 s apenas enquanto houver subscritores.
     */
    val memoryFlow: Flow<Pair<Long, Long>> = flow {
        while (true) {
            emit(readMemoryMetrics())
            delay(2_000L)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    init {
        scope.launch(Dispatchers.IO) {
            refreshSlowMetrics(force = true)
            while (true) {
                delay(SLOW_REFRESH_COOLDOWN_MS)
                refreshSlowMetrics(force = true)
            }
        }
    }

    val liveSnapshot: StateFlow<RealSystemSnapshot> = combine(
        batteryFlow,
        thermalFlow,
        memoryFlow,
        storageFlow,
        appsFlow
    ) { battery, thermal, memory, storage, appsState ->
        val (availRam, totalRam) = memory
        val unusedAppsCount = appsState.apps.count { it.isUnused30Days }
        val activeBgCount = appsState.apps.count { it.foregroundTimeMillis > 15 * 60_000L }

        val score = HealthScoreCalculator.calculateScore(
            SystemHealthInputs(
                freeStorageBytes = storage.freeBytes,
                totalStorageBytes = storage.totalBytes,
                thermalLevel = thermal.level,
                batteryTempCelsius = battery.temperatureCelsius,
                isBatteryHealthy = battery.isHealthy,
                unusedAppsOver30Days = unusedAppsCount,
                availableMemoryBytes = availRam,
                totalMemoryBytes = totalRam
            )
        )

        val isStillLoading = storage.totalBytes == 0L && appsState.apps.isEmpty()

        RealSystemSnapshot(
            isLoading = isStillLoading,
            healthScore = score,
            healthLabel = HealthScoreCalculator.statusLabelPt(score),
            battery = battery,
            thermal = thermal,
            storage = storage,
            availableRamBytes = availRam,
            totalRamBytes = totalRam,
            hasUsageStatsPermission = appsState.hasUsagePermission,
            installedApps = appsState.apps,
            unusedAppsCount = unusedAppsCount,
            highBackgroundAppsCount = activeBgCount,
            tempHistory60s = _tempHistoryState.value,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RealSystemSnapshot(isLoading = true)
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

    /**
     * Atualiza armazenamento e lista de apps instaladas apenas no arranque, no ON_RESUME (com cooldown de 5 min)
     * ou após uma limpeza (force = true).
     */
    suspend fun refreshSlowMetrics(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val usageGranted = hasUsageStatsPermission()
        val permissionChanged = usageGranted != _appsState.value.hasUsagePermission

        if (!force && !permissionChanged && (now - lastSlowRefreshMillis) < SLOW_REFRESH_COOLDOWN_MS) {
            return@withContext
        }
        lastSlowRefreshMillis = now

        val storage = readStorageMetrics(usageGranted)
        _storageState.value = storage

        val apps = readInstalledAppsUsage(usageGranted)
        _appsState.value = SlowAppsState(
            hasUsagePermission = usageGranted,
            apps = apps
        )
    }

    /**
     * Mantido para compatibilidade com chamadas explícitas (ex.: Worker / Análise com 1 clique).
     */
    suspend fun readCurrentSnapshot(): RealSystemSnapshot = withContext(Dispatchers.IO) {
        refreshSlowMetrics(force = true)
        val battery = readBatteryMetricsOnce()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ThermalStatusMapper.fromPowerManagerStatus(powerManager.currentThermalStatus)
        } else {
            ThermalLevelPt.INDISPONIVEL
        }
        val thermal = buildThermalMetrics(powerManager, level)
        val (availRam, totalRam) = readMemoryMetrics()
        val storage = _storageState.value
        val appsState = _appsState.value
        val unusedAppsCount = appsState.apps.count { it.isUnused30Days }
        val activeBgCount = appsState.apps.count { it.foregroundTimeMillis > 15 * 60_000L }

        val score = HealthScoreCalculator.calculateScore(
            SystemHealthInputs(
                freeStorageBytes = storage.freeBytes,
                totalStorageBytes = storage.totalBytes,
                thermalLevel = thermal.level,
                batteryTempCelsius = battery.temperatureCelsius,
                isBatteryHealthy = battery.isHealthy,
                unusedAppsOver30Days = unusedAppsCount,
                availableMemoryBytes = availRam,
                totalMemoryBytes = totalRam
            )
        )

        RealSystemSnapshot(
            isLoading = false,
            healthScore = score,
            healthLabel = HealthScoreCalculator.statusLabelPt(score),
            battery = battery,
            thermal = thermal,
            storage = storage,
            availableRamBytes = availRam,
            totalRamBytes = totalRam,
            hasUsageStatsPermission = appsState.hasUsagePermission,
            installedApps = appsState.apps,
            unusedAppsCount = unusedAppsCount,
            highBackgroundAppsCount = activeBgCount,
            tempHistory60s = _tempHistoryState.value,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    private fun recordCurrentBatteryTempSample() {
        val intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val rawTempDeci = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (rawTempDeci <= 0) return
        val temp = rawTempDeci / 10f

        synchronized(tempWindow) {
            if (tempWindow.size >= 30) {
                tempWindow.removeFirst()
            }
            tempWindow.addLast(temp)
            _tempHistoryState.value = tempWindow.toList().toImmutableList()
        }

        val now = System.currentTimeMillis()
        if (now - lastSampleSavedMillis >= 60_000L) {
            lastSampleSavedMillis = now
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
            val pct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            val status = intent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            scope.launch(Dispatchers.IO) {
                dao.insertBatterySample(
                    BatterySampleEntity(
                        timestampEpochMillis = now,
                        levelPercent = pct,
                        temperatureCelsius = temp,
                        isCharging = isCharging
                    )
                )
                dao.pruneOldBatterySamples(now - 24L * 3600_000L)
            }
        }
    }

    private fun readBatteryMetricsOnce(): LiveBatteryMetrics {
        val intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return parseBatteryIntent(intent)
    }

    private fun parseBatteryIntent(intent: Intent?): LiveBatteryMetrics {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val levelPercent = if (level >= 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val rawTempDeci = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempCelsius = if (rawTempDeci > 0) rawTempDeci / 10f else null

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

    private fun buildThermalMetrics(
        powerManager: PowerManager,
        level: ThermalLevelPt
    ): LiveThermalMetrics {
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

    /**
     * Usa StorageStatsManager.queryExternalStatsForUser(UUID_DEFAULT, myUserHandle()) numa única chamada
     * em vez de percorrer o MediaStore 3 vezes.
     */
    private fun readStorageMetrics(hasUsagePermission: Boolean): LiveStorageMetrics {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val total = statFs.totalBytes
        val free = statFs.availableBytes
        val used = (total - free).coerceAtLeast(0L)

        var photos = 0L
        var videos = 0L
        var audio = 0L
        var externalAvailable = false

        if (hasUsagePermission) {
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            if (storageStatsManager != null) {
                try {
                    val extStats = storageStatsManager.queryExternalStatsForUser(
                        StorageManager.UUID_DEFAULT,
                        Process.myUserHandle()
                    )
                    photos = extStats.imageBytes.coerceAtLeast(0L)
                    videos = extStats.videoBytes.coerceAtLeast(0L)
                    audio = extStats.audioBytes.coerceAtLeast(0L)
                    externalAvailable = true
                } catch (_: Exception) {
                    externalAvailable = false
                }
            }
        }

        val appsAndSystem = (used - photos - videos - audio).coerceAtLeast(0L)

        return LiveStorageMetrics(
            freeBytes = free,
            totalBytes = total,
            usedBytes = used,
            appsAndSystemBytes = appsAndSystem,
            photosBytes = photos,
            videosBytes = videos,
            otherMediaBytes = audio,
            externalStatsAvailable = externalAvailable
        )
    }

    private fun readMemoryMetrics(): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem to info.totalMem
    }

    private fun readInstalledAppsUsage(hasUsagePermission: Boolean): ImmutableList<InstalledAppUsageInfo> {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30L * 24L * 3600_000L)
        val ninetyDaysAgo = now - (90L * 24L * 3600_000L)

        val usageMap = if (hasUsagePermission) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            usm.queryAndAggregateUsageStats(ninetyDaysAgo, now)
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
            pm.getInstalledApplications(0)
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

            @Suppress("DEPRECATION")
            val isGame = (appInfo.category == ApplicationInfo.CATEGORY_GAME) ||
                ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) ||
                (appInfo.packageName in knownGamePackages)

            val stats = usageMap[appInfo.packageName]
            val lastUsed = stats?.lastTimeUsed ?: 0L
            val fgTime = stats?.totalTimeInForeground ?: 0L

            val pkgInfo = try {
                pm.getPackageInfo(appInfo.packageName, 0)
            } catch (_: Exception) {
                null
            }
            val firstInstall = pkgInfo?.firstInstallTime ?: now
            val lastUpdate = pkgInfo?.lastUpdateTime ?: firstInstall
            val packageTimestamp = maxOf(firstInstall, lastUpdate)
            val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0

            val referenceTime = if (lastUsed > 0L) lastUsed else packageTimestamp
            val daysSinceUse = ((now - referenceTime).coerceAtLeast(0L) / (24L * 3600_000L)).toInt()
            val isUnused30Days = if (hasUsagePermission) {
                referenceTime < thirtyDaysAgo
            } else {
                referenceTime < thirtyDaysAgo || (isStopped && daysSinceUse >= 14)
            }

            // Chave em cache: packageName + lastUpdateTime + permissão UsageStats
            val cacheKey = "${appInfo.packageName}:${lastUpdate}:${hasUsagePermission}"
            val sizeBytes = appSizeCache.getOrPut(cacheKey) {
                val apkAndSplitsBytes = buildList {
                    appInfo.sourceDir?.let { add(File(it)) }
                    appInfo.splitSourceDirs?.forEach { add(File(it)) }
                }.sumOf { file -> if (file.exists()) file.length() else 0L }

                val nativeLibsBytes = appInfo.nativeLibraryDir?.let { dirPath ->
                    val dir = File(dirPath)
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.sumOf { it.length() } ?: 0L
                    } else {
                        0L
                    }
                } ?: 0L

                val fallbackAppBytes = apkAndSplitsBytes + nativeLibsBytes

                if (hasUsagePermission && storageStatsManager != null) {
                    try {
                        val st = storageStatsManager.queryStatsForPackage(
                            StorageManager.UUID_DEFAULT,
                            appInfo.packageName,
                            userHandle
                        )
                        (st.appBytes + st.dataBytes + st.cacheBytes).coerceAtLeast(fallbackAppBytes)
                    } catch (_: Exception) {
                        fallbackAppBytes
                    }
                } else {
                    fallbackAppBytes
                }
            }

            val effectiveFgMillis = if (hasUsagePermission) {
                fgTime
            } else if (!isStopped) {
                30 * 60_000L
            } else {
                0L
            }

            InstalledAppUsageInfo(
                packageName = appInfo.packageName,
                appName = label,
                monogram = monogram,
                isGame = isGame,
                lastTimeUsedMillis = referenceTime,
                foregroundTimeMillis = effectiveFgMillis,
                daysSinceUse = daysSinceUse,
                totalSizeBytes = sizeBytes,
                isUnused30Days = isUnused30Days
            )
        }.sortedWith(
            compareByDescending<InstalledAppUsageInfo> { it.totalSizeBytes }
                .thenBy { it.appName }
        ).toImmutableList()
    }

    /**
     * Para processos em fundo permitidos pelo Android (apenas no flavor 'full' com KILL_BACKGROUND_PROCESSES):
     * - Não volta a ler o snapshot completo (usa a lista atual em memória);
     * - Usa UsageStatsManager.queryEvents para excluir a app em primeiro plano e todas as usadas nos últimos 60 s;
     * - Não limita às 12 maiores;
     * - Regista no Histórico exatamente o que foi feito.
     */
    suspend fun stopPermittedBackgroundApps(recordInHistory: Boolean = true): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.IS_FULL_FLAVOR) return@withContext 0
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val currentApps = _appsState.value.apps.ifEmpty {
            readInstalledAppsUsage(hasUsageStatsPermission())
        }

        val now = System.currentTimeMillis()
        val recentActivePackages = mutableSetOf<String>()
        recentActivePackages.add(context.packageName)

        if (hasUsageStatsPermission()) {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val events = usm.queryEvents(now - 60_000L, now)
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                        event.eventType == UsageEvents.Event.USER_INTERACTION
                    ) {
                        event.packageName?.let { recentActivePackages.add(it) }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val candidates = currentApps.filter { app ->
            app.packageName !in recentActivePackages
        }

        var stoppedCount = 0
        for (app in candidates) {
            try {
                am.killBackgroundProcesses(app.packageName)
                stoppedCount++
            } catch (_: Exception) {
            }
        }

        if (recordInHistory && stoppedCount > 0) {
            val detailMsg = if (hasUsageStatsPermission()) {
                "killBackgroundProcesses solicitado ao Android para $stoppedCount app(s) em fundo (excluindo a app em primeiro plano e as ${recentActivePackages.size - 1} usada(s) nos últimos 60 s)."
            } else {
                "killBackgroundProcesses solicitado ao Android para $stoppedCount app(s) instaladas que já se encontravam em segundo plano."
            }
            dao.insertHistory(
                HistoryEntity(
                    actionType = "BACKGROUND_STOP",
                    title = "Processos em fundo terminados ($stoppedCount apps)",
                    details = detailMsg,
                    timestampEpochMillis = now,
                    bytesFreed = 0L,
                    canRestore = false
                )
            )
        }
        stoppedCount
    }
}
