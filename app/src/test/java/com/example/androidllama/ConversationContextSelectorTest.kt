package com.example.androidllama

import com.example.androidllama.inference.ConversationContextSelector
import com.example.androidllama.inference.InferenceMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationContextSelectorTest {
    @Test
    fun unrelatedQuestionExcludesPastTurn() {
        val result = ConversationContextSelector.select(
            listOf(
                message("user", "How do I bake sourdough bread?"),
                message("assistant", "Start with an active starter."),
                message("user", "Explain Kotlin coroutines.")
            )
        )

        assertEquals(listOf("Explain Kotlin coroutines."), result.map { it.content })
    }

    @Test
    fun relatedQuestionIncludesMatchingTurnAndAnswer() {
        val result = ConversationContextSelector.select(
            listOf(
                message("user", "How do Kotlin coroutines work?"),
                message("assistant", "They support structured concurrency."),
                message("user", "Which coroutine scope should Android use?")
            )
        )

        assertEquals(3, result.size)
    }

    @Test
    fun shortFollowUpIncludesImmediatelyPreviousExchange() {
        val result = ConversationContextSelector.select(
            listOf(
                message("user", "Describe the Vulkan backend."),
                message("assistant", "It runs supported operations on a GPU."),
                message("user", "Can you explain that more?")
            )
        )

        assertEquals(3, result.size)
    }

    private fun message(role: String, content: String) = InferenceMessage(role, content)
}
