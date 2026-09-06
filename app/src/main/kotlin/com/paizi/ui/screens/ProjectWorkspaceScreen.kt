package com.paizi.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paizi.database.ProjectEntity
import com.paizi.di.DIModule
import com.paizi.workflow.WorkflowEngine
import kotlinx.coroutines.launch

@Composable
fun ProjectWorkspaceScreen(
    onNavigateToFileExplorer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val projectManager = DIModule.projectManager
    val workflowEngine = DIModule.workflowEngine

    val allProjects by projectManager.getAllProjects().collectAsState(initial = emptyList())
    val activeProject by projectManager.activeProjectFlow.collectAsState()
    val workflowState by workflowEngine.workflowState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectConcept by remember { mutableStateOf("") }
    var newProjectType by remember { mutableStateOf("Android Compose") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Project Workspace",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "End-to-End Autonomous Software Engineering",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_project_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Project")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Project Card
            item {
                if (activeProject != null) {
                    ActiveProjectCard(
                        project = activeProject!!,
                        workflowState = workflowState,
                        onStartPlanning = {
                            coroutineScope.launch {
                                workflowEngine.startPlanning(activeProject!!, activeProject!!.concept)
                            }
                        },
                        onApproveBlueprint = {
                            coroutineScope.launch {
                                workflowEngine.handleUserApproval(activeProject!!, approved = true)
                            }
                        },
                        onRejectBlueprint = {
                            coroutineScope.launch {
                                workflowEngine.handleUserApproval(activeProject!!, approved = false, "Rejected by user")
                            }
                        },
                        onExportZip = {
                            coroutineScope.launch {
                                val zip = projectManager.exportProjectZip(activeProject!!)
                                Toast.makeText(context, "Exported: ${zip.name}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onOpenFiles = onNavigateToFileExplorer
                    )
                } else {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Active Project Selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Select an existing project below or create a new one to begin.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Projects List Section
            item {
                Text(
                    text = "All Projects (${allProjects.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (allProjects.isEmpty()) {
                item {
                    Text(
                        text = "No projects found in workspace database.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(allProjects, key = { it.id }) { proj ->
                    ProjectListItem(
                        project = proj,
                        isActive = proj.id == activeProject?.id,
                        onSelect = {
                            coroutineScope.launch { projectManager.selectProject(proj) }
                        },
                        onDelete = {
                            coroutineScope.launch { projectManager.deleteProject(proj) }
                        }
                    )
                }
            }
        }
    }

    // Create Project Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
                    )
                    OutlinedTextField(
                        value = newProjectConcept,
                        onValueChange = { newProjectConcept = it },
                        label = { Text("Software Concept & Requirements") },
                        modifier = Modifier.fillMaxWidth().testTag("new_project_concept_input"),
                        minLines = 3,
                        maxLines = 6
                    )
                    OutlinedTextField(
                        value = newProjectType,
                        onValueChange = { newProjectType = it },
                        label = { Text("Project Type") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank() && newProjectConcept.isNotBlank()) {
                            coroutineScope.launch {
                                projectManager.createProject(newProjectName, newProjectConcept, newProjectType)
                                newProjectName = ""
                                newProjectConcept = ""
                                showCreateDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_project_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ActiveProjectCard(
    project: ProjectEntity,
    workflowState: WorkflowEngine.WorkflowState,
    onStartPlanning: () -> Unit,
    onApproveBlueprint: () -> Unit,
    onRejectBlueprint: () -> Unit,
    onExportZip: () -> Unit,
    onOpenFiles: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_project_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Type: ${project.type} | ID: ${project.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Surface(
                    color = when (project.status) {
                        "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer
                        "APPROVED" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = project.status,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Concept & Requirement:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = project.concept,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Workflow Stage Tracker
            Text(
                text = "Autonomous Engineering Workflow Stage: ${workflowState.currentStep.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (workflowState.currentStep in listOf(
                            WorkflowEngine.WorkflowStep.PLANNING_BLUEPRINT,
                            WorkflowEngine.WorkflowStep.CODING_IMPLEMENTATION,
                            WorkflowEngine.WorkflowStep.BUILDING,
                            WorkflowEngine.WorkflowStep.TESTING,
                            WorkflowEngine.WorkflowStep.DEBUGGING_FIX_LOOP
                        )) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                    } else if (workflowState.currentStep == WorkflowEngine.WorkflowStep.COMPLETED_SUCCESS) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = workflowState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(12.dp))

            if (workflowState.currentStep == WorkflowEngine.WorkflowStep.IDLE) {
                Button(
                    onClick = onStartPlanning,
                    modifier = Modifier.fillMaxWidth().testTag("start_planning_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1. Start AI Architectural Planning")
                }
            }

            // Approval Gate Card
            if (workflowState.currentStep == WorkflowEngine.WorkflowStep.AWAITING_APPROVAL && workflowState.currentBlueprint != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "USER APPROVAL REQUIRED (Gate Step)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "AI Architect has generated the engineering blueprint. Review below before code generation is authorized.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Blueprint Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = workflowState.currentBlueprint ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onApproveBlueprint,
                                modifier = Modifier.weight(1f).testTag("approve_blueprint_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Approve & Build")
                            }
                            OutlinedButton(
                                onClick = onRejectBlueprint,
                                modifier = Modifier.weight(1f).testTag("reject_blueprint_button")
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenFiles,
                    modifier = Modifier.weight(1f).testTag("open_files_button")
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Browse Files")
                }

                OutlinedButton(
                    onClick = onExportZip,
                    modifier = Modifier.weight(1f).testTag("export_zip_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export ZIP")
                }
            }
        }
    }
}

@Composable
fun ProjectListItem(
    project: ProjectEntity,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_item_${project.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${project.type} • Status: ${project.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isActive) {
                Button(onClick = onSelect) {
                    Text("Select")
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Active",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Delete Project", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
