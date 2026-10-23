package com.nexusguard.android.data.repository

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import android.os.storage.StorageManager
import com.nexusguard.android.data.local.AppStorageSnapshotEntity
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.domain.AppImpactCalculator
import com.nexusguard.android.domain.AppImpactItem
import com.nexusguard.android.domain.AppImpactPeriod
import com.nexusguard.android.domain.NexusFormatters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppImpactRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: NexusGuardDao
) {
    companion object {
        private val ESSENTIAL_SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.downloads",
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending"
        )
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Fase 6: Analisa o impacto real de todas as aplicações instaladas no período escolhido (24 h, 7 d, 30 d):
     * 1. Bateria: Fonte = UsageStatsManager.queryUsageStats (tempo em primeiro plano) +
     *    UsageStatsManager.queryEvents (FOREGROUND_SERVICE_START / FOREGROUND_SERVICE_STOP e contagem de eventos/wakeups).
     * 2. Dados móveis e Wi-Fi: Fonte = NetworkStatsManager.querySummary (TYPE_MOBILE e TYPE_WIFI) por UID,
     *    separando NetworkStats.Bucket.STATE_FOREGROUND de STATE_DEFAULT.
     * 3. Armazenamento: Fonte = StorageStatsManager.queryStatsForPackage (appBytes, dataBytes, cacheBytes) +
     *    comparação com AppStorageSnapshotEntity guardada em Room.
     * 4. Não usadas: Fonte = UsageStatsManager.queryAndAggregateUsageStats (lastTimeUsed para 30/60/90 dias).
     */
    suspend fun loadAppsImpact(
        period: AppImpactPeriod,
        saveStorageSnapshotsToRoom: Boolean = false
    ): ImmutableList<AppImpactItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val startTime = now - (period.days.toLong() * 24L * 3600_000L)
        val hasPerm = hasUsageStatsPermission()

        // Carrega última fotografia de armazenamento guardada em Room para calcular tendência
        val previousStorageMap = runCatching {
            dao.getAllAppStorageSnapshots().associateBy { it.packageName }
        }.getOrDefault(emptyMap())

        // 1. Fonte: UsageStatsManager.queryUsageStats + queryEvents (Bateria e Não usadas)
        val fgTimeInPeriodByPkg = HashMap<String, Long>()
        val fgsTimeInPeriodByPkg = HashMap<String, Long>()
        val wakeupsCountByPkg = HashMap<String, Int>()
        val lastTimeUsed90dByPkg = HashMap<String, Long>()
        val daily7dMinutesByPkg = HashMap<String, IntArray>()

        if (hasPerm) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                // Agrega últimos 90 dias para saber lastTimeUsed exato
                val ninetyDaysAgo = now - (90L * 24L * 3600_000L)
                val aggregated90d = runCatching {
                    usm.queryAndAggregateUsageStats(ninetyDaysAgo, now)
                }.getOrDefault(emptyMap())
                for ((pkg, stat) in aggregated90d) {
                    if (stat.lastTimeUsed > 0L) {
                        lastTimeUsed90dByPkg[pkg] = stat.lastTimeUsed
                    }
                }

                // Tempo em primeiro plano no período (24h / 7d / 30d)
                val periodStats = runCatching {
                    usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, now)
                }.getOrNull().orEmpty()
                for (stat in periodStats) {
                    if (stat.totalTimeInForeground > 0L) {
                        fgTimeInPeriodByPkg[stat.packageName] =
                            (fgTimeInPeriodByPkg[stat.packageName] ?: 0L) + stat.totalTimeInForeground
                    }
                }

                // Gráfico simples de 7 dias (7 buckets diários)
                val sevenDaysAgo = now - (7L * 24L * 3600_000L)
                val dailyStats = runCatching {
                    usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, sevenDaysAgo, now)
                }.getOrNull().orEmpty()
                for (stat in dailyStats) {
                    val mins = (stat.totalTimeInForeground / 60_000L).toInt()
                    if (mins <= 0) continue
                    val dayIndex = (((stat.lastTimeStamp - sevenDaysAgo).coerceAtLeast(0L)) / (24L * 3600_000L))
                        .toInt()
                        .coerceIn(0, 6)
                    val arr = daily7dMinutesByPkg.getOrPut(stat.packageName) { IntArray(7) }
                    arr[dayIndex] += mins
                }

                // Eventos de serviços em primeiro plano (FOREGROUND_SERVICE_START / STOP) e wakeups
                // Para manter a leitura rápida, analisamos até 7 dias de eventos detalhados
                val eventsStart = maxOf(startTime, now - 7L * 24L * 3600_000L)
                try {
                    val events = usm.queryEvents(eventsStart, now)
                    val event = UsageEvents.Event()
                    val activeFgsStartByPkg = HashMap<String, Long>()
                    while (events != null && events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val pkg = event.packageName ?: continue
                        when (event.eventType) {
                            UsageEvents.Event.FOREGROUND_SERVICE_START -> {
                                activeFgsStartByPkg.putIfAbsent(pkg, event.timeStamp)
                                wakeupsCountByPkg[pkg] = (wakeupsCountByPkg[pkg] ?: 0) + 1
                            }
                            UsageEvents.Event.FOREGROUND_SERVICE_STOP -> {
                                val startedAt = activeFgsStartByPkg.remove(pkg)
                                if (startedAt != null && event.timeStamp > startedAt) {
                                    fgsTimeInPeriodByPkg[pkg] =
                                        (fgsTimeInPeriodByPkg[pkg] ?: 0L) + (event.timeStamp - startedAt)
                                }
                            }
                            UsageEvents.Event.ACTIVITY_RESUMED -> {
                                wakeupsCountByPkg[pkg] = (wakeupsCountByPkg[pkg] ?: 0) + 1
                            }
                        }
                    }
                    // Serviços que ainda estão ativos até agora
                    for ((pkg, startedAt) in activeFgsStartByPkg) {
                        if (now > startedAt) {
                            fgsTimeInPeriodByPkg[pkg] =
                                (fgsTimeInPeriodByPkg[pkg] ?: 0L) + (now - startedAt).coerceAtMost(6L * 3600_000L)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // 2. Fonte: NetworkStatsManager.querySummary (TYPE_MOBILE e TYPE_WIFI por UID, separando FG e BG)
        val mobileFgByUid = HashMap<Int, Long>()
        val mobileBgByUid = HashMap<Int, Long>()
        val wifiFgByUid = HashMap<Int, Long>()
        val wifiBgByUid = HashMap<Int, Long>()

        if (hasPerm) {
            val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            if (nsm != null) {
                collectNetworkSummaryByUid(
                    nsm = nsm,
                    networkType = ConnectivityManager.TYPE_WIFI,
                    startTime = startTime,
                    endTime = now,
                    fgMap = wifiFgByUid,
                    bgMap = wifiBgByUid
                )
                collectNetworkSummaryByUid(
                    nsm = nsm,
                    networkType = ConnectivityManager.TYPE_MOBILE,
                    startTime = startTime,
                    endTime = now,
                    fgMap = mobileFgByUid,
                    bgMap = mobileBgByUid
                )
            }
        }

        // 3. Fonte: StorageStatsManager.queryStatsForPackage + PackageManager.getInstalledApplications
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val installedApps = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        val newSnapshotsToSave = mutableListOf<AppStorageSnapshotEntity>()
        val resultItems = ArrayList<AppImpactItem>(installedApps.size)

        for (appInfo in installedApps) {
            val pkg = appInfo.packageName ?: continue
            if (pkg == context.packageName) continue

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLaunchIntent = pm.getLaunchIntentForPackage(pkg) != null

            // Ignora pacotes internos de overlay/sistema sem ícone a menos que sejam apps pré-instaladas desativáveis
            if (isSystem && !isUpdatedSystem && !hasLaunchIntent) continue

            val canBeDisabledBloatware = isSystem &&
                appInfo.enabled &&
                pkg !in ESSENTIAL_SYSTEM_PACKAGES &&
                !pkg.startsWith("com.android.cts")

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(pkg.substringAfterLast('.'))
                .ifBlank { pkg }
            val monogram = label.trim().take(2).uppercase()

            var appCodeBytes = 0L
            var dataBytes = 0L
            var cacheBytes = 0L
            var isEstimatedApk = false

            if (hasPerm && ssm != null) {
                try {
                    val stats = ssm.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        pkg,
                        Process.myUserHandle()
                    )
                    appCodeBytes = stats.appBytes.coerceAtLeast(0L)
                    dataBytes = (stats.dataBytes - stats.cacheBytes).coerceAtLeast(0L)
                    cacheBytes = stats.cacheBytes.coerceAtLeast(0L)
                } catch (_: Exception) {
                    appCodeBytes = appInfo.sourceDir?.let { File(it).length() } ?: 0L
                    isEstimatedApk = true
                }
            } else {
                appCodeBytes = appInfo.sourceDir?.let { File(it).length() } ?: 0L
                isEstimatedApk = true
            }

            val totalBytes = appCodeBytes + dataBytes + cacheBytes
            val prevSnapshot = previousStorageMap[pkg]
            val deltaBytes = if (prevSnapshot != null) totalBytes - prevSnapshot.totalBytes else 0L
            val trendText = AppImpactCalculator.buildStorageTrendTextPt(
                deltaBytes = deltaBytes,
                hadPreviousSnapshot = prevSnapshot != null
            )

            if (!isEstimatedApk && totalBytes > 0L) {
                newSnapshotsToSave.add(
                    AppStorageSnapshotEntity(
                        packageName = pkg,
                        totalBytes = totalBytes,
                        appBytes = appCodeBytes,
                        dataBytes = dataBytes,
                        cacheBytes = cacheBytes,
                        timestampEpochMillis = now
                    )
                )
            }

            val pkgInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
            val firstInstallMillis = pkgInfo?.firstInstallTime ?: 0L
            val lastUsed = lastTimeUsed90dByPkg[pkg]
            val daysSinceUse = if (hasPerm) {
                if (lastUsed != null && lastUsed > 0L) {
                    ((now - lastUsed).coerceAtLeast(0L) / (24L * 3600_000L)).toInt()
                } else if (firstInstallMillis > 0L) {
                    ((now - firstInstallMillis).coerceAtLeast(0L) / (24L * 3600_000L)).toInt().coerceAtLeast(31)
                } else {
                    90
                }
            } else {
                null
            }

            val fgMillis = fgTimeInPeriodByPkg[pkg] ?: 0L
            val fgsMillis = fgsTimeInPeriodByPkg[pkg] ?: 0L
            val wakeups = wakeupsCountByPkg[pkg] ?: 0
            val batteryReason = if (hasPerm) {
                AppImpactCalculator.buildBatteryReasonTextPt(fgMillis, fgsMillis, wakeups)
            } else {
                "Requer acesso a utilização"
            }

            val uid = appInfo.uid
            val dailyArr = daily7dMinutesByPkg[pkg]
            val dailyList = if (dailyArr != null) {
                dailyArr.toList().toImmutableList()
            } else {
                persistentListOf(0, 0, 0, 0, 0, 0, 0)
            }

            resultItems.add(
                AppImpactItem(
                    packageName = pkg,
                    appName = label,
                    monogram = monogram,
                    uid = uid,
                    isSystemApp = isSystem,
                    canBeDisabledBloatware = canBeDisabledBloatware,
                    foregroundMillis = fgMillis,
                    backgroundServiceMillis = fgsMillis,
                    estimatedWakeupsCount = wakeups,
                    batteryReasonTextPt = batteryReason,
                    mobileForegroundBytes = mobileFgByUid[uid] ?: 0L,
                    mobileBackgroundBytes = mobileBgByUid[uid] ?: 0L,
                    wifiForegroundBytes = wifiFgByUid[uid] ?: 0L,
                    wifiBackgroundBytes = wifiBgByUid[uid] ?: 0L,
                    appCodeBytes = appCodeBytes,
                    dataBytes = dataBytes,
                    cacheBytes = cacheBytes,
                    totalStorageBytes = totalBytes,
                    storageDeltaBytes = deltaBytes,
                    storageTrendTextPt = trendText,
                    isStorageEstimatedApk = isEstimatedApk,
                    lastTimeUsedMillis = lastUsed,
                    daysSinceLastUse = daysSinceUse,
                    installedFirstTimeMillis = firstInstallMillis,
                    dailyUsageMinutes7d = dailyList
                )
            )
        }

        if (saveStorageSnapshotsToRoom && newSnapshotsToSave.isNotEmpty()) {
            runCatching { dao.upsertAppStorageSnapshots(newSnapshotsToSave) }
        }

        resultItems.toImmutableList()
    }

    @Suppress("DEPRECATION")
    private fun collectNetworkSummaryByUid(
        nsm: NetworkStatsManager,
        networkType: Int,
        startTime: Long,
        endTime: Long,
        fgMap: MutableMap<Int, Long>,
        bgMap: MutableMap<Int, Long>
    ) {
        var stats: NetworkStats? = null
        try {
            stats = nsm.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats != null && stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid <= 0) continue
                val bytes = (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
                if (bytes <= 0L) continue
                if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                    fgMap[uid] = (fgMap[uid] ?: 0L) + bytes
                } else {
                    bgMap[uid] = (bgMap[uid] ?: 0L) + bytes
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                stats?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Verifica com PackageManager.getPackageInfo quais das apps selecionadas foram realmente desinstaladas
     * e grava no Histórico apenas as confirmadas.
     */
    suspend fun recordVerifiedBatchUninstalls(
        requestedApps: List<AppImpactItem>
    ): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val (removedApps, freedBytes) = AppImpactCalculator.verifyBatchUninstallResult(
            requestedApps = requestedApps,
            isStillInstalled = { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                } catch (_: Exception) {
                    true
                }
            }
        )

        if (removedApps.isNotEmpty()) {
            val names = removedApps.take(4).joinToString(", ") { it.appName }
            val extra = if (removedApps.size > 4) " (+${removedApps.size - 4})" else ""
            dao.insertHistory(
                HistoryEntity(
                    actionType = "BATCH_UNINSTALL",
                    title = "Desinstalação · ${removedApps.size} app(s) (${NexusFormatters.formatBytes(freedBytes)})",
                    details = "Removidas (verificado via getPackageInfo): $names$extra",
                    timestampEpochMillis = System.currentTimeMillis(),
                    bytesFreed = freedBytes
                )
            )
        }

        removedApps.size to freedBytes
    }
}
