package com.paizi.agents.coder

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.coding.CodingEngine
import com.paizi.core.logging.LoggingManager

class CoderAgent(
    aiRouter: AIRouter,
    private val codingEngine: CodingEngine
) : BaseAgent("Coder", "Implements robust Kotlin and Compose code matching approved blueprints", aiRouter) {

    private val TAG = "CoderAgent"

    suspend fun implementTask(taskDescription: String, context: AgentContext): AgentResult {
        LoggingManager.i(TAG, "Implementing task: $taskDescription for '${context.projectName}'")

        val systemPrompt = """
            You are PAIZI's Expert Android Software Engineer (CoderAgent).
            Write production-grade, compilable Kotlin and Jetpack Compose code.
            Adhere strictly to:
            1. Kotlin idiom and modern Android architecture (MVVM, StateFlow, Coroutines).
            2. Jetpack Compose with Material Design 3.
            3. Room Database entities, DAOs, and repository patterns.
            4. Absolute zero fake or placeholder implementations.
            5. Clear file demarcation in your response using:
            ```file:path/to/File.kt
            // content
            ```
        """.trimIndent()

        val userPrompt = """
            Project Name: ${context.projectName}
            Task Description: $taskDescription

            Approved Blueprint Context:
            ${context.currentBlueprint}

            Workspace Overview:
            ${context.filesOverview}
        """.trimIndent()

        return when (val result = aiRouter.routeAndExecute(AIRouter.TaskType.CODING, systemPrompt, userPrompt)) {
            is AIRouter.RouteResult.Success -> {
                val createdFiles = parseAndWriteFiles(result.responseText)
                AgentResult(
                    success = true,
                    summary = "Code implemented successfully. Files modified/created: ${createdFiles.size}",
                    outputData = result.responseText,
                    suggestedNextRole = "BuildAgent"
                )
            }
            is AIRouter.RouteResult.Failure -> {
                AgentResult(
                    success = false,
                    summary = "CoderAgent execution failed: ${result.errorSummary}",
                    outputData = "",
                    errors = listOf(result.errorSummary)
                )
            }
        }
    }

    private fun parseAndWriteFiles(rawResponse: String): List<String> {
        val created = mutableListOf<String>()
        val regex = Regex("```(?:file:)?([a-zA-Z0-9_/\\-\\.]+)\n([\\s\\S]*?)```")
        val matches = regex.findAll(rawResponse)

        for (m in matches) {
            val path = m.groupValues[1].trim()
            val content = m.groupValues[2]
            if (path.isNotBlank() && (path.endsWith(".kt") || path.endsWith(".kts") || path.endsWith(".xml") || path.endsWith(".gradle") || path.endsWith(".md"))) {
                val writeRes = codingEngine.writeFile(path, content)
                if (writeRes.success) {
                    created.add(path)
                }
            }
        }
        return created
    }
}
