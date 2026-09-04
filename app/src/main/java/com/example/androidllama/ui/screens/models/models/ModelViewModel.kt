package com.example.androidllama.ui.screens.models.models

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidllama.data.models.ModelDownloadService
import com.example.androidllama.data.settings.AiSettingsRepository
import com.example.androidllama.inference.LlamaRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ModelViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HuggingFaceModelRepository(application)
    private val settingsRepository = AiSettingsRepository(application)

    val models = ModelStore.models
    var isRefreshing by mutableStateOf(false)
        private set
    var isSearching by mutableStateOf(false)
        private set
    var catalogError by mutableStateOf<String?>(null)
        private set
    var currentPage by mutableStateOf(1)
        private set
    var hasNextPage by mutableStateOf(false)
        private set
    val hasPreviousPage: Boolean
        get() = currentPage > 1

    private val pageUrls = mutableListOf<String?>(null)
    private var catalogJob: Job? = null
    private var catalogRequestVersion = 0
    private var searchJob: Job? = null
    private var searchQuery = ""

    init {
        loadPage(pageUrl = null, targetPage = 1)
    }

    fun refreshModels() {
        loadPage(
            pageUrls.getOrNull(currentPage - 1),
            currentPage,
            forceRefresh = true
        )
    }

    fun searchModels(query: String) {
        searchJob?.cancel()
        catalogJob?.cancel()
        catalogRequestVersion++
        isRefreshing = false
        isSearching = true
        searchJob = viewModelScope.launch {
            delay(400)
            val normalized = query.trim()
            if (normalized != searchQuery) {
                searchQuery = normalized
                loadPage(
                    pageUrl = null,
                    targetPage = 1,
                    resetHistory = true,
                    searchRequest = true
                )
            } else {
                isSearching = false
            }
        }
    }

    fun resetToFirstPageForFilters() {
        if (currentPage > 1) {
            loadPage(pageUrl = null, targetPage = 1, resetHistory = true)
        }
    }

    fun nextPage() {
        val nextUrl = nextPageUrl ?: return
        loadPage(nextUrl, currentPage + 1, rememberUrl = true)
    }

    fun previousPage() {
        if (!hasPreviousPage) return
        loadPage(pageUrls[currentPage - 2], currentPage - 1)
    }

    private var nextPageUrl: String? = null

    private fun loadPage(
        pageUrl: String?,
        targetPage: Int,
        rememberUrl: Boolean = false,
        resetHistory: Boolean = false,
        searchRequest: Boolean = false,
        forceRefresh: Boolean = false
    ) {
        catalogJob?.cancel()
        val requestVersion = ++catalogRequestVersion
        isRefreshing = true
        catalogError = null
        catalogJob = viewModelScope.launch {
            try {
                val page = repository.fetchPublicGgufModels(
                    pageUrl = pageUrl,
                    searchQuery = searchQuery,
                    forceRefresh = forceRefresh
                )
                if (requestVersion != catalogRequestVersion) return@launch
                ModelStore.replace(page.models)
                currentPage = targetPage
                nextPageUrl = page.nextPageUrl
                hasNextPage = page.nextPageUrl != null
                if (resetHistory) {
                    pageUrls.clear()
                    pageUrls += null
                } else if (rememberUrl) {
                    while (pageUrls.size >= targetPage) pageUrls.removeAt(pageUrls.lastIndex)
                    pageUrls += pageUrl
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (requestVersion == catalogRequestVersion) {
                    catalogError = error.message ?: "Could not load Hugging Face models"
                }
            } finally {
                if (requestVersion == catalogRequestVersion) {
                    isRefreshing = false
                    if (searchRequest) isSearching = false
                }
            }
        }
    }

    fun downloadModel(model: ModelInfo) {
        if (model.downloaded || model.downloading) return
        ModelStore.markDownloading(model.id)
        try {
            ModelDownloadService.start(getApplication(), model)
        } catch (error: Throwable) {
            ModelStore.markFailed(model.id, error.message ?: "Could not start download")
        }
    }

    fun cancelDownload(model: ModelInfo) {
        if (!model.downloading) return
        ModelDownloadService.cancel(getApplication(), model.id)
    }

    fun loadModel(model: ModelInfo) {
        if (model.loaded || LlamaRuntime.loadedModelId == model.id) {
            unloadModel(model)
            return
        }
        if (!model.downloaded || model.loading || model.downloading) return
        ModelStore.markLoading(model.id)
        viewModelScope.launch {
            try {
                val file = repository.requireStored(model)
                LlamaRuntime.load(model.id, file.absolutePath, settingsRepository.load())
                ModelStore.markLoaded(model.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                ModelStore.markFailed(model.id, error.message ?: "Could not load model")
            }
        }
    }

    fun deleteModel(model: ModelInfo) {
        if (!model.downloaded || model.downloading || model.loading ||
            model.loaded || LlamaRuntime.loadedModelId == model.id
        ) return
        viewModelScope.launch {
            try {
                repository.delete(model)
                ModelStore.markDeleted(model.id)
                repository.cacheCatalog(models)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                ModelStore.markFailed(model.id, error.message ?: "Could not delete model")
            }
        }
    }

    private fun unloadModel(model: ModelInfo) {
        if (model.loading) return
        ModelStore.markUnloading(model.id)
        viewModelScope.launch {
            try {
                LlamaRuntime.unload()
                ModelStore.markUnloaded(model.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                ModelStore.markUnloadFailed(model.id, error.message ?: "Could not unload model")
            }
        }
    }
}
