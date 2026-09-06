package com.paizi

import com.paizi.files.WorkspaceFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceFileManagerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `test file creation and reading in sandbox`() {
        val root = tempFolder.newFolder("workspace_sandbox")
        val manager = WorkspaceFileManager(root)

        val file = manager.createFile("src/main/Test.kt", "fun main() {}")
        assertTrue(file.exists())
        assertEquals("fun main() {}", manager.readFile("src/main/Test.kt"))
    }

    @Test
    fun `test file edit in sandbox`() {
        val root = tempFolder.newFolder("workspace_sandbox_edit")
        val manager = WorkspaceFileManager(root)

        manager.createFile("config.txt", "port=8080\nhost=localhost")
        val edited = manager.editFile("config.txt", "port=8080", "port=9090")
        assertTrue(edited)
        assertTrue(manager.readFile("config.txt").contains("port=9090"))
    }

    @Test
    fun `test path traversal attack blocked`() {
        val root = tempFolder.newFolder("workspace_sandbox_sec")
        val manager = WorkspaceFileManager(root)

        try {
            manager.resolveSafe("../../etc/passwd")
            fail("SecurityException expected for path traversal attempt")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Access Denied") == true)
        }
    }
}
