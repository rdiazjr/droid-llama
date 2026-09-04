package com.example.androidllama.ui.screens.chat.models

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidllama.data.chat.ChatRepository
import com.example.androidllama.data.chat.ChatSummary
import com.example.androidllama.data.chat.StoredMessage
import com.example.androidllama.data.chat.StoredAttachment
import com.example.androidllama.data.rag.RagRepository
import com.example.androidllama.data.settings.AiSettingsRepository
import com.example.androidllama.data.web.WebSearchClient
import com.example.androidllama.data.web.WebSearchPolicy
import com.example.androidllama.inference.InferenceMessage
import com.example.androidllama.inference.ConversationContextSelector
import com.example.androidllama.inference.LlamaRuntime
import com.example.androidllama.inference.ThinkingStreamParser
import com.example.androidllama.rag.DocumentTextExtractor
import com.example.androidllama.ui.screens.models.models.ModelInfo
import com.example.androidllama.ui.screens.models.models.ModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatAttachment(val uri: String, val displayName: String)

data class DocumentPreviewState(val displayName: String, val content: String)

data class ChatMessage(
    val text: String,
    val role: MessageRole,
    val thinking: String = "",
    val showThinking: Boolean = false,
    val activityLabel: String = "Thinking…",
    val activityDetails: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    val modelName: String? = null,
    val webBrowsingEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val tokensPerSecond: Float? = null,
    val generationDurationMs: Long? = null
)

