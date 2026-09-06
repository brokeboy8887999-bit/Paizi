package com.paizi.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProjectEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        BlueprintEntity::class,
        ApprovalHistoryEntity::class,
        ProjectMemoryEntity::class,
        OnlineProviderConfigEntity::class,
        OfflineModelEntity::class,
        BuildLogEntity::class,
        TestResultEntity::class,
        ProjectFeatureEntity::class,
        ProjectDecisionEntity::class,
        ProjectErrorEntity::class,
        DiagnosticRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PAIZIDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun blueprintDao(): BlueprintDao
    abstract fun approvalDao(): ApprovalDao
    abstract fun projectMemoryDao(): ProjectMemoryDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun offlineModelDao(): OfflineModelDao
    abstract fun buildDao(): BuildDao
    abstract fun testDao(): TestDao
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun featureDao(): FeatureDao
    abstract fun decisionDao(): DecisionDao
    abstract fun errorDao(): ErrorDao

    companion object {
        @Volatile
        private var INSTANCE: PAIZIDatabase? = null

        fun getInstance(context: Context): PAIZIDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PAIZIDatabase::class.java,
                    "paizi_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize default provider and model slots
                            CoroutineScope(Dispatchers.IO).launch {
                                populateDefaultSlots(getInstance(context))
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun ensureDefaultSlotsPopulated(db: PAIZIDatabase) {
            val providerDao = db.providerConfigDao()
            val modelDao = db.offlineModelDao()

            var defaultGeminiKey = ""
            try {
                val field = com.example.BuildConfig::class.java.getField("GEMINI_API_KEY")
                val key = field.get(null) as? String
                if (!key.isNullOrBlank() && key != "MY_GEMINI_API_KEY") {
                    defaultGeminiKey = key.trim()
                }
            } catch (_: Exception) {}

            val currentProviders = providerDao.getAllProvidersList()
            if (currentProviders.size < 10 || currentProviders.any { !it.name.startsWith("API ") }) {
                val existingKeys = currentProviders.associate { it.slotIndex to it.apiKey }
                val defaultProviders = listOf(
                    OnlineProviderConfigEntity(0, "API 1", "https://generativelanguage.googleapis.com/v1beta", existingKeys[0] ?: defaultGeminiKey, "gemini-2.0-flash", isEnabled = true, priority = 1),
                    OnlineProviderConfigEntity(1, "API 2", "https://api.openai.com/v1", existingKeys[1] ?: "", "gpt-4o", isEnabled = false, priority = 2),
                    OnlineProviderConfigEntity(2, "API 3", "https://api.anthropic.com/v1", existingKeys[2] ?: "", "claude-3-5-sonnet-20241022", isEnabled = false, priority = 3),
                    OnlineProviderConfigEntity(3, "API 4", "https://api.groq.com/openai/v1", existingKeys[3] ?: "", "llama-3.3-70b-versatile", isEnabled = false, priority = 4),
                    OnlineProviderConfigEntity(4, "API 5", "https://api.deepseek.com/v1", existingKeys[4] ?: "", "deepseek-chat", isEnabled = false, priority = 5),
                    OnlineProviderConfigEntity(5, "API 6", "http://127.0.0.1:11434/v1", existingKeys[5] ?: "", "qwen2.5-coder:7b", isEnabled = false, priority = 6),
                    OnlineProviderConfigEntity(6, "API 7", "https://openrouter.ai/api/v1", existingKeys[6] ?: "", "meta-llama/llama-3.3-70b-instruct", isEnabled = false, priority = 7),
                    OnlineProviderConfigEntity(7, "API 8", "https://api.mistral.ai/v1", existingKeys[7] ?: "", "codestral-latest", isEnabled = false, priority = 8),
                    OnlineProviderConfigEntity(8, "API 9", "https://api.together.xyz/v1", existingKeys[8] ?: "", "Qwen/Qwen2.5-Coder-32B-Instruct", isEnabled = false, priority = 9),
                    OnlineProviderConfigEntity(9, "API 10", "http://10.0.2.2:8080/v1", existingKeys[9] ?: "", "default-model", isEnabled = false, priority = 10)
                )
                for (p in defaultProviders) {
                    providerDao.insertOrUpdateProvider(p)
                }
            } else {
                // If API 1 has blank key and BuildConfig has it, populate it
                val slot0 = providerDao.getProviderBySlot(0)
                if (slot0 != null && slot0.apiKey.isBlank() && defaultGeminiKey.isNotBlank()) {
                    providerDao.insertOrUpdateProvider(slot0.copy(apiKey = defaultGeminiKey))
                }
            }

            // Strictly the exact 5 offline models
            val currentModels = modelDao.getAllOfflineModelsList()
            val expectedNames = setOf("DeepSeek V4 Pro", "LLaMA 4", "Qwen 3.5", "Mistral", "Lama Queen 2.5")
            if (currentModels.size != 5 || currentModels.any { it.name !in expectedNames }) {
                modelDao.deleteAllModels()
                populateOfflineModels(db)
            }

            // Ensure default project and conversation exist so chat works immediately
            val projectDao = db.projectDao()
            if (projectDao.getProjectById("default_project") == null) {
                projectDao.insertProject(
                    ProjectEntity(
                        id = "default_project",
                        name = "PAIZI Assistant",
                        type = "Android Compose",
                        concept = "Conversational AI and Software Engineering",
                        status = "READY",
                        rootPath = "default"
                    )
                )
            }

            val conversationDao = db.conversationDao()
            if (conversationDao.getConversationById("conv_default") == null) {
                conversationDao.insertConversation(
                    ConversationEntity(
                        id = "conv_default",
                        projectId = "default_project",
                        title = "General Chat"
                    )
                )
            }
        }

        suspend fun populateDefaultSlots(db: PAIZIDatabase) {
            ensureDefaultSlotsPopulated(db)
        }

        private suspend fun populateOfflineModels(db: PAIZIDatabase) {
            val modelDao = db.offlineModelDao()
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
            for (m in models) {
                modelDao.insertOrUpdateModel(m)
            }
        }
    }
}
