package com.example.androidllama.data.rag

data class RagDocument(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long
)
