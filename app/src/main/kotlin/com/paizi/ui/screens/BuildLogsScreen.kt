package com.paizi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paizi.database.BuildLogEntity
import com.paizi.database.TestResultEntity
import com.paizi.di.DIModule
import kotlinx.coroutines.launch

@Composable
fun BuildLogsScreen(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val buildSystemManager = DIModule.buildSystemManager
    val testingSystemManager = DIModule.testingSystemManager
    val activeProject by DIModule.projectManager.activeProjectFlow.collectAsState()

    val testResults by DIModule.database.testDao().getAllTestResults().collectAsState(initial = emptyList())
    var latestBuildOutcome by remember { mutableStateOf<com.paizi.build.BuildSystemManager.BuildOutcome?>(null) }
    var isBuilding by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

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
                    text = "Build & Test Evidence",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Real Gradle builds and local JVM test execution results",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isTesting = true
                            testingSystemManager.runBuiltinTestSuite(activeProject?.id ?: "global")
                            isTesting = false
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.testTag("run_tests_button")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test")
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isBuilding = true
                            latestBuildOutcome = buildSystemManager.executeBuild(activeProject?.id ?: "global")
                            isBuilding = false
                        }
                    },
                    enabled = !isBuilding,
                    modifier = Modifier.testTag("trigger_build_button")
                ) {
                    if (isBuilding) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Build")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Build Output Logs") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Test Evidence (${testResults.size})") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // Build Logs
            ElevatedCard(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (latestBuildOutcome != null) {
                            "Status: ${if (latestBuildOutcome!!.success) "SUCCESS (Exit Code 0)" else "FAILED (Exit Code ${latestBuildOutcome!!.exitCode})"}"
                        } else {
                            "Build Engine Ready. Tap 'Build' to execute Gradle assembleDebug."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (latestBuildOutcome?.success == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = latestBuildOutcome?.logs ?: "// Gradle execution console will appear here...",
                            color = Color(0xFFD4D4D4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            // Test Evidence
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (testResults.isEmpty()) {
                    item {
                        Text("No test runs recorded yet. Tap 'Test' to run the verification suite.")
                    }
                }

                items(testResults, key = { it.id }) { item ->
                    TestEvidenceCard(item)
                }
            }
        }
    }
}

@Composable
fun TestEvidenceCard(test: TestResultEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = test.testName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = if (test.result == "PASS") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = test.result,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (test.result == "PASS") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Input: ${test.inputData}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Expected: ${test.expectedOutput} | Actual: ${test.actualOutput}", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "Duration: ${test.durationMs}ms | Env: ${test.environment}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
