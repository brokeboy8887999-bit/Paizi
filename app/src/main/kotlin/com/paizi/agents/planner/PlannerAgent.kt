package com.paizi.agents.planner

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.core.logging.LoggingManager

class PlannerAgent(
    aiRouter: AIRouter
) : BaseAgent("Planner", "Architects dynamic blueprints from requirements", aiRouter) {

    private val TAG = "PlannerAgent"

    suspend fun generateBlueprint(context: AgentContext): AgentResult {
        LoggingManager.i(TAG, "Generating dynamic blueprint for project '${context.projectName}'")

        val systemPrompt = """
            You are PAIZI's Master Software Architect Agent (PlannerAgent).
            Your task is to analyze the user's software requirement and create a comprehensive, production-grade, dynamic engineering blueprint.
            The blueprint MUST be uniquely tailored to the specific concept and must NEVER use generic placeholders or hardcoded mockups.

            Structure the blueprint using the following mandatory sections:
            # 1. Project Identity & Concept
            - Project Name:
            - Target Platform: Android (Jetpack Compose) / Kotlin
            - Core Concept & Value Proposition:
            - Primary Goals & Metrics:

            # 2. Detailed Requirements
            - Functional Requirements (numbered list with acceptance criteria):
            - Non-Functional Requirements (Performance, Battery, Memory, Accessibility):

            # 3. User Interface & Screen Architecture
            - Screen List:
            - Layouts & Composables per Screen:
            - Interactive Buttons, Gestures & State-driven Actions:
            - Navigation Graph & Backstack Routing:

            # 4. Technical Architecture
            - Software Architecture: MVVM with Repository Pattern
            - Local Database Schema & Room Entities:
            - Networking & API Services:
            - AI Subsystem Integration:
            - Security, Sandbox Boundaries & Permissions:

            # 5. Testing & Verification Plan
            - Local Unit Test Suites:
            - Robolectric JVM Test Cases:
            - Security & Boundary Validation Tests:

            # 6. Build & Dependency Specifications
            - Android Gradle Plugin & SDK Requirements:
            - Third-party Libraries (Room, OkHttp, Compose BOM):
            - Deployment & Release Packaging Requirements:
        """.trimIndent()

        val userPrompt = """
            Requirement:
            ${context.requirements}

            Additional Project Context:
            Name: ${context.projectName}
            ${context.additionalInstructions}
        """.trimIndent()

        return when (val routeResult = aiRouter.routeAndExecute(AIRouter.TaskType.PLANNING, systemPrompt, userPrompt)) {
            is AIRouter.RouteResult.Success -> {
                LoggingManager.i(TAG, "Blueprint successfully generated via ${routeResult.source}")
                AgentResult(
                    success = true,
                    summary = "Dynamic blueprint created successfully via ${routeResult.source}",
                    outputData = routeResult.responseText,
                    suggestedNextRole = "Reviewer"
                )
            }
            is AIRouter.RouteResult.Failure -> {
                LoggingManager.e(TAG, "Planner routing failed: ${routeResult.errorSummary}")
                AgentResult(
                    success = false,
                    summary = routeResult.errorSummary,
                    outputData = "",
                    suggestedNextRole = null,
                    errors = listOf(routeResult.errorSummary, routeResult.actionableAdvice)
                )
            }
        }
    }
}
