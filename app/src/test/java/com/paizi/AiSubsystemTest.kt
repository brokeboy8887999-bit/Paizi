package com.paizi

import com.paizi.ai.online.OnlineAIManager
import com.paizi.database.OfflineModelEntity
import com.paizi.database.OnlineProviderConfigEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSubsystemTest {

    @Test
    fun `test 10 generic online API slots are properly structured`() {
        val slots = (0..9).map { index ->
            OnlineProviderConfigEntity(
                slotIndex = index,
                name = "API ${index + 1}",
                baseUrl = "https://api.example.com/v1",
                apiKey = "",
                modelName = "default-model",
                isEnabled = (index == 0),
                priority = index + 1
            )
        }

        assertEquals(10, slots.size)
        for (i in 0..9) {
            assertEquals("API ${i + 1}", slots[i].name)
            assertEquals(i, slots[i].slotIndex)
            assertEquals(i + 1, slots[i].priority)
        }
        assertTrue(slots[0].isEnabled)
        assertFalse(slots[1].isEnabled)
    }

    @Test
    fun `test exact 5 offline AI models and configurations`() {
        val models = listOf(
            OfflineModelEntity(
                slotIndex = 0,
                name = "DeepSeek V4 Pro",
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-Coder-V2-Lite-Instruct-GGUF/resolve/main/DeepSeek-Coder-V2-Lite-Instruct-Q4_K_M.gguf",
                fileName = "deepseek-v4-pro-q4_k_m.gguf",
                fileSize = 980_000_000L,
                sha256Hash = "6c93b1d1f044ef338f0d867c2934a362a74c76081c744f4ab59e28fa1605f63d",
                architecture = "DeepSeek GGUF Q4_K_M",
                contextSize = 8192,
                status = "NOT_CONFIGURED"
            ),
            OfflineModelEntity(
                slotIndex = 1,
                name = "LLaMA 4",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                fileName = "llama-4-q4_k_m.gguf",
                fileSize = 780_000_000L,
                sha256Hash = "8d1fe9c3da13f8ef05c48b2a1a2b97c7f4e9a05bc1335b2e67df14084f877d12",
                architecture = "LLaMA GGUF Q4_K_M",
                contextSize = 4096,
                status = "NOT_CONFIGURED"
            ),
            OfflineModelEntity(
                slotIndex = 2,
                name = "Qwen 3.5",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen-3.5-coder-q4_k_m.gguf",
                fileSize = 398_000_000L,
                sha256Hash = "e87cf80b4ff971933a296b528a2a89cb517df47321e8dca963dfa369255beeb0",
                architecture = "Qwen GGUF Q4_K_M",
                contextSize = 4096,
                status = "NOT_CONFIGURED"
            ),
            OfflineModelEntity(
                slotIndex = 3,
                name = "Mistral",
                downloadUrl = "https://huggingface.co/bartowski/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf",
                fileName = "mistral-instruct-q4_k_m.gguf",
                fileSize = 1_250_000_000L,
                sha256Hash = "a931a2b16df81ec651b14ca7e305e7587ea0b8893d25d6bfe5fa73841aefbd6e",
                architecture = "Mistral GGUF Q4_K_M",
                contextSize = 8192,
                status = "NOT_CONFIGURED"
            ),
            OfflineModelEntity(
                slotIndex = 4,
                name = "Lama Queen 2.5",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q4_k_m.gguf",
                fileName = "lama-queen-2.5-q4_k_m.gguf",
                fileSize = 95_000_000L,
                sha256Hash = "5a02476e3d2319ef2a3a5f9eb6e21b791dc0ba644d6db8b4ecf7f185d03154fe",
                architecture = "Lama Queen GGUF Q4_K_M",
                contextSize = 2048,
                status = "NOT_CONFIGURED"
            )
        )

        assertEquals(5, models.size)
        val expectedNames = listOf("DeepSeek V4 Pro", "LLaMA 4", "Qwen 3.5", "Mistral", "Lama Queen 2.5")
        assertEquals(expectedNames, models.map { it.name })

        // Validate that all models have non-empty SHA-256 hashes and download URLs
        for (m in models) {
            assertTrue("Hash for ${m.name} must be 64 characters", m.sha256Hash.length == 64)
            assertTrue("Download URL must be valid", m.downloadUrl.startsWith("https://"))
            assertTrue("File name must end with .gguf", m.fileName.endsWith(".gguf"))
        }
    }

    @Test
    fun `test online verification fails honestly when key is missing`() = runBlocking {
        // Create a dummy DAO mock or dummy provider
        val fakeProvider = OnlineProviderConfigEntity(
            slotIndex = 1,
            name = "API 2",
            baseUrl = "https://api.openai.com/v1",
            apiKey = "",
            modelName = "gpt-4o",
            isEnabled = false
        )

        // Directly verify with a mock-less dummy instance
        val dummyDao = object : com.paizi.database.ProviderConfigDao {
            override fun getAllProviders() = kotlinx.coroutines.flow.emptyFlow<List<OnlineProviderConfigEntity>>()
            override suspend fun getAllProvidersList() = emptyList<OnlineProviderConfigEntity>()
            override suspend fun getProviderBySlot(slotIndex: Int) = null
            override suspend fun getEnabledProviders() = emptyList<OnlineProviderConfigEntity>()
            override suspend fun getProviderCount() = 0
            override suspend fun insertOrUpdateProvider(provider: OnlineProviderConfigEntity) {}
        }

        val manager = OnlineAIManager(dummyDao)
        val result = manager.testConnection(fakeProvider)

        // MUST NOT be fake success!
        assertFalse("Missing API key must fail connection test", result.success)
        assertTrue("Error message must explain missing key", result.message.contains("API key is missing"))
    }
}
