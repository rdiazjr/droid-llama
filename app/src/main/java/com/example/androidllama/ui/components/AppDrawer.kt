package com.example.androidllama.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androidllama.R
import com.example.androidllama.data.chat.ChatSummary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    chatHistory: List<ChatSummary>,
    currentChatId: String?,
    onNewChatClick: () -> Unit,
    onChatClick: (ChatSummary) -> Unit,
    onDeleteChat: (ChatSummary) -> Unit,
    onModelsClick: () -> Unit,
    onRagClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var deleteTarget by remember { mutableStateOf<ChatSummary?>(null) }
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp)
    ) {

        Spacer(
            modifier = Modifier.height(30.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFF111518)
            ) {
                Image(
                    painter = painterResource(R.drawable.android_llama_foreground),
                    contentDescription = "DroidLlama logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge
            )
        }

        HorizontalDivider()

        NavigationDrawerItem(
            label = {
                Text("New Chat")
            },
            selected = false,
            onClick = onNewChatClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Chat"
                )
            }
        )

        NavigationDrawerItem(
            label = {
                Text("Models")
            },
            selected = false,
            onClick = onModelsClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Models"
                )
            }
        )

        NavigationDrawerItem(
            label = {
                Text("File Manager")
            },
            selected = false,
            onClick = onRagClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = "File Manager"
                )
            }
        )

        NavigationDrawerItem(
            label = {
                Text("Settings")
            },
            selected = false,
            onClick = onSettingsClick,
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Chat History",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {

            items(chatHistory, key = { it.id }) { chat ->

                val selected = currentChatId == chat.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .combinedClickable(
                            onClick = { onChatClick(chat) },
                            onLongClick = { deleteTarget = chat }
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chat.title,
                                modifier = Modifier.padding(start = 12.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${chat.messageCount} ${if (chat.messageCount == 1) "message" else "messages"}",
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

    }

    deleteTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete chat?") },
            text = { Text("\"${chat.title}\" and all of its messages will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeleteChat(chat)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}
