package com.example.androidllama.rag

/** Bridge this interface to the on-device embedding model used by the app. */
fun interface EmbeddingEngine {
    suspend fun embed(text: String): FloatArray
}
