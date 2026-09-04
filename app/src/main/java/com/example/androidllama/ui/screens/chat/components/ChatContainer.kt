package com.example.androidllama.ui.screens.chat.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidllama.ui.screens.chat.models.ChatMessage
import com.example.androidllama.ui.screens.chat.models.ChatViewModel
import com.example.androidllama.ui.screens.chat.models.MessageRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatContainer(
    viewModel: ChatViewModel
) {
    val loadedModels = viewModel.loadedModels
    val markdownPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::attachDocument) }
    )

    LaunchedEffect(loadedModels.map { it.id }) {
        viewModel.ensureModelSelection()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        if (viewModel.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start Your Legendary Chat Now",
                    modifier = Modifier.padding(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {

                items(viewModel.messages) { message ->

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 20.dp
                            ),
                        verticalAlignment = Alignment.Top
                    ) {

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .offset(y = (-3).dp)
                                .clip(CircleShape)
                                .background(
                                    if (message.role == MessageRole.USER) {
                                        Color.DarkGray
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = message.avatarLetter(),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(
                            modifier = Modifier.width(12.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = message.authorName(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (message.activityDetails.isNotBlank()) {
                                BrowsingBlock(
                                    label = if (message.activityLabel == "Browsing…") {
                                        "Browsing…"
                                    } else {
                                        "Browsed the web"
                                    },
                                    details = message.activityDetails,
                                    active = message.showThinking &&
                                        message.activityLabel == "Browsing…"
                                )
                            }
                            if (
                                message.showThinking &&
                                message.text.isBlank() &&
                                message.activityLabel == "Thinking…"
                            ) {
                                ThinkingBlock(message.thinking)
                            }
                            if (message.text.isNotBlank()) {
                                SelectionContainer {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MarkdownContent(message.text)
                                    }
                                }
                            }
                            message.attachments.forEach { attachment ->
                                DocumentAttachmentChip(
                                    attachment = attachment,
                                    onPreview = { viewModel.openDocumentPreview(attachment) }
                                )
                            }
                            if (
                                message.role == MessageRole.ASSISTANT &&
                                message.webBrowsingEnabled &&
                                message.activityDetails.isBlank()
                            ) {
                                Text(
                                    text = "Searched the web",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (message.role == MessageRole.USER) {
                                Text(
                                    text = formatTimestamp(message.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val generationMetadata = buildList {
                                    message.generationDurationMs?.let { duration ->
                                        add("Generated in ${formatGenerationDuration(duration)}")
                                    }
                                    message.tokensPerSecond?.let { speed ->
                                        add(String.format(Locale.US, "%.1f tokens/s", speed))
                                    }
                                }.joinToString(" • ")
                                if (generationMetadata.isNotBlank()) {
                                    Text(
                                        text = generationMetadata,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Bottom border only
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.LightGray
                    )
                }
            }
        }
        }

        viewModel.generationError?.let { error ->
            Text(
                text = error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        ChatInput(
            value = viewModel.messageText,
            selectedModel = viewModel.selectedModel,
            isGenerating = viewModel.isGenerating,
            webSearchEnabled = viewModel.webSearchEnabled,
            attachment = viewModel.pendingAttachment,
            onValueChange = viewModel::onMessageChange,
            onWebSearchEnabledChange = viewModel::updateWebSearchEnabled,
            onPickDocument = {
                markdownPicker.launch(
                    arrayOf(
                        "application/pdf",
                        "text/*",
                        "application/json",
                        "application/xml",
                        "application/yaml"
                    )
                )
            },
            onPreviewDocument = viewModel::openDocumentPreview,
            onRemoveAttachment = viewModel::removeAttachment,
            onSend = viewModel::sendMessage,
            onStop = viewModel::stopGeneration
        )
    }

    viewModel.documentPreview?.let { preview ->
        DocumentPreviewDialog(
            preview = preview,
            onDismiss = viewModel::dismissDocumentPreview
        )
    }
}

private fun ChatMessage.authorName(): String = when (role) {
    MessageRole.USER -> "You"
    MessageRole.ASSISTANT -> modelName?.takeIf(String::isNotBlank) ?: "AI"
}

private fun ChatMessage.avatarLetter(): String = when (role) {
    MessageRole.USER -> "Y"
    MessageRole.ASSISTANT -> authorName().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
}

private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat(
    "h:mm a • MMM d, yyyy",
    Locale.getDefault()
).format(Date(timestamp))

private fun formatGenerationDuration(durationMs: Long): String = when {
    durationMs < 1_000L -> "$durationMs ms"
    durationMs < 60_000L -> String.format(Locale.US, "%.1f s", durationMs / 1_000.0)
    else -> "%d:%02d".format(
        Locale.US,
        durationMs / 60_000L,
        (durationMs % 60_000L) / 1_000L
    )
}

@Composable
private fun ThinkingBlock(thinking: String) {
    var expanded by remember { mutableStateOf(true) }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thinking…",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse thinking" else "Expand thinking",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && thinking.isNotBlank()) {
                Text(
                    text = thinking,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }
        }
    }
}

@Composable
private fun BrowsingBlock(label: String, details: String, active: Boolean) {
    var expanded by remember(details) { mutableStateOf(active) }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse browsing details" else "Expand browsing details",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (expanded) {
                Text(
                    text = details,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
