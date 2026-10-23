package com.nexusguard.android.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.domain.MonthlyReportSummary
import com.nexusguard.android.domain.NexusFormatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportExporter {

    suspend fun exportHistoryToPdf(
        context: Context,
        targetUri: Uri,
        entries: List<HistoryEntity>,
        currentHealthScore: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(12, 15, 22)
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            val itemTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val itemBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val linePaint = Paint().apply {
                color = Color.rgb(216, 224, 238)
                strokeWidth = 1f
            }

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT"))
            val nowText = dateFormatter.format(Date())
            val totalFreed = entries.sumOf { it.bytesFreed }

            var y = 48f
            canvas.drawText("Nexus Guard Android — Relatório de Histórico", 36f, y, titlePaint)
            y += 20f
            canvas.drawText(
                "Gerado em: $nowText  |  Saúde atual: $currentHealthScore/100  |  Total libertado: ${NexusFormatters.formatBytes(totalFreed)}",
                36f,
                y,
                subPaint
            )
            y += 14f
            canvas.drawLine(36f, y, 559f, y, linePaint)
            y += 22f

            if (entries.isEmpty()) {
                canvas.drawText("Sem ações registadas no Histórico até ao momento.", 36f, y, itemBodyPaint)
            } else {
                for (entry in entries.take(22)) {
                    val dateStr = dateFormatter.format(Date(entry.timestampEpochMillis))
                    canvas.drawText("${entry.title}  [$dateStr]", 36f, y, itemTitlePaint)
                    y += 14f
                    val detailText = entry.details.take(95)
                    canvas.drawText(detailText, 36f, y, itemBodyPaint)
                    y += 10f
                    canvas.drawLine(36f, y, 559f, y, linePaint)
                    y += 16f
                    if (y > 790f) break
                }
            }

            pdfDocument.finishPage(page)

            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                pdfDocument.writeTo(out)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            pdfDocument.close()
        }
    }

    suspend fun exportMonthlyReportToPdf(
        context: Context,
        targetUri: Uri,
        summary: MonthlyReportSummary
    ): Boolean = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(12, 15, 22)
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 85, 105)
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(124, 92, 246)
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val linePaint = Paint().apply {
                color = Color.rgb(216, 224, 238)
                strokeWidth = 1f
            }

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT"))
            val nowText = dateFormatter.format(Date())

            var y = 48f
            canvas.drawText("Nexus Guard — Relatório Mensal do Sistema", 36f, y, titlePaint)
            y += 20f
            canvas.drawText(
                "Período: ${summary.periodLabelPt}  |  Gerado em: $nowText",
                36f,
                y,
                subPaint
            )
            y += 14f
            canvas.drawLine(36f, y, 559f, y, linePaint)
            y += 26f

            // 1. Espaço libertado e Saúde
            canvas.drawText("1. Espaço Libertado e Evolução da Saúde", 36f, y, sectionPaint)
            y += 18f
            canvas.drawText(
                "• Espaço total libertado (30 dias): ${NexusFormatters.formatBytes(summary.totalBytesFreed30d)} (${summary.actionsCount30d} ações)",
                36f,
                y,
                bodyPaint
            )
            y += 16f
            val healthEvolutionStr = if (summary.healthEvolutionPoints.isEmpty()) {
                "${summary.currentHealthScore}/100"
            } else {
                summary.healthEvolutionPoints.joinToString(" → ") { "$it" } + " (atual ${summary.currentHealthScore}/100)"
            }
            canvas.drawText("• Evolução da saúde: $healthEvolutionStr", 36f, y, bodyPaint)
            y += 26f

            // 2. Consumo médio %/h
            canvas.drawText("2. Consumo Médio de Bateria (%/h)", 36f, y, sectionPaint)
            y += 18f
            val screenOnStr = summary.avgScreenOnDrainPerHour?.let {
                String.format(Locale("pt", "PT"), "%.1f %%/h", it)
            } ?: "Em recolha (< 2 h)"
            val idleStr = summary.avgIdleDrainPerHour?.let {
                String.format(Locale("pt", "PT"), "%.1f %%/h", it)
            } ?: "Em recolha (< 2 h)"
            canvas.drawText("• Ecrã ligado: $screenOnStr   |   Em repouso (ecrã desligado): $idleStr", 36f, y, bodyPaint)
            y += 26f

            // 3. Apps com maior impacto
            canvas.drawText("3. Apps com Maior Impacto no Dispositivo", 36f, y, sectionPaint)
            y += 18f
            if (summary.topImpactApps.isEmpty()) {
                canvas.drawText("• Sem dados suficientes de impacto por app.", 36f, y, bodyPaint)
            } else {
                for (app in summary.topImpactApps.take(10)) {
                    canvas.drawText(
                        "• ${app.appName}: ${NexusFormatters.formatBytes(app.totalStorageBytes)} (${app.batteryReasonTextPt})",
                        36f,
                        y,
                        bodyPaint
                    )
                    y += 16f
                }
            }

            pdfDocument.finishPage(page)
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                pdfDocument.writeTo(out)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            pdfDocument.close()
        }
    }
}
