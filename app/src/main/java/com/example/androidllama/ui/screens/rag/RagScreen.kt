package com.example.androidllama.ui.screens.rag

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidllama.data.rag.RagDocument
import com.example.androidllama.ui.screens.rag.components.DeleteDocumentDialog
import com.example.androidllama.ui.screens.rag.components.RagDocumentItem
import com.example.androidllama.ui.screens.rag.components.RagInstructionCard
import com.example.androidllama.ui.screens.rag.components.RenameDocumentDialog

@Composable
fun RagScreen(viewModel: RagViewModel = viewModel()) {
    val state = viewModel.uiState
    var renameTarget by remember { mutableStateOf<RagDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<RagDocument?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> if (uris.isNotEmpty()) viewModel.importDocuments(uris) }
    )
    val instructionPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let(viewModel::importInstruction) }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("File Manager", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Store local reference files and configure how they will be used during retrieval.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !state.isWorking,
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "text/plain",
                                "text/markdown",
                                "application/pdf",
                                "application/json"
                            )
                        )
                    }
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Add files")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !state.isWorking,
                    onClick = { instructionPicker.launch("application/json") }
                ) {
                    androidx.compose.material3.Icon(Icons.Default.UploadFile, contentDescription = null)
                    Text(" Instructions")
                }
            }
        }

        if (state.isWorking) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("  Saving locally…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.message?.let { message ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (state.isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = MaterialTheme.shapes.medium,
                    onClick = viewModel::clearMessage
                ) {
                    Text(message, modifier = Modifier.padding(12.dp))
                }
            }
        }

        item { Text("Custom instructions", style = MaterialTheme.typography.titleMedium) }
        item {
            state.activeInstruction?.let { instruction ->
                RagInstructionCard(
                    instruction = instruction,
                    enabled = !state.isWorking,
                    onDelete = viewModel::deleteInstruction
                )
            } ?: Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "No instruction JSON imported. Default retrieval settings will be used.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Spacer(Modifier.height(2.dp))
            Text("Stored files (${state.documents.size})", style = MaterialTheme.typography.titleMedium)
        }

        if (state.documents.isEmpty() && !state.isWorking) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "No files yet. Add TXT, Markdown, PDF, or JSON documents from your device.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(state.documents, key = { it.id }) { document ->
            RagDocumentItem(
                document = document,
                enabled = !state.isWorking,
                onRename = { renameTarget = document },
                onDelete = { deleteTarget = document }
            )
        }

        item { Spacer(Modifier.height(12.dp)) }
    }

    renameTarget?.let { document ->
        RenameDocumentDialog(
            document = document,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameDocument(document, newName)
            }
        )
    }
    deleteTarget?.let { document ->
        DeleteDocumentDialog(
            document = document,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                viewModel.deleteDocument(document)
            }
        )
    }
}

