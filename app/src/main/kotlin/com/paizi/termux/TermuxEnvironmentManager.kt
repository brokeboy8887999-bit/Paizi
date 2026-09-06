package com.paizi.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.paizi.core.config.AppConfig
import com.paizi.core.logging.LoggingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Real Termux & Android Linux Development Environment Subsystem.
 * Provides actual process execution, toolchain detection (Git, JDK, Gradle, Android SDK, Python, Node),
 * and bridge to Termux commands and local workspace builds.
 */
class TermuxEnvironmentManager(
    private val context: Context
) {
    private val TAG = "TermuxEnvironmentManager"

    companion object {
        const val TERMUX_PACKAGE_NAME = "com.termux"
        const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val TERMUX_PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

        // Common Termux paths
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val TERMUX_BIN = "$TERMUX_PREFIX/bin"
    }

    data class CommandResult(
        val command: String,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
        val isSuccess: Boolean
    ) {
        val combinedLogs: String
            get() = buildString {
                if (stdout.isNotBlank()) appendLine(stdout)
                if (stderr.isNotBlank()) {
                    if (stdout.isNotBlank()) appendLine()
                    appendLine("[STDERR]:")
                    appendLine(stderr)
                }
            }.trim()
    }

    data class ToolchainInfo(
        val name: String,
        val isInstalled: Boolean,
        val version: String?,
        val binaryPath: String?,
        val notes: String
    )

    data class EnvironmentStatus(
        val isTermuxInstalled: Boolean,
        val hasRunCommandPermission: Boolean,
        val hasShellAccess: Boolean,
        val detectedTools: List<ToolchainInfo>,
        val activeWorkspaceRoot: String
    )

    private val _consoleLogsFlow = MutableStateFlow<List<String>>(emptyList())
    val consoleLogsFlow: StateFlow<List<String>> = _consoleLogsFlow.asStateFlow()

    private fun logToConsole(message: String) {
        LoggingManager.i(TAG, message)
        val current = _consoleLogsFlow.value.toMutableList()
        current.add("[${System.currentTimeMillis()}] $message")
        if (current.size > 200) {
            current.removeAt(0)
        }
        _consoleLogsFlow.value = current
    }

    /**
     * Checks whether the official Termux app is installed on this Android device.
     */
    fun isTermuxAppInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            LoggingManager.w(TAG, "Error checking Termux package: ${e.message}")
            false
        }
    }

    /**
     * Verifies if RUN_COMMAND permission is granted.
     */
    fun hasRunCommandPermission(): Boolean {
        return context.checkCallingOrSelfPermission(TERMUX_PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Executes an actual shell command synchronously on IO dispatcher.
     * Uses Android Linux subsystem /system/bin/sh with access to workspace and tools.
     */
    suspend fun executeCommand(
        command: String,
        workingDir: File = AppConfig.getWorkspaceRoot(context),
        timeoutSeconds: Long = 120L,
        extraEnv: Map<String, String> = emptyMap()
    ): CommandResult = withContext(Dispatchers.IO) {
        logToConsole("Executing: $command in ${workingDir.absolutePath}")
        val startTime = System.currentTimeMillis()

        if (!workingDir.exists()) {
            workingDir.mkdirs()
        }

        try {
            val processBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
            processBuilder.directory(workingDir)

            val environment = processBuilder.environment()
            // Add Termux bin and standard bin paths to PATH
            val existingPath = environment["PATH"] ?: "/system/bin:/system/xbin"
            val enhancedPath = "$TERMUX_BIN:/data/data/com.termux/files/usr/bin/applets:$existingPath"
            environment["PATH"] = enhancedPath
            environment["HOME"] = workingDir.absolutePath
            environment["TMPDIR"] = context.cacheDir.absolutePath
            for ((k, v) in extraEnv) {
                environment[k] = v
            }

            val process = processBuilder.start()

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdoutThread = Thread {
                try {
                    var line: String?
                    while (stdoutReader.readLine().also { line = it } != null) {
                        stdoutBuilder.appendLine(line)
                    }
                } catch (_: Exception) {}
            }

            val stderrThread = Thread {
                try {
                    var line: String?
                    while (stderrReader.readLine().also { line = it } != null) {
                        stderrBuilder.appendLine(line)
                    }
                } catch (_: Exception) {}
            }

            stdoutThread.start()
            stderrThread.start()

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                val duration = System.currentTimeMillis() - startTime
                logToConsole("Command timed out after ${timeoutSeconds}s: $command")
                return@withContext CommandResult(
                    command = command,
                    exitCode = -1,
                    stdout = stdoutBuilder.toString(),
                    stderr = "Command timed out after ${timeoutSeconds}s. Process killed.",
                    durationMs = duration,
                    isSuccess = false
                )
            }

            stdoutThread.join(1000)
            stderrThread.join(1000)

            val exitCode = process.exitValue()
            val duration = System.currentTimeMillis() - startTime

            logToConsole("Exit code: $exitCode (${duration}ms)")

            CommandResult(
                command = command,
                exitCode = exitCode,
                stdout = stdoutBuilder.toString().trim(),
                stderr = stderrBuilder.toString().trim(),
                durationMs = duration,
                isSuccess = exitCode == 0
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logToConsole("Execution exception: ${e.message}")
            CommandResult(
                command = command,
                exitCode = -2,
                stdout = "",
                stderr = "Process execution failed: ${e.message}",
                durationMs = duration,
                isSuccess = false
            )
        }
    }

    /**
     * Detects availability of essential development tools.
     */
    suspend fun detectToolchain(): List<ToolchainInfo> = withContext(Dispatchers.IO) {
        val tools = listOf(
            "git" to "Version control",
            "java" to "Java Runtime (JDK)",
            "javac" to "Java Compiler",
            "gradle" to "Gradle Build Tool",
            "python3" to "Python 3 Runtime",
            "node" to "Node.js Engine",
            "npm" to "Node Package Manager",
            "kotlinc" to "Kotlin Compiler",
            "zip" to "Archive utility",
            "unzip" to "Extraction utility",
            "curl" to "Network transfer tool"
        )

        val resultList = mutableListOf<ToolchainInfo>()

        for ((bin, desc) in tools) {
            val checkRes = executeCommand("which $bin 2>&1", timeoutSeconds = 5)
            if (checkRes.isSuccess && checkRes.stdout.isNotBlank()) {
                val path = checkRes.stdout.lines().firstOrNull()?.trim() ?: ""
                val versionRes = executeCommand("$bin --version 2>&1 || $bin -v 2>&1 || $bin -version 2>&1", timeoutSeconds = 5)
                val version = versionRes.stdout.lines().firstOrNull()?.take(60) ?: "Installed"
                resultList.add(
                    ToolchainInfo(
                        name = bin,
                        isInstalled = true,
                        version = version,
                        binaryPath = path,
                        notes = desc
                    )
                )
            } else {
                resultList.add(
                    ToolchainInfo(
                        name = bin,
                        isInstalled = false,
                        version = null,
                        binaryPath = null,
                        notes = "$desc (Run 'pkg install $bin' in Termux to enable)"
                    )
                )
            }
        }
        resultList
    }

    /**
     * Generates a Termux installation and setup script that users or Termux can run
     * to install full Android development toolchain: JDK, Gradle, Python, Node, Git, Android SDK.
     */
    fun generateSetupScriptContent(): String {
        return """
            #!/data/data/com.termux/files/usr/bin/bash
            # PAIZI Autonomous Development Environment Setup Script
            echo "==============================================="
            echo "PAIZI Autonomous Toolchain Installation"
            echo "==============================================="
            pkg update -y
            pkg install -y git openjdk-17 gradle python nodejs zip unzip curl wget clang make
            echo "Toolchain installation complete!"
            echo "Verifying installations:"
            git --version
            java -version
            gradle -v
            python3 --version
            node --version
            echo "==============================================="
            echo "Ready for PAIZI AI Brain Autonomous Builds"
            echo "==============================================="
        """.trimIndent()
    }

    /**
     * Returns full diagnostic overview of the development environment.
     */
    suspend fun getEnvironmentStatus(): EnvironmentStatus = withContext(Dispatchers.IO) {
        val isInstalled = isTermuxAppInstalled()
        val hasPermission = hasRunCommandPermission()
        val shellTest = executeCommand("echo 'PAIZI_SHELL_OK'", timeoutSeconds = 3)
        val hasShell = shellTest.isSuccess && shellTest.stdout.contains("PAIZI_SHELL_OK")
        val tools = detectToolchain()

        EnvironmentStatus(
            isTermuxInstalled = isInstalled,
            hasRunCommandPermission = hasPermission,
            hasShellAccess = hasShell,
            detectedTools = tools,
            activeWorkspaceRoot = AppConfig.getWorkspaceRoot(context).absolutePath
        )
    }

    /**
     * Launches the Termux application if installed.
     */
    fun launchTermuxApp(): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            LoggingManager.e(TAG, "Failed to launch Termux: ${e.message}", e)
            false
        }
    }
}
