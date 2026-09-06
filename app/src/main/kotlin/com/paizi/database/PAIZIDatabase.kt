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

        suspend fun populateDefaultSlots(db: PAIZIDatabase) {
            val providerDao = db.providerConfigDao()
            val providers = listOf(
                OnlineProviderConfigEntity(0, "Gemini 2.5 Flash", "https://generativelanguage.googleapis.com/v1beta", "", "gemini-2.5-flash", isEnabled = true, priority = 1),
                OnlineProviderConfigEntity(1, "OpenAI GPT-4o", "https://api.openai.com/v1", "", "gpt-4o", isEnabled = false, priority = 2),
                OnlineProviderConfigEntity(2, "Anthropic Claude 3.5 Sonnet", "https://api.anthropic.com/v1", "", "claude-3-5-sonnet-20241022", isEnabled = false, priority = 3),
                OnlineProviderConfigEntity(3, "Groq Llama-3.3-70b", "https://api.groq.com/openai/v1", "", "llama-3.3-70b-versatile", isEnabled = false, priority = 4),
                OnlineProviderConfigEntity(4, "DeepSeek Coder V2", "https://api.deepseek.com/v1", "", "deepseek-coder", isEnabled = false, priority = 5),
                OnlineProviderConfigEntity(5, "Ollama Local API", "http://127.0.0.1:11434/v1", "", "qwen2.5-coder:7b", isEnabled = false, priority = 6),
                OnlineProviderConfigEntity(6, "OpenRouter Multi", "https://openrouter.ai/api/v1", "", "meta-llama/llama-3.3-70b-instruct", isEnabled = false, priority = 7),
                OnlineProviderConfigEntity(7, "Mistral Codestral", "https://api.mistral.ai/v1", "", "codestral-latest", isEnabled = false, priority = 8),
                OnlineProviderConfigEntity(8, "Together AI", "https://api.together.xyz/v1", "", "Qwen/Qwen2.5-Coder-32B-Instruct", isEnabled = false, priority = 9),
                OnlineProviderConfigEntity(9, "Custom OpenAI-Compatible", "http://10.0.2.2:8080/v1", "", "default-model", isEnabled = false, priority = 10)
            )
            for (p in providers) {
                providerDao.insertOrUpdateProvider(p)
            }

            val modelDao = db.offlineModelDao()
            val models = listOf(
                OfflineModelEntity(
                    slotIndex = 0,
                    name = "Qwen2.5-Coder 0.5B Instruct (GGUF)",
                    downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
                    fileName = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
                    fileSize = 398_000_000L,
                    sha256Hash = "e87cf80b4ff971933a296b528a2a89cb517df47321e8dca963dfa369255beeb0",
                    architecture = "Qwen2.5 / GGUF Q4_K_M",
                    contextSize = 4096,
                    status = "NOT_CONFIGURED"
                ),
                OfflineModelEntity(
                    slotIndex = 1,
                    name = "Qwen2.5-Coder 1.5B Instruct (GGUF)",
                    downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                    fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                    fileSize = 986_000_000L,
                    sha256Hash = "a41766a5bc3fa71a81dc19385c2c7d9bc213eeecdb7cbb11566cfdcf7564d6db",
                    architecture = "Qwen2.5 / GGUF Q4_K_M",
                    contextSize = 8192,
                    status = "NOT_CONFIGURED"
                ),
                OfflineModelEntity(
                    slotIndex = 2,
                    name = "Llama-3.2-1B-Instruct (GGUF)",
                    downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                    fileName = "llama-3.2-1b-instruct-q4_k_m.gguf",
                    fileSize = 780_000_000L,
                    sha256Hash = "8d1fe9c3da13f8ef05c48b2a1a2b97c7f4e9a05bc1335b2e67df14084f877d12",
                    architecture = "Llama 3.2 / GGUF Q4_K_M",
                    contextSize = 4096,
                    status = "NOT_CONFIGURED"
                ),
                OfflineModelEntity(
                    slotIndex = 3,
                    name = "SmolLM2-135M-Instruct (GGUF)",
                    downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q4_k_m.gguf",
                    fileName = "smollm2-135m-instruct-q4_k_m.gguf",
                    fileSize = 95_000_000L,
                    sha256Hash = "5a02476e3d2319ef2a3a5f9eb6e21b791dc0ba644d6db8b4ecf7f185d03154fe",
                    architecture = "SmolLM2 / GGUF Q4_K_M",
                    contextSize = 2048,
                    status = "NOT_CONFIGURED"
                ),
                OfflineModelEntity(
                    slotIndex = 4,
                    name = "Custom Local GGUF Model",
                    downloadUrl = "",
                    fileName = "custom-model.gguf",
                    fileSize = 0L,
                    sha256Hash = "",
                    architecture = "GGUF Custom",
                    contextSize = 4096,
                    status = "NOT_CONFIGURED"
                )
            )
            for (m in models) {
                modelDao.insertOrUpdateModel(m)
            }
        }
    }
}
