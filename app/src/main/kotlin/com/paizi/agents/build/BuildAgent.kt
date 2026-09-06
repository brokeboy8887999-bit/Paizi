package com.paizi.agents.build

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.build.BuildSystemManager
import com.paizi.core.logging.LoggingManager
import com.paizi.termux.TermuxEnvironmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Specialized Build Agent.
 * Operates strictly inside the PAIZI AI Brain development engine.
 * Controls real compilation, toolchain verification, dependency resolution,
 * build execution, error extraction, and artifact verification.
 */
class BuildAgent(
    aiRouter: AIRouter,
    private val buildSystemManager: BuildSystemManager,
    private val termuxManager: TermuxEnvironmentManager? = null
) : BaseAgent(
    roleName = "BuildAgent",
    description = "Autonomous build system engineer that executes real compilations, manages toolchain dependencies, and verifies physical artifacts.",
    aiRouter = aiRouter
) {
    private val TAG = "BuildAgent"

    /**
     * Executes real build workflow for the active project.
     * Enforces the NO FAKE SUCCESS rule.
     */
    suspend fun executeBuild(
        context: AgentContext,
        targetTask: String = "assembleDebug"
    ): AgentResult = withContext(Dispatchers.IO) {
        LoggingManager.i(TAG, "BuildAgent starting real build for ${context.projectName} ($targetTask)")

        // 1. Toolchain health check
        termuxManager?.let { tm ->
            val toolStatus = tm.getEnvironmentStatus()
            LoggingManager.i(TAG, "Environment status: TermuxInstalled=${toolStatus.isTermuxInstalled}, ShellAccess=${toolStatus.hasShellAccess}")
        }

        // 2. Real build execution
        val outcome = buildSystemManager.executeBuild(
            projectId = context.projectId,
            targetTask = targetTask
        )

        // 3. Process outcome
        if (outcome.success) {
            val summary = buildString {
                append("✅ Build Succeeded! (${outcome.target}) in ${outcome.durationMs}ms.")
                outcome.artifactPath?.let {
                    append(" Physical artifact verified: $it")
                }
            }
            AgentResult(
                success = true,
                summary = summary,
                outputData = outcome.logs,
                suggestedNextRole = "TesterAgent"
            )
        } else {
            // Extract core error details from logs
            val errorSnippet = extractKeyErrorLines(outcome.logs)
            val summary = buildString {
                append("❌ Build Failed with exit code ${outcome.exitCode} (${outcome.durationMs}ms).")
                outcome.failureReason?.let { append(" Reason: $it.") }
                if (errorSnippet.isNotBlank()) {
                    append("\nPrimary error output:\n$errorSnippet")
                }
            }

            AgentResult(
                success = false,
                summary = summary,
                outputData = outcome.logs,
                suggestedNextRole = "DebuggerAgent",
                errors = listOfNotNull(outcome.failureReason, errorSnippet.take(500))
            )
        }
    }

    /**
     * Extracts compiler error lines from build logs for rapid debugging by DebuggerAgent.
     */
    private fun extractKeyErrorLines(logs: String): String {
        val lines = logs.lines()
        val errorLines = lines.filter { line ->
            val l = line.lowercase()
            l.contains("error:") || l.contains("failure:") || l.contains("exception:") ||
                    l.contains("fatal:") || l.contains("unresolved reference") ||
                    l.contains("syntaxerror") || l.contains("cannot find symbol")
        }
        return errorLines.take(15).joinToString("\n").take(2000)
    }
}
