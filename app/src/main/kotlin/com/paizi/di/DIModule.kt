package com.paizi.di

import android.content.Context
import com.paizi.agents.coder.CoderAgent
import com.paizi.agents.debugger.DebuggerAgent
import com.paizi.agents.planner.PlannerAgent
import com.paizi.agents.reviewer.ReviewerAgent
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
import com.paizi.testing.TestingSystemManager
import com.paizi.vision.VisionManager
import com.paizi.voice.VoiceManager
import com.paizi.workflow.WorkflowEngine

object DIModule {
    @Volatile
    private var isInitialized = false

    lateinit var database: PAIZIDatabase
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

            onlineAIManager = OnlineAIManager(database.providerConfigDao())
            offlineAIManager = OfflineAIManager(appContext, database.offlineModelDao())

            aiRouter = AIRouter(
                providerConfigDao = database.providerConfigDao(),
                offlineModelDao = database.offlineModelDao(),
                onlineAIManager = onlineAIManager,
                offlineAIManager = offlineAIManager
            )

            projectManager = ProjectManager(appContext, database)
            buildSystemManager = BuildSystemManager(appContext, database.buildDao())
            testingSystemManager = TestingSystemManager(database.testDao())

            val defaultWorkspace = WorkspaceFileManager(AppConfig.getWorkspaceRoot(appContext))
            val codingEngine = CodingEngine(defaultWorkspace)

            plannerAgent = PlannerAgent(aiRouter)
            reviewerAgent = ReviewerAgent(aiRouter)
            coderAgent = CoderAgent(aiRouter, codingEngine)
            debuggerAgent = DebuggerAgent(aiRouter, codingEngine)
            testerAgent = TesterAgent(aiRouter, database.testDao())

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

            isInitialized = true
        }
    }
}
