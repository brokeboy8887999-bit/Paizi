package com.paizi.agents.file

import com.paizi.agents.base.BaseAgent
import com.paizi.ai.router.AIRouter
import com.paizi.coding.CodingEngine
import java.io.File

class FileAgent(
    aiRouter: AIRouter,
    private val codingEngine: CodingEngine
) : BaseAgent(
    roleName = "File Agent",
    description = "Manages file tree structure, creates directories, moves assets, and audits file integrity",
    aiRouter = aiRouter
) {
    suspend fun auditAndOrganizeProject(context: AgentContext): AgentResult {
        val rootFiles = codingEngine.listFiles()
        val summary = buildString {
            appendLine("Project Files Audit (${rootFiles.size} items):")
            rootFiles.forEach { f ->
                appendLine("- ${f.name} (${if (f.isDirectory) "directory" else "${f.sizeBytes} bytes"})")
            }
        }

        return AgentResult(
            success = true,
            summary = "Workspace files audited successfully",
            outputData = summary,
            suggestedNextRole = "Reviewer Agent"
        )
    }

    fun writeFile(relativePath: String, content: String): Boolean {
        val res = codingEngine.writeFile(relativePath, content)
        return res.success
    }

    fun readFile(relativePath: String): String? {
        return try {
            codingEngine.readFile(relativePath)
        } catch (_: Exception) {
            null
        }
    }
}
