package com.example.androidllama

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.androidllama.inference.InferenceMessage
import com.example.androidllama.inference.LlamaRuntime
import com.example.androidllama.data.models.ModelDownloadService
import com.example.androidllama.ui.screens.models.models.HuggingFaceModelRepository
import com.example.androidllama.ui.screens.models.models.ModelInfo
import com.example.androidllama.ui.screens.models.models.ModelStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LlamaRuntimeInstrumentedTest {
    @After
    fun unloadModel() = runBlocking {
        if (LlamaRuntime.loadedModelId != null) LlamaRuntime.unload()
    }

    @Test
    fun loadsModelAndStreamsTokens() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.filesDir, "smoke/stories260K.gguf")
        assumeTrue("Push the optional smoke-test GGUF before running this test", model.isFile)

        val modelInfo = smokeModel().copy(downloaded = true)
        val description = LlamaRuntime.load(modelInfo.id, model.absolutePath)
        assertTrue(description.isNotBlank())

        ModelStore.replace(listOf(modelInfo))
        assertTrue(ModelStore.models.single().loaded)
        ModelStore.replace(listOf(modelInfo.copy(loaded = false)))
        assertTrue("Catalog refresh must preserve the native loaded model", ModelStore.models.single().loaded)

        val output = StringBuilder()
        withTimeout(30_000) {
            LlamaRuntime.generate(
                listOf(InferenceMessage(role = "user", content = "Once upon a time"))
            ).take(16).collect(output::append)
        }

        assertTrue("Expected at least one generated token", output.isNotEmpty())
    }

    @Test
    fun foregroundServiceDownloadsWithoutViewModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = smokeModel()
        val repository = HuggingFaceModelRepository(context)
        runCatching { repository.delete(model) }
        ModelStore.replace(listOf(model))

        try {
            ModelDownloadService.start(context, model)
            withTimeout(60_000) {
                while (true) {
                    val current = ModelStore.models.single()
                    if (current.downloaded || current.error != null) break
                    delay(100)
                }
            }
            val result = ModelStore.models.single()
            assertTrue(result.error ?: "Foreground download did not complete", result.downloaded)
        } finally {
            runCatching { repository.delete(model) }
            repository.cacheCatalog(emptyList())
        }
    }

    private fun smokeModel() = ModelInfo(
        id = "ggml-org/test-model-stories260K::stories260K-f32.gguf",
        name = "stories260K",
        size = "1.2 MB",
        quantization = "F32",
        repositoryId = "ggml-org/test-model-stories260K",
        fileName = "stories260K-f32.gguf",
        sizeBytes = 1_185_376L,
        parameterCount = 260_000L
    )
}
