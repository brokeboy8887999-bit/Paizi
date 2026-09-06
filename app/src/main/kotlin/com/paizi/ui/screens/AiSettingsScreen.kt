package com.paizi.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.paizi.database.OfflineModelEntity
import com.paizi.database.OnlineProviderConfigEntity
import com.paizi.di.DIModule
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val providerDao = DIModule.database.providerConfigDao()
    val modelDao = DIModule.database.offlineModelDao()
    val offlineAIManager = DIModule.offlineAIManager

    val providers by providerDao.getAllProviders().collectAsState(initial = emptyList())
    val offlineModels by modelDao.getAllOfflineModels().collectAsState(initial = emptyList())
    val progressMap by offlineAIManager.downloadProgressFlow.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Online Providers (10 Slots)", "Offline GGUF Models (5 Slots)")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "AI Subsystem Configuration",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Zero fake AI: Configure 10 real online providers or 5 offline GGUF local models",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                    icon = {
                        Icon(
                            if (index == 0) Icons.Default.Cloud else Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTabIndex == 0) {
            // Online Providers List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(providers, key = { it.slotIndex }) { p ->
                    OnlineProviderCard(
                        provider = p,
                        onSave = { updated ->
                            coroutineScope.launch {
                                providerDao.insertOrUpdateProvider(updated)
                                Toast.makeText(context, "Saved slot ${updated.slotIndex + 1}: ${updated.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        } else {
            // Offline Models List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(offlineModels, key = { it.slotIndex }) { m ->
                    val progress = progressMap[m.slotIndex]
                    OfflineModelCard(
                        model = m,
                        progress = progress,
                        onDownload = {
                            coroutineScope.launch {
                                offlineAIManager.startOrResumeDownload(m.slotIndex, this)
                            }
                        },
                        onPause = {
                            offlineAIManager.pauseDownload(m.slotIndex)
                        },
                        onDelete = {
                            coroutineScope.launch {
                                offlineAIManager.deleteModel(m.slotIndex)
                            }
                        },
                        onLoad = {
                            coroutineScope.launch {
                                val loaded = offlineAIManager.loadModel(m.slotIndex)
                                Toast.makeText(context, if (loaded) "Loaded ${m.name}" else "Failed to load model", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUnload = {
                            coroutineScope.launch {
                                offlineAIManager.unloadModel()
                                Toast.makeText(context, "Unloaded model", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineProviderCard(
    provider: OnlineProviderConfigEntity,
    onSave: (OnlineProviderConfigEntity) -> Unit
) {
    var name by remember(provider) { mutableStateOf(provider.name) }
    var baseUrl by remember(provider) { mutableStateOf(provider.baseUrl) }
    var apiKey by remember(provider) { mutableStateOf(provider.apiKey) }
    var modelName by remember(provider) { mutableStateOf(provider.modelName) }
    var isEnabled by remember(provider) { mutableStateOf(provider.isEnabled) }
    var showApiKey by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_slot_${provider.slotIndex}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Slot ${provider.slotIndex + 1}: $name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Priority: ${provider.priority}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEnabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        modifier = Modifier.testTag("provider_switch_${provider.slotIndex}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Provider Display Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL Endpoint") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key (Stored locally in Room DB)") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Identifier (e.g. gemini-2.5-flash, gpt-4o)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onSave(
                        provider.copy(
                            name = name,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            modelName = modelName,
                            isEnabled = isEnabled
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("save_provider_${provider.slotIndex}")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Configuration")
            }
        }
    }
}

@Composable
fun OfflineModelCard(
    model: OfflineModelEntity,
    progress: com.paizi.ai.offline.OfflineAIManager.DownloadProgress?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_model_slot_${model.slotIndex}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Slot ${model.slotIndex + 1}: ${model.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Arch: ${model.architecture} | Target: ${model.fileName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Surface(
                    color = when (model.status) {
                        "INSTALLED" -> MaterialTheme.colorScheme.primaryContainer
                        "LOADED", "ACTIVE" -> MaterialTheme.colorScheme.tertiaryContainer
                        "DOWNLOADING" -> MaterialTheme.colorScheme.secondaryContainer
                        "ERROR" -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = model.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "File Size: ${if (model.fileSize > 0) "${model.fileSize / (1024 * 1024)}MB" else "Unknown"} | Context: ${model.contextSize} tokens",
                style = MaterialTheme.typography.bodySmall
            )
            if (model.sha256Hash.isNotBlank()) {
                Text(
                    text = "SHA-256: ${model.sha256Hash.take(16)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (model.lastError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Last Error: ${model.lastError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Real download progress bar
            if (model.status == "DOWNLOADING" || progress?.isDownloading == true) {
                Spacer(modifier = Modifier.height(8.dp))
                val pct = progress?.percent ?: 0f
                LinearProgressIndicator(
                    progress = { pct / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val downloadedMb = (progress?.downloadedBytes ?: 0L) / (1024 * 1024)
                    val totalMb = (progress?.totalBytes ?: model.fileSize) / (1024 * 1024)
                    Text(
                        text = "${downloadedMb}MB / ${totalMb}MB (${"%.1f".format(pct)}%)",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "HTTP Resumable Stream",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (model.status == "DOWNLOADING") {
                    Button(onClick = onPause, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                } else if (model.status != "INSTALLED" && model.status != "LOADED") {
                    Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (model.status == "PAUSED") "Resume" else "Download")
                    }
                }

                if (model.status == "INSTALLED") {
                    Button(onClick = onLoad, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load in RAM")
                    }
                } else if (model.status == "LOADED") {
                    OutlinedButton(onClick = onUnload, modifier = Modifier.weight(1f)) {
                        Text("Unload")
                    }
                }

                if (model.status in listOf("INSTALLED", "PAUSED", "ERROR")) {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
