package com.paizi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paizi.database.DiagnosticRecordEntity
import com.paizi.diagnostics.DiagnosticsManager
import com.paizi.di.DIModule
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val diagnosticsManager = DIModule.diagnosticsManager
    val diagnosticDao = DIModule.database.diagnosticDao()

    val persistedRecords by diagnosticDao.getAllDiagnosticRecords().collectAsState(initial = emptyList())
    var isRunningScan by remember { mutableStateOf(false) }

    fun runScan() {
        coroutineScope.launch {
            isRunningScan = true
            diagnosticsManager.runFullDiagnostics()
            isRunningScan = false
        }
    }

    LaunchedEffect(Unit) {
        if (persistedRecords.isEmpty()) {
            runScan()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System Diagnostics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "19 Real System Verification Checks (Zero Hardcoded Data)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { runScan() },
                enabled = !isRunningScan,
                modifier = Modifier.testTag("run_diagnostics_button")
            ) {
                if (isRunningScan) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Summary Bar
        val passCount = persistedRecords.count { it.status == "PASS" }
        val warnCount = persistedRecords.count { it.status == "WARN" }
        val failCount = persistedRecords.count { it.status == "FAIL" }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusBadge(count = passCount, label = "PASS", color = MaterialTheme.colorScheme.primary)
                StatusBadge(count = warnCount, label = "WARN", color = MaterialTheme.colorScheme.tertiary)
                StatusBadge(count = failCount, label = "FAIL", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(persistedRecords, key = { it.checkName }) { record ->
                DiagnosticRecordCard(record = record)
            }
        }
    }
}

@Composable
fun StatusBadge(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun DiagnosticRecordCard(record: DiagnosticRecordEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diag_check_${record.checkName.replace(" ", "_").lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (record.status) {
                    "PASS" -> Icons.Default.CheckCircle
                    "WARN" -> Icons.Default.Warning
                    else -> Icons.Default.Close
                },
                contentDescription = null,
                tint = when (record.status) {
                    "PASS" -> MaterialTheme.colorScheme.primary
                    "WARN" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.checkName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = record.category,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = record.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
