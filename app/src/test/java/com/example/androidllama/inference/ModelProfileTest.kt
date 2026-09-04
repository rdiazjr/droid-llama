package com.example.androidllama.inference

import com.example.androidllama.data.settings.AiSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelProfileTest {
    private val profile = ModelProfile(
        hasChatTemplate = true,
        temperature = 0.6f,
        topP = 0.85f,
        topK = 20,
        minP = 0.1f,
        repeatPenalty = 1.05f
    )

    @Test
    fun appliesEmbeddedSamplingDefaultsWhenEnabled() {
        val resolved = profile.applyTo(AiSettings(useModelDefaults = true))

        assertEquals(0.6f, resolved.temperature)
        assertEquals(0.85f, resolved.topP)
        assertEquals(20, resolved.topK)
        assertEquals(0.1f, resolved.minP)
        assertEquals(1.05f, resolved.repeatPenalty)
    }

    @Test
    fun keepsUserSamplingSettingsWhenDisabled() {
        val settings = AiSettings(
            temperature = 1.2f,
            topP = 0.7f,
            topK = 60,
            minP = 0.2f,
            repeatPenalty = 1.2f,
            useModelDefaults = false
        )

        assertEquals(settings, profile.applyTo(settings))
    }
}
