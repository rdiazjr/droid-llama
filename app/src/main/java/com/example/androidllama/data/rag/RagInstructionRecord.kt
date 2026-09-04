package com.example.androidllama.data.rag

data class RagInstructionRecord(
    val id: String,
    val name: String,
    val rawJson: String,
    val createdAt: Long
)
