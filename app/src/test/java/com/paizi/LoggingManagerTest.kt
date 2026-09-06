package com.paizi

import com.paizi.core.logging.LoggingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingManagerTest {

    @Test
    fun `test secret redaction removes openai key patterns`() {
        val raw = "Bearer sk-12345678901234567890abcdef"
        val redacted = LoggingManager.redact(raw)
        assertFalse(redacted.contains("sk-12345678901234567890abcdef"))
        assertTrue(redacted.contains("[REDACTED"))
    }

    @Test
    fun `test secret redaction removes gemini key patterns`() {
        val raw = "API Key: AIzaSyD12345678901234567890123456789012"
        val redacted = LoggingManager.redact(raw)
        assertFalse(redacted.contains("AIzaSyD12345678901234567890123456789012"))
        assertTrue(redacted.contains("[REDACTED_GEMINI_KEY]"))
    }

    @Test
    fun `test custom registered secret is redacted`() {
        val customSecret = "super_secret_custom_token_999"
        LoggingManager.registerSecret(customSecret)
        val text = "Request header Authorization: $customSecret"
        val redacted = LoggingManager.redact(text)
        assertFalse(redacted.contains(customSecret))
        assertTrue(redacted.contains("[REDACTED_SECRET]"))
    }
}
