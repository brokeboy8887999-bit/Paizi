package com.paizi.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM online_providers ORDER BY priority ASC, slotIndex ASC")
    fun getAllProviders(): Flow<List<OnlineProviderConfigEntity>>

    @Query("SELECT * FROM online_providers ORDER BY slotIndex ASC")
    suspend fun getAllProvidersList(): List<OnlineProviderConfigEntity>

    @Query("SELECT * FROM online_providers WHERE slotIndex = :slotIndex LIMIT 1")
    suspend fun getProviderBySlot(slotIndex: Int): OnlineProviderConfigEntity?

    @Query("SELECT * FROM online_providers WHERE isEnabled = 1 ORDER BY priority ASC")
    suspend fun getEnabledProviders(): List<OnlineProviderConfigEntity>

    @Query("SELECT COUNT(*) FROM online_providers")
    suspend fun getProviderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProvider(provider: OnlineProviderConfigEntity)
}

@Dao
interface OfflineModelDao {
    @Query("SELECT * FROM offline_models ORDER BY slotIndex ASC")
    fun getAllOfflineModels(): Flow<List<OfflineModelEntity>>

    @Query("SELECT * FROM offline_models ORDER BY slotIndex ASC")
    suspend fun getAllOfflineModelsList(): List<OfflineModelEntity>

    @Query("SELECT * FROM offline_models WHERE slotIndex = :slotIndex LIMIT 1")
    suspend fun getModelBySlot(slotIndex: Int): OfflineModelEntity?

    @Query("SELECT * FROM offline_models WHERE status = 'LOADED' OR status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveModel(): OfflineModelEntity?

    @Query("SELECT COUNT(*) FROM offline_models")
    suspend fun getModelCount(): Int

    @Query("DELETE FROM offline_models")
    suspend fun deleteAllModels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateModel(model: OfflineModelEntity)
}

@Dao
interface BuildDao {
    @Query("SELECT * FROM build_logs WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getBuildLogsForProject(projectId: String): Flow<List<BuildLogEntity>>

    @Query("SELECT * FROM build_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBuildLog(): BuildLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuildLog(buildLog: BuildLogEntity)
}

@Dao
interface TestDao {
    @Query("SELECT * FROM test_results WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getTestResultsForProject(projectId: String): Flow<List<TestResultEntity>>

    @Query("SELECT * FROM test_results ORDER BY timestamp DESC")
    fun getAllTestResults(): Flow<List<TestResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestResult(result: TestResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTestResults(results: List<TestResultEntity>)
}

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostics_records ORDER BY checkName ASC")
    fun getAllDiagnosticRecords(): Flow<List<DiagnosticRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDiagnostic(record: DiagnosticRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDiagnostics(records: List<DiagnosticRecordEntity>)
}

@Dao
interface FeatureDao {
    @Query("SELECT * FROM project_features WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getFeaturesForProject(projectId: String): Flow<List<ProjectFeatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeature(feature: ProjectFeatureEntity)

    @Update
    suspend fun updateFeature(feature: ProjectFeatureEntity)
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM project_decisions WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getDecisionsForProject(projectId: String): Flow<List<ProjectDecisionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: ProjectDecisionEntity)
}

@Dao
interface ErrorDao {
    @Query("SELECT * FROM project_errors WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getErrorsForProject(projectId: String): Flow<List<ProjectErrorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertError(error: ProjectErrorEntity)

    @Update
    suspend fun updateError(error: ProjectErrorEntity)
}
