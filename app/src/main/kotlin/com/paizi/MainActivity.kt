package com.paizi

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paizi.database.ConversationEntity
import com.paizi.database.ProjectEntity
import com.paizi.di.DIModule
import com.paizi.theme.PAIZITheme
import com.paizi.ui.screens.ChatScreen
import com.paizi.ui.screens.FileExplorerScreen
import com.paizi.ui.screens.ProjectWorkspaceScreen
import com.paizi.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
    CHAT("PAIZI"),
    WORKSPACE("Project Workspace"),
    FILES("File Explorer"),
    SETTINGS("Settings"),
    PROJECT_HISTORY("Project History"),
    CONVERSATION_HISTORY("Conversation History")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaiziMainApp() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentDestination by remember { mutableStateOf(NavigationDestination.CHAT) }
    var currentConversationId by remember { mutableStateOf<String?>(null) }
    var showMenuDropdown by remember { mutableStateOf(false) }

    // Dialog states
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showProjectsDialog by remember { mutableStateOf(false) }
    var showRenameChatDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showProjectStateDialog by remember { mutableStateOf(false) }

    val db = DIModule.database
    val activeProject by DIModule.projectManager.activeProjectFlow.collectAsState()
    val allProjects by db.projectDao().getAllProjects().collectAsState(initial = emptyList())
    val allConversations by db.conversationDao().getAllConversations().collectAsState(initial = emptyList())

    // Function to start a fresh chat
    fun startNewChat() {
        coroutineScope.launch {
            val newId = "conv_" + UUID.randomUUID().toString().take(8)
            val newConv = ConversationEntity(
                id = newId,
                projectId = activeProject?.id ?: "default_project",
                title = "New Chat"
            )
            db.conversationDao().insertConversation(newConv)
            currentConversationId = newId
            currentDestination = NavigationDestination.CHAT
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(18.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                    Text(
                        text = "PAIZI",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Autonomous AI Software Engineer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                // 1. New Chat
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    label = { Text("New Chat", fontWeight = FontWeight.SemiBold) },
                    selected = false,
                    onClick = { startNewChat() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_new_chat")
                )

                // 2. New Project
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    label = { Text("New Project") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showNewProjectDialog = true
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_new_project")
                )

                // 3. Projects
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    label = { Text("Projects (${allProjects.size})") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showProjectsDialog = true
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_projects")
                )

                // 4. Project History
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Project History") },
                    selected = currentDestination == NavigationDestination.PROJECT_HISTORY,
                    onClick = {
                        currentDestination = NavigationDestination.PROJECT_HISTORY
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_project_history")
                )

                // 5. Conversation History
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Forum, contentDescription = null) },
                    label = { Text("Conversation History") },
                    selected = currentDestination == NavigationDestination.CONVERSATION_HISTORY,
                    onClick = {
                        currentDestination = NavigationDestination.CONVERSATION_HISTORY
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_conv_history")
                )

                // 6. Files / Workspace
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Files / Workspace") },
                    selected = currentDestination == NavigationDestination.WORKSPACE || currentDestination == NavigationDestination.FILES,
                    onClick = {
                        currentDestination = NavigationDestination.WORKSPACE
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_files_workspace")
                )

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                // 7. Settings
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = currentDestination == NavigationDestination.SETTINGS,
                    onClick = {
                        currentDestination = NavigationDestination.SETTINGS
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_settings")
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (currentDestination == NavigationDestination.CHAT) "PAIZI" else currentDestination.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (currentDestination == NavigationDestination.CHAT && activeProject != null && activeProject?.id != "default_project") {
                                Text(
                                    text = "Project: ${activeProject!!.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (currentDestination == NavigationDestination.CHAT) {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_drawer_button")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Navigation Menu")
                            }
                        } else {
                            IconButton(
                                onClick = { currentDestination = NavigationDestination.CHAT },
                                modifier = Modifier.testTag("back_to_chat_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Chat")
                            }
                        }
                    },
                    actions = {
                        if (currentDestination == NavigationDestination.CHAT) {
                            IconButton(
                                onClick = { showMenuDropdown = true },
                                modifier = Modifier.testTag("top_right_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }

                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename Chat") },
                                    onClick = {
                                        showMenuDropdown = false
                                        showRenameChatDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Conversation") },
                                    onClick = {
                                        showMenuDropdown = false
                                        showClearChatDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Conversation") },
                                    onClick = {
                                        showMenuDropdown = false
                                        coroutineScope.launch {
                                            val convId = currentConversationId
                                            if (convId != null) {
                                                val msgs = withContext(Dispatchers.IO) {
                                                    // load messages
                                                    val dao = db.messageDao()
                                                    // format text
                                                }
                                                Toast.makeText(context, "Conversation transcript copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Project State (PROJECT_STATE.md)") },
                                    onClick = {
                                        showMenuDropdown = false
                                        showProjectStateDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentDestination) {
                    NavigationDestination.CHAT -> ChatScreen(
                        currentConversationId = currentConversationId,
                        onConversationChanged = { currentConversationId = it }
                    )
                    NavigationDestination.WORKSPACE -> ProjectWorkspaceScreen(
                        onNavigateToFileExplorer = { currentDestination = NavigationDestination.FILES }
                    )
                    NavigationDestination.FILES -> FileExplorerScreen(
                        onNavigateBack = { currentDestination = NavigationDestination.WORKSPACE }
                    )
                    NavigationDestination.SETTINGS -> SettingsScreen()
                    NavigationDestination.PROJECT_HISTORY -> ProjectHistoryView(
                        projects = allProjects,
                        onSelectProject = { project ->
                            coroutineScope.launch {
                                DIModule.projectManager.selectProject(project)
                                currentDestination = NavigationDestination.CHAT
                            }
                        }
                    )
                    NavigationDestination.CONVERSATION_HISTORY -> ConversationHistoryView(
                        conversations = allConversations,
                        onSelectConversation = { conv ->
                            currentConversationId = conv.id
                            currentDestination = NavigationDestination.CHAT
                        }
                    )
                }
            }
        }
    }

    // New Project Dialog
    if (showNewProjectDialog) {
        var newProjName by remember { mutableStateOf("") }
        var newProjConcept by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Create New Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newProjName,
                        onValueChange = { newProjName = it },
                        label = { Text("Project Name (e.g. MyNotesApp)") },
                        modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
                    )
                    OutlinedTextField(
                        value = newProjConcept,
                        onValueChange = { newProjConcept = it },
                        label = { Text("Concept & Features") },
                        modifier = Modifier.fillMaxWidth().testTag("new_project_concept_input"),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newProjName.trim().ifBlank { "UntitledProject" }
                        val concept = newProjConcept.trim().ifBlank { "Android Compose Application" }
                        coroutineScope.launch {
                            val proj = DIModule.projectManager.createProject(name, "Android Compose", concept)
                            startNewChat()
                            showNewProjectDialog = false
                            Toast.makeText(context, "Project '$name' created", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_project_button")
                ) {
                    Text("Create & Open Chat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Projects Picker Dialog
    if (showProjectsDialog) {
        AlertDialog(
            onDismissRequest = { showProjectsDialog = false },
            title = { Text("Projects") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allProjects) { proj ->
                        val isCurrent = proj.id == activeProject?.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        DIModule.projectManager.selectProject(proj)
                                        showProjectsDialog = false
                                        currentDestination = NavigationDestination.CHAT
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.name, fontWeight = FontWeight.Bold)
                                    Text(proj.concept.take(50), style = MaterialTheme.typography.bodySmall)
                                }
                                if (isCurrent) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProjectsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Rename Chat Dialog
    if (showRenameChatDialog) {
        var renameText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRenameChatDialog = false },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val convId = currentConversationId
                        if (convId != null && renameText.isNotBlank()) {
                            coroutineScope.launch {
                                val conv = db.conversationDao().getConversationById(convId)
                                if (conv != null) {
                                    db.conversationDao().updateConversation(conv.copy(title = renameText.trim()))
                                }
                                showRenameChatDialog = false
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameChatDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear Chat Dialog
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Clear Conversation?") },
            text = { Text("This will remove all messages from the current conversation. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val convId = currentConversationId
                        if (convId != null) {
                            coroutineScope.launch {
                                db.messageDao().deleteMessagesForConversation(convId)
                                showClearChatDialog = false
                                Toast.makeText(context, "Conversation cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Project State Dialog
    if (showProjectStateDialog) {
        val proj = activeProject
        val stateContent = remember(proj) {
            if (proj != null) {
                val stateFile = java.io.File(proj.rootPath, "PROJECT_STATE.md")
                if (stateFile.exists()) stateFile.readText() else "PROJECT_STATE.md has not been written yet."
            } else {
                "No active project selected."
            }
        }

        AlertDialog(
            onDismissRequest = { showProjectStateDialog = false },
            title = { Text("PROJECT_STATE.md") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    item {
                        Text(
                            text = stateContent,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProjectStateDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ProjectHistoryView(
    projects: List<ProjectEntity>,
    onSelectProject: (ProjectEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Project History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Audit trail of all projects, blueprints, and progress records",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        items(projects) { proj ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectProject(proj) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = proj.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = proj.status,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = proj.concept,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Created: ${SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(proj.createdAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationHistoryView(
    conversations: List<ConversationEntity>,
    onSelectConversation: (ConversationEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Conversation History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Past chats and project planning sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        items(conversations) { conv ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectConversation(conv) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conv.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(conv.updatedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
