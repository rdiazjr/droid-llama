package com.example.androidllama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidllama.ui.components.AppLayout
import com.example.androidllama.ui.components.DroidLlamaSplash
import com.example.androidllama.ui.screens.settings.SettingsViewModel
import com.example.androidllama.ui.theme.AndroidLlamaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            var showSplash by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(900)
                showSplash = false
            }
            AndroidLlamaTheme(themeMode = settingsViewModel.settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        DroidLlamaSplash()
                    } else {
                        AppLayout(settingsViewModel = settingsViewModel)
                    }
                }
            }
        }
    }
}
