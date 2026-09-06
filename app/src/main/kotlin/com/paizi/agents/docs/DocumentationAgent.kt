package com.paizi.agents.docs

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter

class DocumentationAgent(
    aiRouter: AIRouter
) : BaseAgent(
    roleName = "Documentation Agent",
    description = "Generates comprehensive README.md, technical specifications, and API documentation",
    aiRouter = aiRouter
) {
    suspend fun generateProjectDocumentation(context: AgentContext): AgentResult {
        val systemPrompt = "You are the Documentation Agent in PAIZI. " +
                "Generate a professional, polished README.md and technical documentation for the application. " +
                "Include project overview, features, build instructions, architectural components, and testing procedures."

        val userPrompt = """
            Project Name: ${context.projectName}
            Requirements: ${context.requirements}
            Blueprint: ${context.currentBlueprint}
            Files Overview: ${context.filesOverview}
            
            Generate a full README.md in clean markdown format.
        """.trimIndent()

        val response = aiRouter.routeAndExecute(
            taskType = AIRouter.TaskType.GENERAL,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )

        return when (response) {
            is AIRouter.RouteResult.Success -> {
                AgentResult(
                    success = true,
                    summary = "Project documentation generated",
                    outputData = response.responseText
                )
            }
            is AIRouter.RouteResult.Failure -> {
                AgentResult(
                    success = false,
                    summary = "Documentation generation failed: ${response.errorSummary}",
                    errors = listOf(response.errorSummary)
                )
            }
        }
    }
}
