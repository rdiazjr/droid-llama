package com.example.androidllama.data.settings

import android.content.Context

class AiSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): AiSettings {
        val defaults = AiSettings()
        return AiSettings(
            themeMode = enumValueOrDefault(
                preferences.getString(KEY_THEME_MODE, null),
                defaults.themeMode
            ),
            backend = enumValueOrDefault(preferences.getString(KEY_BACKEND, null), defaults.backend),
            cpuThreads = preferences.getInt(KEY_CPU_THREADS, defaults.cpuThreads),
            gpuLayers = preferences.getInt(KEY_GPU_LAYERS, defaults.gpuLayers),
            temperature = preferences.getFloat(KEY_TEMPERATURE, defaults.temperature),
            maxTokens = preferences.getInt(KEY_MAX_TOKENS, defaults.maxTokens),
            contextWindow = preferences.getInt(KEY_CONTEXT_WINDOW, defaults.contextWindow),
            topP = preferences.getFloat(KEY_TOP_P, defaults.topP),
            topK = preferences.getInt(KEY_TOP_K, defaults.topK),
            minP = preferences.getFloat(KEY_MIN_P, defaults.minP),
            repeatPenalty = preferences.getFloat(KEY_REPEAT_PENALTY, defaults.repeatPenalty),
            seed = preferences.getInt(KEY_SEED, defaults.seed),
            useModelDefaults = preferences.getBoolean(
                KEY_USE_MODEL_DEFAULTS,
                defaults.useModelDefaults
            ),
            reasoningMode = enumValueOrDefault(
                preferences.getString(KEY_REASONING_MODE, null),
                defaults.reasoningMode
            ),
            personality = enumValueOrDefault(
                preferences.getString(KEY_PERSONALITY, null),
                defaults.personality
            ),
            systemPrompt = preferences.getString(KEY_SYSTEM_PROMPT, defaults.systemPrompt)
                ?: defaults.systemPrompt,
            streamResponses = preferences.getBoolean(KEY_STREAM, defaults.streamResponses),
            useMemoryMapping = preferences.getBoolean(KEY_MMAP, defaults.useMemoryMapping),
            rememberConversation = preferences.getBoolean(KEY_REMEMBER, defaults.rememberConversation),
            includeRagCitations = preferences.getBoolean(KEY_CITATIONS, defaults.includeRagCitations)
        )
    }

    fun save(settings: AiSettings) {
        preferences.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_BACKEND, settings.backend.name)
            .putInt(KEY_CPU_THREADS, settings.cpuThreads)
            .putInt(KEY_GPU_LAYERS, settings.gpuLayers)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putInt(KEY_MAX_TOKENS, settings.maxTokens)
            .putInt(KEY_CONTEXT_WINDOW, settings.contextWindow)
            .putFloat(KEY_TOP_P, settings.topP)
            .putInt(KEY_TOP_K, settings.topK)
            .putFloat(KEY_MIN_P, settings.minP)
            .putFloat(KEY_REPEAT_PENALTY, settings.repeatPenalty)
            .putInt(KEY_SEED, settings.seed)
            .putBoolean(KEY_USE_MODEL_DEFAULTS, settings.useModelDefaults)
            .putString(KEY_REASONING_MODE, settings.reasoningMode.name)
            .putString(KEY_PERSONALITY, settings.personality.name)
            .putString(KEY_SYSTEM_PROMPT, settings.systemPrompt)
            .putBoolean(KEY_STREAM, settings.streamResponses)
            .putBoolean(KEY_MMAP, settings.useMemoryMapping)
            .putBoolean(KEY_REMEMBER, settings.rememberConversation)
            .putBoolean(KEY_CITATIONS, settings.includeRagCitations)
            .apply()
    }

    fun reset(): AiSettings = AiSettings().also(::save)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val PREFERENCES_NAME = "ai_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_BACKEND = "backend"
        const val KEY_CPU_THREADS = "cpu_threads"
        const val KEY_GPU_LAYERS = "gpu_layers"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_CONTEXT_WINDOW = "context_window"
        const val KEY_TOP_P = "top_p"
        const val KEY_TOP_K = "top_k"
        const val KEY_MIN_P = "min_p"
        const val KEY_REPEAT_PENALTY = "repeat_penalty"
        const val KEY_SEED = "seed"
        const val KEY_USE_MODEL_DEFAULTS = "use_model_defaults"
        const val KEY_REASONING_MODE = "reasoning_mode"
        const val KEY_PERSONALITY = "personality"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_STREAM = "stream_responses"
        const val KEY_MMAP = "memory_mapping"
        const val KEY_REMEMBER = "remember_conversation"
        const val KEY_CITATIONS = "rag_citations"
    }
}
