package com.paizi.coding

import com.paizi.core.logging.LoggingManager
import com.paizi.files.WorkspaceFileManager
import java.io.File

class CodingEngine(
    private val fileManager: WorkspaceFileManager
) {
    private val TAG = "CodingEngine"

    data class CodeModificationResult(
        val success: Boolean,
        val filePath: String,
        val message: String,
        val diff: String? = null
    )

    fun listFiles(dir: String = ""): List<WorkspaceFileManager.FileNode> {
        return fileManager.listFiles(dir)
    }

    fun readFile(relativePath: String): String {
        return fileManager.readFile(relativePath)
    }

    fun createFile(relativePath: String, content: String): CodeModificationResult {
        return try {
            fileManager.createFile(relativePath, content)
            CodeModificationResult(
                success = true,
                filePath = relativePath,
                message = "File created successfully (${content.length} characters)"
            )
        } catch (e: Exception) {
            LoggingManager.e(TAG, "Failed creating file $relativePath", e)
            CodeModificationResult(
                success = false,
                filePath = relativePath,
                message = "Error creating file: ${e.message}"
            )
        }
    }

    fun writeFile(relativePath: String, content: String): CodeModificationResult {
        return try {
            val file = fileManager.resolveSafe(relativePath)
            val oldContent = if (file.exists()) file.readText() else ""
            fileManager.writeFile(relativePath, content)
            val diff = generateSimpleDiff(relativePath, oldContent, content)
            CodeModificationResult(
                success = true,
                filePath = relativePath,
                message = "File written successfully",
                diff = diff
            )
        } catch (e: Exception) {
            LoggingManager.e(TAG, "Failed writing file $relativePath", e)
            CodeModificationResult(
                success = false,
                filePath = relativePath,
                message = "Error writing file: ${e.message}"
            )
        }
    }

    fun editFile(relativePath: String, targetSubstring: String, replacement: String): CodeModificationResult {
        return try {
            val file = fileManager.resolveSafe(relativePath)
            val original = file.readText()
            val replaced = fileManager.editFile(relativePath, targetSubstring, replacement)
            if (replaced) {
                val updated = file.readText()
                val diff = generateSimpleDiff(relativePath, original, updated)
                CodeModificationResult(
                    success = true,
                    filePath = relativePath,
                    message = "Target string replaced successfully",
                    diff = diff
                )
            } else {
                CodeModificationResult(
                    success = false,
                    filePath = relativePath,
                    message = "Target substring not found in file"
                )
            }
        } catch (e: Exception) {
            LoggingManager.e(TAG, "Failed editing file $relativePath", e)
            CodeModificationResult(
                success = false,
                filePath = relativePath,
                message = "Error editing file: ${e.message}"
            )
        }
    }

    fun deleteFile(relativePath: String): Boolean {
        return fileManager.deleteFile(relativePath)
    }

    fun renameOrMoveFile(oldPath: String, newPath: String): Boolean {
        return fileManager.renameFile(oldPath, newPath)
    }

    fun search(query: String): List<WorkspaceFileManager.SearchResult> {
        return fileManager.searchFiles(query)
    }

    private fun generateSimpleDiff(path: String, old: String, new: String): String {
        val oldLines = old.lines()
        val newLines = new.lines()
        return buildString {
            appendLine("--- a/$path")
            appendLine("+++ b/$path")
            val maxLines = maxOf(oldLines.size, newLines.size)
            for (i in 0 until maxLines) {
                val o = oldLines.getOrNull(i)
                val n = newLines.getOrNull(i)
                if (o != n) {
                    if (o != null) appendLine("-$o")
                    if (n != null) appendLine("+$n")
                }
            }
        }
    }
}
