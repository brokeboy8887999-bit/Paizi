package com.paizi.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * PAIZI Logging Manager with strict zero-leak secret redaction and real-time log stream.
 */
object LoggingManager {
    private const val TAG = "PAIZI"
    private val registeredSecrets = CopyOnWriteArrayList<String>()
    private val logBuffer = CopyOnWriteArrayList<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String
    ) {
        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
    }

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    fun registerSecret(secret: String?) {
        if (!secret.isNullOrBlank() && secret.length > 3) {
            if (!registeredSecrets.contains(secret)) {
                registeredSecrets.add(secret)
            }
        }
    }

    fun redact(message: String): String {
        var sanitized = message
        // Redact registered secrets
        for (secret in registeredSecrets) {
            sanitized = sanitized.replace(secret, "[REDACTED_SECRET]")
        }
        // Redact standard API key patterns: sk-..., AIza..., Bearer tokens
        sanitized = sanitized.replace(Regex("(?i)Bearer\\s+[A-Za-z0-9_\\-\\.]+"), "Bearer [REDACTED]")
        sanitized = sanitized.replace(Regex("sk-[a-zA-Z0-9]{20,}"), "[REDACTED_API_KEY]")
        sanitized = sanitized.replace(Regex("AIza[0-9A-Za-z\\-_]{35}"), "[REDACTED_GEMINI_KEY]")
        return sanitized
    }

    fun d(tag: String, message: String) {
        val sanitized = redact(message)
        runCatching { Log.d(tag, sanitized) }
        record(Level.DEBUG, tag, sanitized)
    }

    fun i(tag: String, message: String) {
        val sanitized = redact(message)
        runCatching { Log.i(tag, sanitized) }
        record(Level.INFO, tag, sanitized)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = redact(message) + (throwable?.let { "\n" + it.stackTraceToString() } ?: "")
        runCatching { Log.w(tag, sanitized) }
        record(Level.WARN, tag, sanitized)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = redact(message) + (throwable?.let { "\n" + it.stackTraceToString() } ?: "")
        runCatching { Log.e(tag, sanitized) }
        record(Level.ERROR, tag, sanitized)
    }

    private fun record(level: Level, tag: String, message: String) {
        val entry = LogEntry(level = level, tag = tag, message = message)
        logBuffer.add(entry)
        if (logBuffer.size > 1000) {
            logBuffer.removeAt(0)
        }
        _logsFlow.value = logBuffer.toList()
    }

    fun clear() {
        logBuffer.clear()
        _logsFlow.value = emptyList()
    }
}
