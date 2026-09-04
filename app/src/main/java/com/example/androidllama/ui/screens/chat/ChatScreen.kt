package com.example.androidllama.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidllama.ui.screens.chat.components.ChatContainer
import com.example.androidllama.ui.screens.chat.models.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    ChatContainer(
        viewModel = viewModel
    )
}