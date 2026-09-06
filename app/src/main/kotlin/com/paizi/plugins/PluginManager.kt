package com.paizi.plugins

import com.paizi.core.logging.LoggingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PluginManager {
    private val TAG = "PluginManager"

    data class PluginDescriptor(
        val id: String,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val permissions: List<String>,
        val isEnabled: Boolean
    )

    private val _installedPlugins = MutableStateFlow<List<PluginDescriptor>>(
        listOf(
            PluginDescriptor("plg_git_export", "Git Repository Exporter", "1.0.0", "PAIZI Core", "Generates git repository structure and initial commits", listOf("WORKSPACE_READ"), true),
            PluginDescriptor("plg_code_formatter", "Kotlin Code Formatter", "1.1.0", "PAIZI Core", "Formats Kotlin files according to ktlint standard conventions", listOf("WORKSPACE_READ", "WORKSPACE_WRITE"), true),
            PluginDescriptor("plg_markdown_viewer", "Markdown Documentation Generator", "1.0.2", "PAIZI Core", "Generates interactive README and developer docs", listOf("WORKSPACE_READ", "WORKSPACE_WRITE"), true),
            PluginDescriptor("plg_vulnerability_scanner", "OWASP Security Scanner", "2.0.0", "Security Team", "Scans project files for hardcoded credentials and unsafe dependencies", listOf("WORKSPACE_READ"), true)
        )
    )
    val installedPlugins: StateFlow<List<PluginDescriptor>> = _installedPlugins.asStateFlow()

    fun togglePlugin(id: String) {
        val list = _installedPlugins.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        _installedPlugins.value = list
        LoggingManager.i(TAG, "Toggled plugin status for $id")
    }
}
