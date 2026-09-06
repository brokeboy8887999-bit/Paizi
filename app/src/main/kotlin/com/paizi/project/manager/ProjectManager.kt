package com.paizi.project.manager

import android.content.Context
import com.paizi.core.config.AppConfig
import com.paizi.core.logging.LoggingManager
import com.paizi.database.PAIZIDatabase
import com.paizi.database.ProjectEntity
import com.paizi.database.ProjectMemoryEntity
import com.paizi.files.WorkspaceFileManager
import com.paizi.project.memory.ProjectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectManager(
    private val context: Context,
    private val database: PAIZIDatabase
) {
    private val TAG = "ProjectManager"
    private val projectDao = database.projectDao()
    private val memoryDao = database.projectMemoryDao()

    private val _activeProjectFlow = MutableStateFlow<ProjectEntity?>(null)
    val activeProjectFlow: StateFlow<ProjectEntity?> = _activeProjectFlow.asStateFlow()

    private var activeFileManager: WorkspaceFileManager? = null

    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    fun getActiveFileManager(): WorkspaceFileManager? = activeFileManager

    suspend fun createProject(name: String, concept: String, type: String = "Android Compose"): ProjectEntity = withContext(Dispatchers.IO) {
        val id = "proj_" + UUID.randomUUID().toString().take(8)
        val projectDir = File(AppConfig.getWorkspaceRoot(context), id)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }

        val entity = ProjectEntity(
            id = id,
            name = name.trim().ifBlank { "New Project" },
            type = type,
            concept = concept.trim(),
            status = "CREATED",
            rootPath = projectDir.absolutePath
        )
        projectDao.insertProject(entity)

        // Initialize initial PROJECT_STATE.md
        val state = ProjectState(
            id = id,
            name = entity.name,
            projectType = type,
            requirements = concept,
            buildStatus = "NOT_BUILT"
        )
        val stateFile = File(projectDir, "PROJECT_STATE.md")
        ProjectState.saveToFile(state, stateFile)

        // Record initial memory
        memoryDao.insertMemory(
            ProjectMemoryEntity(
                projectId = id,
                key = "ORIGINAL_CONCEPT",
                value = concept,
                category = "REQUIREMENT"
            )
        )

        selectProject(entity)
        LoggingManager.i(TAG, "Created new project '$name' ($id) at ${projectDir.absolutePath}")
        entity
    }

    suspend fun selectProject(project: ProjectEntity) {
        _activeProjectFlow.value = project
        val dir = File(project.rootPath)
        activeFileManager = WorkspaceFileManager(dir)
        LoggingManager.i(TAG, "Selected active project: ${project.name} (${project.id})")
    }

    suspend fun selectProjectById(id: String) {
        val project = projectDao.getProjectById(id)
        if (project != null) {
            selectProject(project)
        }
    }

    suspend fun deleteProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        projectDao.deleteProject(project)
        val dir = File(project.rootPath)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        if (_activeProjectFlow.value?.id == project.id) {
            _activeProjectFlow.value = null
            activeFileManager = null
        }
        LoggingManager.i(TAG, "Deleted project: ${project.name}")
    }

    suspend fun exportProjectZip(project: ProjectEntity): File = withContext(Dispatchers.IO) {
        val dir = File(project.rootPath)
        val fm = WorkspaceFileManager(dir)
        val zipFile = File(AppConfig.getExportsDirectory(context), "${project.name.replace(" ", "_")}_${project.id}.zip")
        fm.exportToZip(zipFile)
        LoggingManager.i(TAG, "Exported project '${project.name}' to ${zipFile.absolutePath} (${zipFile.length()} bytes)")
        zipFile
    }

    suspend fun importProjectZip(zipFile: File, newProjectName: String): ProjectEntity = withContext(Dispatchers.IO) {
        val id = "proj_" + UUID.randomUUID().toString().take(8)
        val projectDir = File(AppConfig.getWorkspaceRoot(context), id)
        projectDir.mkdirs()

        val fm = WorkspaceFileManager(projectDir)
        fm.extractZipSafely(zipFile)

        val entity = ProjectEntity(
            id = id,
            name = newProjectName.ifBlank { zipFile.nameWithoutExtension },
            type = "Imported Android Project",
            concept = "Imported from ${zipFile.name}",
            status = "IMPORTED",
            rootPath = projectDir.absolutePath
        )
        projectDao.insertProject(entity)
        selectProject(entity)
        LoggingManager.i(TAG, "Successfully imported project '${entity.name}' safely from ZIP")
        entity
    }
}
