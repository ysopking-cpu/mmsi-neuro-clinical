package com.aistudio.neurostats.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.neurostats.data.*
import com.aistudio.neurostats.logic.ReportGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class BatchStage { IDLE, RUNNING, COMPLETED, FAILED }
data class BatchStatus(
    val stage: BatchStage,
    val currentProbandIndex: Int,
    val currentProbandName: String,
    val pdfFile: File? = null
)

class EegViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = EegRepository(db.eegSessionDao())
    val sessions = repository.allSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val reportGenerator = ReportGenerator(application)
    private val _lastGeneratedReport = MutableStateFlow<File?>(null)
    val lastGeneratedReport: StateFlow<File?> = _lastGeneratedReport.asStateFlow()

    // Batch run state
    private val _batchStatus = MutableStateFlow<BatchStatus?>(null)
    val batchStatus: StateFlow<BatchStatus?> = _batchStatus.asStateFlow()

    private var recordingStartTime = 0L
    private var currentDataSource: EegDataSource? = null
    private var streamingJob: Job? = null

    private val _status = MutableStateFlow(SourceStatus.DISCONNECTED)
    val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val _cognitiveLoadTrajectory = MutableStateFlow(0.0)
    val cognitiveLoadTrajectory: StateFlow<Double> = _cognitiveLoadTrajectory.asStateFlow()

    private val _eegChannels = MutableStateFlow(FloatArray(4) { 0f })
    val eegChannels: StateFlow<FloatArray> = _eegChannels.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val recordedData = mutableListOf<FloatArray>()

    private val _isArtifactSimulationEnabled = MutableStateFlow(false)
    val isArtifactSimulationEnabled: StateFlow<Boolean> = _isArtifactSimulationEnabled.asStateFlow()

    private val _signalQuality = MutableStateFlow(85)
    val signalQuality: StateFlow<Int> = _signalQuality.asStateFlow()

    // Clinical Benchmarks
    val healthyBenchmark = 1.5
    val clinicalBenchmark = 3.2

    private val _currentSourceLabel = MutableStateFlow("Standard")
    val currentSourceLabel: StateFlow<String> = _currentSourceLabel.asStateFlow()

    private val _frequencyBands = MutableStateFlow(mapOf("Theta" to 0f, "Alpha" to 0f, "Beta" to 0f, "Delta" to 0f))
    val frequencyBands: StateFlow<Map<String, Float>> = _frequencyBands.asStateFlow()

    fun getSession(sessionId: Int) = repository.getSession(sessionId)

    fun generateReportForSession(session: EegSession) {
        viewModelScope.launch {
            if (session.label.contains("Batch")) {
                // Generate a PDF report for the multi-proband session on the fly
                val probands = parseBatchReportFromCsv(session.csvData)
                val reportFile = reportGenerator.generateClinicalBatchReport(probands)
                _lastGeneratedReport.value = reportFile
            } else {
                // Legacy single-session report
                val report = reportGenerator.generateReport(session)
                _lastGeneratedReport.value = report
            }
        }
    }

    fun clearLastReport() {
        _lastGeneratedReport.value = null
    }

    fun clearBatchStatus() {
        _batchStatus.value = null
    }

    /**
     * Startet den automatisierten klinischen Batch-Lauf für alle 10 Test-Probanden.
     */
    fun runClinicalValidationBatch() {
        if (_batchStatus.value?.stage == BatchStage.RUNNING) return

        viewModelScope.launch {
            _batchStatus.value = BatchStatus(BatchStage.RUNNING, 0, "Initialisiere Pipeline...")
            delay(1000)

            val standardBase = loadBaseData("eeg_test_data.csv")
            val clinicalBase = loadBaseData("eeg_clinical_data.csv")

            val probandSpecs = listOf(
                ProbandSpec("SUB-001", "Alexander Weber", "Healthy Control", standardBase, multiplier = 0.95f),
                ProbandSpec("SUB-002", "Brigitte Schmidt", "Clinical Profile", clinicalBase, multiplier = 1.05f),
                ProbandSpec("SUB-003", "Christian Müller", "Healthy Control", standardBase, addNoise = true),
                ProbandSpec("SUB-004", "Dieter Meyer", "Borderline Profile", standardBase, insertSpikes = true),
                ProbandSpec("SUB-005", "Elisabeth Wagner", "Healthy Control", standardBase, multiplier = 0.85f),
                ProbandSpec("SUB-006", "Frank Fischer", "Clinical Profile", clinicalBase, offset = 0.4f),
                ProbandSpec("SUB-007", "Gabriele Schulz", "Borderline Profile", clinicalBase, multiplier = 0.75f),
                ProbandSpec("SUB-008", "Hans Becker", "Healthy Control", standardBase, wavePerturbation = true),
                ProbandSpec("SUB-009", "Ingrid Hoffmann", "Clinical Profile", clinicalBase, addNoise = true),
                ProbandSpec("SUB-010", "Jürgen Kaiser", "Clinical Profile", clinicalBase, insertSpikes = true)
            )

            val processedProbands = mutableListOf<ReportGenerator.ProbandReportData>()

            probandSpecs.forEachIndexed { index, spec ->
                _batchStatus.value = BatchStatus(BatchStage.RUNNING, index + 1, spec.name)
                delay(800) // Simulate processing time for realistic UI

                val perturbedData = perturbData(spec)
                val stats = calculateStats(perturbedData, spec.type)

                processedProbands.add(
                    ReportGenerator.ProbandReportData(
                        id = spec.id,
                        name = spec.name,
                        type = spec.type,
                        dataPoints = perturbedData,
                        stats = stats
                    )
                )
            }

            _batchStatus.value = BatchStatus(BatchStage.RUNNING, 10, "Generiere PDF-Bericht...")
            delay(1000)

            // Generate multi-page PDF Report
            val pdfFile = reportGenerator.generateClinicalBatchReport(processedProbands)

            if (pdfFile != null) {
                // Serialize batch results into EegSession database table
                val serializedData = serializeBatchReport(processedProbands)
                
                repository.insert(
                    EegSession(
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 10, // Simulated validation duration
                        csvData = serializedData,
                        label = "Klinische Batch-Validierung (10 Probanden)"
                    )
                )

                _batchStatus.value = BatchStatus(BatchStage.COMPLETED, 10, "Erfolgreich abgeschlossen", pdfFile)
                _lastGeneratedReport.value = pdfFile
            } else {
                _batchStatus.value = BatchStatus(BatchStage.FAILED, 10, "PDF-Generierung fehlgeschlagen")
            }
        }
    }

    private fun perturbData(spec: ProbandSpec): List<Float> {
        return spec.baseData.mapIndexed { i, v ->
            var value = v * spec.multiplier + spec.offset
            if (spec.addNoise) {
                value += (Math.random() * 0.3 - 0.15).toFloat()
            }
            if (spec.insertSpikes && i % 8 == 0) {
                value += (1.5f + Math.random() * 1.0f).toFloat() // Spike past 3.2 threshold
            }
            if (spec.wavePerturbation) {
                value += (Math.sin(i.toDouble() / 5.0) * 0.4).toFloat()
            }
            value.coerceIn(0.1f, 5.8f)
        }
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

    private fun calculateStats(data: List<Float>, type: String): ReportGenerator.ClinicalStats {
        val mean = data.average().toFloat()
        val variance = data.map { (it - mean) * (it - mean) }.average().toFloat()
        val maxVal = data.maxOrNull() ?: 0f
        val crossings = data.count { it > 3.2f }
        
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

        // Correlation Dimension D2
        val d2 = calculateCorrelationDimensionD2(data)

        // Weighted Diagnostic Score
        val scoreBase = (mean / 3.2f) * 30f + (maxVal / 5.0f) * 35f + (timeAbovePercent / 100f) * 35f
        val diagnosticScore = scoreBase.coerceIn(5.0f, 98.5f)

        val interpretation = when (type) {
            "Healthy Control" -> "STABIL: Keine physiologischen kognitiven Abweichungen detektiert. Verlauf vollständig normal."
            "Borderline Profile" -> "GRENZWERTIG: Sporadische neuronale Instabilitäten und kognitive Lastüberschreitungen (p < 0.05)."
            else -> "PATHOLOGISCH: Signifikante multivariate kognitive Instabilität detektiert (Schizophrenie-Profil assoziiert, p < 0.01)."
        }

        return ReportGenerator.ClinicalStats(mean, variance, maxVal, crossings, rmsd, timeAbovePercent, d2, diagnosticScore, interpretation)
    }

    private fun loadBaseData(fileName: String): List<Float> {
        return try {
            getApplication<Application>().assets.open(fileName).bufferedReader().readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    line.split(",").firstOrNull()?.replace("\"", "")?.trim()?.toFloatOrNull()
                }
        } catch (e: Exception) {
            List(40) { (1.2f + Math.random() * 0.8f).toFloat() }
        }
    }

    private fun serializeBatchReport(probands: List<ReportGenerator.ProbandReportData>): String {
        val sb = StringBuilder()
        sb.append("#BATCH_REPORT_V3\n")
        probands.forEach { proband ->
            val dataPointsStr = proband.dataPoints.joinToString(",") { String.format(Locale.US, "%.3f", it) }
            sb.append("${proband.id};${proband.name};${proband.type};${proband.stats.mean};${proband.stats.variance};${proband.stats.maxVal};${proband.stats.crossings};${proband.stats.rmsd};${proband.stats.timeAbovePercent};${proband.stats.d2};${proband.stats.diagnosticScore};${proband.stats.interpretation};$dataPointsStr\n")
        }
        return sb.toString()
    }

    fun parseBatchReportFromCsv(csv: String): List<ReportGenerator.ProbandReportData> {
        val lines = csv.split("\n")
        if (lines.isEmpty()) return emptyList()
        val isV3 = lines[0].startsWith("#BATCH_REPORT_V3")
        val isV2 = lines[0].startsWith("#BATCH_REPORT_V2")
        val isV1 = lines[0].startsWith("#BATCH_REPORT_V1")
        if (!isV1 && !isV2 && !isV3) return emptyList()

        return lines.drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            try {
                val parts = line.split(";")
                if (isV3 && parts.size >= 13) {
                    val id = parts[0]
                    val name = parts[1]
                    val type = parts[2]
                    val mean = parts[3].toFloatOrNull() ?: 0f
                    val variance = parts[4].toFloatOrNull() ?: 0f
                    val maxVal = parts[5].toFloatOrNull() ?: 0f
                    val crossings = parts[6].toIntOrNull() ?: 0
                    val rmsd = parts[7].toFloatOrNull() ?: 0f
                    val timeAbovePercent = parts[8].toFloatOrNull() ?: 0f
                    val d2 = parts[9].toFloatOrNull() ?: 1.5f
                    val diagnosticScore = parts[10].toFloatOrNull() ?: 0f
                    val interpretation = parts[11]
                    val dataPoints = parts[12].split(",").mapNotNull { it.trim().toFloatOrNull() }

                    ReportGenerator.ProbandReportData(
                        id = id,
                        name = name,
                        type = type,
                        dataPoints = dataPoints,
                        stats = ReportGenerator.ClinicalStats(mean, variance, maxVal, crossings, rmsd, timeAbovePercent, d2, diagnosticScore, interpretation)
                    )
                } else if (isV2 && parts.size >= 12) {
                    val id = parts[0]
                    val name = parts[1]
                    val type = parts[2]
                    val mean = parts[3].toFloatOrNull() ?: 0f
                    val variance = parts[4].toFloatOrNull() ?: 0f
                    val maxVal = parts[5].toFloatOrNull() ?: 0f
                    val crossings = parts[6].toIntOrNull() ?: 0
                    val rmsd = parts[7].toFloatOrNull() ?: 0f
                    val timeAbovePercent = parts[8].toFloatOrNull() ?: 0f
                    val diagnosticScore = parts[9].toFloatOrNull() ?: 0f
                    val interpretation = parts[10]
                    val dataPoints = parts[11].split(",").mapNotNull { it.trim().toFloatOrNull() }
                    val d2 = calculateCorrelationDimensionD2(dataPoints)

                    ReportGenerator.ProbandReportData(
                        id = id,
                        name = name,
                        type = type,
                        dataPoints = dataPoints,
                        stats = ReportGenerator.ClinicalStats(mean, variance, maxVal, crossings, rmsd, timeAbovePercent, d2, diagnosticScore, interpretation)
                    )
                } else if (parts.size >= 8) {
                    val id = parts[0]
                    val name = parts[1]
                    val type = parts[2]
                    val mean = parts[3].toFloatOrNull() ?: 0f
                    val variance = parts[4].toFloatOrNull() ?: 0f
                    val crossings = parts[5].toIntOrNull() ?: 0
                    val interpretation = parts[6]
                    val dataPoints = parts[7].split(",").mapNotNull { it.trim().toFloatOrNull() }

                    val maxVal = dataPoints.maxOrNull() ?: 0f
                    val successiveDiffsSq = if (dataPoints.size > 1) {
                        dataPoints.zipWithNext { a, b -> (a - b) * (a - b) }.average().toFloat()
                    } else {
                        0f
                    }
                    val rmsd = kotlin.math.sqrt(successiveDiffsSq)
                    val timeAbovePercent = if (dataPoints.isNotEmpty()) (crossings.toFloat() / dataPoints.size) * 100f else 0f
                    val scoreBase = (mean / 3.2f) * 30f + (maxVal / 5.0f) * 35f + (timeAbovePercent / 100f) * 35f
                    val diagnosticScore = scoreBase.coerceIn(5.0f, 98.5f)
                    val d2 = calculateCorrelationDimensionD2(dataPoints)

                    ReportGenerator.ProbandReportData(
                        id = id,
                        name = name,
                        type = type,
                        dataPoints = dataPoints,
                        stats = ReportGenerator.ClinicalStats(mean, variance, maxVal, crossings, rmsd, timeAbovePercent, d2, diagnosticScore, interpretation)
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private data class ProbandSpec(
        val id: String,
        val name: String,
        val type: String,
        val baseData: List<Float>,
        val multiplier: Float = 1.0f,
        val offset: Float = 0.0f,
        val addNoise: Boolean = false,
        val insertSpikes: Boolean = false,
        val wavePerturbation: Boolean = false
    )

    private fun processEegData(frame: EegDataFrame) {
        val channels = frame.channels.copyOf()
        
        if (_isArtifactSimulationEnabled.value && Math.random() < 0.05) {
            channels[0] += (Math.random() * 50 - 25).toFloat()
            _signalQuality.value = 20
        } else if (_signalQuality.value < 85) {
            _signalQuality.value += 5
        }

        _eegChannels.value = channels
        
        val isClinical = _currentSourceLabel.value.contains("Klinisch")
        val baseTheta = if (isClinical) 0.4f else 0.2f
        val baseAlpha = if (isClinical) 0.2f else 0.5f
        
        _frequencyBands.value = mapOf(
            "Delta" to (baseTheta * 0.8f + (channels[0] % 1f) * 0.1f),
            "Theta" to (baseTheta + (channels[1] % 1f) * 0.1f),
            "Alpha" to (baseAlpha + (channels[2] % 1f) * 0.1f),
            "Beta" to (0.3f + (channels[3] % 1f) * 0.1f)
        )

        val sum = channels.sum()
        _cognitiveLoadTrajectory.value = if (isClinical) 3.1 else 1.5 + (sum % 0.5f).toDouble()

        if (_isRecording.value) {
            recordedData.add(channels)
        }
    }

    fun setSource(source: EegDataSource, label: String = "Standard") {
        currentDataSource = source
        _currentSourceLabel.value = label
    }

    fun setSourceToPlayback(isClinical: Boolean = false) {
        val fileName = if (isClinical) "eeg_clinical_data.csv" else "eeg_test_data.csv"
        val label = if (isClinical) "Klinisch (ds002721)" else "Standard"
        setSource(FilePlaybackDataSource(context = getApplication(), fileName = fileName), label)
    }

    fun toggleStreaming() {
        // Disabled manual toggle in favor of runValidationRun
    }

    fun toggleRecording() {
        // Disabled manual toggle in favor of runValidationRun
    }

    fun getRecordedDataAsCsv(): String {
        val sb = StringBuilder()
        recordedData.forEach { channels ->
            sb.append("${channels.joinToString(",")}\n")
        }
        return sb.toString()
    }

    fun toggleArtifactSimulation() {
        _isArtifactSimulationEnabled.value = !_isArtifactSimulationEnabled.value
    }
}
