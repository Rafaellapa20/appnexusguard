package com.nexusguard.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nexusguard.android.MainActivity
import com.nexusguard.android.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class GameBoostForegroundService : Service() {

    @Inject
    lateinit var boostManager: GameBoostManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                boostManager.stopBoostSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val initialState = boostManager.sessionState.value
                startForeground(NOTIFICATION_ID, buildNotification(initialState))
                updateJob?.cancel()
                updateJob = serviceScope.launch {
                    boostManager.sessionState.collectLatest { state ->
                        if (!state.isActive) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.notify(NOTIFICATION_ID, buildNotification(state))
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(state: BoostSessionState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GameBoostForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val tempText = state.currentTempCelsius?.let {
            String.format(Locale("pt", "PT"), "%.0f °C", it)
        } ?: "N/D"
        val dndText = if (state.dndEnabled) "Não incomodar ligado" else "Não incomodar desligado"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gauge)
            .setContentTitle("Boost ativo · ${state.gameName} · ${state.formattedElapsed}")
            .setContentText("Bateria $tempText · Térmico: ${state.currentThermalLevel.labelPt} · $dndText")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(0, "Terminar", stopPending)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Modo Jogo (Boost Ativo)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra o cronómetro, temperatura da bateria e estado térmico durante o jogo"
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "nexus_guard_game_boost"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.nexusguard.android.action.START_BOOST"
        const val ACTION_STOP = "com.nexusguard.android.action.STOP_BOOST"
    }
}
