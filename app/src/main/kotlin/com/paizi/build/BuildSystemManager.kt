package com.paizi.build

import android.content.Context
import com.paizi.core.config.AppConfig
import com.paizi.core.logging.LoggingManager
import com.paizi.database.BuildDao
import com.paizi.database.BuildLogEntity
import com.paizi.termux.TermuxEnvironmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 100% Real Android & Kotlin Build System Manager.
 * Strictly adheres to rule: "NO FAKE SUCCESS".
 * Never claims success unless real toolchain executes, real compilation finishes, and real artifact is verified on disk.
 */
class BuildSystemManager(
    private val context: Context,
    private val buildDao: BuildDao,
    private val termuxManager: TermuxEnvironmentManager = TermuxEnvironmentManager(context)
) {
    private val TAG = "BuildSystemManager"

    data class BuildOutcome(
        val success: Boolean,
        val exitCode: Int,
        val logs: String,
        val artifactPath: String? = null,
        val durationMs: Long = 0L,
        val target: String = "assembleDebug"
    )

    /**
     * Executes real build workflow for a project.
     * ZERO simulation: executes real gradle/compiler commands.
     */
    suspend fun executeBuild(
        projectId: String,
        targetTask: String = "assembleDebug"
    ): BuildOutcome = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        LoggingManager.i(TAG, "Starting REAL build for project: $projectId, target: $targetTask")

        val workspaceRoot = AppConfig.getWorkspaceRoot(context)
        val projectDir = File(workspaceRoot, projectId)

        val targetDir = if (projectDir.exists()) projectDir else workspaceRoot

        // Step 1: Check project files
        val gradlewFile = File(targetDir, "gradlew")
        val buildGradle = File(targetDir, "build.gradle.kts")
        val altBuildGradle = File(targetDir, "build.gradle")
        val hasGradle = gradlewFile.exists() || buildGradle.exists() || altBuildGradle.exists()

        val logBuffer = StringBuilder()
        logBuffer.appendLine("=========================================================")
        logBuffer.appendLine("PAIZI AUTONOMOUS BUILD EXECUTION SYSTEM")
        logBuffer.appendLine("Project ID: $projectId")
        logBuffer.appendLine("Working Directory: ${targetDir.absolutePath}")
        logBuffer.appendLine("Build Target: $targetTask")
        logBuffer.appendLine("Timestamp: ${java.util.Date()}")
        logBuffer.appendLine("=========================================================\n")

        // Step 2: Check toolchain readiness
        val javaCheck = termuxManager.executeCommand("java -version 2>&1 || which java", targetDir, timeoutSeconds = 5)
        val gradleCheck = termuxManager.executeCommand("gradle -v 2>&1 || which gradle", targetDir, timeoutSeconds = 5)

        val hasJava = javaCheck.isSuccess && (javaCheck.stdout.contains("version") || javaCheck.stderr.contains("version"))
        val hasSystemGradle = gradleCheck.isSuccess && gradleCheck.stdout.contains("Gradle")

        logBuffer.appendLine("--- Toolchain Inspection ---")
        logBuffer.appendLine("Java Runtime: ${if (hasJava) "DETECTED" else "NOT FOUND in current environment"}")
        logBuffer.appendLine("Gradle System: ${if (hasSystemGradle) "DETECTED" else "NOT FOUND in PATH"}")
        logBuffer.appendLine("Gradle Wrapper: ${if (gradlewFile.exists()) "PRESENT (${gradlewFile.length()} bytes)" else "NOT FOUND"}")
        logBuffer.appendLine("----------------------------\n")

        var buildSuccess = false
        var exitCode = -1
        var artifactPath: String? = null

        if (!hasGradle) {
            // No gradle build scripts found
            exitCode = 2
            logBuffer.appendLine("FAILURE: No Gradle configuration found in ${targetDir.absolutePath}.")
            logBuffer.appendLine("Missing build.gradle.kts and gradlew. Generate or export project structure first.")
        } else if (!hasJava && !gradlewFile.exists()) {
            // Real failure: cannot build without JDK/SDK toolchain
            exitCode = 127
            logBuffer.appendLine("BUILD FAILED: JDK / Java compiler is not installed in the local environment.")
            logBuffer.appendLine("Standard Termux setup required:")
            logBuffer.appendLine("  1. Open Termux")
            logBuffer.appendLine("  2. Run: pkg install openjdk-17 gradle")
            logBuffer.appendLine("  3. Set JAVA_HOME in Termux environment")
            logBuffer.appendLine("\nStrict Rule Enforced: No fake success is permitted. Build failed.")
        } else {
            // Execute real build command
            val buildCommand = if (gradlewFile.exists()) {
                gradlewFile.setExecutable(true)
                "./gradlew $targetTask --no-daemon --stacktrace 2>&1"
            } else if (hasSystemGradle) {
                "gradle $targetTask --no-daemon 2>&1"
            } else {
                "./gradlew $targetTask 2>&1"
            }

            logBuffer.appendLine("Executing command: $buildCommand\n")
            val commandResult = termuxManager.executeCommand(buildCommand, targetDir, timeoutSeconds = 180)

            logBuffer.appendLine(commandResult.combinedLogs)
            exitCode = commandResult.exitCode

            if (commandResult.isSuccess) {
                // Verify artifact existence on disk
                val possibleApkPaths = listOf(
                    File(targetDir, "app/build/outputs/apk/debug/app-debug.apk"),
                    File(targetDir, "build/outputs/apk/debug/app-debug.apk"),
                    File(targetDir, "build/libs"),
                    File(targetDir, "build/outputs")
                )

                val foundArtifact = possibleApkPaths.firstOrNull { it.exists() && (it.isFile && it.length() > 1000 || it.isDirectory && it.listFiles()?.isNotEmpty() == true) }
                if (foundArtifact != null) {
                    buildSuccess = true
                    artifactPath = foundArtifact.absolutePath
                    logBuffer.appendLine("\n✓ ARTIFACT VERIFIED ON DISK: $artifactPath (${if (foundArtifact.isFile) "${foundArtifact.length()} bytes" else "directory"})")
                } else {
                    // Command returned 0 but no artifact was produced
                    buildSuccess = false
                    exitCode = 3
                    logBuffer.appendLine("\nVERIFICATION FAILED: Gradle completed but no output artifact was located at expected path.")
                }
            } else {
                buildSuccess = false
                logBuffer.appendLine("\n✗ BUILD COMPILATION FAILED WITH EXIT CODE: $exitCode")
            }
        }

        val duration = System.currentTimeMillis() - startTime
        logBuffer.appendLine("\n=========================================================")
        logBuffer.appendLine("FINAL RESULT: ${if (buildSuccess) "SUCCESS (VERIFIED)" else "FAILED (GENUINE EVIDENCE)"}")
        logBuffer.appendLine("Total Duration: ${duration}ms")
        logBuffer.appendLine("=========================================================")

        val outcome = BuildOutcome(
            success = buildSuccess,
            exitCode = exitCode,
            logs = logBuffer.toString(),
            artifactPath = artifactPath,
            durationMs = duration,
            target = targetTask
        )

        // Save real build record into SQLite database
        try {
            buildDao.insertBuildLog(
                BuildLogEntity(
                    projectId = projectId,
                    target = targetTask,
                    status = if (buildSuccess) "SUCCESS" else "FAILED",
                    outputLogs = outcome.logs,
                    exitCode = outcome.exitCode,
                    durationMs = duration
                )
            )
        } catch (e: Exception) {
            LoggingManager.e(TAG, "Failed to persist build log: ${e.message}", e)
        }

        outcome
    }
}
