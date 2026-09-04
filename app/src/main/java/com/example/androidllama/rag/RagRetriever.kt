package com.example.androidllama.rag

data class RetrievedChunk(
    val documentName: String,
    val content: String,
    val score: Float
)

/** Retrieval implementation is added once document chunks and an embedding runtime exist. */
fun interface RagRetriever {
    suspend fun retrieve(query: String): List<RetrievedChunk>
}
