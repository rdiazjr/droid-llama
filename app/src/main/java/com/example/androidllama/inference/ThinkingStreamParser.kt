package com.example.androidllama.inference

data class ParsedGeneration(
    val thinking: String,
    val response: String
)

object ThinkingStreamParser {
    private const val OPEN = "<think>"
    private const val CLOSE = "</think>"

    fun parse(raw: String): ParsedGeneration {
        val start = raw.indexOf(OPEN, ignoreCase = true)
        if (start >= 0) {
            val thinkingStart = start + OPEN.length
            val end = raw.indexOf(CLOSE, startIndex = thinkingStart, ignoreCase = true)
            return if (end >= 0) {
                ParsedGeneration(
                    thinking = raw.substring(thinkingStart, end).trim(),
                    response = raw.substring(end + CLOSE.length).trimStart()
                )
            } else {
                ParsedGeneration(
                    thinking = raw.substring(thinkingStart).trimStart(),
                    response = ""
                )
            }
        }

        val closeOnly = raw.indexOf(CLOSE, ignoreCase = true)
        if (closeOnly >= 0) {
            return ParsedGeneration(
                thinking = raw.substring(0, closeOnly).trim(),
                response = raw.substring(closeOnly + CLOSE.length).trimStart()
            )
        }

        val firstContent = raw.indexOfFirst { !it.isWhitespace() }
        if (firstContent >= 0 && OPEN.startsWith(raw.substring(firstContent), ignoreCase = true)) {
            return ParsedGeneration(thinking = "", response = "")
        }
        return ParsedGeneration(thinking = "", response = raw)
    }
}
