package com.paizi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun ChatScreen(
    activeProject: ProjectEntity?,
    onNavigateToWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var inputPrompt by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var activeConversationId by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    // Initialize conversation for active project if needed
    LaunchedEffect(activeProject?.id) {
        if (activeProject != null) {
            val db = DIModule.database
            val existing = db.conversationDao().getConversationById("conv_${activeProject.id}")
            if (existing == null) {
                val newConv = ConversationEntity(
                    id = "conv_${activeProject.id}",
                    projectId = activeProject.id,
                    title = "Main Conversation"
                )
                db.conversationDao().insertConversation(newConv)
                activeConversationId = newConv.id
            } else {
                activeConversationId = existing.id
            }
        }
    }

    val messagesFlow = remember(activeConversationId) {
        if (activeConversationId != null) {
            DIModule.database.messageDao().getMessagesForConversation(activeConversationId!!)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        if (activeProject == null) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "No Active Project Selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create or switch to a project to bind this conversation and autonomous agent memory.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onNavigateToWorkspace) {
                        Text("Go to Projects & Workspace")
                    }
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active Project: ${activeProject.name} (${activeProject.type})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PAIZI Autonomous AI is ready.\nDescribe your software requirement or ask for code implementation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    onCopyText = { clipboardManager.setText(AnnotatedString(it)) }
                )
            }
        }

        // Selected Attachment Preview
        if (selectedImageUri != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vision Attachment Selected",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { selectedImageUri = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove Image", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.testTag("chat_attachment_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Attachment", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(
                onClick = {
                    DIModule.voiceManager.startListening { voiceText ->
                        inputPrompt = if (inputPrompt.isBlank()) voiceText else "$inputPrompt $voiceText"
                    }
                },
                modifier = Modifier.testTag("chat_voice_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = { Text("Describe application feature...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(6.dp),
                    strokeWidth = 3.dp
                )
            } else {
                IconButton(
                    onClick = {
                        val prompt = inputPrompt.trim()
                        if (prompt.isNotBlank() && activeProject != null && activeConversationId != null) {
                            coroutineScope.launch {
                                isSending = true
                                val userMsg = MessageEntity(
                                    id = "msg_" + UUID.randomUUID().toString().take(8),
                                    conversationId = activeConversationId!!,
                                    role = "user",
                                    content = prompt,
                                    attachmentPath = selectedImageUri?.toString()
                                )
                                DIModule.database.messageDao().insertMessage(userMsg)
                                inputPrompt = ""
                                selectedImageUri = null

                                // Route through AIRouter
                                val response = DIModule.aiRouter.routeAndExecute(
                                    taskType = AIRouter.TaskType.GENERAL,
                                    systemPrompt = "You are PAIZI AI Software Engineer. Project: ${activeProject.name}. Concept: ${activeProject.concept}",
                                    userPrompt = prompt
                                )

                                when (response) {
                                    is AIRouter.RouteResult.Success -> {
                                        val aiMsg = MessageEntity(
                                            id = "msg_" + UUID.randomUUID().toString().take(8),
                                            conversationId = activeConversationId!!,
                                            role = "assistant",
                                            content = response.responseText,
                                            modelUsed = response.modelName,
                                            providerUsed = response.source,
                                            tokenUsage = response.tokenUsage
                                        )
                                        DIModule.database.messageDao().insertMessage(aiMsg)
                                    }
                                    is AIRouter.RouteResult.Failure -> {
                                        val errText = "Error: ${response.errorSummary}\n\nActionable Advice: ${response.actionableAdvice}"
                                        val errEntity = MessageEntity(
                                            id = "msg_" + UUID.randomUUID().toString().take(8),
                                            conversationId = activeConversationId!!,
                                            role = "assistant",
                                            content = errText,
                                            isError = true
                                        )
                                        DIModule.database.messageDao().insertMessage(errEntity)
                                    }
                                }
                                isSending = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("chat_send_button"),
                    enabled = inputPrompt.isNotBlank() && !isSending
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = if (inputPrompt.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    onCopyText: (String) -> Unit
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = align
    ) {
        // Meta Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                text = if (isUser) "You" else message.providerUsed ?: "PAIZI AI",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (message.modelUsed != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = message.modelUsed,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Bubble Content
        Surface(
            color = if (message.isError) MaterialTheme.colorScheme.errorContainer else bgColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(max = 340.dp)
                .testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else textColor,
                    lineHeight = 20.sp
                )

                if (message.tokenUsage > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tokens: ${message.tokenUsage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onCopyText(message.content) }) {
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
