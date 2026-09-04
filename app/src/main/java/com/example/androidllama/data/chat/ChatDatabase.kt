package com.example.androidllama.data.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class ChatDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY NOT NULL,
                conversation_id TEXT NOT NULL,
                text TEXT NOT NULL,
                role TEXT NOT NULL,
                model_name TEXT,
                web_browsing INTEGER NOT NULL DEFAULT 0,
                tokens_per_second REAL,
                generation_duration_ms INTEGER,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE attachments (
                id TEXT PRIMARY KEY NOT NULL,
                message_id TEXT NOT NULL,
                uri TEXT NOT NULL,
                display_name TEXT NOT NULL,
                FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX messages_conversation_index ON messages(conversation_id, created_at)")
        db.execSQL("CREATE INDEX attachments_message_index ON attachments(message_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN web_browsing INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE messages ADD COLUMN tokens_per_second REAL")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE messages ADD COLUMN generation_duration_ms INTEGER")
        }
    }

    fun saveMessage(
        conversationId: String?,
        title: String,
        message: StoredMessage
    ): String {
        val db = writableDatabase
        val resolvedConversationId = conversationId ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()

        db.beginTransaction()
        try {
            if (conversationId == null) {
                db.insertOrThrow(
                    "conversations",
                    null,
                    ContentValues().apply {
                        put("id", resolvedConversationId)
                        put("title", title)
                        put("created_at", now)
                        put("updated_at", now)
                    }
                )
            }
            db.insertOrThrow(
                "messages",
                null,
                ContentValues().apply {
                    put("id", messageId)
                    put("conversation_id", resolvedConversationId)
                    put("text", message.text)
                    put("role", message.role)
                    put("model_name", message.modelName)
                    put("web_browsing", if (message.webBrowsingEnabled) 1 else 0)
                    message.tokensPerSecond?.let { put("tokens_per_second", it) }
                    message.generationDurationMs?.let { put("generation_duration_ms", it) }
                    put("created_at", message.createdAt)
                }
            )
            message.attachments.forEach { attachment ->
                db.insertOrThrow(
                    "attachments",
                    null,
                    ContentValues().apply {
                        put("id", UUID.randomUUID().toString())
                        put("message_id", messageId)
                        put("uri", attachment.uri)
                        put("display_name", attachment.displayName)
                    }
                )
            }
            db.update(
                "conversations",
                ContentValues().apply { put("updated_at", now) },
                "id = ?",
                arrayOf(resolvedConversationId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return resolvedConversationId
    }

    fun getConversations(): List<ChatSummary> = readableDatabase.rawQuery(
        """
        SELECT c.id, c.title, c.updated_at, COUNT(m.id) AS message_count
        FROM conversations c
        INNER JOIN messages m ON m.conversation_id = c.id
        GROUP BY c.id, c.title, c.updated_at
        ORDER BY c.updated_at DESC
        """.trimIndent(),
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ChatSummary(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        messageCount = cursor.getInt(cursor.getColumnIndexOrThrow("message_count"))
                    )
                )
            }
        }
    }

    fun deleteConversation(conversationId: String): Boolean =
        writableDatabase.delete("conversations", "id = ?", arrayOf(conversationId)) > 0

    fun getMessages(conversationId: String): List<StoredMessage> = readableDatabase.query(
        "messages",
        arrayOf(
            "id",
            "text",
            "role",
            "model_name",
            "web_browsing",
            "created_at",
            "tokens_per_second",
            "generation_duration_ms"
        ),
        "conversation_id = ?",
        arrayOf(conversationId),
        null,
        null,
        "created_at ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val messageId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                add(
                    StoredMessage(
                        text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                        role = cursor.getString(cursor.getColumnIndexOrThrow("role")),
                        modelName = cursor.getString(cursor.getColumnIndexOrThrow("model_name")),
                        webBrowsingEnabled = cursor.getInt(
                            cursor.getColumnIndexOrThrow("web_browsing")
                        ) == 1,
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        tokensPerSecond = cursor.getColumnIndexOrThrow("tokens_per_second").let { index ->
                            if (cursor.isNull(index)) null else cursor.getFloat(index)
                        },
                        generationDurationMs = cursor.getColumnIndexOrThrow(
                            "generation_duration_ms"
                        ).let { index ->
                            if (cursor.isNull(index)) null else cursor.getLong(index)
                        },
                        attachments = getAttachments(messageId)
                    )
                )
            }
        }
    }

    private fun getAttachments(messageId: String): List<StoredAttachment> = readableDatabase.query(
        "attachments",
        arrayOf("uri", "display_name"),
        "message_id = ?",
        arrayOf(messageId),
        null,
        null,
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    StoredAttachment(
                        uri = cursor.getString(cursor.getColumnIndexOrThrow("uri")),
                        displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name"))
                    )
                )
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "chats.db"
        const val DATABASE_VERSION = 4
    }
}
