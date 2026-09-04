package com.example.androidllama.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.androidllama.data.settings.AiSettings
import com.example.androidllama.data.settings.AiSettingsRepository
import com.example.androidllama.data.settings.AppThemeMode
import com.example.androidllama.data.settings.PersonalityPreset
import com.example.androidllama.data.settings.ReasoningMode
import com.example.androidllama.data.settings.RuntimeBackend
import com.example.androidllama.inference.LlamaRuntime
import com.example.androidllama.inference.BackendDevice

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AiSettingsRepository(application)

    var settings by mutableStateOf(repository.load())
        private set

    val supportedBackends: Set<RuntimeBackend> = LlamaRuntime.supportedBackends()
    val backendDevices: List<BackendDevice> = LlamaRuntime.backendDevices()

    fun unavailableBackendReason(backend: RuntimeBackend): String = when (backend) {
        RuntimeBackend.CPU -> "CPU is always available."
        RuntimeBackend.VULKAN ->
            "Vulkan is included, but Android did not report a compatible Vulkan compute device or driver."
        RuntimeBackend.OPENCL ->
            "OpenCL is included, but no vendor OpenCL driver was exposed to the app. OpenGL and OpenCL are different APIs, so OpenGL support does not guarantee OpenCL support."
    }

    init {
        if (settings.backend !in supportedBackends) {
            settings = settings.copy(backend = RuntimeBackend.CPU, gpuLayers = 0)
            repository.save(settings)
        }
    }

    fun setThemeMode(value: AppThemeMode) = update { copy(themeMode = value) }
    fun setBackend(value: RuntimeBackend) {
        if (value in supportedBackends) update { copy(backend = value) }
    }
    fun setCpuThreads(value: Int) = update { copy(cpuThreads = value.coerceIn(1, 32)) }
    fun setGpuLayers(value: Int) = update { copy(gpuLayers = value.coerceIn(0, 100)) }
    fun setTemperature(value: Float) = update { copy(temperature = value.coerceIn(0f, 2f)) }
    fun setMaxTokens(value: Int) = update { copy(maxTokens = value.coerceIn(64, 8192)) }
    fun setContextWindow(value: Int) = update { copy(contextWindow = value) }
    fun setTopP(value: Float) = update { copy(topP = value.coerceIn(0.1f, 1f)) }
    fun setTopK(value: Int) = update { copy(topK = value.coerceIn(1, 100)) }
    fun setMinP(value: Float) = update { copy(minP = value.coerceIn(0f, 1f)) }
    fun setRepeatPenalty(value: Float) = update { copy(repeatPenalty = value.coerceIn(0.8f, 1.5f)) }
    fun setSeed(value: Int) = update { copy(seed = value) }
    fun setUseModelDefaults(value: Boolean) = update { copy(useModelDefaults = value) }
    fun setReasoningMode(value: ReasoningMode) = update { copy(reasoningMode = value) }

    fun setPersonality(value: PersonalityPreset) = update {
        copy(
            personality = value,
            systemPrompt = value.prompt ?: systemPrompt
        )
    }

    fun setSystemPrompt(value: String) = update {
        copy(personality = PersonalityPreset.CUSTOM, systemPrompt = value.take(4000))
    }

    fun setStreamResponses(value: Boolean) = update { copy(streamResponses = value) }
    fun setMemoryMapping(value: Boolean) = update { copy(useMemoryMapping = value) }
    fun setRememberConversation(value: Boolean) = update { copy(rememberConversation = value) }
    fun setRagCitations(value: Boolean) = update { copy(includeRagCitations = value) }

    fun reset() {
        settings = repository.reset()
    }

    private fun update(transform: AiSettings.() -> AiSettings) {
        settings = settings.transform()
        repository.save(settings)
    }
}
