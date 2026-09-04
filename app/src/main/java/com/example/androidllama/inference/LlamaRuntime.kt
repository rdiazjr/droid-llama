package com.example.androidllama.inference

import com.example.androidllama.data.settings.AiSettings
import com.example.androidllama.data.settings.RuntimeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class InferenceMessage(
    val role: String,
    val content: String
)

data class BackendDevice(
    val backend: RuntimeBackend,
    val name: String,
    val description: String,
    val totalMemoryBytes: Long?
)

data class ModelProfile(
    val hasChatTemplate: Boolean,
    val temperature: Float?,
    val topP: Float?,
    val topK: Int?,
    val minP: Float?,
    val repeatPenalty: Float?
) {
    fun applyTo(settings: AiSettings): AiSettings {
        if (!settings.useModelDefaults) return settings
        return settings.copy(
            temperature = temperature ?: settings.temperature,
            topP = topP ?: settings.topP,
            topK = topK ?: settings.topK,
            minP = minP ?: settings.minP,
            repeatPenalty = repeatPenalty ?: settings.repeatPenalty
        )
    }
}

object LlamaRuntime {
    private val operationMutex = Mutex()

    @Volatile
    var loadedModelId: String? = null
        private set

    @Volatile
    var loadedModelProfile: ModelProfile? = null
        private set

    suspend fun load(modelId: String, path: String, settings: AiSettings = AiSettings()): String {
        LlamaNative.cancel()
        return operationMutex.withLock {
            withContext(Dispatchers.IO) {
                loadedModelId = null
                loadedModelProfile = null
                val selectedBackend = settings.backend.takeIf { it in supportedBackends() }
                    ?: RuntimeBackend.CPU
                val description = LlamaNative.loadModel(
                    path = path,
                    backend = selectedBackend.name,
                    gpuLayers = if (selectedBackend == RuntimeBackend.CPU) 0 else settings.gpuLayers,
                    useMemoryMapping = settings.useMemoryMapping
                )
                loadedModelProfile = LlamaNative.loadedModelProfile().toModelProfile()
                loadedModelId = modelId
                description
            }
        }
    }

    suspend fun unload() {
        LlamaNative.cancel()
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                LlamaNative.unloadModel()
                loadedModelId = null
                loadedModelProfile = null
            }
        }
    }

    fun cancel() {
        LlamaNative.cancel()
    }

    fun supportedBackends(): Set<RuntimeBackend> = runCatching {
        LlamaNative.supportedBackends()
            .mapNotNull { name ->
                RuntimeBackend.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
            .toSet()
            .plus(RuntimeBackend.CPU)
    }.getOrDefault(setOf(RuntimeBackend.CPU))

    fun backendDevices(): List<BackendDevice> = runCatching {
        LlamaNative.backendDevices().mapNotNull { record ->
            val fields = record.split('\t', limit = 4)
            val backend = RuntimeBackend.entries.firstOrNull {
                it.name.equals(fields.getOrNull(0), ignoreCase = true)
            } ?: return@mapNotNull null
            BackendDevice(
                backend = backend,
                name = fields.getOrNull(1).orEmpty().ifBlank { backend.label },
                description = fields.getOrNull(2).orEmpty(),
                totalMemoryBytes = fields.getOrNull(3)?.toLongOrNull()?.takeIf { it > 0L }
            )
        }
    }.getOrElse {
        listOf(BackendDevice(RuntimeBackend.CPU, "CPU", "Android CPU", null))
    }

    fun generate(
        messages: List<InferenceMessage>,
        settings: AiSettings = AiSettings()
    ): Flow<String> = callbackFlow {
        require(messages.isNotEmpty()) { "At least one chat message is required" }
        val worker = launch(Dispatchers.Default) {
            try {
                operationMutex.withLock {
                    check(loadedModelId != null) { "No model is loaded" }
                    val effectiveSettings = loadedModelProfile?.applyTo(settings) ?: settings
                    LlamaNative.generate(
                        roles = messages.map { it.role }.toTypedArray(),
                        contents = messages.map { it.content }.toTypedArray(),
                        threads = effectiveSettings.cpuThreads,
                        contextWindow = effectiveSettings.contextWindow,
                        maxTokens = effectiveSettings.maxTokens,
                        temperature = effectiveSettings.temperature,
                        topP = effectiveSettings.topP,
                        topK = effectiveSettings.topK,
                        minP = effectiveSettings.minP,
                        repeatPenalty = effectiveSettings.repeatPenalty,
                        seed = effectiveSettings.seed,
                        reasoningMode = effectiveSettings.reasoningMode.name,
                        reasoningBudget = effectiveSettings.reasoningMode.tokenBudget,
                        callback = TokenCallback { token -> trySend(token).isSuccess }
                    )
                }
                close()
            } catch (error: Throwable) {
                close(error)
            }
        }
        awaitClose {
            LlamaNative.cancel()
            worker.cancel()
        }
    }

    private fun Array<String>.toModelProfile(): ModelProfile {
        fun value(index: Int): String? = getOrNull(index)?.takeIf(String::isNotBlank)
        return ModelProfile(
            hasChatTemplate = value(0) == "1",
            temperature = value(1)?.toFloatOrNull(),
            topP = value(2)?.toFloatOrNull(),
            topK = value(3)?.toIntOrNull(),
            minP = value(4)?.toFloatOrNull(),
            repeatPenalty = value(5)?.toFloatOrNull()
        )
    }
}
