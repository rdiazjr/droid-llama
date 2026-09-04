package com.example.androidllama.rag

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.androidllama.data.rag.RagDocument
import java.io.File
import java.util.UUID

class DocumentImporter(private val context: Context) {
    private val textExtractor = DocumentTextExtractor(context)

    fun import(uri: Uri): RagDocument {
        val resolver = context.contentResolver
        val metadata = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            name to size
        }

        val id = UUID.randomUUID().toString()
        val displayName = metadata?.first?.takeIf { it.isNotBlank() } ?: "document-$id"
        val documentDirectory = File(context.filesDir, "rag/$id")
        check(documentDirectory.mkdirs() || documentDirectory.isDirectory) {
            "Could not create private storage for $displayName."
        }

        val target = File(documentDirectory, "original")
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open $displayName." }
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val extractedText = textExtractor.extract(target, mimeType, displayName)
            require(extractedText.isNotBlank()) { "$displayName does not contain readable text." }
            File(documentDirectory, EXTRACTED_TEXT_FILE).writeText(extractedText)
        } catch (error: Exception) {
            documentDirectory.deleteRecursively()
            throw error
        }

        val now = System.currentTimeMillis()
        return RagDocument(
            id = id,
            displayName = displayName,
            mimeType = resolver.getType(uri) ?: "application/octet-stream",
            localPath = target.absolutePath,
            sizeBytes = metadata?.second ?: target.length(),
            createdAt = now,
            updatedAt = now
        )
    }

    companion object {
        const val EXTRACTED_TEXT_FILE = "content.txt"
    }
}
