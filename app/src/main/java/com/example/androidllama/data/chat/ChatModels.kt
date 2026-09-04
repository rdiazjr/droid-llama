package com.example.androidllama.data.chat

data class ChatSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int
)

data class StoredAttachment(
    val uri: String,
    val displayName: String
)

data class StoredMessage(
    val text: String,
    val role: String,
    val attachments: List<StoredAttachment>,
    val modelName: String?,
    val webBrowsingEnabled: Boolean,
    val createdAt: Long,
    val tokensPerSecond: Float?,
    val generationDurationMs: Long?
)
