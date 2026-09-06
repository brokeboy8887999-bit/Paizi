package com.paizi.agents.analysis

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter

class AnalysisAgent(
    aiRouter: AIRouter
) : BaseAgent(
    roleName = "Analysis Agent",
    description = "Performs deep requirement analysis, code complexity metrics, security audits, and risk assessments",
    aiRouter = aiRouter
) {
    suspend fun analyzeRequirementsAndRisks(context: AgentContext): AgentResult {
        val systemPrompt = "You are the Analysis Agent in PAIZI. " +
                "Perform rigorous static analysis and requirement feasibility checking. " +
                "Identify missing edge cases, dependency risks, storage constraints, and security issues."

        val userPrompt = """
            Project Name: ${context.projectName}
            Raw Requirements: ${context.requirements}
            Blueprint: ${context.currentBlueprint}
            
            Deliver an in-depth analytical assessment:
            1. Scope & Feasibility
            2. Potential Technical Bottlenecks
            3. Android Permission & Security Considerations
            4. Suggested Mitigations
        """.trimIndent()

        val response = aiRouter.routeAndExecute(
            taskType = AIRouter.TaskType.REASONING,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )

        return when (response) {
            is AIRouter.RouteResult.Success -> {
                AgentResult(
                    success = true,
                    summary = "Requirement analysis complete",
                    outputData = response.responseText,
                    suggestedNextRole = "Planner Agent"
                )
            }
            is AIRouter.RouteResult.Failure -> {
                AgentResult(
                    success = false,
                    summary = "Analysis failed: ${response.errorSummary}",
                    errors = listOf(response.errorSummary)
                )
            }
        }
    }
}
