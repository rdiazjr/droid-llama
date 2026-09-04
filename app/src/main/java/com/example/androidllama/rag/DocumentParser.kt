package com.example.androidllama.rag

import com.example.androidllama.data.rag.RagDocument

/** Implement format-specific extraction before building the embedding index. */
fun interface DocumentParser {
    suspend fun parse(document: RagDocument): String
}
