package com.example.androidllama.ui.screens.models.models

data class ModelInfo(
    val id: String,
    val name: String,
    val size: String,
    val quantization: String,
    val tags: List<String> = emptyList(),
    val repositoryId: String,
    val fileName: String,
    val revision: String = "main",
    val sizeBytes: Long? = null,
    val parameterCount: Long,
    val downloaded: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Int? = null,
    val error: String? = null
) {
    val parameterBillions: Double
        get() = parameterCount / 1_000_000_000.0

    val parameterBucket: Int
        get() = parameterBillions.toInt().coerceIn(1, 7)
}
