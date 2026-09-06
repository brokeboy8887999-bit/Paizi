package com.paizi.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "online_providers")
data class OnlineProviderConfigEntity(
    @PrimaryKey val slotIndex: Int, // 0..9 for 10 slots
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val isEnabled: Boolean = false,
    val priority: Int = slotIndex,
    val timeoutSeconds: Long = 60L,
    val customHeadersJson: String = "{}"
)

@Entity(tableName = "offline_models")
data class OfflineModelEntity(
    @PrimaryKey val slotIndex: Int, // 0..4 for 5 slots
    val name: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val sha256Hash: String,
    val architecture: String, // e.g. "GGUF-Q4_K_M", "Qwen2.5-Coder"
    val contextSize: Int = 4096,
    val status: String, // NOT_CONFIGURED, DOWNLOADING, PAUSED, PARTIAL, VERIFYING, INSTALLED, LOADING, LOADED, ACTIVE, ERROR
    val downloadedBytes: Long = 0L,
    val localFilePath: String = "",
    val lastError: String? = null
)

@Entity(
    tableName = "build_logs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class BuildLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val target: String,
    val status: String, // SUCCESS, FAILED, RUNNING
    val outputLogs: String,
    val exitCode: Int,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "test_results",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class TestResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val testName: String,
    val inputData: String,
    val expectedOutput: String,
    val actualOutput: String,
    val result: String, // PASS, FAIL, SKIPPED
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val buildVersion: String = "1.0.0",
    val environment: String = "Android Local JVM"
)

@Entity(
    tableName = "project_features",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ProjectFeatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "project_decisions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ProjectDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val decision: String,
    val rationale: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "project_errors",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ProjectErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val attemptNumber: Int,
    val errorMessage: String,
    val rootCause: String,
    val proposedFix: String,
    val fixApplied: Boolean = false,
    val resolved: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "diagnostics_records")
data class DiagnosticRecordEntity(
    @PrimaryKey val checkName: String,
    val category: String,
    val status: String, // PASS, FAIL, WARN
    val detail: String,
    val lastCheckedTimestamp: Long = System.currentTimeMillis()
)
