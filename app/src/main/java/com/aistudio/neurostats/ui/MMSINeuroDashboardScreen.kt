package com.aistudio.neurostats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import com.aistudio.neurostats.data.SourceStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MMSINeuroDashboardScreen(
    viewModel: EegViewModel = viewModel(),
    onNavigateToSession: (Int) -> Unit
) {
    val batchStatus by viewModel.batchStatus.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val context = LocalContext.current
    var showStudyDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MMSI Neuro Stats - Validierungs-Plattform", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Platform introduction
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MMSI v3.6.3 Validierungs-Pipeline", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Diese Plattform validiert die statistischen Trajektorien-Abweichungen W(t) der 10 klinischen Test-Probanden der Studie ds002721 (Schizophrenie-Profile vs. gesunde Kontrollgruppe).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showStudyDialog = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Studien-Details anzeigen (ds002721)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Real-time Batch Progress Panel
            batchStatus?.let { status ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when(status.stage) {
                            BatchStage.COMPLETED -> Color(240, 253, 244)
                            BatchStage.FAILED -> Color(254, 242, 242)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = when(status.stage) {
                                BatchStage.RUNNING -> "Batch-Validierung läuft..."
                                BatchStage.COMPLETED -> "Validierung Erfolgreich Abgeschlossen!"
                                BatchStage.FAILED -> "Validierung Fehlgeschlagen"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = when(status.stage) {
                                BatchStage.COMPLETED -> Color(21, 128, 61)
                                BatchStage.FAILED -> Color(185, 28, 28)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (status.stage == BatchStage.RUNNING) {
                            val progress = status.currentProbandIndex / 10f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Verarbeite Proband ${status.currentProbandIndex}/10: ${status.currentProbandName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (status.stage == BatchStage.COMPLETED) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Fertig", tint = Color(34, 197, 94))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Bericht generiert: MMSI_Clinical_Batch_Report.pdf", style = MaterialTheme.typography.bodySmall, color = Color(21, 128, 61))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    status.pdfFile?.let { file ->
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Konsolidierten Bericht teilen"))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(34, 197, 94)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Konsolidierten PDF-Bericht Teilen")
                            }
                        }
                    }
                }
            }

            // Primary Validation Trigger Button
            Button(
                onClick = { viewModel.runClinicalValidationBatch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(64.dp),
                enabled = batchStatus?.stage != BatchStage.RUNNING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (batchStatus?.stage == BatchStage.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Analysiere Test-Probanden...")
                } else {
                    Text("10 Probanden Batch-Validierung starten", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // History
            Text("Klinische Berichts-Historie", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Noch keine Berichte generiert. Starte die Pipeline oben.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Column(modifier = Modifier.heightIn(max = 600.dp)) {
                    sessions.forEach { session ->
                        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onNavigateToSession(session.id) }
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(session.label, style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Befund-Zeitpunkt: $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showStudyDialog) {
        AlertDialog(
            onDismissRequest = { showStudyDialog = false },
            title = { Text("Studie ds002721 (Schizophrenie)") },
            text = { Text("MMSI (Multivariate Multi-Scale Instabilities) v3.6.3 analysiert die kognitiven Lasttrajektorien W(t) der Probanden. Ein W(t) Wert über dem klinischen Grenzwert von 3.2 weist auf erhöhte neuronale Instabilitäten und kognitive Systemlast hin. Dieser Test führt alle 10 Referenz-Probanden vollautomatisch durch die klinische Auswertungs-Pipeline.") },
            confirmButton = { TextButton(onClick = { showStudyDialog = false }) { Text("OK") } }
        )
    }
}
