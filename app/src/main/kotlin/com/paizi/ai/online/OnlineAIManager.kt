package com.paizi.ai.online

import com.example.BuildConfig
import com.paizi.core.logging.LoggingManager
import com.paizi.database.OnlineProviderConfigEntity
import com.paizi.database.ProviderConfigDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class OnlineAIManager(
    private val providerConfigDao: ProviderConfigDao
) {
    private val TAG = "OnlineAIManager"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ConnectionTestResult(
        val success: Boolean,
        val latencyMs: Long,
        val message: String,
        val snippet: String? = null
    )

    /**
     * Executes an AI completion request against a specific provider slot.
     */
    suspend fun executeRequest(
        provider: OnlineProviderConfigEntity,
        systemPrompt: String,
        userPrompt: String
    ): OnlineAiResponse = withContext(Dispatchers.IO) {
        val effectiveApiKey = resolveApiKey(provider)
        LoggingManager.registerSecret(effectiveApiKey)

        val timeoutSeconds = if (provider.timeoutSeconds > 0) provider.timeoutSeconds else 60L
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        val isGemini = provider.baseUrl.contains("generativelanguage.googleapis.com") ||
                provider.name.contains("Gemini", ignoreCase = true) ||
                provider.modelName.contains("gemini", ignoreCase = true) ||
                effectiveApiKey.startsWith("AIza")
        val isAnthropic = provider.baseUrl.contains("anthropic.com") ||
                provider.name.contains("Claude", ignoreCase = true) ||
                provider.name.contains("Anthropic", ignoreCase = true) ||
                provider.modelName.contains("claude", ignoreCase = true) ||
                effectiveApiKey.startsWith("sk-ant")

        try {
            when {
                isGemini -> executeGeminiRequest(client, provider, effectiveApiKey, systemPrompt, userPrompt)
                isAnthropic -> executeAnthropicRequest(client, provider, effectiveApiKey, systemPrompt, userPrompt)
                else -> executeOpenAiCompatibleRequest(client, provider, effectiveApiKey, systemPrompt, userPrompt)
            }
        } catch (e: SocketTimeoutException) {
            LoggingManager.w(TAG, "Timeout calling provider ${provider.name}")
            OnlineAiResponse.Error(ErrorType.TIMEOUT, "Request timed out after ${timeoutSeconds}s: ${e.message}")
        } catch (e: UnknownHostException) {
            LoggingManager.w(TAG, "Network unavailable for ${provider.name}")
            OnlineAiResponse.Error(ErrorType.NETWORK_UNAVAILABLE, "Network unreachable: cannot resolve host (${e.message})")
        } catch (e: OnlineAiException) {
            LoggingManager.w(TAG, "Provider error (${e.type}) for ${provider.name}: ${e.message}")
            OnlineAiResponse.Error(e.type, e.message ?: "Unknown provider error")
        } catch (e: Exception) {
            LoggingManager.e(TAG, "Unexpected error calling ${provider.name}", e)
            OnlineAiResponse.Error(ErrorType.HTTP_ERROR, "Unexpected error: ${e.message}")
        }
    }

    /**
     * Performs a 100% REAL connection test by sending a minimal live prompt to the provider.
     * Never returns fake success.
     */
    suspend fun testConnection(provider: OnlineProviderConfigEntity): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val effectiveApiKey = resolveApiKey(provider)

        if (effectiveApiKey.isBlank() && !provider.baseUrl.contains("127.0.0.1") && !provider.baseUrl.contains("10.0.2.2") && !provider.baseUrl.contains("localhost")) {
            return@withContext ConnectionTestResult(
                success = false,
                latencyMs = 0,
                message = "API key is missing. Enter a valid key for ${provider.name} and save before testing."
            )
        }

        val response = executeRequest(
            provider = provider,
            systemPrompt = "You are a connectivity test assistant.",
            userPrompt = "Test connection probe. Reply with the single word: READY."
        )

        val latencyMs = System.currentTimeMillis() - startTime

        when (response) {
            is OnlineAiResponse.Success -> {
                ConnectionTestResult(
                    success = true,
                    latencyMs = latencyMs,
                    message = "Connection successful! Received response in ${latencyMs}ms from ${provider.modelName}.",
                    snippet = response.text.trim().take(120)
                )
            }
            is OnlineAiResponse.Error -> {
                ConnectionTestResult(
                    success = false,
                    latencyMs = latencyMs,
                    message = "Failed: [${response.errorType}] ${response.message}"
                )
            }
        }
    }

    fun resolveApiKey(provider: OnlineProviderConfigEntity): String {
        if (provider.apiKey.isNotBlank()) {
            return provider.apiKey.trim()
        }
        // Fallback to BuildConfig.GEMINI_API_KEY if this is a Gemini provider
        if (provider.baseUrl.contains("generativelanguage.googleapis.com") ||
            provider.name.contains("Gemini", ignoreCase = true) ||
            provider.modelName.contains("gemini", ignoreCase = true) ||
            provider.slotIndex == 0) {
            try {
                val geminiField = BuildConfig::class.java.getField("GEMINI_API_KEY")
                val key = geminiField.get(null) as? String
                if (!key.isNullOrBlank() && key != "MY_GEMINI_API_KEY") {
                    return key.trim()
                }
            } catch (_: Exception) {}
        }
        return ""
    }

    private fun executeGeminiRequest(
        client: OkHttpClient,
        provider: OnlineProviderConfigEntity,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ): OnlineAiResponse {
        if (apiKey.isBlank()) {
            throw OnlineAiException(ErrorType.AUTHENTICATION, "API key is required for Gemini provider (${provider.name})")
        }

        // Normalize base URL
        var baseUrl = provider.baseUrl.trim().trimEnd('/')
        if (!baseUrl.contains("/v1") && !baseUrl.contains("/v1beta")) {
            baseUrl = "$baseUrl/v1beta"
        }

        // Normalize model name (fallback from invalid gemini-2.5-flash to gemini-2.0-flash)
        val normalizedModel = if (provider.modelName.trim() == "gemini-2.5-flash") "gemini-2.0-flash" else provider.modelName.trim()

        val url = "$baseUrl/models/$normalizedModel:generateContent?key=$apiKey"

        val contents = JSONArray()
        if (systemPrompt.isNotBlank()) {
            val sysPart = JSONObject().put("text", "System: $systemPrompt\n\nUser Prompt:")
            val sysContent = JSONObject().put("role", "user").put("parts", JSONArray().put(sysPart))
            contents.put(sysContent)
        }
        val userPart = JSONObject().put("text", userPrompt)
        val userContent = JSONObject().put("role", "user").put("parts", JSONArray().put(userPart))
        contents.put(userContent)

        val payload = JSONObject().put("contents", contents)
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                handleHttpError(response.code, responseBody)
            }

            try {
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.getJSONObject("content")
                    val parts = contentObj.getJSONArray("parts")
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        textBuilder.append(parts.getJSONObject(i).optString("text", ""))
                    }
                    val tokenUsage = json.optJSONObject("usageMetadata")?.optInt("totalTokenCount", 0) ?: 0
                    return OnlineAiResponse.Success(
                        text = textBuilder.toString(),
                        tokenUsage = tokenUsage,
                        model = normalizedModel,
                        provider = provider.name
                    )
                } else {
                    throw OnlineAiException(ErrorType.INVALID_RESPONSE, "Gemini response returned no candidates: $responseBody")
                }
            } catch (e: Exception) {
                if (e is OnlineAiException) throw e
                throw OnlineAiException(ErrorType.INVALID_RESPONSE, "Failed to parse Gemini JSON: ${e.message}")
            }
        }
    }

    private fun executeAnthropicRequest(
        client: OkHttpClient,
        provider: OnlineProviderConfigEntity,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ): OnlineAiResponse {
        if (apiKey.isBlank()) {
            throw OnlineAiException(ErrorType.AUTHENTICATION, "API key is required for Anthropic Claude (${provider.name})")
        }

        val endpoint = if (provider.baseUrl.endsWith("/messages")) {
            provider.baseUrl
        } else {
            "${provider.baseUrl.trimEnd('/')}/messages"
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val payload = JSONObject().apply {
            put("model", provider.modelName)
            put("max_tokens", 4096)
            put("messages", messages)
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                handleHttpError(response.code, responseBody)
            }

            try {
                val json = JSONObject(responseBody)
                val contentArray = json.getJSONArray("content")
                val textBuilder = StringBuilder()
                for (i in 0 until contentArray.length()) {
                    val part = contentArray.getJSONObject(i)
                    if (part.optString("type") == "text") {
                        textBuilder.append(part.optString("text", ""))
                    }
                }
                val usage = json.optJSONObject("usage")?.optInt("output_tokens", 0) ?: 0
                return OnlineAiResponse.Success(
                    text = textBuilder.toString(),
                    tokenUsage = usage,
                    model = provider.modelName,
                    provider = provider.name
                )
            } catch (e: Exception) {
                if (e is OnlineAiException) throw e
                throw OnlineAiException(ErrorType.INVALID_RESPONSE, "Failed to parse Anthropic JSON: ${e.message}")
            }
        }
    }

    private fun executeOpenAiCompatibleRequest(
        client: OkHttpClient,
        provider: OnlineProviderConfigEntity,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ): OnlineAiResponse {
        val endpoint = if (provider.baseUrl.endsWith("/chat/completions")) {
            provider.baseUrl
        } else {
            "${provider.baseUrl.trimEnd('/')}/chat/completions"
        }

        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))

        val payload = JSONObject().apply {
            put("model", provider.modelName)
            put("messages", messages)
            put("temperature", 0.7)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        // Apply custom headers if present
        try {
            val customHeaders = JSONObject(provider.customHeadersJson)
            val keys = customHeaders.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                requestBuilder.addHeader(k, customHeaders.getString(k))
            }
        } catch (_: Exception) {}

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                handleHttpError(response.code, responseBody)
            }

            try {
                val json = JSONObject(responseBody)
                val choices = json.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    val content = message.getString("content")
                    val usage = json.optJSONObject("usage")?.optInt("total_tokens", 0) ?: 0
                    return OnlineAiResponse.Success(
                        text = content,
                        tokenUsage = usage,
                        model = provider.modelName,
                        provider = provider.name
                    )
                } else {
                    throw OnlineAiException(ErrorType.INVALID_RESPONSE, "Empty choices list from provider")
                }
            } catch (e: Exception) {
                if (e is OnlineAiException) throw e
                throw OnlineAiException(ErrorType.INVALID_RESPONSE, "Failed to parse provider response: ${e.message}")
            }
        }
    }

    private fun handleHttpError(code: Int, body: String) {
        val sanitizedBody = LoggingManager.redact(body)
        when (code) {
            401, 403 -> throw OnlineAiException(ErrorType.AUTHENTICATION, "HTTP $code Authentication failed: $sanitizedBody")
            429 -> throw OnlineAiException(ErrorType.RATE_LIMIT, "HTTP 429 Rate limit exceeded: $sanitizedBody")
            404 -> throw OnlineAiException(ErrorType.MODEL_UNAVAILABLE, "HTTP 404 Model or endpoint not found: $sanitizedBody")
            in 500..599 -> throw OnlineAiException(ErrorType.HTTP_ERROR, "HTTP $code Server Error: $sanitizedBody")
            else -> throw OnlineAiException(ErrorType.HTTP_ERROR, "HTTP $code Error: $sanitizedBody")
        }
    }

    enum class ErrorType {
        TIMEOUT, AUTHENTICATION, RATE_LIMIT, HTTP_ERROR, INVALID_RESPONSE, NETWORK_UNAVAILABLE, MODEL_UNAVAILABLE
    }

    class OnlineAiException(val type: ErrorType, message: String) : IOException(message)

    sealed class OnlineAiResponse {
        data class Success(
            val text: String,
            val tokenUsage: Int,
            val model: String,
            val provider: String
        ) : OnlineAiResponse()

        data class Error(
            val errorType: ErrorType,
            val message: String
        ) : OnlineAiResponse()
    }
}
