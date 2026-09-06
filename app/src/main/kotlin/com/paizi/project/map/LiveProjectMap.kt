package com.paizi.project.map

/**
 * Real-time, dynamic Project Blueprint and Progress Map.
 * Strictly adheres to rule:
 * - REAL CHECKMARKS (☑) only when work is physically completed and verified.
 * - REAL PERCENTAGE: weighted mathematically from actual tasks, NO arbitrary numbers,
 *   NO 100% until Final Completion Gate passes!
 */
enum class TaskStatus(val symbol: String, val label: String) {
    PENDING("☐", "Pending"),
    IN_PROGRESS("🔄", "In Progress"),
    COMPLETED("☑", "Completed"),
    FAILED("❌", "Failed"),
    BLOCKED("⚠️", "Blocked"),
    NOT_SUPPORTED("⛔", "Not Supported")
}

data class BlueprintTask(
    val id: String,
    val title: String,
    val category: String = "GENERAL", // PLANNING, ARCHITECTURE, UI, DATABASE, LOGIC, TEST, BUILD, VERIFICATION
    val status: TaskStatus = TaskStatus.PENDING,
    val weight: Int = 10,
    val requirementTrace: String? = null,
    val evidence: String? = null
)

data class LiveProjectMap(
    val projectName: String,
    val projectId: String,
    val projectType: String = "Android Compose",
    val tasks: List<BlueprintTask> = emptyList(),
    val currentTaskTitle: String = "Planning",
    val currentActionDescription: String = "Initializing development pipeline...",
    val finalArtifactPath: String? = null,
    val finalVerificationStatus: String = "PENDING", // PENDING, VERIFIED, FAILED, BLOCKED, NOT_SUPPORTED
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Real weighted progress percentage.
     * ZERO random numbers or fake animations.
     * Strictly capped at 99% until final completion gate is VERIFIED!
     */
    val progressPercentage: Int
        get() {
            if (tasks.isEmpty()) return 0
            val totalWeight = tasks.sumOf { it.weight }
            if (totalWeight <= 0) return 0
            val completedWeight = tasks.filter { it.status == TaskStatus.COMPLETED }.sumOf { it.weight }
            val raw = (completedWeight * 100) / totalWeight
            return if (raw >= 100 && finalVerificationStatus != "VERIFIED") 99 else raw.coerceIn(0, 100)
        }

    /**
     * Formats the Live Project Map cleanly for conversational display in Chat:
     *
     * PROJECT: Expense App
     * 72% COMPLETE
     *
     * ☑ Planning
     * ☑ Architecture
     * ☑ UI
     * ☑ Database
     * 🔄 Features
     * ☐ Testing
     * ☐ Build
     * ☐ Final Verification
     *
     * CURRENTLY: Building the application...
     */
    fun toConversationalText(): String {
        return buildString {
            appendLine("PROJECT: $projectName")
            appendLine("$progressPercentage% COMPLETE")
            appendLine()
            for (task in tasks) {
                appendLine("${task.status.symbol} ${task.title}")
            }
            appendLine()
            appendLine("CURRENTLY: $currentActionDescription")
            if (finalArtifactPath != null && finalVerificationStatus == "VERIFIED") {
                appendLine()
                appendLine("✅ FINAL ARTIFACT: $finalArtifactPath")
            } else if (finalVerificationStatus == "FAILED") {
                appendLine()
                appendLine("❌ STATUS: FAILED — Review logs for error fix loop results.")
            } else if (finalVerificationStatus == "BLOCKED") {
                appendLine()
                appendLine("⚠️ STATUS: BLOCKED — Dependency or environment constraint.")
            } else if (finalVerificationStatus == "NOT_SUPPORTED") {
                appendLine()
                appendLine("⛔ STATUS: NOT SUPPORTED — Toolchain unavailable locally.")
            }
        }
    }

    companion object {
        /**
         * Generates a truly dynamic task breakdown based on the user's project requirements.
         */
        fun generateDynamicPlan(
            projectName: String,
            projectId: String,
            requirements: String,
            projectType: String = "Android Compose"
        ): LiveProjectMap {
            val reqLower = requirements.lowercase()
            val tasks = mutableListOf<BlueprintTask>()

            // 1. Planning is always the first foundational phase
            tasks.add(
                BlueprintTask(
                    id = "task_plan",
                    title = "Planning",
                    category = "PLANNING",
                    status = TaskStatus.IN_PROGRESS,
                    weight = 10,
                    requirementTrace = requirements.take(120),
                    evidence = "Dynamic blueprint initialization"
                )
            )

            // 2. Architecture design
            tasks.add(
                BlueprintTask(
                    id = "task_arch",
                    title = "Architecture",
                    category = "ARCHITECTURE",
                    status = TaskStatus.PENDING,
                    weight = 15,
                    requirementTrace = "Component hierarchy and data flow"
                )
            )

            // 3. UI / Interface (if user mentions UI, screen, app, views)
            val hasUi = reqLower.contains("ui") || reqLower.contains("screen") || reqLower.contains("view") ||
                    reqLower.contains("app") || reqLower.contains("calculator") || reqLower.contains("todo") ||
                    reqLower.contains("expense") || reqLower.contains("web") || reqLower.contains("frontend")
            if (hasUi) {
                tasks.add(
                    BlueprintTask(
                        id = "task_ui",
                        title = "UI",
                        category = "UI",
                        status = TaskStatus.PENDING,
                        weight = 15,
                        requirementTrace = "Compose / Frontend user interface"
                    )
                )
            }

            // 4. Database / Persistence (if user mentions database, store, room, sql, save, expense, list)
            val hasDb = reqLower.contains("database") || reqLower.contains("db") || reqLower.contains("room") ||
                    reqLower.contains("sql") || reqLower.contains("save") || reqLower.contains("store") ||
                    reqLower.contains("expense") || reqLower.contains("todo") || reqLower.contains("persist")
            if (hasDb) {
                tasks.add(
                    BlueprintTask(
                        id = "task_db",
                        title = "Database",
                        category = "DATABASE",
                        status = TaskStatus.PENDING,
                        weight = 15,
                        requirementTrace = "Local Room / SQLite persistence entities and DAOs"
                    )
                )
            }

            // 5. Core Business Logic / Features
            tasks.add(
                BlueprintTask(
                    id = "task_features",
                    title = "Features",
                    category = "LOGIC",
                    status = TaskStatus.PENDING,
                    weight = 20,
                    requirementTrace = "Core functional logic and state management"
                )
            )

            // 6. Testing
            tasks.add(
                BlueprintTask(
                    id = "task_test",
                    title = "Testing",
                    category = "TEST",
                    status = TaskStatus.PENDING,
                    weight = 10,
                    requirementTrace = "Automated unit and integration test suite"
                )
            )

            // 7. Real Build & Toolchain execution
            tasks.add(
                BlueprintTask(
                    id = "task_build",
                    title = "Build",
                    category = "BUILD",
                    status = TaskStatus.PENDING,
                    weight = 15,
                    requirementTrace = "Compiler / Gradle / Toolchain compilation execution"
                )
            )

            // 8. Final Verification Gate
            tasks.add(
                BlueprintTask(
                    id = "task_verification",
                    title = "Final Verification",
                    category = "VERIFICATION",
                    status = TaskStatus.PENDING,
                    weight = 10,
                    requirementTrace = "Physical artifact verification and integrity checks"
                )
            )

            return LiveProjectMap(
                projectName = projectName,
                projectId = projectId,
                projectType = projectType,
                tasks = tasks,
                currentTaskTitle = "Planning",
                currentActionDescription = "Analyzing requirements and formulating dynamic architecture blueprint..."
            )
        }
    }
}
