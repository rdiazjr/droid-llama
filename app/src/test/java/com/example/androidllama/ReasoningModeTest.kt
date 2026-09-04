package com.example.androidllama

import com.example.androidllama.data.settings.AiSettings
import com.example.androidllama.data.settings.ReasoningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningModeTest {
    @Test
    fun directAnswersAreTheDefault() {
        val settings = AiSettings()

        assertEquals(ReasoningMode.OFF, settings.reasoningMode)
        assertEquals(512, settings.maxTokens)
    }

    @Test
    fun briefReasoningLeavesRoomForAnAnswer() {
        assertEquals(128, ReasoningMode.BRIEF.tokenBudget)
        assertTrue(ReasoningMode.FULL.tokenBudget < 0)
    }
}
