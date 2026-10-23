package com.nexusguard.android.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.nexusguard.android.data.local.HistoryEntity
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
}
