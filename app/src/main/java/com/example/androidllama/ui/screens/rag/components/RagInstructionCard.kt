package com.example.androidllama.ui.screens.rag.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidllama.data.rag.RagInstructionRecord
import com.example.androidllama.rag.RagInstructionValidator

@Composable
fun RagInstructionCard(
    instruction: RagInstructionRecord,
    enabled: Boolean,
    onDelete: () -> Unit
) {
    val parsed = runCatching { RagInstructionValidator.parse(instruction.rawJson) }.getOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(instruction.name, style = MaterialTheme.typography.titleSmall)
                parsed?.let {
                    Text(
                        "Top ${it.topK} • chunk ${it.chunkSize} • score ${it.minimumScore}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "Remove instructions")
            }
        }
    }
}
