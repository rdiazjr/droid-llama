package com.example.androidllama.rag

class TextChunker {
    fun chunk(text: String, chunkSize: Int, overlap: Int): List<String> {
        require(chunkSize > 0)
        require(overlap in 0 until chunkSize)
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + chunkSize, text.length)
            result += text.substring(start, end).trim()
            if (end == text.length) break
            start = end - overlap
        }
        return result.filter(String::isNotBlank)
    }
}
