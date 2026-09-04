package com.example.androidllama.ui.screens.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androidllama.ui.screens.chat.models.ChatAttachment
import com.example.androidllama.ui.screens.models.models.ModelInfo

@Composable
fun ChatInput(
    value: String,
    selectedModel: ModelInfo?,
    isGenerating: Boolean,
    webSearchEnabled: Boolean,
    attachment: ChatAttachment?,
    onValueChange: (String) -> Unit,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onPickDocument: () -> Unit,
    onPreviewDocument: (ChatAttachment) -> Unit,
    onRemoveAttachment: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            attachment?.let { selectedAttachment ->
                DocumentAttachmentChip(
                    attachment = selectedAttachment,
                    onPreview = { onPreviewDocument(selectedAttachment) },
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPickDocument,
                    enabled = !isGenerating,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach document")
                }

                IconButton(
                    onClick = { onWebSearchEnabledChange(!webSearchEnabled) },
                    enabled = !isGenerating,
                    modifier = Modifier.size(44.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (webSearchEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = if (webSearchEnabled) "Disable web search" else "Enable web search",
                            modifier = Modifier.padding(10.dp),
                            tint = if (webSearchEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty()) {
                                    Text(
                                        when {
                                            selectedModel == null -> "Load a model first"
                                            attachment != null -> "Ask about the file…"
                                            else -> "Ask something…"
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                FilledIconButton(
                    onClick = if (isGenerating) onStop else onSend,
                    enabled = isGenerating ||
                        ((value.isNotBlank() || attachment != null) && selectedModel != null),
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape
                ) {
                    if (isGenerating) Icon(Icons.Default.Stop, contentDescription = "Stop generation")
                    else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun DocumentAttachmentChip(
    attachment: ChatAttachment,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .weight(1f)
            ) {
                Text(
                    text = attachment.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Document · Tap to preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            onRemove?.let { remove ->
                IconButton(onClick = remove, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                }
            }
        }
    }
}
