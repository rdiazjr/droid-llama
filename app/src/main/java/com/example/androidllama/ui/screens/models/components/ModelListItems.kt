package com.example.androidllama.ui.screens.models.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidllama.ui.screens.models.models.ModelInfo

@Composable
fun ModelListItem(
    model: ModelInfo,
    onDownloadClick: () -> Unit,
    onLoadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelDownload: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete downloaded model?") },
            text = { Text("${model.name} will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteClick()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            color = Color.LightGray
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = String.format(
                    java.util.Locale.US,
                    "%.1fB parameters • %s • %s",
                    model.parameterBillions,
                    model.size,
                    model.quantization
                ),
                fontSize = 13.sp,
                color = Color.Gray
            )

            if (model.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    model.tags.forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            model.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (model.downloaded) {
                    Button(onClick = onLoadClick, enabled = !model.loading) {
                        if (model.loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            when {
                                model.loaded && model.loading -> "Unloading…"
                                model.loading -> "Loading…"
                                model.loaded -> "Unload"
                                else -> "Load"
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = !model.loading && !model.loaded
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete ${model.name}",
                            tint = if (model.loaded || model.loading) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                } else if (model.downloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            if (model.downloadProgress == null) {
                                LinearProgressIndicator(modifier = Modifier.width(140.dp))
                            } else {
                                LinearProgressIndicator(
                                    progress = { model.downloadProgress / 100f },
                                    modifier = Modifier.width(140.dp)
                                )
                            }
                            Text(
                                model.downloadProgress?.let { "$it%" } ?: "Downloading…",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = onCancelDownload) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel ${model.name} download"
                            )
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Button(onClick = onDownloadClick) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}
