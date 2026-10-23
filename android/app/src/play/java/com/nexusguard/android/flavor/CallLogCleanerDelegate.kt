package com.nexusguard.android.flavor

import android.content.Context

/**
 * Implementação do flavor `play`: não inclui permissões nem código de registo de chamadas
 * para cumprir integralmente as políticas da Google Play.
 */
object CallLogCleanerDelegate {

    const val IS_AVAILABLE: Boolean = false

    val requiredPermissions: Array<String> = emptyArray()

    fun hasPermissions(context: Context): Boolean = false

    suspend fun countCallsOlderThanMonths(context: Context, months: Int): Int = 0

    suspend fun deleteCallsOlderThanMonths(context: Context, months: Int): Int = 0
}
