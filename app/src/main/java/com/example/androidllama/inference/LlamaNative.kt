package com.example.androidllama.inference

internal fun interface TokenCallback {
    fun onToken(token: String): Boolean
}

internal object LlamaNative {
    init {
        System.loadLibrary("android_llama")
    }

    external fun loadModel(
        path: String,
        backend: String,
        gpuLayers: Int,
        useMemoryMapping: Boolean
    ): String
    external fun loadedModelProfile(): Array<String>
    external fun unloadModel()
    external fun cancel()
    external fun supportedBackends(): Array<String>
    external fun backendDevices(): Array<String>
    external fun generate(
        roles: Array<String>,
        contents: Array<String>,
        threads: Int,
        contextWindow: Int,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        seed: Int,
        reasoningMode: String,
        reasoningBudget: Int,
        callback: TokenCallback
    )
}
