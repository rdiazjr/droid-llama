package com.example.androidllama.data.web

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

object WebSearchPolicy {
    private val currentInformationTerms = Regex(
        """\b(latest|currently|current|today|tonight|yesterday|tomorrow|recent|news|weather|forecast|score|scores|price|stock|exchange rate|president|prime minister|ceo|version|release date)\b""",
        RegexOption.IGNORE_CASE
    )
    private val uncertaintyTerms = Regex(
        """(\[\[SEARCH_WEB]]|i (?:do not|don't) know|i(?:'m| am) not sure|cannot (?:verify|confirm|find)|can't (?:verify|confirm|find)|do not have (?:access|enough information)|don't have (?:access|enough information)|knowledge cutoff|no (?:reliable )?information|unable to (?:verify|confirm|determine)|as of my last)""",
        RegexOption.IGNORE_CASE
    )

    fun shouldSearchBeforeAnswer(query: String): Boolean =
        currentInformationTerms.containsMatchIn(query)

    fun shouldSearchAfterAnswer(answer: String): Boolean =
        answer.isBlank() || uncertaintyTerms.containsMatchIn(answer)
}

object WebSearchClient {
    private val resultPattern = Regex(
        """<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>.*?<a[^>]+class="result__snippet"[^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val tagPattern = Regex("<[^>]+>")

    fun search(query: String, limit: Int = 5): List<WebSearchResult> {
        val encodedQuery = URLEncoder.encode(query.take(300), StandardCharsets.UTF_8.name())
        val connection = URL("https://html.duckduckgo.com/html/?q=$encodedQuery")
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android) AppleWebKit/537.36 AndroidLlama/1.0"
            )
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Web search returned HTTP ${connection.responseCode}")
            }
            parseResults(connection.inputStream.bufferedReader().use { it.readText() }, limit)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseResults(html: String, limit: Int = 5): List<WebSearchResult> =
        resultPattern.findAll(html).mapNotNull { match ->
            val url = unwrapDuckDuckGoUrl(decodeHtml(match.groupValues[1]))
            val title = cleanHtml(match.groupValues[2])
            val snippet = cleanHtml(match.groupValues[3])
            if (url.startsWith("http") && title.isNotBlank()) {
                WebSearchResult(title = title, url = url, snippet = snippet)
            } else {
                null
            }
        }.distinctBy(WebSearchResult::url).take(limit).toList()

    fun formatForPrompt(results: List<WebSearchResult>): String = buildString {
        appendLine("LIVE WEB SEARCH RESULTS")
        results.forEachIndexed { index, result ->
            appendLine("RESULT [${index + 1}]")
            appendLine("Title: ${result.title}")
            appendLine("Source URL: ${result.url}")
            appendLine("Web evidence: ${result.snippet}")
            appendLine()
        }
        append(
            "Answer from the evidence above. Cite factual claims with [number] and " +
                "finish with a Sources section containing the source URLs used."
        )
    }

    private fun unwrapDuckDuckGoUrl(rawUrl: String): String {
        val absolute = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
        return runCatching {
            val uri = URI(absolute)
            val encodedTarget = uri.rawQuery
                ?.split('&')
                ?.firstOrNull { it.startsWith("uddg=") }
                ?.substringAfter('=')
            if (encodedTarget != null) {
                URLDecoder.decode(encodedTarget, StandardCharsets.UTF_8.name())
            } else {
                absolute
            }
        }.getOrDefault(absolute)
    }

    private fun cleanHtml(value: String): String =
        decodeHtml(tagPattern.replace(value, " ")).replace(Regex("\\s+"), " ").trim()

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
