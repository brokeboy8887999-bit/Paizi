package com.paizi.agents.browser

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.browser.BrowserController

class BrowserAgent(
    aiRouter: AIRouter,
    private val browserController: BrowserController
) : BaseAgent(
    roleName = "Browser Agent",
    description = "Searches developer documentation, verifies API endpoints, and retrieves web documentation",
    aiRouter = aiRouter
) {
    suspend fun lookupDocumentation(query: String, context: AgentContext): AgentResult {
        val result = browserController.fetchWebOrSearch(query)

        val systemPrompt = "You are the Browser Agent in PAIZI. Summarize technical documentation relevant to: $query"
        val userPrompt = "Query: $query\nSearch results:\n$result"

        val summary = aiRouter.routeAndExecute(
            taskType = AIRouter.TaskType.GENERAL,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )

        return when (summary) {
            is AIRouter.RouteResult.Success -> {
                AgentResult(
                    success = true,
                    summary = "Retrieved web documentation for '$query'",
                    outputData = summary.responseText
                )
            }
            is AIRouter.RouteResult.Failure -> {
                AgentResult(
                    success = true,
                    summary = "Raw documentation retrieved",
                    outputData = result
                )
            }
        }
    }
}
