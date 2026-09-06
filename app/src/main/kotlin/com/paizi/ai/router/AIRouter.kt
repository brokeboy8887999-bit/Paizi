package com.paizi.ai.router

import com.paizi.ai.offline.OfflineAIManager
import com.paizi.ai.online.OnlineAIManager
import com.paizi.core.logging.LoggingManager
import com.paizi.database.OfflineModelDao
import com.paizi.database.ProviderConfigDao

class AIRouter(
    private val providerConfigDao: ProviderConfigDao,
    private val offlineModelDao: OfflineModelDao,
    private val onlineAIManager: OnlineAIManager,
    private val offlineAIManager: OfflineAIManager
) {
    private val TAG = "AIRouter"

    enum class TaskType {
        PLANNING, CODING, DEBUGGING, REASONING, GENERAL
    }

    sealed class RouteResult {
        data class Success(
            val responseText: String,
            val source: String, // "Offline Model: <name>" or "Online Provider: <name>"
            val modelName: String,
            val tokenUsage: Int,
            val fallbackChain: List<String> = emptyList()
        ) : RouteResult()

        data class Failure(
            val errorSummary: String,
            val attemptedSources: List<String>,
            val actionableAdvice: String
        ) : RouteResult()
    }

    suspend fun routeAndExecute(
        taskType: TaskType,
        systemPrompt: String,
        userPrompt: String,
        preferLocal: Boolean = false
    ): RouteResult {
        val attemptedSources = mutableListOf<String>()
        val fallbackChain = mutableListOf<String>()

        // Step 1: Check Local Offline Model if requested or active
        val activeOfflineModel = offlineModelDao.getActiveModel()
        if (activeOfflineModel != null && (preferLocal || activeOfflineModel.status == "ACTIVE" || activeOfflineModel.status == "LOADED")) {
            attemptedSources.add("Offline: ${activeOfflineModel.name}")
            try {
                LoggingManager.i(TAG, "Routing $taskType to active offline model: ${activeOfflineModel.name}")
                val fullPrompt = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n$userPrompt" else userPrompt
                val inference = offlineAIManager.generateInference(fullPrompt)
                return RouteResult.Success(
                    responseText = inference,
                    source = "Offline GGUF (${activeOfflineModel.name})",
                    modelName = activeOfflineModel.name,
                    tokenUsage = 0,
                    fallbackChain = fallbackChain
                )
            } catch (e: Exception) {
                LoggingManager.w(TAG, "Offline inference failed for ${activeOfflineModel.name}: ${e.message}. Attempting online fallback.")
                fallbackChain.add("Offline failed: ${e.message}")
            }
        }

        // Step 2: Query enabled Online Providers ordered by priority
        val enabledProviders = providerConfigDao.getEnabledProviders()
        if (enabledProviders.isEmpty()) {
            LoggingManager.w(TAG, "No online providers are currently enabled.")
            return RouteResult.Failure(
                errorSummary = "No AI providers configured or enabled.",
                attemptedSources = attemptedSources,
                actionableAdvice = "Open 'AI Settings' in the navigation drawer and enable at least one online provider or download an offline model."
            )
        }

        for (provider in enabledProviders) {
            val providerTag = "Online: ${provider.name} (${provider.modelName})"
            attemptedSources.add(providerTag)
            LoggingManager.i(TAG, "Attempting request via $providerTag for $taskType")

            val response = onlineAIManager.executeRequest(
                provider = provider,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt
            )

            when (response) {
                is OnlineAIManager.OnlineAiResponse.Success -> {
                    LoggingManager.i(TAG, "Successful response from $providerTag")
                    return RouteResult.Success(
                        responseText = response.text,
                        source = "Online Provider (${provider.name})",
                        modelName = response.model,
                        tokenUsage = response.tokenUsage,
                        fallbackChain = fallbackChain
                    )
                }
                is OnlineAIManager.OnlineAiResponse.Error -> {
                    val logMsg = "Provider ${provider.name} failed with ${response.errorType}: ${response.message}"
                    LoggingManager.w(TAG, logMsg)
                    fallbackChain.add(logMsg)
                    // Continue loop to fallback to next enabled provider
                }
            }
        }

        // Step 3: All providers failed
        LoggingManager.e(TAG, "All candidate AI routes exhausted without success: $attemptedSources")
        return RouteResult.Failure(
            errorSummary = "All configured AI providers failed to return a response.",
            attemptedSources = attemptedSources,
            actionableAdvice = "Verify network connection and API key configurations in 'AI Settings'. Fallback history:\n" +
                    fallbackChain.joinToString("\n") { "• $it" }
        )
    }
}
