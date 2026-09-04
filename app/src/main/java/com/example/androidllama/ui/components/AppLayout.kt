package com.example.androidllama.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.androidllama.ui.navigation.Routes
import com.example.androidllama.ui.screens.chat.ChatScreen
import com.example.androidllama.ui.screens.chat.models.ChatViewModel
import com.example.androidllama.ui.screens.models.ModelScreen
import com.example.androidllama.ui.screens.models.models.HuggingFaceModelRepository
import com.example.androidllama.ui.screens.models.models.ModelStore
import com.example.androidllama.ui.screens.rag.RagScreen
import com.example.androidllama.ui.screens.settings.SettingsScreen
import com.example.androidllama.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppLayout(settingsViewModel: SettingsViewModel) {

    val appContext = LocalContext.current.applicationContext

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()
    val loadedModels = chatViewModel.loadedModels

    LaunchedEffect(Unit) {
        val downloadedModels = withContext(Dispatchers.IO) {
            HuggingFaceModelRepository(appContext).readCachedDownloadedModels()
        }
        ModelStore.mergeDownloaded(downloadedModels)
    }

    LaunchedEffect(loadedModels.map { it.id }) {
        chatViewModel.ensureModelSelection()
    }

    fun navigate(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
        }

        scope.launch {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                chatHistory = chatViewModel.conversations,
                currentChatId = chatViewModel.currentConversationId,

                onNewChatClick = {
                    chatViewModel.newChat()
                    navigate(Routes.CHAT)
                },

                onChatClick = { chat ->
                    chatViewModel.openChat(chat.id)
                    navigate(Routes.CHAT)
                },

                onDeleteChat = { chat ->
                    chatViewModel.deleteChat(chat.id)
                },

                onModelsClick = {
                    navigate(Routes.MODELS)
                },

                onRagClick = {
                    navigate(Routes.RAG)
                },

                onSettingsClick = {
                    navigate(Routes.SETTINGS)
                }
            )
        }
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {

            AppHeader(
                loadedModels = loadedModels,
                selectedModel = chatViewModel.selectedModel,
                onModelSelected = chatViewModel::selectModel,
                onMenuClick = {
                    scope.launch {
                        drawerState.open()
                    }
                },

                onNewChatClick = {
                    chatViewModel.newChat()
                    navigate(Routes.CHAT)
                }
            )

            NavHost(
                navController = navController,
                startDestination = Routes.CHAT,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    )
            ) {

                composable(Routes.CHAT) {
                    ChatScreen(viewModel = chatViewModel)
                }

                composable(Routes.MODELS) {
                    ModelScreen()
                }

                composable(Routes.RAG) {
                    RagScreen()
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
