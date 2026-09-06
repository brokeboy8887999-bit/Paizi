package com.paizi.agents.reviewer

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.core.logging.LoggingManager

class ReviewerAgent(
    aiRouter: AIRouter
) : BaseAgent("Reviewer", "Reviews blueprints and code for correctness, performance, and security", aiRouter) {

    private val TAG = "ReviewerAgent"

    suspend fun reviewBlueprint(blueprint: String, context: AgentContext): AgentResult {
        LoggingManager.i(TAG, "Reviewing dynamic blueprint for '${context.projectName}'")

        val systemPrompt = """
            You are PAIZI's Senior Code & Architecture Reviewer Agent.
            Your job is to critically assess dynamic blueprints and code implementations.
            Evaluate against:
            1. Completeness of requirements
            2. Security guidelines: No hardcoded secrets, sandbox boundaries, path traversal protection.
            3. Android best practices: Jetpack Compose, Material 3, ViewModel, Flow, Room repository.
            4. Feasibility and testability.

            Provide an explicit decision:
            APPROVED: If the blueprint is solid, secure, and production-grade.
            REVISION_REQUIRED: If critical gaps or security violations exist, list specific remediation items.
        """.trimIndent()

        val userPrompt = """
            Project Name: ${context.projectName}
            Requirements: ${context.requirements}

            Blueprint to review:
            $blueprint
        """.trimIndent()

        return when (val result = aiRouter.routeAndExecute(AIRouter.TaskType.REASONING, systemPrompt, userPrompt)) {
            is AIRouter.RouteResult.Success -> {
                val approved = result.responseText.contains("APPROVED", ignoreCase = true) &&
                        !result.responseText.contains("REVISION_REQUIRED", ignoreCase = true)
                AgentResult(
                    success = approved,
                    summary = if (approved) "Blueprint approved by Reviewer" else "Reviewer requested revisions",
                    outputData = result.responseText,
                    suggestedNextRole = if (approved) "Coder" else "Planner"
                )
            }
            is AIRouter.RouteResult.Failure -> {
                AgentResult(
                    success = false,
                    summary = "Reviewer could not complete analysis: ${result.errorSummary}",
                    outputData = "",
                    errors = listOf(result.errorSummary)
                )
            }
        }
    }
}
