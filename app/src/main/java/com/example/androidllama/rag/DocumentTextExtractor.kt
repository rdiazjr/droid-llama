package com.example.androidllama.rag

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

class DocumentTextExtractor(context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun extract(file: File, mimeType: String, displayName: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val text = when {
            mimeType == "application/pdf" || extension == "pdf" -> extractPdf(file)
            mimeType.startsWith("text/") ||
                mimeType in TEXT_APPLICATION_MIME_TYPES || extension in TEXT_EXTENSIONS ->
                file.readText(Charsets.UTF_8)
            else -> throw IllegalArgumentException("$displayName is not a supported RAG document.")
        }
        return text.replace('\u0000', ' ').take(MAX_INDEXED_CHARACTERS).trim()
    }

    private fun extractPdf(file: File): String = PDDocument.load(file).use { document ->
        PDFTextStripper().getText(document)
    }

    private companion object {
        const val MAX_INDEXED_CHARACTERS = 2_000_000
        val TEXT_APPLICATION_MIME_TYPES = setOf(
            "application/json",
            "application/xml",
            "application/yaml",
            "application/x-yaml",
            "application/javascript"
        )
        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "csv", "tsv", "xml", "yaml", "yml",
            "log", "html", "htm", "css", "js", "ts", "kt", "java", "py", "c",
            "cpp", "h", "hpp", "sql", "sh"
        )
    }
}
