package com.example.androidllama

import com.example.androidllama.data.web.WebSearchClient
import com.example.androidllama.data.web.WebSearchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchPolicyTest {
    @Test
    fun searchesImmediatelyForCurrentInformation() {
        assertTrue(WebSearchPolicy.shouldSearchBeforeAnswer("What is the latest Android version?"))
        assertFalse(WebSearchPolicy.shouldSearchBeforeAnswer("Explain dependency injection"))
    }

    @Test
    fun retriesWhenModelCannotAnswer() {
        assertTrue(WebSearchPolicy.shouldSearchAfterAnswer("I don't have access to current data."))
        assertTrue(WebSearchPolicy.shouldSearchAfterAnswer("[[SEARCH_WEB]]"))
        assertFalse(WebSearchPolicy.shouldSearchAfterAnswer("Dependency injection supplies dependencies from outside."))
    }

    @Test
    fun parsesAndUnwrapsSearchResults() {
        val html = """
            <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fnews">Example &amp; News</a>
            <a class="result__snippet">A useful <b>result</b>.</a>
        """.trimIndent()

        val result = WebSearchClient.parseResults(html, limit = 1).single()

        assertEquals("Example & News", result.title)
        assertEquals("https://example.com/news", result.url)
        assertEquals("A useful result .", result.snippet)
    }
}
