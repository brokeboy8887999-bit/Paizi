package com.paizi.agents.base

import com.paizi.ai.router.AIRouter

abstract class BaseAgent(
    val roleName: String,
    val description: String,
    protected val aiRouter: AIRouter
) {
    data class AgentContext(
        val projectId: String,
        val projectName: String,
        val requirements: String,
        val currentBlueprint: String = "",
        val filesOverview: String = "",
        val lastBuildError: String? = null,
        val lastTestFailure: String? = null,
        val additionalInstructions: String = ""
    )

    data class AgentResult(
        val success: Boolean,
        val summary: String,
        val outputData: String = "",
        val suggestedNextRole: String? = null,
        val errors: List<String> = emptyList()
    )
}
