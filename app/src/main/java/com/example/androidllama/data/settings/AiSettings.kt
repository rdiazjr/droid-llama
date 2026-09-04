package com.example.androidllama.data.settings

enum class RuntimeBackend(val label: String, val description: String) {
    CPU("CPU", "Most compatible"),
    VULKAN("Vulkan", "GPU acceleration"),
    OPENCL("OpenCL", "Supported GPU devices")
}

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class PersonalityPreset(val label: String, val prompt: String?) {
    BALANCED(
        "Balanced",
        "Be helpful, accurate, and clear. Match the user's level of technical detail and acknowledge uncertainty."
    ),
    CONCISE(
        "Concise",
        "Answer directly and briefly. Prioritize the essential result and avoid unnecessary explanation."
    ),
    FRIENDLY(
        "Friendly",
        "Answer warmly and conversationally while remaining accurate and practical."
    ),
    PROFESSIONAL(
        "Professional",
        "Use a polished, objective tone. Organize complex answers clearly and avoid casual language."
    ),
    CREATIVE(
        "Creative",
        "Be imaginative and expressive. Offer original alternatives while clearly separating facts from ideas."
    ),
    CUSTOM("Custom", null)
}

enum class ReasoningMode(
    val label: String,
    val description: String,
    val tokenBudget: Int
) {
    OFF("Off", "Answer directly without a hidden reasoning pass.", 1),
    BRIEF("Brief", "Allow a short reasoning pass of up to 128 tokens.", 128),
    FULL("Full", "Allow the model to reason without a separate limit.", -1)
}

data class AiSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val backend: RuntimeBackend = RuntimeBackend.CPU,
    val cpuThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 16),
    val gpuLayers: Int = 0,
    val temperature: Float = 0.8f,
    val maxTokens: Int = 512,
    val contextWindow: Int = 4096,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.0f,
    val seed: Int = -1,
    val useModelDefaults: Boolean = true,
    val reasoningMode: ReasoningMode = ReasoningMode.OFF,
    val personality: PersonalityPreset = PersonalityPreset.BALANCED,
    val systemPrompt: String = PersonalityPreset.BALANCED.prompt.orEmpty(),
    val streamResponses: Boolean = true,
    val useMemoryMapping: Boolean = true,
    val rememberConversation: Boolean = true,
    val includeRagCitations: Boolean = true
)
