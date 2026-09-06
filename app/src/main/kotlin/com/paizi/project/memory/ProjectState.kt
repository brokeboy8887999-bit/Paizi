package com.paizi.project.memory

import java.io.File

/**
 * Data structure representing the complete persistent state of a PAIZI project.
 * Matches specifications for PROJECT_STATE.md serialization and recovery.
 */
data class ProjectState(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val projectType: String = "Android Compose",
    val requirements: String = "",
    val architecture: String = "",
    val dynamicBlueprint: String = "",
    val currentTask: String = "",
    val completedTasks: List<String> = emptyList(),
    val pendingTasks: List<String> = emptyList(),
    val recordedErrors: List<String> = emptyList(),
    val appliedFixes: List<String> = emptyList(),
    val testSummary: String = "No tests executed yet",
    val buildStatus: String = "NOT_BUILT",
    val decisions: List<String> = emptyList(),
    val activeAiProvider: String = "Online Provider",
    val activeModel: String = "gemini-2.5-flash",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Serializes this project state into standard markdown formatted for PROJECT_STATE.md.
     */
    fun toMarkdown(): String {
        return buildString {
            appendLine("# PROJECT_STATE: $name")
            appendLine()
            appendLine("## 1. Project Identity")
            appendLine("- **ID:** $id")
            appendLine("- **Name:** $name")
            appendLine("- **Version:** $version")
            appendLine("- **Type:** $projectType")
            appendLine("- **Active Provider:** $activeAiProvider")
            appendLine("- **Active Model:** $activeModel")
            appendLine("- **Last Updated:** $lastUpdated")
            appendLine()

            appendLine("## 2. Requirements")
            appendLine(requirements.ifBlank { "No requirements recorded." })
            appendLine()

            appendLine("## 3. Architecture")
            appendLine(architecture.ifBlank { "Architecture specification pending." })
            appendLine()

            appendLine("## 4. Dynamic Blueprint")
            appendLine(dynamicBlueprint.ifBlank { "Dynamic blueprint pending user approval." })
            appendLine()

            appendLine("## 5. Current Task")
            appendLine(currentTask.ifBlank { "Idle / Awaiting instructions." })
            appendLine()

            appendLine("## 6. Completed Tasks")
            if (completedTasks.isEmpty()) {
                appendLine("- None")
            } else {
                completedTasks.forEach { appendLine("- [x] $it") }
            }
            appendLine()

            appendLine("## 7. Pending Tasks")
            if (pendingTasks.isEmpty()) {
                appendLine("- None")
            } else {
                pendingTasks.forEach { appendLine("- [ ] $it") }
            }
            appendLine()

            appendLine("## 8. Errors & Debugging History")
            if (recordedErrors.isEmpty()) {
                appendLine("- None recorded")
            } else {
                recordedErrors.forEach { appendLine("- [ERROR] $it") }
            }
            appendLine()

            appendLine("## 9. Applied Fixes")
            if (appliedFixes.isEmpty()) {
                appendLine("- None recorded")
            } else {
                appliedFixes.forEach { appendLine("- [FIX] $it") }
            }
            appendLine()

            appendLine("## 10. Build Status")
            appendLine("- **Status:** $buildStatus")
            appendLine()

            appendLine("## 11. Testing Summary")
            appendLine(testSummary)
            appendLine()

            appendLine("## 12. Architectural Decisions")
            if (decisions.isEmpty()) {
                appendLine("- None recorded")
            } else {
                decisions.forEach { appendLine("- [DECISION] $it") }
            }
            appendLine()
        }
    }

    companion object {
        /**
         * Parses a PROJECT_STATE.md markdown document into a ProjectState object.
         */
        fun fromMarkdown(markdown: String): ProjectState {
            val lines = markdown.lines()
            var currentSection = ""
            val sectionContent = mutableMapOf<String, StringBuilder>()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("## ")) {
                    currentSection = trimmed.substring(3).trim()
                    sectionContent.putIfAbsent(currentSection, StringBuilder())
                } else if (currentSection.isNotEmpty()) {
                    sectionContent[currentSection]?.appendLine(line)
                }
            }

            fun getContent(sectionPrefix: String): String {
                val entry = sectionContent.entries.firstOrNull { it.key.startsWith(sectionPrefix) }
                return entry?.value?.toString()?.trim() ?: ""
            }

            fun getListItems(sectionPrefix: String, bulletPrefix: String): List<String> {
                val text = getContent(sectionPrefix)
                if (text.isBlank() || text.equals("None", ignoreCase = true) || text.equals("- None", ignoreCase = true)) {
                    return emptyList()
                }
                return text.lines()
                    .map { it.trim() }
                    .filter { it.startsWith("- ") }
                    .map {
                        var cleaned = it.removePrefix("- ").trim()
                        if (cleaned.startsWith("[x] ")) cleaned = cleaned.removePrefix("[x] ").trim()
                        if (cleaned.startsWith("[ ] ")) cleaned = cleaned.removePrefix("[ ] ").trim()
                        if (cleaned.startsWith("[ERROR] ")) cleaned = cleaned.removePrefix("[ERROR] ").trim()
                        if (cleaned.startsWith("[FIX] ")) cleaned = cleaned.removePrefix("[FIX] ").trim()
                        if (cleaned.startsWith("[DECISION] ")) cleaned = cleaned.removePrefix("[DECISION] ").trim()
                        cleaned
                    }
                    .filter { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
            }

            val identityText = getContent("1. Project Identity")
            var id = "project_" + System.currentTimeMillis()
            var name = "Untitled Project"
            var version = "1.0.0"
            var projectType = "Android Compose"
            var activeProvider = "Online Provider"
            var activeModel = "gemini-2.5-flash"
            var lastUpdated = System.currentTimeMillis()

            identityText.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("- **ID:**") -> id = trimmed.substringAfter("- **ID:**").trim()
                    trimmed.startsWith("- **Name:**") -> name = trimmed.substringAfter("- **Name:**").trim()
                    trimmed.startsWith("- **Version:**") -> version = trimmed.substringAfter("- **Version:**").trim()
                    trimmed.startsWith("- **Type:**") -> projectType = trimmed.substringAfter("- **Type:**").trim()
                    trimmed.startsWith("- **Active Provider:**") -> activeProvider = trimmed.substringAfter("- **Active Provider:**").trim()
                    trimmed.startsWith("- **Active Model:**") -> activeModel = trimmed.substringAfter("- **Active Model:**").trim()
                    trimmed.startsWith("- **Last Updated:**") -> lastUpdated = trimmed.substringAfter("- **Last Updated:**").trim().toLongOrNull() ?: System.currentTimeMillis()
                }
            }

            val buildStatusText = getContent("10. Build Status")
            val buildStatus = if (buildStatusText.contains("**Status:**")) {
                buildStatusText.substringAfter("**Status:**").trim().lines().firstOrNull() ?: "NOT_BUILT"
            } else {
                "NOT_BUILT"
            }

            return ProjectState(
                id = id,
                name = name,
                version = version,
                projectType = projectType,
                requirements = getContent("2. Requirements"),
                architecture = getContent("3. Architecture"),
                dynamicBlueprint = getContent("4. Dynamic Blueprint"),
                currentTask = getContent("5. Current Task"),
                completedTasks = getListItems("6. Completed Tasks", "- [x] "),
                pendingTasks = getListItems("7. Pending Tasks", "- [ ] "),
                recordedErrors = getListItems("8. Errors & Debugging History", "- [ERROR] "),
                appliedFixes = getListItems("9. Applied Fixes", "- [FIX] "),
                buildStatus = buildStatus,
                testSummary = getContent("11. Testing Summary").ifBlank { "No tests executed yet" },
                decisions = getListItems("12. Architectural Decisions", "- [DECISION] "),
                activeAiProvider = activeProvider,
                activeModel = activeModel,
                lastUpdated = lastUpdated
            )
        }

        fun saveToFile(state: ProjectState, targetFile: File) {
            targetFile.writeText(state.toMarkdown())
        }

        fun loadFromFile(sourceFile: File): ProjectState? {
            if (!sourceFile.exists()) return null
            return fromMarkdown(sourceFile.readText())
        }
    }
}
