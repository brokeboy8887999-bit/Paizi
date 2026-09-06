package com.paizi.di

import android.content.Context
import com.paizi.agents.analysis.AnalysisAgent
import com.paizi.agents.browser.BrowserAgent
import com.paizi.agents.build.BuildAgent
import com.paizi.agents.coder.CoderAgent
import com.paizi.agents.debugger.DebuggerAgent
import com.paizi.agents.docs.DocumentationAgent
import com.paizi.agents.file.FileAgent
import com.paizi.agents.planner.PlannerAgent
import com.paizi.agents.pm.ProjectManagerAgent
import com.paizi.agents.reviewer.ReviewerAgent
import com.paizi.agents.swe.SoftwareEngineerAgent
import com.paizi.agents.tester.TesterAgent
import com.paizi.ai.offline.OfflineAIManager
import com.paizi.ai.online.OnlineAIManager
import com.paizi.ai.router.AIRouter
import com.paizi.browser.BrowserController
import com.paizi.build.BuildSystemManager
import com.paizi.coding.CodingEngine
import com.paizi.core.config.AppConfig
import com.paizi.database.PAIZIDatabase
import com.paizi.diagnostics.DiagnosticsManager
import com.paizi.files.WorkspaceFileManager
import com.paizi.plugins.PluginManager
import com.paizi.project.manager.ProjectManager
import com.paizi.termux.TermuxEnvironmentManager
import com.paizi.testing.TestingSystemManager
import com.paizi.vision.VisionManager
import com.paizi.voice.VoiceManager
import com.paizi.workflow.WorkflowEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DIModule {
    @Volatile
    private var isInitialized = false

    lateinit var database: PAIZIDatabase
        private set
    lateinit var termuxEnvironmentManager: TermuxEnvironmentManager
        private set
    lateinit var onlineAIManager: OnlineAIManager
        private set
    lateinit var offlineAIManager: OfflineAIManager
        private set
    lateinit var aiRouter: AIRouter
        private set
    lateinit var projectManager: ProjectManager
        private set
    lateinit var buildSystemManager: BuildSystemManager
        private set
    lateinit var testingSystemManager: TestingSystemManager
        private set
    lateinit var plannerAgent: PlannerAgent
        private set
    lateinit var reviewerAgent: ReviewerAgent
        private set
    lateinit var coderAgent: CoderAgent
        private set
    lateinit var debuggerAgent: DebuggerAgent
        private set
    lateinit var testerAgent: TesterAgent
        private set
    lateinit var projectManagerAgent: ProjectManagerAgent
        private set
    lateinit var softwareEngineerAgent: SoftwareEngineerAgent
        private set
    lateinit var buildAgent: BuildAgent
        private set
    lateinit var fileAgent: FileAgent
        private set
    lateinit var browserAgent: BrowserAgent
        private set
    lateinit var documentationAgent: DocumentationAgent
        private set
    lateinit var analysisAgent: AnalysisAgent
        private set
    lateinit var workflowEngine: WorkflowEngine
        private set
    lateinit var diagnosticsManager: DiagnosticsManager
        private set
    lateinit var visionManager: VisionManager
        private set
    lateinit var browserController: BrowserController
        private set
    lateinit var voiceManager: VoiceManager
        private set
    lateinit var pluginManager: PluginManager
        private set

    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return

            val appContext = context.applicationContext
            database = PAIZIDatabase.getInstance(appContext)
            termuxEnvironmentManager = TermuxEnvironmentManager(appContext)

            onlineAIManager = OnlineAIManager(database.providerConfigDao())
            offlineAIManager = OfflineAIManager(appContext, database.offlineModelDao())

            aiRouter = AIRouter(
                providerConfigDao = database.providerConfigDao(),
                offlineModelDao = database.offlineModelDao(),
                onlineAIManager = onlineAIManager,
                offlineAIManager = offlineAIManager
            )

            projectManager = ProjectManager(appContext, database)
            buildSystemManager = BuildSystemManager(appContext, database.buildDao(), termuxEnvironmentManager)
            testingSystemManager = TestingSystemManager(database.testDao())

            val defaultWorkspace = WorkspaceFileManager(AppConfig.getWorkspaceRoot(appContext))
            val codingEngine = CodingEngine(defaultWorkspace)

            plannerAgent = PlannerAgent(aiRouter)
            reviewerAgent = ReviewerAgent(aiRouter)
            coderAgent = CoderAgent(aiRouter, codingEngine)
            debuggerAgent = DebuggerAgent(aiRouter, codingEngine)
            testerAgent = TesterAgent(aiRouter, database.testDao())
            projectManagerAgent = ProjectManagerAgent(aiRouter)
            softwareEngineerAgent = SoftwareEngineerAgent(aiRouter)
            buildAgent = BuildAgent(aiRouter, buildSystemManager)
            fileAgent = FileAgent(aiRouter, codingEngine)
            browserAgent = BrowserAgent(aiRouter, BrowserController(appContext))
            documentationAgent = DocumentationAgent(aiRouter)
            analysisAgent = AnalysisAgent(aiRouter)

            workflowEngine = WorkflowEngine(
                database = database,
                plannerAgent = plannerAgent,
                reviewerAgent = reviewerAgent,
                coderAgent = coderAgent,
                debuggerAgent = debuggerAgent,
                testerAgent = testerAgent,
                buildSystemManager = buildSystemManager,
                testingSystemManager = testingSystemManager
            )

            diagnosticsManager = DiagnosticsManager(appContext, database, database.diagnosticDao())
            visionManager = VisionManager(appContext)
            browserController = BrowserController(appContext)
            voiceManager = VoiceManager(appContext)
            pluginManager = PluginManager()

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                PAIZIDatabase.ensureDefaultSlotsPopulated(database)
            }

            isInitialized = true
        }
    }
}
