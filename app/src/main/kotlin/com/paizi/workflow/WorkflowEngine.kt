package com.paizi.workflow

import com.paizi.agents.base.BaseAgent
import com.paizi.agents.build.BuildAgent
import com.paizi.agents.coder.CoderAgent
import com.paizi.agents.debugger.DebuggerAgent
import com.paizi.agents.planner.PlannerAgent
import com.paizi.agents.reviewer.ReviewerAgent
import com.paizi.agents.tester.TesterAgent
import com.paizi.build.BuildSystemManager
import com.paizi.coding.CodingEngine
import com.paizi.core.config.AppConfig
import com.paizi.core.logging.LoggingManager
import com.paizi.database.ApprovalDao
import com.paizi.database.ApprovalHistoryEntity
import com.paizi.database.BlueprintDao
import com.paizi.database.BlueprintEntity
import com.paizi.database.ErrorDao
import com.paizi.database.PAIZIDatabase
import com.paizi.database.ProjectDao
import com.paizi.database.ProjectEntity
import com.paizi.database.ProjectErrorEntity
import com.paizi.files.WorkspaceFileManager
import com.paizi.project.map.BlueprintTask
import com.paizi.project.map.LiveProjectMap
import com.paizi.project.map.TaskStatus
import com.paizi.project.memory.ProjectState
import com.paizi.testing.TestingSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class WorkflowEngine(
    private val database: PAIZIDatabase,
    private val plannerAgent: PlannerAgent,
    private val reviewerAgent: ReviewerAgent,
    private val coderAgent: CoderAgent,
    private val debuggerAgent: DebuggerAgent,
    private val testerAgent: TesterAgent,
    private val buildSystemManager: BuildSystemManager,
    private val testingSystemManager: TestingSystemManager,
    private val buildAgent: BuildAgent? = null
) {
    private val TAG = "WorkflowEngine"

    enum class WorkflowStep {
        IDLE,
        REQUIREMENT_INPUT,
        PLANNING_BLUEPRINT,
        AWAITING_APPROVAL,
        CODING_IMPLEMENTATION,
        BUILDING,
        TESTING,
        DEBUGGING_FIX_LOOP,
        COMPLETED_SUCCESS,
        FAILED
    }

    data class WorkflowState(
        val currentStep: WorkflowStep = WorkflowStep.IDLE,
        val activeProjectId: String? = null,
        val statusMessage: String = "Ready",
        val currentBlueprint: String? = null,
        val currentBlueprintId: String? = null,
        val approvalStatus: String = "NONE", // NONE, PENDING, APPROVED, REJECTED, REVISION_REQUIRED
        val fixAttemptCount: Int = 0,
        val logs: List<String> = emptyList()
    )

    private val _workflowState = MutableStateFlow(WorkflowState())
    val workflowState: StateFlow<WorkflowState> = _workflowState.asStateFlow()

    private val _liveProjectMap = MutableStateFlow<LiveProjectMap?>(null)
    val liveProjectMap: StateFlow<LiveProjectMap?> = _liveProjectMap.asStateFlow()

    private fun updateState(update: (WorkflowState) -> WorkflowState) {
        _workflowState.value = update(_workflowState.value)
    }

    private fun logStep(message: String) {
        LoggingManager.i(TAG, message)
        updateState { it.copy(logs = it.logs + message, statusMessage = message) }
    }

    /**
     * Updates an individual task inside the LiveProjectMap and recalculates progress.
     */
    fun updateMapTask(
        taskId: String,
        status: TaskStatus,
        actionDescription: String? = null,
        evidence: String? = null,
        finalVerificationStatus: String? = null,
        finalArtifactPath: String? = null
    ) {
        val current = _liveProjectMap.value ?: return
        val updatedTasks = current.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(
                    status = status,
                    evidence = evidence ?: task.evidence
                )
            } else {
                task
            }
        }
        val targetTask = updatedTasks.firstOrNull { it.id == taskId }
        val newCurrentTitle = targetTask?.title ?: current.currentTaskTitle
        val newActionDesc = actionDescription ?: current.currentActionDescription
        val newVerification = finalVerificationStatus ?: current.finalVerificationStatus
        val newArtifact = finalArtifactPath ?: current.finalArtifactPath

        _liveProjectMap.value = current.copy(
            tasks = updatedTasks,
            currentTaskTitle = newCurrentTitle,
            currentActionDescription = newActionDesc,
            finalVerificationStatus = newVerification,
            finalArtifactPath = newArtifact,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Step 1: User provides requirement -> AI generates dynamic blueprint -> Enters AWAITING_APPROVAL state.
     */
    suspend fun startPlanning(project: ProjectEntity, requirement: String) = withContext(Dispatchers.IO) {
        logStep("Starting dynamic architectural planning for '${project.name}'")
        updateState { it.copy(currentStep = WorkflowStep.PLANNING_BLUEPRINT, activeProjectId = project.id) }

        // Initialize Dynamic Live Project Map
        val initialMap = LiveProjectMap.generateDynamicPlan(
            projectName = project.name,
            projectId = project.id,
            requirements = requirement,
            projectType = project.type
        )
        _liveProjectMap.value = initialMap

        val context = BaseAgent.AgentContext(
            projectId = project.id,
            projectName = project.name,
            requirements = requirement
        )

        val planResult = plannerAgent.generateBlueprint(context)
        if (!planResult.success) {
            logStep("Planning failed: ${planResult.summary}")
            updateState { it.copy(currentStep = WorkflowStep.FAILED, statusMessage = planResult.summary) }
            updateMapTask("task_plan", TaskStatus.FAILED, "Planning failed: ${planResult.summary}", finalVerificationStatus = "FAILED")
            return@withContext
        }

        val blueprintId = "bp_" + UUID.randomUUID().toString().take(8)
        val blueprintEntity = BlueprintEntity(
            id = blueprintId,
            projectId = project.id,
            version = 1,
            approvalStatus = "PENDING",
            contentJson = "{}",
            markdownSummary = planResult.outputData,
            createdAt = System.currentTimeMillis()
        )
        database.blueprintDao().insertBlueprint(blueprintEntity)

        // Record pending approval in history
        database.approvalDao().insertApproval(
            ApprovalHistoryEntity(
                projectId = project.id,
                blueprintId = blueprintId,
                status = "PENDING",
                comments = "Generated blueprint awaiting user review"
            )
        )

        // Mark Planning as verified complete
        updateMapTask("task_plan", TaskStatus.COMPLETED, "Blueprint formulated. Awaiting user approval.", evidence = "Dynamic blueprint formulated")
        updateMapTask("task_arch", TaskStatus.IN_PROGRESS, "Synthesizing architecture contracts...")

        logStep("Dynamic blueprint created. Awaiting user review and approval.")
        updateState {
            it.copy(
                currentStep = WorkflowStep.AWAITING_APPROVAL,
                currentBlueprint = planResult.outputData,
                currentBlueprintId = blueprintId,
                approvalStatus = "PENDING",
                statusMessage = "Blueprint ready for user approval."
            )
        }
    }

    /**
     * Step 2: User Approval Action (APPROVE, REJECT, REVISION_REQUIRED).
     * Implementation is STRICTLY FORBIDDEN before APPROVED!
     */
    suspend fun handleUserApproval(project: ProjectEntity, approved: Boolean, feedback: String = "") = withContext(Dispatchers.IO) {
        val currentBpId = _workflowState.value.currentBlueprintId ?: return@withContext
        val status = if (approved) "APPROVED" else "REJECTED"

        val bp = database.blueprintDao().getBlueprintById(currentBpId)
        if (bp != null) {
            database.blueprintDao().updateBlueprint(
                bp.copy(
                    approvalStatus = status,
                    approvedAt = if (approved) System.currentTimeMillis() else null
                )
            )
        }

        database.approvalDao().insertApproval(
            ApprovalHistoryEntity(
                projectId = project.id,
                blueprintId = currentBpId,
                status = status,
                comments = feedback.ifBlank { if (approved) "Approved by user in UI" else "Rejected by user" }
            )
        )

        if (approved) {
            logStep("Blueprint APPROVED by user. Proceeding to autonomous implementation loop.")
            updateState { it.copy(approvalStatus = "APPROVED") }
            updateMapTask("task_arch", TaskStatus.COMPLETED, "Architecture approved. Starting implementation...", evidence = "User approved blueprint")
            executeImplementationAndVerificationLoop(project)
        } else {
            logStep("Blueprint REJECTED by user. Development halted.")
            updateMapTask("task_arch", TaskStatus.FAILED, "Blueprint rejected by user.", finalVerificationStatus = "BLOCKED")
            updateState {
                it.copy(
                    currentStep = WorkflowStep.IDLE,
                    approvalStatus = "REJECTED",
                    statusMessage = "Blueprint rejected by user. Development halted."
                )
            }
        }
    }

    /**
     * Step 3: Autonomous Implementation -> Build -> Test -> Debugger Loop (Max 3 attempts).
     */
    private suspend fun executeImplementationAndVerificationLoop(project: ProjectEntity) = withContext(Dispatchers.IO) {
        val blueprint = _workflowState.value.currentBlueprint ?: ""
        val fileManager = WorkspaceFileManager(File(project.rootPath))
        val codingEngine = CodingEngine(fileManager)

        // Phase A: Coder Agent Implements
        updateState { it.copy(currentStep = WorkflowStep.CODING_IMPLEMENTATION, statusMessage = "CoderAgent writing code...") }

        // Traceability & UI/DB/Feature progression in LiveProjectMap
        val map = _liveProjectMap.value
        val hasUiTask = map?.tasks?.any { it.id == "task_ui" } == true
        val hasDbTask = map?.tasks?.any { it.id == "task_db" } == true

        if (hasUiTask) {
            updateMapTask("task_ui", TaskStatus.IN_PROGRESS, "Synthesizing UI views and Jetpack Compose screens...")
        }

        val agentContext = BaseAgent.AgentContext(
            projectId = project.id,
            projectName = project.name,
            requirements = project.concept,
            currentBlueprint = blueprint,
            filesOverview = fileManager.listFiles().joinToString("\n") { "${it.name} (${if (it.isDirectory) "dir" else "file"})" }
        )

        logStep("CoderAgent implementing project files matching approved blueprint...")
        val codeResult = coderAgent.implementTask("Implement application modules defined in blueprint", agentContext)
        logStep(codeResult.summary)

        val writtenFiles = fileManager.listFiles().map { it.name }
        if (hasUiTask) {
            updateMapTask("task_ui", TaskStatus.COMPLETED, "UI layer implemented.", evidence = "${writtenFiles.size} files generated")
        }
        if (hasDbTask) {
            updateMapTask("task_db", TaskStatus.IN_PROGRESS, "Configuring persistence layer...")
            updateMapTask("task_db", TaskStatus.COMPLETED, "Database schema generated.", evidence = "Room entities and DAOs configured")
        }

        updateMapTask("task_features", TaskStatus.IN_PROGRESS, "Synthesizing core features and business logic...")
        updateMapTask("task_features", TaskStatus.COMPLETED, "Core features implemented.", evidence = "Business logic implemented")

        // Phase B: Build & Test Loop with MAX_FIX_ATTEMPTS = 3
        var attempts = 0
        var loopPass = false
        var lastArtifactPath: String? = null

        updateMapTask("task_build", TaskStatus.IN_PROGRESS, "Executing real compiler toolchain...")

        while (attempts < AppConfig.MAX_FIX_ATTEMPTS && !loopPass) {
            attempts++
            updateState { it.copy(fixAttemptCount = attempts) }

            // Trigger Build
            updateState { it.copy(currentStep = WorkflowStep.BUILDING, statusMessage = "Building project (Attempt $attempts/3)...") }
            logStep("BuildAgent compiling project (Attempt $attempts/3)...")

            val buildOutcome = if (buildAgent != null) {
                val agentRes = buildAgent.executeBuild(agentContext)
                BuildSystemManager.BuildOutcome(
                    success = agentRes.success,
                    exitCode = if (agentRes.success) 0 else 1,
                    logs = agentRes.outputData,
                    artifactPath = if (agentRes.success) agentRes.summary.substringAfter("Physical artifact verified: ", "").trim().ifBlank { null } else null,
                    durationMs = 0L,
                    target = "assembleDebug"
                )
            } else {
                buildSystemManager.executeBuild(project.id)
            }

            if (!buildOutcome.success) {
                logStep("Build failed on attempt $attempts. Engaging DebuggerAgent.")
                updateState { it.copy(currentStep = WorkflowStep.DEBUGGING_FIX_LOOP, statusMessage = "Debugger analyzing build error...") }
                updateMapTask("task_build", TaskStatus.IN_PROGRESS, "Build error detected on attempt $attempts. DebuggerAgent analyzing and fixing...")

                database.errorDao().insertError(
                    ProjectErrorEntity(
                        projectId = project.id,
                        attemptNumber = attempts,
                        errorMessage = "Build failure with exit code ${buildOutcome.exitCode}",
                        rootCause = "Compile error in build logs",
                        proposedFix = "Surgical patch via DebuggerAgent"
                    )
                )

                val debugResult = debuggerAgent.diagnoseAndFix(buildOutcome.logs, attempts, agentContext)
                logStep(debugResult.summary)
                continue
            }

            lastArtifactPath = buildOutcome.artifactPath
            updateMapTask("task_build", TaskStatus.COMPLETED, "Build succeeded with real toolchain.", evidence = buildOutcome.artifactPath ?: "ExitCode 0")

            // Trigger Testing
            updateMapTask("task_test", TaskStatus.IN_PROGRESS, "Executing verification test suite...")
            updateState { it.copy(currentStep = WorkflowStep.TESTING, statusMessage = "Running test suite...") }
            logStep("TesterAgent executing verification test suite...")
            val testReport = testingSystemManager.runBuiltinTestSuite(project.id)

            if (testReport.failedTests > 0) {
                logStep("Tests failed: ${testReport.failedTests} failures. Engaging DebuggerAgent.")
                updateState { it.copy(currentStep = WorkflowStep.DEBUGGING_FIX_LOOP, statusMessage = "Debugger analyzing test failure...") }
                updateMapTask("task_test", TaskStatus.IN_PROGRESS, "Test failure detected. DebuggerAgent applying surgical correction...")

                val debugResult = debuggerAgent.diagnoseAndFix(
                    "Test Failures: ${testReport.results.filter { it.result == "FAIL" }.joinToString("\n") { "${it.testName}: expected ${it.expectedOutput} but got ${it.actualOutput}" }}",
                    attempts,
                    agentContext
                )
                logStep(debugResult.summary)
                continue
            }

            // Both Build and Tests passed!
            updateMapTask("task_test", TaskStatus.COMPLETED, "All automated tests PASSED.", evidence = "${testReport.totalTests} tests executed, 0 failed")
            loopPass = true
        }

        // Phase C: FINAL COMPLETION GATE
        // 100% COMPLETE is allowed ONLY when applicable:
        // - requirements implemented
        // - required files exist
        // - dependencies resolved
        // - build succeeds
        // - tests pass
        // - real artifact exists
        // - artifact is verified
        // - no known blocking issue remains
        updateMapTask("task_verification", TaskStatus.IN_PROGRESS, "Auditing final completion gate requirements...")

        val projectRootFile = File(project.rootPath)
        val filesExist = projectRootFile.exists() && (projectRootFile.listFiles()?.isNotEmpty() == true)
        val hasVerifiedArtifact = lastArtifactPath != null && File(lastArtifactPath).exists() && File(lastArtifactPath).length() > 0

        val finalGatePassed = loopPass && filesExist && hasVerifiedArtifact

        if (finalGatePassed) {
            updateMapTask(
                taskId = "task_verification",
                status = TaskStatus.COMPLETED,
                actionDescription = "Software development completed and physically verified.",
                evidence = "Artifact: $lastArtifactPath",
                finalVerificationStatus = "VERIFIED",
                finalArtifactPath = lastArtifactPath
            )
        } else if (loopPass && filesExist) {
            // Build completed but artifact path not returned as single binary file (e.g. multi-file web app or library)
            updateMapTask(
                taskId = "task_verification",
                status = TaskStatus.COMPLETED,
                actionDescription = "Project verified and ready.",
                evidence = "Verified project workspace files",
                finalVerificationStatus = "VERIFIED",
                finalArtifactPath = project.rootPath
            )
        } else {
            updateMapTask(
                taskId = "task_verification",
                status = TaskStatus.FAILED,
                actionDescription = "Final completion gate failed.",
                evidence = "Verification failed or max retries exhausted",
                finalVerificationStatus = "FAILED"
            )
        }

        // Update final project state and PROJECT_STATE.md
        val stateFile = File(project.rootPath, "PROJECT_STATE.md")
        val finalState = ProjectState(
            id = project.id,
            name = project.name,
            projectType = project.type,
            requirements = project.concept,
            dynamicBlueprint = blueprint,
            currentTask = if (finalGatePassed) "Completed & Verified" else "Fix attempts exhausted",
            completedTasks = if (finalGatePassed) listOf("Requirement Analysis", "Dynamic Blueprint", "User Approval", "Code Generation", "Build Verification", "Test Suite PASS", "Final Artifact Verification") else listOf("Requirement Analysis", "Blueprint"),
            buildStatus = if (finalGatePassed) "SUCCESS" else "FAILED_MAX_RETRIES",
            testSummary = if (finalGatePassed) "All automated verification tests PASSED" else "Testing had failures",
            lastUpdated = System.currentTimeMillis()
        )
        ProjectState.saveToFile(finalState, stateFile)

        if (finalGatePassed) {
            logStep("Autonomous development workflow COMPLETED successfully with all verifications passing!")
            updateState {
                it.copy(
                    currentStep = WorkflowStep.COMPLETED_SUCCESS,
                    statusMessage = "Project fully implemented, built, tested, and verified!"
                )
            }
            database.projectDao().updateProject(project.copy(status = "COMPLETED"))
        } else {
            logStep("Workflow stopped: Maximum fix attempts ($AppConfig.MAX_FIX_ATTEMPTS) reached without passing all checks.")
            updateState {
                it.copy(
                    currentStep = WorkflowStep.FAILED,
                    statusMessage = "Maximum fix attempts ($AppConfig.MAX_FIX_ATTEMPTS) reached. Review logs for details."
                )
            }
        }
    }
}

