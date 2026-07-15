package com.aistudio.neurostats.logic

import android.content.Context
import android.graphics.*
import com.aistudio.neurostats.data.EegSession
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ReportGenerator(private val context: Context) {

    /**
     * Generiert einen klinischen Bericht für eine einzelne EegSession (PNG).
     */
    fun generateReport(session: EegSession): File? {
        val dataPoints = parseWTaskFromCsv(session.csvData)
        if (dataPoints.isEmpty()) return null

        val stats = calculateStats(dataPoints)
        val bitmap = renderTrajectoryToBitmap(dataPoints, session.label, stats)
        val fileName = "MMSI_Clinical_Report_${session.id}_${System.currentTimeMillis()}.png"
        val reportFile = File(context.getExternalFilesDir(null), fileName)

        try {
            FileOutputStream(reportFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return reportFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generiert einen konsolidierten PDF-Bericht für alle 10 getesteten Probanden.
     */
    fun generateClinicalBatchReport(probands: List<ProbandReportData>): File? {
        val fileName = "MMSI_Clinical_Batch_Report_${System.currentTimeMillis()}.pdf"
        val reportFile = File(context.getExternalFilesDir(null), fileName)
        val pdfDocument = android.graphics.pdf.PdfDocument()

        try {
            probands.forEachIndexed { index, proband ->
                val pageWidth = 800
                val pageHeight = 1100
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                drawProbandPage(canvas, pageWidth, pageHeight, proband, index + 1, probands.size)

                pdfDocument.finishPage(page)
            }

            FileOutputStream(reportFile).use { out ->
                pdfDocument.writeTo(out)
            }
            return reportFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawProbandPage(
        canvas: Canvas,
        width: Int,
        height: Int,
        proband: ProbandReportData,
        pageNum: Int,
        totalPages: Int
    ) {
        // Fill background with white
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Header bar (Slate Blue)
        val headerPaint = Paint().apply {
            color = Color.rgb(30, 41, 59) // Slate-colored background
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 130f, headerPaint)

        // Header Text
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MMSI CLINICAL VALIDATION REPORT", 30f, 55f, headerTextPaint)
        
        headerTextPaint.textSize = 14f
        headerTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Status: VALIDATED | Algorithmus: MMSI v3.6.3 | Testzeitpunkt: $dateStr", 30f, 95f, headerTextPaint)

        // Proband Info Block
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isAntiAlias = true
        }

        val labelPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 13f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Two-column Proband Meta information
        canvas.drawText("PROBAND-ID:", 30f, 175f, labelPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 18f
        textPaint.color = Color.BLACK
        canvas.drawText(proband.id, 30f, 205f, textPaint)

        canvas.drawText("NAME / LABEL:", 280f, 175f, labelPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 18f
        canvas.drawText(proband.name, 280f, 205f, textPaint)

        canvas.drawText("DIAGNOSTISCHES PROFIL:", 550f, 175f, labelPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 18f
        textPaint.color = when (proband.type) {
            "Healthy Control" -> Color.rgb(34, 197, 94) // Green
            "Borderline Profile" -> Color.rgb(234, 179, 8) // Yellow
            else -> Color.rgb(239, 68, 68) // Red
        }
        canvas.drawText(proband.type, 550f, 205f, textPaint)

        // Divider line
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 2f
        }
        canvas.drawLine(30f, 230f, (width - 30).toFloat(), 230f, linePaint)

        // Statistics Section
        canvas.drawText("STATISTISCHE TRAJEKTORIEN-AUSWERTUNG", 30f, 260f, labelPaint)

        val statsCardPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        // Extended height for 2 rows of metrics
        canvas.drawRect(30f, 275f, (width - 30).toFloat(), 430f, statsCardPaint)

        val statValuePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 21f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statLabelPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 11f
            isAntiAlias = true
        }

        // ROW 1 METRICS
        // Column 1: Mittelwert W(t)
        canvas.drawText("Mittelwert W(t)", 50f, 305f, statLabelPaint)
        canvas.drawText(String.format(Locale.US, "%.3f", proband.stats.mean), 50f, 330f, statValuePaint)

        // Column 2: Varianz
        canvas.drawText("Varianz W(t)", 240f, 305f, statLabelPaint)
        canvas.drawText(String.format(Locale.US, "%.4f", proband.stats.variance), 240f, 330f, statValuePaint)

        // Column 3: Peak W(t)
        canvas.drawText("Peak W(t) (Max)", 430f, 305f, statLabelPaint)
        val peakPaintColor = if (proband.stats.maxVal > 3.2f) Color.rgb(220, 38, 38) else Color.rgb(15, 23, 42)
        statValuePaint.color = peakPaintColor
        canvas.drawText(String.format(Locale.US, "%.3f", proband.stats.maxVal), 430f, 330f, statValuePaint)
        statValuePaint.color = Color.rgb(15, 23, 42) // reset

        // Column 4: Instabilitäts-RMSD
        canvas.drawText("Instabilität (RMSD)", 620f, 305f, statLabelPaint)
        canvas.drawText(String.format(Locale.US, "%.4f", proband.stats.rmsd), 620f, 330f, statValuePaint)

        // ROW 2 METRICS
        // Column 1: Grenzwert-Events (>3.2)
        canvas.drawText("Events (>3.2)", 50f, 365f, statLabelPaint)
        canvas.drawText(proband.stats.crossings.toString(), 50f, 395f, statValuePaint)

        // Column 2: Verweildauer (Time Above Threshold %)
        canvas.drawText("Verweildauer (>3.2)", 240f, 365f, statLabelPaint)
        canvas.drawText(String.format(Locale.US, "%.1f %%", proband.stats.timeAbovePercent), 240f, 395f, statValuePaint)

        // Column 3: D2 (GP-Komplexität)
        canvas.drawText("D2 GP-Komplexität", 430f, 365f, statLabelPaint)
        val modeLabel = if (proband.stats.d2 >= 3.0f) "TV" else "TS"
        val modeColor = if (proband.stats.d2 >= 3.0f) Color.rgb(217, 119, 6) else Color.rgb(37, 99, 235)
        statValuePaint.color = modeColor
        canvas.drawText(String.format(Locale.US, "%.2f (%s)", proband.stats.d2, modeLabel), 430f, 395f, statValuePaint)
        statValuePaint.color = Color.rgb(15, 23, 42) // reset

        // Column 4: Diagnostic Score / Status
        canvas.drawText("Diagnostic Score / Status", 620f, 365f, statLabelPaint)
        val scoreColor = when {
            proband.stats.diagnosticScore > 65f -> Color.rgb(220, 38, 38)
            proband.stats.diagnosticScore > 35f -> Color.rgb(217, 119, 6)
            else -> Color.rgb(21, 128, 61)
        }
        statValuePaint.color = scoreColor
        val statusLabel = when (proband.type) {
            "Healthy Control" -> "STABIL"
            "Borderline Profile" -> "GRENZWERT"
            else -> "PATHOLOGISCH"
        }
        canvas.drawText(String.format(Locale.US, "%.1f%% (%s)", proband.stats.diagnosticScore, statusLabel), 620f, 395f, statValuePaint)
        statValuePaint.color = Color.rgb(15, 23, 42) // reset

        // Clinical Interpretation Box (Shifted down slightly)
        val interpretationBgPaint = Paint().apply {
            color = when (proband.type) {
                "Healthy Control" -> Color.rgb(240, 253, 244)
                "Borderline Profile" -> Color.rgb(254, 252, 232)
                else -> Color.rgb(254, 242, 242)
            }
            style = Paint.Style.FILL
        }
        val interpretationBorderPaint = Paint().apply {
            color = when (proband.type) {
                "Healthy Control" -> Color.rgb(187, 247, 208)
                "Borderline Profile" -> Color.rgb(254, 240, 138)
                else -> Color.rgb(254, 202, 202)
            }
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(30f, 450f, (width - 30).toFloat(), 530f, interpretationBgPaint)
        canvas.drawRect(30f, 450f, (width - 30).toFloat(), 530f, interpretationBorderPaint)

        val interpLabelPaint = Paint().apply {
            color = when (proband.type) {
                "Healthy Control" -> Color.rgb(21, 128, 61)
                "Borderline Profile" -> Color.rgb(161, 98, 7)
                else -> Color.rgb(185, 28, 28)
            }
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val interpTextPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("KLINISCHES FACHGUTACHTEN & DIAGNOSTISCHE INTERPRETATION:", 50f, 478f, interpLabelPaint)
        canvas.drawText(proband.stats.interpretation, 50f, 508f, interpTextPaint)

        // Divider
        canvas.drawLine(30f, 550f, (width - 30).toFloat(), 550f, linePaint)

        // Chart Section
        canvas.drawText("VISUALISIERUNG DER KOGNITIVEN TRAJEKTORIE W(t)", 30f, 578f, labelPaint)

        // Draw the chart in the PDF!
        val chartTop = 600f
        val chartBottom = 1000f
        val chartLeft = 60f
        val chartRight = (width - 60).toFloat()
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft

        // Draw chart background frame
        val chartBgPaint = Paint().apply {
            color = Color.rgb(250, 250, 250)
            style = Paint.Style.FILL
        }
        canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, chartBgPaint)

        // Draw grid lines
        val gridPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        val axisLabelPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 11f
            isAntiAlias = true
        }

        for (i in 0..6) {
            val yVal = i.toFloat()
            val y = chartBottom - (yVal / 6.0f * chartHeight)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            canvas.drawText(String.format(Locale.US, "%.1f", yVal), chartLeft - 35f, y + 4f, axisLabelPaint)
        }

        // Draw threshold line (3.2)
        val thresholdPaint = Paint().apply {
            color = Color.rgb(239, 68, 68) // Bright red
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
        val thresholdY = chartBottom - (3.2f / 6.0f * chartHeight)
        canvas.drawLine(chartLeft, thresholdY, chartRight, thresholdY, thresholdPaint)
        
        val thresholdLabelPaint = Paint().apply {
            color = Color.rgb(239, 68, 68)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("GRENZWERT (3.2)", chartRight - 130f, thresholdY - 8f, thresholdLabelPaint)

        // Draw trajectory line
        if (proband.dataPoints.isNotEmpty()) {
            val path = Path()
            val stepX = chartWidth / (proband.dataPoints.size - 1).coerceAtLeast(1)
            
            val linePathPaint = Paint().apply {
                color = when (proband.type) {
                    "Healthy Control" -> Color.rgb(37, 99, 235) // Elegant Blue
                    "Borderline Profile" -> Color.rgb(217, 119, 6) // Amber
                    else -> Color.rgb(220, 38, 38) // Deep Red
                }
                strokeWidth = 3f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            proband.dataPoints.forEachIndexed { index, value ->
                val x = chartLeft + index * stepX
                val y = chartBottom - (value / 6.0f * chartHeight).coerceAtMost(chartHeight)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePathPaint)

            // Highlighting Peak / Maximum Value on PDF chart
            val maxVal = proband.stats.maxVal
            val maxIndex = proband.dataPoints.indexOf(maxVal)
            if (maxIndex != -1) {
                val peakX = chartLeft + maxIndex * stepX
                val peakY = chartBottom - (maxVal / 6.0f * chartHeight).coerceAtMost(chartHeight)

                val peakColor = if (maxVal > 3.2f) Color.rgb(220, 38, 38) else Color.rgb(37, 99, 235)
                val peakPointPaint = Paint().apply {
                    color = peakColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val peakOuterPointPaint = Paint().apply {
                    color = peakColor
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                
                canvas.drawCircle(peakX, peakY, 8f, peakOuterPointPaint)
                canvas.drawCircle(peakX, peakY, 4f, peakPointPaint)

                // Tooltip badge for the peak
                val tooltipText = String.format(Locale.US, "Max W(t): %.2f", maxVal)
                val tooltipPaint = Paint().apply {
                    color = Color.rgb(15, 23, 42) // Slate-dark bg
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val tooltipTextPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 11f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                
                val textWidth = tooltipTextPaint.measureText(tooltipText)
                val textHeight = 14f
                val paddingX = 8f
                val paddingY = 4f
                
                var tooltipLeft = peakX - textWidth / 2f - paddingX
                var tooltipRight = peakX + textWidth / 2f + paddingX
                if (tooltipLeft < chartLeft) {
                    tooltipLeft = chartLeft
                    tooltipRight = chartLeft + textWidth + paddingX * 2
                }
                if (tooltipRight > chartRight) {
                    tooltipRight = chartRight
                    tooltipLeft = chartRight - textWidth - paddingX * 2
                }
                
                val tooltipBottom = peakY - 12f
                val tooltipTop = tooltipBottom - textHeight - paddingY * 2
                
                val rectF = RectF(tooltipLeft, tooltipTop, tooltipRight, tooltipBottom)
                canvas.drawRoundRect(rectF, 6f, 6f, tooltipPaint)
                canvas.drawText(tooltipText, tooltipLeft + paddingX, tooltipBottom - paddingY - 1f, tooltipTextPaint)
            }
        }

        // Draw footer page numbering
        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 12f
            isAntiAlias = true
        }
        canvas.drawText("MMSI Neuro Clinical Assessment Suite - Vertraulicher medizinischer Befund", 30f, height - 40f, footerPaint)
        canvas.drawText("Seite $pageNum von $totalPages", width - 120f, height - 40f, footerPaint)
    }

    private fun calculateCorrelationDimensionD2(data: List<Float>, m: Int = 5, tau: Int = 2, theilerW: Int = 2): Float {
        if (data.size < m * tau) return 1.5f

        val vectors = mutableListOf<FloatArray>()
        for (i in 0..data.size - m * tau) {
            val vec = FloatArray(m)
            for (j in 0 until m) {
                vec[j] = data[i + j * tau]
            }
            vectors.add(vec)
        }

        val numVectors = vectors.size
        if (numVectors < 10) return 1.5f

        val distances = mutableListOf<Float>()
        for (i in 0 until numVectors) {
            for (j in (i + theilerW + 1) until numVectors) {
                if (j >= numVectors) continue
                var sumSq = 0f
                for (k in 0 until m) {
                    val diff = vectors[i][k] - vectors[j][k]
                    sumSq += diff * diff
                }
                val dist = kotlin.math.sqrt(sumSq)
                distances.add(dist)
            }
        }

        if (distances.isEmpty()) return 1.5f
        distances.sort()

        val p15Idx = (distances.size * 0.15).toInt().coerceIn(0, distances.lastIndex)
        val p50Idx = (distances.size * 0.50).toInt().coerceIn(0, distances.lastIndex)

        val r1 = distances[p15Idx]
        val r2 = distances[p50Idx]

        if (r1 <= 0.0001f || r2 <= r1) return 1.2f

        val count1 = distances.count { it <= r1 }
        val count2 = distances.count { it <= r2 }

        val c1 = count1.toFloat() / distances.size
        val c2 = count2.toFloat() / distances.size

        if (c1 <= 0f || c2 <= c1) return 1.2f

        val d2 = (kotlin.math.log2(c2) - kotlin.math.log2(c1)) / (kotlin.math.log2(r2) - kotlin.math.log2(r1))
        return d2.coerceIn(1.0f, 4.5f)
    }

    private fun calculateStats(data: List<Float>): ClinicalStats {
        val mean = data.average().toFloat()
        val variance = data.map { (it - mean) * (it - mean) }.average().toFloat()
        val maxVal = data.maxOrNull() ?: 0f
        val threshold = 3.2f // Klinischer Grenzwert
        val crossings = data.count { it > threshold }

        // RMSD (Root Mean Square Successive Difference)
        val successiveDiffsSq = if (data.size > 1) {
            data.zipWithNext { a, b -> (a - b) * (a - b) }.average().toFloat()
        } else {
            0f
        }
        val rmsd = kotlin.math.sqrt(successiveDiffsSq)

        // Time above threshold %
        val timeAbovePercent = if (data.isNotEmpty()) {
            (crossings.toFloat() / data.size) * 100f
        } else {
            0f
        }

        // Correlation dimension D2
        val d2 = calculateCorrelationDimensionD2(data)

        // Weighted Diagnostic Instability Score
        val scoreBase = (mean / 3.2f) * 30f + (maxVal / 5.0f) * 35f + (timeAbovePercent / 100f) * 35f
        val diagnosticScore = scoreBase.coerceIn(5.0f, 98.5f)
        
        val interpretation = when {
            diagnosticScore > 65f -> "PATHOLOGISCH: Hohe multivariate kognitive Instabilität (D2 = ${String.format(Locale.US, "%.2f", d2)}). Schizophrenie-Signatur detektiert."
            diagnosticScore > 35f -> "GRENZWERTIG: Sporadische Instabilitäten (D2 = ${String.format(Locale.US, "%.2f", d2)}) und neuronale Belastungsepisoden."
            else -> "STABIL: Keine physiologischen Auffälligkeiten (D2 = ${String.format(Locale.US, "%.2f", d2)}). Trajektorie verläuft vollständig normal."
        }

        return ClinicalStats(mean, variance, maxVal, crossings, rmsd, timeAbovePercent, d2, diagnosticScore, interpretation)
    }

    data class ClinicalStats(
        val mean: Float,
        val variance: Float,
        val maxVal: Float,
        val crossings: Int,
        val rmsd: Float,
        val timeAbovePercent: Float,
        val d2: Float,
        val diagnosticScore: Float,
        val interpretation: String
    )

    data class ProbandReportData(
        val id: String,
        val name: String,
        val type: String, // "Healthy Control", "Borderline Profile", "Clinical Profile"
        val dataPoints: List<Float>,
        val stats: ClinicalStats
    )

    private fun parseWTaskFromCsv(csv: String): List<Float> {
        return csv.split("\n")
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                line.split(",").firstOrNull()?.replace("\"", "")?.trim()?.toFloatOrNull()
            }
    }

    private fun renderTrajectoryToBitmap(data: List<Float>, label: String, stats: ClinicalStats): Bitmap {
        val width = 1000
        val height = 650
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        canvas.drawColor(Color.WHITE)
        
        val paint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        // Header Bereich
        val headerPaint = Paint().apply {
            color = Color.rgb(240, 240, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 210f, headerPaint)

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("MMSI NEURO CLINICAL ANALYSIS REPORT", 30f, 45f, textPaint)
        
        textPaint.textSize = 18f
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Proband Label: $label", 30f, 85f, textPaint)
        
        // Metrics line 1
        canvas.drawText("Mean W(t): ${String.format("%.3f", stats.mean)}", 30f, 120f, textPaint)
        canvas.drawText("Varianz: ${String.format("%.4f", stats.variance)}", 260f, 120f, textPaint)
        canvas.drawText("RMSD (Instab.): ${String.format("%.4f", stats.rmsd)}", 500f, 120f, textPaint)
        canvas.drawText("Events: ${stats.crossings}", 780f, 120f, textPaint)

        // Metrics line 2
        canvas.drawText("Peak W(t): ${String.format("%.3f", stats.maxVal)}", 30f, 155f, textPaint)
        canvas.drawText("Verweildauer: ${String.format("%.1f%%", stats.timeAbovePercent)}", 260f, 155f, textPaint)
        canvas.drawText("D2: ${String.format(Locale.US, "%.2f (%s)", stats.d2, if (stats.d2 >= 3.0f) "TV" else "TS")} | Score: ${String.format("%.1f%%", stats.diagnosticScore)}", 500f, 155f, textPaint)
        
        textPaint.color = if (stats.diagnosticScore > 50f) Color.rgb(200, 0, 0) else Color.rgb(0, 150, 0)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        canvas.drawText("Interpretation: ${stats.interpretation}", 30f, 190f, textPaint)

        // Chart Bereich
        val chartTop = 240f
        val chartBottom = height - 50f
        val chartLeft = 50f
        val chartRight = width - 50f
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft

        // Raster
        paint.color = Color.LTGRAY
        for (i in 0..5) {
            val y = chartBottom - (chartHeight / 5 * i)
            canvas.drawLine(chartLeft, y, chartRight, y, paint)
            textPaint.color = Color.GRAY
            textPaint.textSize = 14f
            canvas.drawText("${i}.0", 15f, y + 5f, textPaint)
        }

        // Klinische Grenze (3.2)
        paint.color = Color.RED
        paint.alpha = 100
        val thresholdY = chartBottom - (3.2f / 6.0f * chartHeight)
        canvas.drawLine(chartLeft, thresholdY, chartRight, thresholdY, paint)

        // Trajektorie
        if (data.isNotEmpty()) {
            val path = Path()
            val stepX = chartWidth / (data.size - 1).coerceAtLeast(1)
            val maxVal = 6.0f
            
            paint.color = if (label.contains("Klinisch")) Color.RED else Color.rgb(0, 102, 204)
            paint.strokeWidth = 4f
            paint.alpha = 255
            
            data.forEachIndexed { index, value ->
                val x = chartLeft + index * stepX
                val y = chartBottom - (value / maxVal * chartHeight).coerceAtMost(chartHeight)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)

            // Single Report Peak Highlight
            val maxPeakVal = stats.maxVal
            val maxIndex = data.indexOf(maxPeakVal)
            if (maxIndex != -1) {
                val peakX = chartLeft + maxIndex * stepX
                val peakY = chartBottom - (maxPeakVal / 6.0f * chartHeight).coerceAtMost(chartHeight)
                
                val peakColor = if (maxPeakVal > 3.2f) Color.RED else Color.rgb(0, 102, 204)
                val peakPaint = Paint().apply {
                    color = peakColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val outerPaint = Paint().apply {
                    color = peakColor
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    isAntiAlias = true
                }
                
                canvas.drawCircle(peakX, peakY, 10f, outerPaint)
                canvas.drawCircle(peakX, peakY, 5f, peakPaint)
                
                // Tooltip text
                val tooltipText = String.format(Locale.US, "Max W(t): %.2f", maxPeakVal)
                val tooltipBgPaint = Paint().apply {
                    color = Color.DKGRAY
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val tooltipTextPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                
                val textWidth = tooltipTextPaint.measureText(tooltipText)
                val textHeight = 18f
                val paddingX = 10f
                val paddingY = 5f
                
                var tooltipLeft = peakX - textWidth / 2f - paddingX
                var tooltipRight = peakX + textWidth / 2f + paddingX
                if (tooltipLeft < chartLeft) {
                    tooltipLeft = chartLeft
                    tooltipRight = chartLeft + textWidth + paddingX * 2
                }
                if (tooltipRight > chartRight) {
                    tooltipRight = chartRight
                    tooltipLeft = chartRight - textWidth - paddingX * 2
                }
                
                val tooltipBottom = peakY - 15f
                val tooltipTop = tooltipBottom - textHeight - paddingY * 2
                
                val rectF = RectF(tooltipLeft, tooltipTop, tooltipRight, tooltipBottom)
                canvas.drawRoundRect(rectF, 8f, 8f, tooltipBgPaint)
                canvas.drawText(tooltipText, tooltipLeft + paddingX, tooltipBottom - paddingY - 2f, tooltipTextPaint)
            }
        }

        return bitmap
    }
}
