package com.example.androidllama.ui.screens.rag

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidllama.data.rag.RagDocument
import com.example.androidllama.data.rag.RagInstructionRecord
import com.example.androidllama.data.rag.RagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RagUiState(
    val documents: List<RagDocument> = emptyList(),
    val activeInstruction: RagInstructionRecord? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class RagViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RagRepository(application)

    var uiState by mutableStateOf(RagUiState())
        private set

    init {
        refresh()
    }

    fun importDocuments(uris: List<Uri>) = runOperation("Files imported.") {
        val errors = repository.importDocuments(uris)
        if (errors.isNotEmpty()) {
            throw IllegalStateException(errors.joinToString("\n"))
        }
    }

    fun renameDocument(document: RagDocument, newName: String) = runOperation("File renamed.") {
        repository.renameDocument(document.id, newName)
    }

    fun deleteDocument(document: RagDocument) = runOperation("File deleted.") {
        repository.deleteDocument(document)
    }

    fun importInstruction(uri: Uri) = runOperation("RAG instructions imported.") {
        repository.importInstruction(uri)
    }

    fun deleteInstruction() = runOperation("RAG instructions removed.") {
        repository.deleteActiveInstruction()
    }

    fun clearMessage() {
        uiState = uiState.copy(message = null)
    }

    private fun refresh() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                repository.getDocuments() to repository.getActiveInstruction()
            }
            uiState = uiState.copy(
                documents = data.first,
                activeInstruction = data.second,
                isWorking = false
            )
        }
    }

    private fun runOperation(successMessage: String, operation: () -> Unit) {
        if (uiState.isWorking) return
        uiState = uiState.copy(isWorking = true, message = null)
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching(operation).exceptionOrNull()
            }
            val data = withContext(Dispatchers.IO) {
                repository.getDocuments() to repository.getActiveInstruction()
            }
            uiState = uiState.copy(
                documents = data.first,
                activeInstruction = data.second,
                isWorking = false,
                message = error?.message ?: successMessage,
                isError = error != null
            )
        }
    }
}
