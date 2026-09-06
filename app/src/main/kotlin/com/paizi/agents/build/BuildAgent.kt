package com.paizi.agents.build

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.build.BuildSystemManager

class BuildAgent(
    aiRouter: AIRouter,
    private val buildSystemManager: BuildSystemManager
) : BaseAgent(
    roleName = "Build Agent",
    description = "Oversees real compilation, Gradle automation, build diagnostics, and artifact verification",
    aiRouter = aiRouter
) {
    suspend fun triggerAndEvaluateBuild(context: AgentContext): AgentResult {
        val buildOutcome = buildSystemManager.executeBuild(context.projectId)

        return if (buildOutcome.success) {
            AgentResult(
                success = true,
                summary = "Build succeeded! Artifact verified at ${buildOutcome.artifactPath ?: "outputs"}",
                outputData = buildOutcome.logs,
                suggestedNextRole = "Tester Agent"
            )
        } else {
            // Engage AI router to summarize root cause if needed
            val systemPrompt = "You are the Build Agent. Analyze the Gradle build failure and provide concise root causes."
            val userPrompt = "Build Output:\n${buildOutcome.logs.takeLast(2500)}"
            val aiAnalysis = aiRouter.routeAndExecute(AIRouter.TaskType.DEBUGGING, systemPrompt, userPrompt)

            val analysisText = if (aiAnalysis is AIRouter.RouteResult.Success) aiAnalysis.responseText else "Build compilation failed with exit code ${buildOutcome.exitCode}"

            AgentResult(
                success = false,
                summary = "Build failed (Exit Code ${buildOutcome.exitCode}): $analysisText",
                outputData = buildOutcome.logs,
                suggestedNextRole = "Debugger Agent",
                errors = listOf("Exit code ${buildOutcome.exitCode}")
            )
        }
    }
}
