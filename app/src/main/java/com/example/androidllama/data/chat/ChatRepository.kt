package com.example.androidllama.data.chat

import android.content.Context

class ChatRepository(context: Context) {
    private val database = ChatDatabase(context.applicationContext)

    fun getConversations(): List<ChatSummary> = database.getConversations()

    fun getMessages(conversationId: String): List<StoredMessage> =
        database.getMessages(conversationId)

    fun deleteConversation(conversationId: String): Boolean =
        database.deleteConversation(conversationId)

    fun saveMessage(
        conversationId: String?,
        title: String,
        message: StoredMessage
    ): String = database.saveMessage(conversationId, title, message)
}
