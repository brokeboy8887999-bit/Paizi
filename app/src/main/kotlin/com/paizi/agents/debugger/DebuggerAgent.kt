package com.paizi.agents.debugger

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.coding.CodingEngine
import com.paizi.core.logging.LoggingManager

class DebuggerAgent(
    aiRouter: AIRouter,
    private val codingEngine: CodingEngine
) : BaseAgent("Debugger", "Diagnoses compilation and test failures and prescribes minimal targeted fixes", aiRouter) {

    private val TAG = "DebuggerAgent"

    data class DiagnosticDiagnosis(
        val rootCause: String,
        val affectedFiles: List<String>,
        val fixSummary: String,
        val patches: List<PatchOperation>
    )

    data class PatchOperation(
        val filePath: String,
        val targetSubstring: String,
        val replacementString: String
    )

    suspend fun diagnoseAndFix(errorLogs: String, attemptNumber: Int, context: AgentContext): AgentResult {
        LoggingManager.i(TAG, "Diagnosing failure (Attempt $attemptNumber/3) for '${context.projectName}'")

        val systemPrompt = """
            You are PAIZI's Lead Debugging & Systems Diagnostics Engineer (DebuggerAgent).
            Analyze compilation, build, or test failure logs.
            Provide:
            1. Exact root cause analysis.
            2. Minimal targeted surgical fix. Do NOT rewrite the entire codebase or remove features.
            3. Exact string replacement patches using:
            ```patch:path/to/File.kt
            <<<<<<< SEARCH
            exact text to replace
            =======
            replacement text
            >>>>>>>
            ```
        """.trimIndent()

        val userPrompt = """
            Project Name: ${context.projectName}
            Current Retry Attempt: $attemptNumber / 3
            Failure Logs:
            $errorLogs

            Workspace Overview:
            ${context.filesOverview}
        """.trimIndent()

        return when (val result = aiRouter.routeAndExecute(AIRouter.TaskType.DEBUGGING, systemPrompt, userPrompt)) {
            is AIRouter.RouteResult.Success -> {
                val appliedPatches = applyPatches(result.responseText)
                AgentResult(
                    success = appliedPatches > 0,
                    summary = "Debugger diagnosed root cause. Applied $appliedPatches targeted fixes.",
                    outputData = result.responseText,
                    suggestedNextRole = "BuildAgent"
                )
            }
            is AIRouter.RouteResult.Failure -> {
                AgentResult(
                    success = false,
                    summary = "Debugger routing failed: ${result.errorSummary}",
                    outputData = "",
                    errors = listOf(result.errorSummary)
                )
            }
        }
    }

    private fun applyPatches(rawResponse: String): Int {
        var count = 0
        val patchRegex = Regex("```(?:patch:)?([a-zA-Z0-9_/\\-\\.]+)\n<<<<<<< SEARCH\n([\\s\\S]*?)\n=======\n([\\s\\S]*?)\n>>>>>>>\n```")
        for (match in patchRegex.findAll(rawResponse)) {
            val filePath = match.groupValues[1].trim()
            val search = match.groupValues[2]
            val replace = match.groupValues[3]
            val res = codingEngine.editFile(filePath, search, replace)
            if (res.success) {
                count++
                LoggingManager.i(TAG, "Successfully applied surgical patch to $filePath")
            } else {
                LoggingManager.w(TAG, "Patch target string not matched in $filePath")
            }
        }
        return count
    }
}
