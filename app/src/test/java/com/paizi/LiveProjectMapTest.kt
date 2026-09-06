package com.paizi

import com.paizi.project.map.BlueprintTask
import com.paizi.project.map.LiveProjectMap
import com.paizi.project.map.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveProjectMapTest {

    @Test
    fun `test dynamic plan generation generates proper blueprint tasks and initial zero percentage`() {
        val map = LiveProjectMap.generateDynamicPlan(
            projectName = "Expense Tracker",
            projectId = "proj_test_1",
            requirements = "Build an offline expense app with SQLite database and Compose UI",
            projectType = "Android Compose"
        )

        assertEquals("Expense Tracker", map.projectName)
        assertEquals("proj_test_1", map.projectId)
        assertNotNull(map.tasks)
        assertTrue(map.tasks.isNotEmpty())

        // Initial state: Planning is in progress, other tasks are pending
        val planTask = map.tasks.first { it.id == "task_plan" }
        assertEquals(TaskStatus.IN_PROGRESS, planTask.status)

        // Progress percentage must be 0 or strictly based on completed weights
        assertEquals(0, map.progressPercentage)
        assertFalse("Cannot be 100% at start", map.progressPercentage == 100)
    }

    @Test
    fun `test real weighted progress calculation and no fake 100 percent before final verification`() {
        val tasks = listOf(
            BlueprintTask(id = "1", title = "Planning", status = TaskStatus.COMPLETED, weight = 10),
            BlueprintTask(id = "2", title = "Architecture", status = TaskStatus.COMPLETED, weight = 15),
            BlueprintTask(id = "3", title = "UI", status = TaskStatus.COMPLETED, weight = 20),
            BlueprintTask(id = "4", title = "Features", status = TaskStatus.IN_PROGRESS, weight = 25),
            BlueprintTask(id = "5", title = "Build", status = TaskStatus.PENDING, weight = 15),
            BlueprintTask(id = "6", title = "Final Verification", status = TaskStatus.PENDING, weight = 15)
        )

        val map = LiveProjectMap(
            projectName = "Test Project",
            projectId = "test_1",
            tasks = tasks,
            currentTaskTitle = "Features",
            currentActionDescription = "Coding features..."
        )

        // Total weight = 100. Completed weight = 10 + 15 + 20 = 45.
        assertEquals(45, map.progressPercentage)

        // If all tasks except Final Verification are completed:
        val nearCompleteTasks = tasks.map {
            if (it.id != "6") it.copy(status = TaskStatus.COMPLETED) else it.copy(status = TaskStatus.IN_PROGRESS)
        }
        val nearCompleteMap = map.copy(tasks = nearCompleteTasks)

        // Weight = 85%, NOT 100%
        assertEquals(85, nearCompleteMap.progressPercentage)
        assertTrue("Cannot be 100% until final completion gate passes", nearCompleteMap.progressPercentage < 100)

        // Now final verification passes
        val verifiedTasks = tasks.map { it.copy(status = TaskStatus.COMPLETED) }
        val verifiedMap = map.copy(
            tasks = verifiedTasks,
            finalVerificationStatus = "VERIFIED",
            finalArtifactPath = "/data/app.apk"
        )
        assertEquals(100, verifiedMap.progressPercentage)
        assertEquals("VERIFIED", verifiedMap.finalVerificationStatus)
    }

    @Test
    fun `test conversational text formatting matches master prompt example`() {
        val tasks = listOf(
            BlueprintTask(id = "1", title = "Planning", status = TaskStatus.COMPLETED, weight = 15),
            BlueprintTask(id = "2", title = "Architecture", status = TaskStatus.COMPLETED, weight = 15),
            BlueprintTask(id = "3", title = "UI", status = TaskStatus.COMPLETED, weight = 20),
            BlueprintTask(id = "4", title = "Database", status = TaskStatus.COMPLETED, weight = 20),
            BlueprintTask(id = "5", title = "Features", status = TaskStatus.IN_PROGRESS, weight = 10),
            BlueprintTask(id = "6", title = "Testing", status = TaskStatus.PENDING, weight = 10),
            BlueprintTask(id = "7", title = "Build", status = TaskStatus.PENDING, weight = 5),
            BlueprintTask(id = "8", title = "Final Verification", status = TaskStatus.PENDING, weight = 5)
        )

        val map = LiveProjectMap(
            projectName = "Expense App",
            projectId = "p_expense",
            tasks = tasks,
            currentTaskTitle = "Features",
            currentActionDescription = "Building the application..."
        )

        val formattedText = map.toConversationalText()
        assertTrue(formattedText.contains("PROJECT: Expense App"))
        assertTrue(formattedText.contains("70% COMPLETE"))
        assertTrue(formattedText.contains("☑ Planning"))
        assertTrue(formattedText.contains("☑ Architecture"))
        assertTrue(formattedText.contains("☑ UI"))
        assertTrue(formattedText.contains("☑ Database"))
        assertTrue(formattedText.contains("🔄 Features"))
        assertTrue(formattedText.contains("☐ Testing"))
        assertTrue(formattedText.contains("☐ Build"))
        assertTrue(formattedText.contains("☐ Final Verification"))
        assertTrue(formattedText.contains("CURRENTLY: Building the application..."))
    }
}
