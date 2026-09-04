package com.example.androidllama

import com.example.androidllama.inference.ThinkingStreamParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingStreamParserTest {
    @Test
    fun separatesThinkingFromResponse() {
        val parsed = ThinkingStreamParser.parse("<think>reasoning</think>Final answer")
        assertEquals("reasoning", parsed.thinking)
        assertEquals("Final answer", parsed.response)
    }

    @Test
    fun keepsPartialOpeningTagOutOfResponse() {
        assertEquals("", ThinkingStreamParser.parse("<thi").response)
    }

    @Test
    fun passesThroughModelsWithoutThinkingTags() {
        assertEquals("Direct answer", ThinkingStreamParser.parse("Direct answer").response)
    }

    @Test
    fun removesStandaloneClosingMarker() {
        val parsed = ThinkingStreamParser.parse("</think>\nFinal answer")
        assertEquals("", parsed.thinking)
        assertEquals("Final answer", parsed.response)
    }
}
