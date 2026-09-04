package com.example.androidllama.rag

data class RagInstruction(
    val name: String,
    val version: Int,
    val systemPrompt: String,
    val noContextResponse: String,
    val chunkSize: Int,
    val chunkOverlap: Int,
    val topK: Int,
    val minimumScore: Double,
    val includeCitations: Boolean
)
