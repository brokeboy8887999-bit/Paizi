package com.paizi.agents.pm

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter

class ProjectManagerAgent(
    aiRouter: AIRouter
) : BaseAgent(
    roleName = "Project Manager Agent",
    description = "Coordinates project lifecycle, milestones, dependencies, and state synchronizations",
    aiRouter = aiRouter
) {
    suspend fun coordinateProject(context: AgentContext): AgentResult {
        val systemPrompt = "You are the Project Manager Agent of PAIZI. " +
                "Your role is to orchestrate software milestones, track completed and pending tasks, " +
                "and ensure strict adherence to approved requirements without scope bloat."

        val userPrompt = """
            Project: ${context.projectName} (ID: ${context.projectId})
            Requirements: ${context.requirements}
            Current Blueprint: ${context.currentBlueprint}
            Files Overview: ${context.filesOverview}
            
            Produce an executive milestone plan detailing:
            1. Current Development Phase
            2. Core Deliverables
            3. Task Allocation (Coder, Tester, Build, Docs)
            4. Risk Management & Verification Strategy
        """.trimIndent()

        val response = aiRouter.routeAndExecute(
            taskType = AIRouter.TaskType.PLANNING,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )

        return when (response) {
            is AIRouter.RouteResult.Success -> {
                AgentResult(
                    success = true,
                    summary = "Project plan created by ProjectManagerAgent",
                    outputData = response.responseText,
                    suggestedNextRole = "Software Engineer Agent"
                )
            }
            is AIRouter.RouteResult.Failure -> {
                AgentResult(
                    success = false,
                    summary = "PM coordination failed: ${response.errorSummary}",
                    errors = listOf(response.errorSummary)
                )
            }
        }
    }
}