enum class MessageRole { USER, ASSISTANT }

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)
    private val ragRepository = RagRepository(application)
    private val settingsRepository = AiSettingsRepository(application)
    private var sessionVersion = 0
    private var generationJob: Job? = null

    var messageText by mutableStateOf("")
        private set

    val messages = mutableStateListOf<ChatMessage>()
    val conversations = mutableStateListOf<ChatSummary>()

    var currentConversationId by mutableStateOf<String?>(null)
        private set

    var isGenerating by mutableStateOf(false)
        private set

    var generationError by mutableStateOf<String?>(null)
        private set

    var selectedModelId by mutableStateOf<String?>(null)
        private set

    var webSearchEnabled by mutableStateOf(false)
        private set

    var pendingAttachment by mutableStateOf<ChatAttachment?>(null)
        private set

    var documentPreview by mutableStateOf<DocumentPreviewState?>(null)
        private set

    val loadedModels: List<ModelInfo>
        get() = ModelStore.models.filter { it.loaded }

    val selectedModel: ModelInfo?
        get() = loadedModels.firstOrNull { it.id == selectedModelId }

    init {
        refreshConversations()
    }

    fun onMessageChange(text: String) {
        messageText = text
    }

    fun updateWebSearchEnabled(enabled: Boolean) {
        if (!isGenerating) webSearchEnabled = enabled
    }

    fun attachDocument(uri: Uri) {
        if (isGenerating) return
        generationError = null
        viewModelScope.launch {
            try {
                val attachment = withContext(Dispatchers.IO) {
                    createDocumentAttachment(uri)
                }
                pendingAttachment = attachment
                openDocumentPreview(attachment)
            } catch (error: Throwable) {
                generationError = error.message ?: "Could not open the document."
            }
        }
    }

    fun removeAttachment() {
        if (!isGenerating) pendingAttachment = null
    }

    fun openDocumentPreview(attachment: ChatAttachment) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { readDocument(attachment) }
                documentPreview = DocumentPreviewState(attachment.displayName, content)
            } catch (error: Throwable) {
                generationError = error.message ?: "Could not preview ${attachment.displayName}."
            }
        }
    }

    fun dismissDocumentPreview() {
        documentPreview = null
    }

    fun selectModel(model: ModelInfo) {
        if (model.loaded) selectedModelId = model.id
    }

    fun ensureModelSelection() {
        val loaded = loadedModels
        if (loaded.none { it.id == selectedModelId }) {
            selectedModelId = loaded.firstOrNull()?.id
        }
    }

    fun sendMessage() {
        val model = selectedModel
        val attachment = pendingAttachment
        if ((messageText.isBlank() && attachment == null) || model == null || isGenerating) return

        val userMessage = ChatMessage(
            text = messageText.trim(),
            role = MessageRole.USER,
            attachments = listOfNotNull(attachment),
            webBrowsingEnabled = webSearchEnabled
        )
        val targetConversationId = currentConversationId
        val targetSession = sessionVersion
        messageText = ""
        pendingAttachment = null
        documentPreview = null
        generationError = null
        isGenerating = true

        generationJob = viewModelScope.launch {
            var assistantIndex = -1
            try {
                val conversationId = withContext(Dispatchers.IO) {
                    repository.saveMessage(
                        conversationId = targetConversationId,
                        title = userMessage.text.lineSequence().first().trim().take(60)
                            .ifBlank { attachment?.displayName.orEmpty().take(60) }
                            .ifBlank { "New chat" },
                        message = userMessage.toStoredMessage()
                    )
                }
                if (targetSession != sessionVersion) return@launch

                currentConversationId = conversationId
                messages += userMessage
                refreshConversations()
                val settings = settingsRepository.load()
                val allInferenceMessages = withContext(Dispatchers.IO) {
                    messages.mapNotNull { message ->
                        message.toInferenceContent().takeIf { it.isNotBlank() }?.let { content ->
                        InferenceMessage(
                            role = if (message.role == MessageRole.USER) "user" else "assistant",
                            content = content
                        )
                    }
                    }
                }
                val conversationMessages = if (settings.rememberConversation) {
                    ConversationContextSelector.select(allInferenceMessages)
                } else {
                    allInferenceMessages.takeLast(1)
                }
                val ragPrompt = withContext(Dispatchers.IO) {
                    ragRepository.buildPrompt(
                        query = userMessage.text.ifBlank { attachment?.displayName.orEmpty() },
                        includeCitations = settings.includeRagCitations
                    )
                }
                fun buildInferenceMessages(
                    webContext: String? = null,
                    allowWebSearchRequest: Boolean = false
                ): List<InferenceMessage> = buildList {
                    val orderedInstructions = buildList {
                        ragPrompt?.takeIf(String::isNotBlank)?.let(::add)
                        settings.systemPrompt.trim().takeIf(String::isNotBlank)?.let { prompt ->
                            add("Assistant settings:\n$prompt")
                        }
                        if (allowWebSearchRequest) {
                            add(
                                "If the answer requires current or factual information that is " +
                                    "not available in this conversation, respond with exactly " +
                                    "$WEB_SEARCH_SENTINEL and nothing else. Otherwise answer normally."
                            )
                        }
                    }.joinToString("\n\n")
                    if (orderedInstructions.isNotBlank()) {
                        add(InferenceMessage(role = "system", content = orderedInstructions))
                    }
                    if (webContext == null) {
                        addAll(conversationMessages)
                    } else {
                        val latestUserMessage = conversationMessages.lastOrNull {
                            it.role == "user"
                        }?.content ?: userMessage.text
                        val priorMessages = if (conversationMessages.lastOrNull()?.role == "user") {
                            conversationMessages.dropLast(1)
                        } else {
                            conversationMessages
                        }
                        addAll(priorMessages)
                        add(
                            InferenceMessage(
                                role = "user",
                                content = buildString {
                                    appendLine("Answer this question using the live web evidence below:")
                                    appendLine(latestUserMessage)
                                    appendLine()
                                    appendLine("<live_web_evidence>")
                                    appendLine(webContext)
                                    appendLine("</live_web_evidence>")
                                    appendLine()
                                    appendLine(
                                        "The evidence was retrieved from the web just now. " +
                                            "Use it as the primary source of truth, answer the " +
                                            "question directly, cite supporting result numbers, " +
                                            "and do not say that you lack web access."
                                    )
                                }
                            )
                        )
                    }
                }

                messages += ChatMessage(
                    text = "",
                    role = MessageRole.ASSISTANT,
                    showThinking = true,
                    modelName = model.name
                )
                assistantIndex = messages.lastIndex
                val responseStartedAt = System.nanoTime()

                suspend fun generateAnswer(
                    inferenceMessages: List<InferenceMessage>,
                    searchedWeb: Boolean
                ): String {
                    val rawOutput = StringBuilder()
                    var firstTokenAt: Long? = null
                    var generatedTokenCount = 0
                    fun currentTokenSpeed(): Float? {
                        val startedAt = firstTokenAt ?: return null
                        if (generatedTokenCount < 2) return null
                        val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
                        return if (elapsedSeconds > 0.0) {
                            ((generatedTokenCount - 1) / elapsedSeconds).toFloat()
                        } else {
                            null
                        }
                    }

                    messages.getOrNull(assistantIndex)?.let { current ->
                        messages[assistantIndex] = current.copy(
                            text = "",
                            thinking = "",
                            showThinking = true,
                            activityLabel = "Thinking…",
                            activityDetails = if (searchedWeb) current.activityDetails else "",
                            webBrowsingEnabled = searchedWeb,
                            tokensPerSecond = null
                        )
                    }
                    LlamaRuntime.generate(inferenceMessages, settings).collect { token ->
                        if (targetSession != sessionVersion) throw CancellationException()
                        if (firstTokenAt == null) firstTokenAt = System.nanoTime()
                        generatedTokenCount++
                        rawOutput.append(token)
                        if (settings.streamResponses) {
                            val current = messages.getOrNull(assistantIndex)
                                ?: throw CancellationException()
                            val parsed = ThinkingStreamParser.parse(rawOutput.toString())
                            messages[assistantIndex] = current.copy(
                                text = parsed.response,
                                thinking = parsed.thinking,
                                showThinking = parsed.response.isBlank() && parsed.thinking.isNotBlank(),
                                webBrowsingEnabled = searchedWeb,
                                tokensPerSecond = currentTokenSpeed()
                            )
                        }
                    }

                    val parsed = ThinkingStreamParser.parse(rawOutput.toString())
                    if (!settings.streamResponses) {
                        val current = messages.getOrNull(assistantIndex) ?: throw CancellationException()
                        messages[assistantIndex] = current.copy(
                            text = parsed.response,
                            thinking = parsed.thinking,
                            showThinking = false,
                            webBrowsingEnabled = searchedWeb,
                            tokensPerSecond = currentTokenSpeed()
                        )
                    }
                    return parsed.response
                }

                fun updateBrowsingActivity(details: String) {
                    messages.getOrNull(assistantIndex)?.let { current ->
                        val hideSearchRequest = current.text.trim() == WEB_SEARCH_SENTINEL
                        messages[assistantIndex] = current.copy(
                            text = if (hideSearchRequest) "" else current.text,
                            thinking = if (hideSearchRequest) "" else current.thinking,
                            showThinking = true,
                            activityLabel = "Browsing…",
                            activityDetails = details,
                            webBrowsingEnabled = true
                        )
                    }
                }

                suspend fun searchWeb(): String? = try {
                    updateBrowsingActivity("Searching the web for “${userMessage.text}”…")
                    val results = withContext(Dispatchers.IO) {
                        WebSearchClient.search(userMessage.text)
                    }
                    if (results.isEmpty()) {
                        updateBrowsingActivity(
                            "Searched the web for “${userMessage.text}”\n\nNo results were found."
                        )
                        generationError = "No web results were found; using the local model."
                        null
                    } else {
                        updateBrowsingActivity(
                            buildString {
                                appendLine("Searching the web for “${userMessage.text}”")
                                appendLine()
                                appendLine("Found ${results.size} results:")
                                results.forEachIndexed { index, result ->
                                    appendLine("${index + 1}. ${result.title}")
                                    appendLine("   ${result.url}")
                                }
                            }.trim()
                        )
                        WebSearchClient.formatForPrompt(results)
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    updateBrowsingActivity(
                        "Tried to search the web for “${userMessage.text}”\n\nThe search was unavailable."
                    )
                    generationError = "Web search unavailable; using the local model."
                    null
                }

                var searchedWeb = false
                val searchBeforeAnswer = userMessage.webBrowsingEnabled &&
                    WebSearchPolicy.shouldSearchBeforeAnswer(userMessage.text)
                var answer: String

                if (searchBeforeAnswer) {
                    val webContext = searchWeb()
                    if (webContext != null) {
                        searchedWeb = true
                        answer = generateAnswer(
                            buildInferenceMessages(webContext = webContext),
                            searchedWeb = true
                        )
                    } else {
                        answer = generateAnswer(buildInferenceMessages(), searchedWeb = false)
                    }
                } else {
                    answer = generateAnswer(
                        buildInferenceMessages(
                            allowWebSearchRequest = userMessage.webBrowsingEnabled
                        ),
                        searchedWeb = false
                    )
                    if (userMessage.webBrowsingEnabled &&
                        WebSearchPolicy.shouldSearchAfterAnswer(answer)
                    ) {
                        val webContext = searchWeb()
                        if (webContext != null) {
                            searchedWeb = true
                            answer = generateAnswer(
                                buildInferenceMessages(webContext = webContext),
                                searchedWeb = true
                            )
                        } else if (answer.trim() == WEB_SEARCH_SENTINEL) {
                            val current = messages.getOrNull(assistantIndex)
                            if (current != null) {
                                answer = "I couldn't search the web right now. Check your connection and try again."
                                messages[assistantIndex] = current.copy(
                                    text = answer,
                                    thinking = "",
                                    showThinking = false
                                )
                            }
                        }
                    }
                }

                val assistant = messages.getOrNull(assistantIndex)
                if (assistant != null && assistant.text.isNotBlank()) {
                    val completedAssistant = assistant.copy(
                        thinking = "",
                        showThinking = false,
                        activityLabel = if (searchedWeb) "Browsed the web" else assistant.activityLabel,
                        webBrowsingEnabled = searchedWeb,
                        generationDurationMs = (
                            (System.nanoTime() - responseStartedAt) / 1_000_000L
                        ).coerceAtLeast(1L)
                    )
                    messages[assistantIndex] = completedAssistant
                    withContext(Dispatchers.IO) {
                        repository.saveMessage(
                            conversationId = conversationId,
                            title = userMessage.text.take(60),
                            message = completedAssistant.toStoredMessage()
                        )
                    }
                } else if (targetSession == sessionVersion && assistantIndex >= 0) {
                    messages.removeAt(assistantIndex)
                    generationError = "The model returned no text"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (targetSession == sessionVersion) {
                    if (assistantIndex >= 0 && messages.getOrNull(assistantIndex)?.text.isNullOrBlank()) {
                        messages.removeAt(assistantIndex)
                    }
                    generationError = error.message ?: "Generation failed"
                }
            } finally {
                if (targetSession == sessionVersion) {
                    isGenerating = false
                    if (generationError == ACTIVE_GENERATION_NAVIGATION_MESSAGE) {
                        generationError = null
                    }
                }
                refreshConversations()
            }
        }
    }

    fun stopGeneration() {
        LlamaRuntime.cancel()
    }

    fun newChat() {
        if (isGenerating) {
            generationError = ACTIVE_GENERATION_NAVIGATION_MESSAGE
            return
        }
        cancelCurrentGeneration()
        sessionVersion++
        currentConversationId = null
        messages.clear()
        messageText = ""
        pendingAttachment = null
        documentPreview = null
        generationError = null
        isGenerating = false
    }

    fun openChat(conversationId: String) {
        if (conversationId == currentConversationId) return
        if (isGenerating) {
            generationError = ACTIVE_GENERATION_NAVIGATION_MESSAGE
            return
        }
        cancelCurrentGeneration()
        sessionVersion++
        val targetSession = sessionVersion
        currentConversationId = conversationId
        messages.clear()
        messageText = ""
        pendingAttachment = null
        documentPreview = null
        generationError = null
        isGenerating = false

        viewModelScope.launch {
            val storedMessages = withContext(Dispatchers.IO) { repository.getMessages(conversationId) }
            if (targetSession == sessionVersion && currentConversationId == conversationId) {
                messages.addAll(storedMessages.map { it.toChatMessage() })
            }
        }
    }

    fun deleteChat(conversationId: String) {
        if (conversationId == currentConversationId && isGenerating) {
            generationError = ACTIVE_GENERATION_NAVIGATION_MESSAGE
            return
        }
        if (conversationId == currentConversationId) newChat()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteConversation(conversationId) }
            refreshConversations()
        }
    }

    override fun onCleared() {
        cancelCurrentGeneration()
        super.onCleared()
    }

    private fun cancelCurrentGeneration() {
        LlamaRuntime.cancel()
        generationJob?.cancel()
        generationJob = null
    }

    private fun refreshConversations() {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { repository.getConversations() }
            conversations.clear()
            conversations.addAll(stored)
        }
    }

    private fun createDocumentAttachment(uri: Uri): ChatAttachment {
        val resolver = getApplication<Application>().contentResolver
        val metadata = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            name to size
        }
        val displayName = metadata?.first?.takeIf(String::isNotBlank) ?: "document.txt"
        val mimeType = resolver.getType(uri).orEmpty()
        require(
            !mimeType.startsWith("image/") &&
                !mimeType.startsWith("video/") &&
                !mimeType.startsWith("audio/")
        ) { "Images, videos, and audio files are not supported." }
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(
            mimeType == "application/pdf" ||
                mimeType.startsWith("text/") ||
                mimeType in SUPPORTED_APPLICATION_MIME_TYPES ||
                extension in SUPPORTED_DOCUMENT_EXTENSIONS
        ) { "Select a PDF or readable text document." }
        require((metadata?.second ?: 0L) <= MAX_ATTACHMENT_BYTES) {
            "Attachments must be 20 MB or smaller."
        }
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val attachment = ChatAttachment(uri.toString(), displayName)
        readDocument(attachment)
        return attachment
    }

    private fun readDocument(attachment: ChatAttachment): String {
        val resolver = getApplication<Application>().contentResolver
        val uri = Uri.parse(attachment.uri)
        val mimeType = resolver.getType(uri).orEmpty()
        val temporary = File.createTempFile("chat-attachment-", ".tmp", getApplication<Application>().cacheDir)
        val content = try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open ${attachment.displayName}." }
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            require(temporary.length() <= MAX_ATTACHMENT_BYTES) {
                "Attachments must be 20 MB or smaller."
            }
            DocumentTextExtractor(getApplication()).extract(
                temporary,
                mimeType,
                attachment.displayName
            ).take(MAX_ATTACHMENT_CHARACTERS)
        } finally {
            temporary.delete()
        }
        require(content.isNotBlank()) { "${attachment.displayName} is empty." }
        return content
    }

    private fun ChatMessage.toInferenceContent(): String = buildString {
        text.takeIf(String::isNotBlank)?.let(::append)
        attachments.forEach { attachment ->
            if (isNotEmpty()) append("\n\n")
            append("Attached document: ${attachment.displayName}\n\n")
            append(readDocument(attachment))
        }
    }

    private fun ChatMessage.toStoredMessage() = StoredMessage(
        text = text,
        role = role.name,
        attachments = attachments.map { StoredAttachment(it.uri, it.displayName) },
        modelName = modelName,
        webBrowsingEnabled = webBrowsingEnabled,
        createdAt = createdAt,
        tokensPerSecond = tokensPerSecond,
        generationDurationMs = generationDurationMs
    )

    private fun StoredMessage.toChatMessage() = ChatMessage(
        text = text,
        role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
        attachments = attachments.map { ChatAttachment(it.uri, it.displayName) },
        modelName = modelName,
        webBrowsingEnabled = webBrowsingEnabled,
        createdAt = createdAt,
        tokensPerSecond = tokensPerSecond,
        generationDurationMs = generationDurationMs
    )

    private companion object {
        const val WEB_SEARCH_SENTINEL = "[[SEARCH_WEB]]"
        const val MAX_ATTACHMENT_BYTES = 20_000_000L
        const val MAX_ATTACHMENT_CHARACTERS = 200_000
        val SUPPORTED_APPLICATION_MIME_TYPES = setOf(
            "application/json",
            "application/xml",
            "application/yaml",
            "application/x-yaml",
            "application/javascript"
        )
        val SUPPORTED_DOCUMENT_EXTENSIONS = setOf(
            "pdf", "txt", "md", "markdown", "json", "csv", "tsv", "xml",
            "yaml", "yml", "log", "html", "htm", "css", "js", "ts", "kt",
            "java", "py", "c", "cpp", "h", "hpp", "sql", "sh"
        )
        const val ACTIVE_GENERATION_NAVIGATION_MESSAGE =
            "The AI is still thinking. Stop the response before switching chats."
    }
}
