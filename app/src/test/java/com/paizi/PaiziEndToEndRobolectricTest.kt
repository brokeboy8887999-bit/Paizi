package com.paizi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.paizi.database.PAIZIDatabase
import com.paizi.di.DIModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaiziEndToEndRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        DIModule.initialize(context)
    }

    @Test
    fun `test DIModule initializes all specialized agents and managers`() {
        assertNotNull("ProjectManagerAgent must be provided", DIModule.projectManagerAgent)
        assertNotNull("SoftwareEngineerAgent must be provided", DIModule.softwareEngineerAgent)
        assertNotNull("BuildAgent must be provided", DIModule.buildAgent)
        assertNotNull("FileAgent must be provided", DIModule.fileAgent)
        assertNotNull("BrowserAgent must be provided", DIModule.browserAgent)
        assertNotNull("DocumentationAgent must be provided", DIModule.documentationAgent)
        assertNotNull("AnalysisAgent must be provided", DIModule.analysisAgent)
        assertNotNull("TermuxEnvironmentManager must be provided", DIModule.termuxEnvironmentManager)
        assertNotNull("BuildSystemManager must be provided", DIModule.buildSystemManager)
        assertNotNull("OnlineAIManager must be provided", DIModule.onlineAIManager)
        assertNotNull("OfflineAIManager must be provided", DIModule.offlineAIManager)
        assertNotNull("AIRouter must be provided", DIModule.aiRouter)
        assertNotNull("WorkflowEngine must be provided", DIModule.workflowEngine)
    }

    @Test
    fun `test default database population adheres strictly to master specification`() = runBlocking {
        val db = DIModule.database
        PAIZIDatabase.ensureDefaultSlotsPopulated(db)

        val providers = db.providerConfigDao().getAllProvidersList()
        assertEquals("Must have strictly 10 online provider slots", 10, providers.size)
        for (i in 0..9) {
            assertEquals("API ${i + 1}", providers[i].name)
            assertEquals(i, providers[i].slotIndex)
        }

        val offlineModels = db.offlineModelDao().getAllOfflineModelsList()
        assertEquals("Must have strictly 5 offline models", 5, offlineModels.size)
        val expectedModelNames = listOf("DeepSeek V4 Pro", "LLaMA 4", "Qwen 3.5", "Mistral", "Lama Queen 2.5")
        assertEquals(expectedModelNames, offlineModels.map { it.name })
    }

    @Test
    fun `test BuildSystemManager enforces No Fake Success rule`() = runBlocking {
        val buildSystemManager = DIModule.buildSystemManager
        val dummyProjectDir = File(context.filesDir, "test_proj_nonexistent")
        dummyProjectDir.mkdirs()

        // Running a build on a project with no gradlew or files must return failure, NEVER fake success
        val result = buildSystemManager.executeBuild("test_proj_nonexistent")

        assertFalse("Build on invalid project directory must fail", result.success)
        assertTrue("Exit code must be non-zero on failure", result.exitCode != 0)
        assertFalse("Output artifact must not exist for failed build", result.artifactPath != null && File(result.artifactPath).exists())
    }

    @Test
    fun `test TermuxEnvironmentManager detects system shell capability`() = runBlocking {
        val termuxManager = DIModule.termuxEnvironmentManager
        val status = termuxManager.getEnvironmentStatus()

        assertNotNull("Toolchain status must not be null", status)
        assertNotNull("Detected tools list must not be null", status.detectedTools)
        assertTrue("Workspace root must be set", status.activeWorkspaceRoot.isNotBlank())
    }
}
