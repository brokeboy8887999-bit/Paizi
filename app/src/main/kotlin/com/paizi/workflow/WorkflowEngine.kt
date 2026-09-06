package com.paizi.workflow

import com.paizi.agents.base.BaseAgent
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
    private val testingSystemManager: TestingSystemManager
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

    private fun updateState(update: (WorkflowState) -> WorkflowState) {
        _workflowState.value = update(_workflowState.value)
    }

    private fun logStep(message: String) {
        LoggingManager.i(TAG, message)
        updateState { it.copy(logs = it.logs + message, statusMessage = message) }
    }

    /**
     * Step 1: User provides requirement -> AI generates dynamic blueprint -> Enters AWAITING_APPROVAL state.
     */
    suspend fun startPlanning(project: ProjectEntity, requirement: String) = withContext(Dispatchers.IO) {
        logStep("Starting dynamic architectural planning for '${project.name}'")
        updateState { it.copy(currentStep = WorkflowStep.PLANNING_BLUEPRINT, activeProjectId = project.id) }

        val context = BaseAgent.AgentContext(
            projectId = project.id,
            projectName = project.name,
            requirements = requirement
        )

        val planResult = plannerAgent.generateBlueprint(context)
        if (!planResult.success) {
            logStep("Planning failed: ${planResult.summary}")
            updateState { it.copy(currentStep = WorkflowStep.FAILED, statusMessage = planResult.summary) }
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
            executeImplementationAndVerificationLoop(project)
        } else {
            logStep("Blueprint REJECTED by user. Development halted.")
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
        logStep("CoderAgent implementing project files matching approved blueprint...")

        val agentContext = BaseAgent.AgentContext(
            projectId = project.id,
            projectName = project.name,
            requirements = project.concept,
            currentBlueprint = blueprint,
            filesOverview = fileManager.listFiles().joinToString("\n") { "${it.name} (${if (it.isDirectory) "dir" else "file"})" }
        )

        val codeResult = coderAgent.implementTask("Implement application modules defined in blueprint", agentContext)
        logStep(codeResult.summary)

        // Phase B: Build & Test Loop with MAX_FIX_ATTEMPTS = 3
        var attempts = 0
        var loopPass = false

        while (attempts < AppConfig.MAX_FIX_ATTEMPTS && !loopPass) {
            attempts++
            updateState { it.copy(fixAttemptCount = attempts) }

            // Trigger Build
            updateState { it.copy(currentStep = WorkflowStep.BUILDING, statusMessage = "Building project (Attempt $attempts/3)...") }
            logStep("BuildAgent compiling project (Attempt $attempts/3)...")
            val buildOutcome = buildSystemManager.executeBuild(project.id)

            if (!buildOutcome.success) {
                logStep("Build failed on attempt $attempts. Engaging DebuggerAgent.")
                updateState { it.copy(currentStep = WorkflowStep.DEBUGGING_FIX_LOOP, statusMessage = "Debugger analyzing build error...") }

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

            // Trigger Testing
            updateState { it.copy(currentStep = WorkflowStep.TESTING, statusMessage = "Running test suite...") }
            logStep("TesterAgent executing verification test suite...")
            val testReport = testingSystemManager.runBuiltinTestSuite(project.id)

            if (testReport.failedTests > 0) {
                logStep("Tests failed: ${testReport.failedTests} failures. Engaging DebuggerAgent.")
                updateState { it.copy(currentStep = WorkflowStep.DEBUGGING_FIX_LOOP, statusMessage = "Debugger analyzing test failure...") }

                val debugResult = debuggerAgent.diagnoseAndFix(
                    "Test Failures: ${testReport.results.filter { it.result == "FAIL" }.joinToString("\n") { "${it.testName}: expected ${it.expectedOutput} but got ${it.actualOutput}" }}",
                    attempts,
                    agentContext
                )
                logStep(debugResult.summary)
                continue
            }

            // Both Build and Tests passed!
            loopPass = true
        }

        // Update final project state and PROJECT_STATE.md
        val stateFile = File(project.rootPath, "PROJECT_STATE.md")
        val finalState = ProjectState(
            id = project.id,
            name = project.name,
            projectType = project.type,
            requirements = project.concept,
            dynamicBlueprint = blueprint,
            currentTask = if (loopPass) "Completed & Verified" else "Fix attempts exhausted",
            completedTasks = if (loopPass) listOf("Requirement Analysis", "Dynamic Blueprint", "User Approval", "Code Generation", "Build Verification", "Test Suite PASS") else listOf("Requirement Analysis", "Blueprint"),
            buildStatus = if (loopPass) "SUCCESS" else "FAILED_MAX_RETRIES",
            testSummary = if (loopPass) "All automated verification tests PASSED" else "Testing had failures",
            lastUpdated = System.currentTimeMillis()
        )
        ProjectState.saveToFile(finalState, stateFile)

        if (loopPass) {
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
