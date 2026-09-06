package com.paizi.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paizi.ai.router.AIRouter
import com.paizi.database.ConversationEntity
import com.paizi.database.MessageEntity
import com.paizi.database.ProjectEntity
import com.paizi.di.DIModule
import com.paizi.project.map.BlueprintTask
import com.paizi.project.map.LiveProjectMap
import com.paizi.project.map.TaskStatus
import com.paizi.workflow.WorkflowEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentConversationId: String?,
    onConversationChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var inputPrompt by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var activeConvId by remember { mutableStateOf(currentConversationId) }

    val db = DIModule.database
    val workflowEngine = DIModule.workflowEngine
    val workflowState by workflowEngine.workflowState.collectAsState()
    val liveProjectMap by workflowEngine.liveProjectMap.collectAsState()
    val activeProject by DIModule.projectManager.activeProjectFlow.collectAsState()

    // Activity result launcher for zero-permission photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            Toast.makeText(context, "Attachment selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Ensure an active conversation exists
    LaunchedEffect(currentConversationId) {
        if (currentConversationId != null) {
            activeConvId = currentConversationId
        } else {
            val latestConv = db.conversationDao().getLatestConversation()
            if (latestConv != null) {
                activeConvId = latestConv.id
                onConversationChanged(latestConv.id)
            } else {
                val newId = "conv_" + UUID.randomUUID().toString().take(8)
                val newConv = ConversationEntity(
                    id = newId,
                    projectId = activeProject?.id ?: "default_project",
                    title = "New Conversation"
                )
                db.conversationDao().insertConversation(newConv)
                activeConvId = newId
                onConversationChanged(newId)
            }
        }
    }

    val messagesFlow = remember(activeConvId) {
        if (activeConvId != null) {
            db.messageDao().getMessagesForConversation(activeConvId!!)
        } else {
            null
        }
    }
    val messages by messagesFlow?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(promptText: String) {
        val trimmed = promptText.trim()
        val convId = activeConvId ?: return
        if (trimmed.isBlank() && selectedImageUri == null) return

        coroutineScope.launch {
            isSending = true
            val userMsg = MessageEntity(
                id = "msg_" + UUID.randomUUID().toString().take(8),
                conversationId = convId,
                role = "user",
                content = trimmed,
                attachmentPath = selectedImageUri?.toString()
            )
            db.messageDao().insertMessage(userMsg)
            inputPrompt = ""
            val attachedUri = selectedImageUri
            selectedImageUri = null

            // Update conversation title if first message
            if (messages.isEmpty() || messages.size <= 2) {
                val existingConv = db.conversationDao().getConversationById(convId)
                if (existingConv != null && (existingConv.title == "New Conversation" || existingConv.title == "General Chat")) {
                    val newTitle = trimmed.take(28)
                    db.conversationDao().updateConversation(existingConv.copy(title = newTitle))
                }
            }

            // Detect if user wants to build a project/application
            val lower = trimmed.lowercase()
            val isProjectIntent = lower.contains("app") || lower.contains("application") ||
                    lower.contains("project") || lower.contains("build") || lower.contains("create") ||
                    lower.contains("make") || lower.contains("calculator") || lower.contains("todo") ||
                    lower.contains("بناؤ") || lower.contains("بنانی") || lower.contains("سافٹ ویئر") ||
                    lower.contains("نئی ایپ")

            if (isProjectIntent) {
                // Ensure active project exists or create one
                var targetProject = activeProject
                if (targetProject == null || targetProject.id == "default_project") {
                    val projName = if (trimmed.length > 25) trimmed.take(25) + "..." else trimmed
                    val newProj = DIModule.projectManager.createProject(
                        name = projName,
                        concept = trimmed,
                        type = "Android Compose"
                    )
                    targetProject = newProj
                }

                // Launch Planning Workflow with Live Project Map integration
                workflowEngine.startPlanning(targetProject, trimmed)

                val planResultState = workflowEngine.workflowState.value
                val blueprintText = planResultState.currentBlueprint
                val currentMap = workflowEngine.liveProjectMap.value

                val planningResponse = StringBuilder()
                planningResponse.appendLine("🚀 **PAIZI Autonomous Engineering Pipeline Initialized**\n")
                planningResponse.appendLine("I have analyzed your requirement and formulated a dynamic project blueprint for **${targetProject.name}**.\n")

                if (!blueprintText.isNullOrBlank()) {
                    planningResponse.appendLine("### 📋 Generated Project Blueprint\n")
                    planningResponse.appendLine(blueprintText)
                    planningResponse.appendLine("\n---\n**Status**: Awaiting User Review & Approval.\n*Implementation is strictly locked until you approve the blueprint below.*")
                } else if (currentMap != null) {
                    planningResponse.appendLine(currentMap.toConversationalText())
                }

                val aiMsg = MessageEntity(
                    id = "msg_" + UUID.randomUUID().toString().take(8),
                    conversationId = convId,
                    role = "assistant",
                    content = planningResponse.toString(),
                    modelUsed = "PlannerAgent",
                    providerUsed = "PAIZI Multi-Agent Workflow"
                )
                db.messageDao().insertMessage(aiMsg)
            } else {
                // General conversational request (Urdu / English / Coding Q&A)
                var visionSummary = ""
                if (attachedUri != null) {
                    try {
                        val processed = DIModule.visionManager.processImageUri(attachedUri)
                        visionSummary = "\n[Attached Image: ${processed.width}x${processed.height} (${processed.mimeType})]"
                    } catch (_: Exception) {}
                }

                val systemPrompt = "You are PAIZI, a friendly, brilliant, and autonomous AI Software Engineer. " +
                        "You converse fluently in English, Urdu, or any language the user speaks. " +
                        "You assist with questions, software engineering, architecture, debugging, or casual conversation. " +
                        "Always be helpful, precise, and polite. If the user greets you with 'السلام علیکم', respond with a warm Islamic greeting and offer your software assistance."

                val finalUserPrompt = if (visionSummary.isNotBlank()) "$trimmed\n$visionSummary" else trimmed

                val routeResponse = DIModule.aiRouter.routeAndExecute(
                    taskType = AIRouter.TaskType.GENERAL,
                    systemPrompt = systemPrompt,
                    userPrompt = finalUserPrompt
                )

                when (routeResponse) {
                    is AIRouter.RouteResult.Success -> {
                        val aiMsg = MessageEntity(
                            id = "msg_" + UUID.randomUUID().toString().take(8),
                            conversationId = convId,
                            role = "assistant",
                            content = routeResponse.responseText,
                            modelUsed = routeResponse.modelName,
                            providerUsed = routeResponse.source,
                            tokenUsage = routeResponse.tokenUsage
                        )
                        db.messageDao().insertMessage(aiMsg)
                    }
                    is AIRouter.RouteResult.Failure -> {
                        val errText = "⚠️ **AI Connection Issue**\n\n${routeResponse.errorSummary}\n\n**Actionable Guidance**: ${routeResponse.actionableAdvice}"
                        val errEntity = MessageEntity(
                            id = "msg_" + UUID.randomUUID().toString().take(8),
                            conversationId = convId,
                            role = "assistant",
                            content = errText,
                            isError = true
                        )
                        db.messageDao().insertMessage(errEntity)
                    }
                }
            }
            isSending = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Dynamic Live Project Blueprint / Map (when active)
        liveProjectMap?.let { map ->
            LiveProjectMapCard(
                map = map,
                isAwaitingApproval = workflowState.currentStep == WorkflowEngine.WorkflowStep.AWAITING_APPROVAL,
                onApprovePlan = {
                    coroutineScope.launch {
                        val project = activeProject
                        if (project != null) {
                            workflowEngine.handleUserApproval(project, approved = true, feedback = "Approved from Live Project Map")
                            val confirmMsg = MessageEntity(
                                id = "msg_" + UUID.randomUUID().toString().take(8),
                                conversationId = activeConvId ?: "",
                                role = "assistant",
                                content = "✅ **Blueprint Approved!**\n\nCoderAgent has started autonomous implementation. Code files and test evidence are being generated in the workspace."
                            )
                            db.messageDao().insertMessage(confirmMsg)
                        } else {
                            Toast.makeText(context, "No active project context found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // Main Conversation Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                // ChatGPT-style Clean Empty State
                EmptyChatWelcomeView(
                    onStarterPromptSelected = { starter ->
                        inputPrompt = starter
                        sendMessage(starter)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            onCopyText = {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onApprovePlan = {
                                coroutineScope.launch {
                                    val project = activeProject
                                    if (project != null) {
                                        workflowEngine.handleUserApproval(project, approved = true, feedback = "Approved from Chat")
                                        val confirmMsg = MessageEntity(
                                            id = "msg_" + UUID.randomUUID().toString().take(8),
                                            conversationId = activeConvId ?: "",
                                            role = "assistant",
                                            content = "✅ **Blueprint Approved!**\n\nCoderAgent has started autonomous implementation. Code files and test evidence are being generated in the workspace."
                                        )
                                        db.messageDao().insertMessage(confirmMsg)
                                    } else {
                                        Toast.makeText(context, "No active project context found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }

                    if (isSending) {
                        item {
                            ThinkingIndicator()
                        }
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }

        // Bottom Chat Composer (ChatGPT-style)
        ChatComposer(
            inputPrompt = inputPrompt,
            onInputChange = { inputPrompt = it },
            selectedImageUri = selectedImageUri,
            onClearImage = { selectedImageUri = null },
            isSending = isSending,
            onSend = { sendMessage(inputPrompt) },
            onPickAttachment = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onVoiceInput = {
                DIModule.voiceManager.startListening { text ->
                    inputPrompt = if (inputPrompt.isBlank()) text else "$inputPrompt $text"
                }
            }
        )
    }
}

@Composable
fun EmptyChatWelcomeView(onStarterPromptSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "PAIZI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PAIZI Autonomous AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "میں آج آپ کے لیے کیا بنا سکتا ہوں؟",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Chat casually, ask questions, or describe any Android app to build.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Quick action chips
        val suggestions = listOf(
            "👋 السلام علیکم",
            "📱 Make an Android To-Do App",
            "🧮 Build a Calculator App",
            "⚡ Continue active project"
        )

        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStarterPromptSelected(suggestion) }
                        .testTag("starter_chip_${suggestion.take(10)}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: MessageEntity,
    onCopyText: (String) -> Unit,
    onApprovePlan: () -> Unit
) {
    val isUser = message.role == "user"
    val isBlueprint = message.content.contains("📋 Generated Project Blueprint")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 4.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                color = when {
                    message.isError -> MaterialTheme.colorScheme.errorContainer
                    isUser -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.testTag("chat_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            message.isError -> MaterialTheme.colorScheme.onErrorContainer
                            isUser -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        lineHeight = 22.sp
                    )

                    // If message contains blueprint, show Approval Action Card right here!
                    if (isBlueprint && !isUser) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚡ Autonomous Workflow Control",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ready to synthesize Kotlin files, tests, and build architecture?",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onApprovePlan,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_approve_blueprint_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve Plan & Start Coding")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isUser && message.modelUsed != null) {
                            Text(
                                text = "🤖 ${message.modelUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = { onCopyText(message.content) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = "PAIZI is thinking and formulating response...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ChatComposer(
    inputPrompt: String,
    onInputChange: (String) -> Unit,
    selectedImageUri: Uri?,
    onClearImage: () -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    onPickAttachment: () -> Unit,
    onVoiceInput: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Attachment preview badge if selected
            if (selectedImageUri != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📎 Attachment selected for vision analysis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearImage, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachment Plus Button
                IconButton(
                    onClick = onPickAttachment,
                    modifier = Modifier.size(40.dp).testTag("chat_attachment_button")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Attachment",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Voice Mic Button
                IconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier.size(40.dp).testTag("chat_voice_button")
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text Input
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "Message PAIZI...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    )
                )

                // Send Button
                Surface(
                    shape = CircleShape,
                    color = if (inputPrompt.isNotBlank() && !isSending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = onSend,
                        enabled = inputPrompt.isNotBlank() && !isSending,
                        modifier = Modifier.testTag("chat_send_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputPrompt.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveProjectMapCard(
    map: LiveProjectMap,
    isAwaitingApproval: Boolean = false,
    onApprovePlan: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("live_project_map_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: PROJECT: <Name> and <Progress>% COMPLETE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PROJECT: ${map.projectName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${map.progressPercentage}% COMPLETE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (map.progressPercentage == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse Map" else "Expand Map",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Real Mathematical Progress Indicator (NO fake animation)
            LinearProgressIndicator(
                progress = { map.progressPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (map.progressPercentage == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Task sections list
                    map.tasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.status.symbol,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = when (task.status) {
                                    TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                    TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                                    TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                                    TaskStatus.BLOCKED, TaskStatus.NOT_SUPPORTED -> MaterialTheme.colorScheme.error
                                    TaskStatus.PENDING -> MaterialTheme.colorScheme.outline
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (task.status == TaskStatus.IN_PROGRESS) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!task.evidence.isNullOrBlank()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = task.evidence.take(24),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CURRENTLY: action description
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CURRENTLY: ${map.currentActionDescription}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Awaiting Approval Action Button
                    if (isAwaitingApproval) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onApprovePlan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("map_approve_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve Plan & Start Autonomous Build")
                        }
                    }

                    // Final Completion Gate Results
                    if (map.finalArtifactPath != null && map.finalVerificationStatus == "VERIFIED") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "FINAL ARTIFACT: ${map.finalArtifactPath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (map.finalVerificationStatus == "FAILED" || map.finalVerificationStatus == "BLOCKED") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "STATUS: ${map.finalVerificationStatus} — Verification gate failed.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
