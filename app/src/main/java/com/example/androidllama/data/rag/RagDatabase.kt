package com.example.androidllama.data.rag

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RagDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE documents (
                id TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                local_path TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE instructions (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                raw_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getDocuments(): List<RagDocument> = readableDatabase.query(
        "documents",
        null,
        null,
        null,
        null,
        null,
        "created_at DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    RagDocument(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                        mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
                        localPath = cursor.getString(cursor.getColumnIndexOrThrow("local_path")),
                        sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                    )
                )
            }
        }
    }

    fun insertDocument(document: RagDocument) {
        writableDatabase.insertOrThrow("documents", null, document.toValues())
    }

    fun renameDocument(id: String, displayName: String, updatedAt: Long): Boolean {
        val values = ContentValues().apply {
            put("display_name", displayName)
            put("updated_at", updatedAt)
        }
        return writableDatabase.update("documents", values, "id = ?", arrayOf(id)) > 0
    }

    fun deleteDocument(id: String): Boolean =
        writableDatabase.delete("documents", "id = ?", arrayOf(id)) > 0

    fun getActiveInstruction(): RagInstructionRecord? = readableDatabase.query(
        "instructions",
        null,
        "is_active = 1",
        null,
        null,
        null,
        "created_at DESC",
        "1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        RagInstructionRecord(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            rawJson = cursor.getString(cursor.getColumnIndexOrThrow("raw_json")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    fun replaceActiveInstruction(instruction: RagInstructionRecord) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.execSQL("UPDATE instructions SET is_active = 0")
            val values = ContentValues().apply {
                put("id", instruction.id)
                put("name", instruction.name)
                put("raw_json", instruction.rawJson)
                put("created_at", instruction.createdAt)
                put("is_active", 1)
            }
            writableDatabase.insertOrThrow("instructions", null, values)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun deleteActiveInstruction() {
        writableDatabase.delete("instructions", "is_active = 1", null)
    }

    private fun RagDocument.toValues() = ContentValues().apply {
        put("id", id)
        put("display_name", displayName)
        put("mime_type", mimeType)
        put("local_path", localPath)
        put("size_bytes", sizeBytes)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private companion object {
        const val DATABASE_NAME = "rag.db"
        const val DATABASE_VERSION = 1
    }
}
