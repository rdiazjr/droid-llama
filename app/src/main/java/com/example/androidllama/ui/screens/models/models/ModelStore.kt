package com.example.androidllama.ui.screens.models.models

import androidx.compose.runtime.mutableStateListOf
import com.example.androidllama.inference.LlamaRuntime

object ModelStore {
    val models = mutableStateListOf<ModelInfo>()

    fun mergeDownloaded(cachedModels: List<ModelInfo>) {
        cachedModels.filter { it.downloaded }.forEach { cached ->
            val index = models.indexOfFirst { it.id == cached.id }
            if (index < 0) {
                models += cached.copy(loaded = cached.id == LlamaRuntime.loadedModelId)
            } else {
                val current = models[index]
                models[index] = current.copy(
                    downloaded = true,
                    loaded = current.id == LlamaRuntime.loadedModelId
                )
            }
        }
    }

    fun replace(models: List<ModelInfo>) {
        val existing = this.models.associateBy { it.id }
        val incomingIds = models.mapTo(mutableSetOf()) { it.id }
        val retainedLocalModels = existing.values.filter { model ->
            model.id !in incomingIds &&
                (model.downloaded || model.downloading || model.loaded || model.loading)
        }
        this.models.clear()
        this.models.addAll(retainedLocalModels)
        this.models.addAll(models.map { incoming ->
            val previous = existing[incoming.id]
            incoming.copy(
                loaded = incoming.id == LlamaRuntime.loadedModelId,
                loading = previous?.loading == true,
                downloading = previous?.downloading == true,
                downloadProgress = previous?.downloadProgress,
                error = previous?.error
            )
        })
    }

    fun markDownloading(modelId: String) = update(modelId) {
        it.copy(downloading = true, downloadProgress = null, error = null)
    }

    fun updateProgress(modelId: String, progress: Int?) = update(modelId) {
        it.copy(downloadProgress = progress)
    }

    fun markDownloaded(modelId: String) = update(modelId) {
        it.copy(downloaded = true, downloading = false, downloadProgress = 100, error = null)
    }

    fun markDownloadCancelled(modelId: String) = update(modelId) {
        it.copy(downloading = false, downloadProgress = null, error = null)
    }

    fun markLoaded(modelId: String) {
        models.indices.forEach { index ->
            val model = models[index]
            if (model.loaded || model.loading || model.id == modelId) {
                models[index] = model.copy(
                    loaded = model.id == modelId,
                    loading = false,
                    error = null
                )
            }
        }
    }

    fun markLoading(modelId: String) {
        models.indices.forEach { index ->
            val model = models[index]
            if (model.loaded || model.loading || model.id == modelId) {
                models[index] = model.copy(
                    loaded = false,
                    loading = model.id == modelId,
                    error = null
                )
            }
        }
    }

    fun markUnloading(modelId: String) = update(modelId) {
        it.copy(loaded = true, loading = true, error = null)
    }

    fun markUnloaded(modelId: String) = update(modelId) {
        it.copy(loaded = false, loading = false, error = null)
    }

    fun markUnloadFailed(modelId: String, message: String) = update(modelId) {
        it.copy(loaded = true, loading = false, error = message)
    }

    fun markDeleted(modelId: String) = update(modelId) {
        it.copy(
            downloaded = false,
            loaded = false,
            loading = false,
            downloading = false,
            downloadProgress = null,
            error = null
        )
    }

    fun markFailed(modelId: String, message: String) = update(modelId) {
        it.copy(
            loaded = false,
            loading = false,
            downloading = false,
            downloadProgress = null,
            error = message
        )
    }

    private fun update(modelId: String, transform: (ModelInfo) -> ModelInfo) {
        val index = models.indexOfFirst { it.id == modelId }
        if (index >= 0) models[index] = transform(models[index])
    }
}
