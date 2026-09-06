package com.paizi.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getConversationsForProject(projectId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface BlueprintDao {
    @Query("SELECT * FROM blueprints WHERE projectId = :projectId ORDER BY version DESC")
    fun getBlueprintsForProject(projectId: String): Flow<List<BlueprintEntity>>

    @Query("SELECT * FROM blueprints WHERE projectId = :projectId ORDER BY version DESC LIMIT 1")
    suspend fun getLatestBlueprint(projectId: String): BlueprintEntity?

    @Query("SELECT * FROM blueprints WHERE id = :id LIMIT 1")
    suspend fun getBlueprintById(id: String): BlueprintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlueprint(blueprint: BlueprintEntity)

    @Update
    suspend fun updateBlueprint(blueprint: BlueprintEntity)
}

@Dao
interface ApprovalDao {
    @Query("SELECT * FROM approvals WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getApprovalsForProject(projectId: String): Flow<List<ApprovalHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApproval(approval: ApprovalHistoryEntity)
}

@Dao
interface ProjectMemoryDao {
    @Query("SELECT * FROM project_memories WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getMemoriesForProject(projectId: String): Flow<List<ProjectMemoryEntity>>

    @Query("SELECT * FROM project_memories WHERE projectId = :projectId AND `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(projectId: String, key: String): ProjectMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: ProjectMemoryEntity)

    @Query("DELETE FROM project_memories WHERE projectId = :projectId AND `key` = :key")
    suspend fun deleteMemory(projectId: String, key: String)
}
