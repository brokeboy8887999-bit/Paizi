package com.paizi.agents.swe

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter

class SoftwareEngineerAgent(
    aiRouter: AIRouter
) : BaseAgent(
    roleName = "Software Engineer Agent",
    description = "Designs technical architecture, data schemas, API contracts, and modular software abstractions",
    aiRouter = aiRouter
) {
    suspend fun designSystemArchitecture(context: AgentContext): AgentResult {
        val systemPrompt = "You are the Senior Software Engineer Agent in PAIZI. " +
                "You architect clean Android Jetpack Compose and Kotlin systems. " +
                "Design scalable MVVM architecture, state flow models, Room database schemas, and clean component hierarchies."

        val userPrompt = """
            Requirements: ${context.requirements}
            Blueprint: ${context.currentBlueprint}
            Existing Files: ${context.filesOverview}
            
            Formulate the technical engineering specification:
            1. Package and Module Structure
            2. Room Persistence Entities and DAOs
            3. State Management (StateFlow & ViewModel)
            4. Jetpack Compose UI Hierarchy
            5. Error Handling and Resilience
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
                    summary = "Software architecture formulated",
                    outputData = response.responseText,
                    suggestedNextRole = "Coder Agent"
                )
            }
            is AIRouter.RouteResult.Failure -> {
                AgentResult(
                    success = false,
                    summary = "Architecture design failed: ${response.errorSummary}",
                    errors = listOf(response.errorSummary)
                )
            }
        }
    }
}
