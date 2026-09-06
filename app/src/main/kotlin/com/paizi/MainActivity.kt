package com.paizi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paizi.di.DIModule
import com.paizi.theme.PAIZITheme
import com.paizi.ui.screens.AiSettingsScreen
import com.paizi.ui.screens.BuildLogsScreen
import com.paizi.ui.screens.ChatScreen
import com.paizi.ui.screens.DiagnosticsScreen
import com.paizi.ui.screens.FileExplorerScreen
import com.paizi.ui.screens.PluginsScreen
import com.paizi.ui.screens.ProjectWorkspaceScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize all app singletons
        DIModule.initialize(this)

        setContent {
            PAIZITheme {
                PaiziMainApp()
            }
        }
    }
}

enum class NavigationDestination(val title: String) {
    CHAT("Chat & Code"),
    WORKSPACE("Project Workspace"),
    FILES("File Explorer"),
    AI_SETTINGS("AI Subsystem"),
    DIAGNOSTICS("Diagnostics"),
    BUILDS("Build & Tests"),
    PLUGINS("Plugins")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaiziMainApp() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var currentDestination by remember { mutableStateOf(NavigationDestination.WORKSPACE) }

    val activeProject by DIModule.projectManager.activeProjectFlow.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "PAIZI Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
                )
                Text(
                    text = "Autonomous AI Software Engineer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Work, contentDescription = null) },
                    label = { Text("Project Workspace") },
                    selected = currentDestination == NavigationDestination.WORKSPACE,
                    onClick = {
                        currentDestination = NavigationDestination.WORKSPACE
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    label = { Text("Chat & AI Assistance") },
                    selected = currentDestination == NavigationDestination.CHAT,
                    onClick = {
                        currentDestination = NavigationDestination.CHAT
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Sandbox File Explorer") },
                    selected = currentDestination == NavigationDestination.FILES,
                    onClick = {
                        currentDestination = NavigationDestination.FILES
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("AI Settings (Online/Offline)") },
                    selected = currentDestination == NavigationDestination.AI_SETTINGS,
                    onClick = {
                        currentDestination = NavigationDestination.AI_SETTINGS
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    label = { Text("System Diagnostics (19 Checks)") },
                    selected = currentDestination == NavigationDestination.DIAGNOSTICS,
                    onClick = {
                        currentDestination = NavigationDestination.DIAGNOSTICS
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("Build Logs & Test Evidence") },
                    selected = currentDestination == NavigationDestination.BUILDS,
                    onClick = {
                        currentDestination = NavigationDestination.BUILDS
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                    label = { Text("Plugins & Extensions") },
                    selected = currentDestination == NavigationDestination.PLUGINS,
                    onClick = {
                        currentDestination = NavigationDestination.PLUGINS
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentDestination.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { DIModule.browserController.openUrl("https://ai.google.dev") },
                            modifier = Modifier.testTag("open_browser_action")
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Open Browser")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Work, contentDescription = null) },
                        label = { Text("Workspace") },
                        selected = currentDestination == NavigationDestination.WORKSPACE,
                        onClick = { currentDestination = NavigationDestination.WORKSPACE }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                        label = { Text("Chat") },
                        selected = currentDestination == NavigationDestination.CHAT,
                        onClick = { currentDestination = NavigationDestination.CHAT }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("AI") },
                        selected = currentDestination == NavigationDestination.AI_SETTINGS,
                        onClick = { currentDestination = NavigationDestination.AI_SETTINGS }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                        label = { Text("Diagnostics") },
                        selected = currentDestination == NavigationDestination.DIAGNOSTICS,
                        onClick = { currentDestination = NavigationDestination.DIAGNOSTICS }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentDestination) {
                    NavigationDestination.CHAT -> ChatScreen(
                        activeProject = activeProject,
                        onNavigateToWorkspace = { currentDestination = NavigationDestination.WORKSPACE }
                    )
                    NavigationDestination.WORKSPACE -> ProjectWorkspaceScreen(
                        onNavigateToFileExplorer = { currentDestination = NavigationDestination.FILES }
                    )
                    NavigationDestination.FILES -> FileExplorerScreen(
                        onNavigateBack = { currentDestination = NavigationDestination.WORKSPACE }
                    )
                    NavigationDestination.AI_SETTINGS -> AiSettingsScreen()
                    NavigationDestination.DIAGNOSTICS -> DiagnosticsScreen()
                    NavigationDestination.BUILDS -> BuildLogsScreen()
                    NavigationDestination.PLUGINS -> PluginsScreen()
                }
            }
        }
    }
}
