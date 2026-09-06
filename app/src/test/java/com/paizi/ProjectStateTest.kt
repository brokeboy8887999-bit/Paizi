package com.paizi

import com.paizi.project.memory.ProjectState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStateTest {

    @Test
    fun `test markdown serialization and parsing round trip`() {
        val original = ProjectState(
            id = "proj_test_123",
            name = "Weather Dashboard",
            version = "1.0.0",
            projectType = "Android Compose",
            requirements = "Create a modern weather forecast application with radar",
            architecture = "MVVM with Room local cache",
            dynamicBlueprint = "Screen 1: WeatherOverviewScreen\nScreen 2: ForecastScreen",
            currentTask = "Implementing ForecastScreen",
            completedTasks = listOf("Project Setup", "Database Layer"),
            pendingTasks = listOf("Radar Map", "Settings"),
            recordedErrors = listOf("NullPointerException in API client"),
            appliedFixes = listOf("Added null-check on response body"),
            testSummary = "12/12 unit tests passing",
            buildStatus = "SUCCESS",
            decisions = listOf("Use Room instead of raw SQLite")
        )

        val markdown = original.toMarkdown()
        val parsed = ProjectState.fromMarkdown(markdown)

        assertEquals(original.id, parsed.id)
        assertEquals(original.name, parsed.name)
        assertEquals(original.requirements.trim(), parsed.requirements.trim())
        assertEquals(original.architecture.trim(), parsed.architecture.trim())
        assertEquals(original.buildStatus, parsed.buildStatus)
        assertEquals(2, parsed.completedTasks.size)
        assertEquals("Project Setup", parsed.completedTasks[0])
        assertEquals("Database Layer", parsed.completedTasks[1])
        assertEquals(2, parsed.pendingTasks.size)
        assertEquals("Radar Map", parsed.pendingTasks[0])
        assertEquals(1, parsed.recordedErrors.size)
        assertEquals(1, parsed.appliedFixes.size)
    }
}
