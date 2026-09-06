package com.paizi.files

import com.paizi.core.logging.LoggingManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sandboxed file manager that strictly enforces workspace boundary containment,
 * anti-path-traversal sanitization, and ZIP Slip vulnerability protection.
 */
class WorkspaceFileManager(private val workspaceRoot: File) {

    init {
        if (!workspaceRoot.exists()) {
            workspaceRoot.mkdirs()
        }
    }

    val rootPath: String
        get() = workspaceRoot.canonicalPath

    /**
     * Resolves a relative path and strictly validates that the resulting file
     * is contained inside the workspaceRoot directory.
     * Throws SecurityException on path traversal attacks (e.g., "../../evil.txt").
     */
    fun resolveSafe(relativePath: String): File {
        val sanitized = relativePath.trim().removePrefix("/")
        val target = File(workspaceRoot, sanitized)
        val canonicalWorkspace = workspaceRoot.canonicalPath
        val canonicalTarget = target.canonicalPath

        if (!canonicalTarget.startsWith(canonicalWorkspace)) {
            LoggingManager.e("WorkspaceFileManager", "Path traversal attempt blocked: $relativePath -> $canonicalTarget")
            throw SecurityException("Access Denied: Path traversal detected for '$relativePath'. Target outside sandbox.")
        }
        return target
    }

    fun listFiles(relativeDir: String = ""): List<FileNode> {
        val targetDir = resolveSafe(relativeDir)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return emptyList()
        }
        return listNodesRecursive(targetDir, workspaceRoot)
    }

    private fun listNodesRecursive(current: File, root: File): List<FileNode> {
        val files = current.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return emptyList()
        return files.map { file ->
            val relPath = file.canonicalPath.removePrefix(root.canonicalPath).removePrefix("/")
            FileNode(
                name = file.name,
                relativePath = relPath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                children = if (file.isDirectory) listNodesRecursive(file, root) else emptyList()
            )
        }
    }

    fun readFile(relativePath: String): String {
        val file = resolveSafe(relativePath)
        if (!file.exists()) {
            throw IOException("File not found: $relativePath")
        }
        if (file.isDirectory) {
            throw IOException("Target is a directory, not a file: $relativePath")
        }
        return file.readText()
    }

    fun createFile(relativePath: String, content: String = ""): File {
        val file = resolveSafe(relativePath)
        if (file.exists()) {
            throw IOException("File already exists: $relativePath")
        }
        file.parentFile?.mkdirs()
        file.writeText(content)
        LoggingManager.i("WorkspaceFileManager", "Created file: $relativePath")
        return file
    }

    fun writeFile(relativePath: String, content: String): File {
        val file = resolveSafe(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        LoggingManager.i("WorkspaceFileManager", "Wrote file: $relativePath (${content.length} chars)")
        return file
    }

    fun editFile(relativePath: String, targetString: String, replacementString: String): Boolean {
        val file = resolveSafe(relativePath)
        if (!file.exists() || !file.isFile) {
            throw IOException("File not found for edit: $relativePath")
        }
        val current = file.readText()
        if (!current.contains(targetString)) {
            return false
        }
        val updated = current.replaceFirst(targetString, replacementString)
        file.writeText(updated)
        LoggingManager.i("WorkspaceFileManager", "Edited file: $relativePath")
        return true
    }

    fun deleteFile(relativePath: String): Boolean {
        val file = resolveSafe(relativePath)
        if (!file.exists()) {
            return false
        }
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    fun renameFile(oldRelativePath: String, newRelativePath: String): Boolean {
        val oldFile = resolveSafe(oldRelativePath)
        val newFile = resolveSafe(newRelativePath)
        if (!oldFile.exists()) {
            throw IOException("Source file does not exist: $oldRelativePath")
        }
        if (newFile.exists()) {
            throw IOException("Destination file already exists: $newRelativePath")
        }
        newFile.parentFile?.mkdirs()
        return oldFile.renameTo(newFile)
    }

    fun moveFile(sourceRelative: String, targetRelative: String): Boolean {
        return renameFile(sourceRelative, targetRelative)
    }

    fun searchFiles(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return emptyList()

        fun searchRecursive(dir: File) {
            val children = dir.listFiles() ?: return
            for (f in children) {
                if (f.isDirectory) {
                    searchRecursive(f)
                } else if (f.isFile && (f.extension in listOf("kt", "java", "xml", "gradle", "kts", "md", "txt", "json", "properties"))) {
                    try {
                        val text = f.readText()
                        text.lines().forEachIndexed { index, line ->
                            if (line.contains(query, ignoreCase = true)) {
                                val relPath = f.canonicalPath.removePrefix(workspaceRoot.canonicalPath).removePrefix("/")
                                results.add(
                                    SearchResult(
                                        relativePath = relPath,
                                        lineNumber = index + 1,
                                        lineContent = line.trim()
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        searchRecursive(workspaceRoot)
        return results
    }

    /**
     * ZIP Slip protection: Safely unzips an archive into the workspaceRoot.
     * Rejects any archive entry whose resolved canonical path lies outside workspaceRoot.
     */
    fun extractZipSafely(zipFile: File) {
        val canonicalDest = workspaceRoot.canonicalPath
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val resolvedTarget = File(workspaceRoot, entry.name)
                val canonicalTarget = resolvedTarget.canonicalPath

                if (!canonicalTarget.startsWith(canonicalDest)) {
                    throw SecurityException("ZIP Slip Attempt Detected: Entry '${entry.name}' attempts to break out of workspace destination.")
                }

                if (entry.isDirectory) {
                    resolvedTarget.mkdirs()
                } else {
                    resolvedTarget.parentFile?.mkdirs()
                    FileOutputStream(resolvedTarget).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Exports the workspace directory into a target ZIP file.
     */
    fun exportToZip(destinationZip: File) {
        destinationZip.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destinationZip)).use { zos ->
            workspaceRoot.walkTopDown().forEach { file ->
                val relPath = file.canonicalPath.removePrefix(workspaceRoot.canonicalPath).removePrefix("/")
                if (relPath.isNotEmpty()) {
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$relPath/"))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(relPath))
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    data class FileNode(
        val name: String,
        val relativePath: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val children: List<FileNode> = emptyList()
    )

    data class SearchResult(
        val relativePath: String,
        val lineNumber: Int,
        val lineContent: String
    )
}
