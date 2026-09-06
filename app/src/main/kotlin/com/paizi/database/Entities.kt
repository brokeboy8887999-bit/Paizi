package com.paizi.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // e.g. "Android Compose", "Kotlin Library", "Kotlin CLI"
    val concept: String,
    val status: String, // CREATED, PLANNING, APPROVED, IMPLEMENTING, BUILT, TESTED, COMPLETED
    val rootPath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val activeProviderId: String? = null,
    val activeModelName: String? = null
)

@Entity(
    tableName = "conversations",
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
data class ConversationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system", "agent"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val providerUsed: String? = null,
    val tokenUsage: Int = 0,
    val isError: Boolean = false,
    val attachmentPath: String? = null
)

@Entity(
    tableName = "blueprints",
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
data class BlueprintEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val version: Int,
    val approvalStatus: String, // PENDING, APPROVED, REJECTED, REVISION_REQUIRED
    val contentJson: String,
    val markdownSummary: String,
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null
)

@Entity(
    tableName = "approvals",
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
data class ApprovalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val blueprintId: String,
    val status: String, // PENDING, APPROVED, REJECTED, REVISION_REQUIRED
    val comments: String,
    val reviewer: String = "User",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "project_memories",
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
data class ProjectMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val key: String,
    val value: String,
    val category: String, // REQUIREMENT, ARCHITECTURE, DECISION, FIX, BLUEPRINT
    val timestamp: Long = System.currentTimeMillis()
)
