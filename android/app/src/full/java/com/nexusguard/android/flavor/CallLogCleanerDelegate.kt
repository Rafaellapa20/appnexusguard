package com.nexusguard.android.flavor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementação do flavor `full` para limpeza opcional do registo de chamadas antigo.
 * Só executa se o utilizador ativar explicitamente e conceder READ_CALL_LOG e WRITE_CALL_LOG.
 */
object CallLogCleanerDelegate {

    const val IS_AVAILABLE: Boolean = true

    val requiredPermissions: Array<String> = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG
    )

    fun hasPermissions(context: Context): Boolean {
        val readOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        val writeOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        return readOk && writeOk
    }

    suspend fun countCallsOlderThanMonths(context: Context, months: Int): Int = withContext(Dispatchers.IO) {
        if (!hasPermissions(context)) return@withContext 0
        val cutoffMillis = System.currentTimeMillis() - (months.coerceAtLeast(1) * 30L * 24L * 3600L * 1000L)
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.DATE} < ?",
                arrayOf(cutoffMillis.toString()),
                null
            )?.use { cursor ->
                cursor.count
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    suspend fun deleteCallsOlderThanMonths(context: Context, months: Int): Int = withContext(Dispatchers.IO) {
        if (!hasPermissions(context)) return@withContext 0
        val cutoffMillis = System.currentTimeMillis() - (months.coerceAtLeast(1) * 30L * 24L * 3600L * 1000L)
        try {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.DATE} < ?",
                arrayOf(cutoffMillis.toString())
            ).coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }
}
