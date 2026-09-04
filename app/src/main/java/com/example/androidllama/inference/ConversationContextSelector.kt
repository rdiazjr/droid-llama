package com.example.androidllama.inference

/** Keeps prior turns only when the current user message appears to depend on them. */
object ConversationContextSelector {
    private val wordPattern = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "do", "for",
        "from", "had", "has", "have", "how", "i", "if", "in", "is", "it", "me", "my",
        "of", "on", "or", "our", "so", "that", "the", "their", "then", "there", "this",
        "to", "was", "we", "were", "what", "when", "where", "which", "who", "why", "will",
        "with", "would", "you", "your"
    )
    private val followUpWords = setOf(
        "it", "that", "this", "they", "them", "those", "these", "previous", "earlier",
        "above", "again", "continue", "more", "same"
    )
    private val followUpPhrases = listOf(
        "what about", "how about", "tell me more", "go on", "and then", "why is that",
        "how so", "can you explain", "please continue"
    )

    fun select(messages: List<InferenceMessage>): List<InferenceMessage> {
        val currentIndex = messages.indexOfLast { it.role == "user" }
        if (currentIndex <= 0) return messages.takeLast(1)

        val current = messages[currentIndex]
        val normalizedCurrent = current.content.lowercase()
        if (isDirectFollowUp(normalizedCurrent)) {
            val previousUser = (currentIndex - 1 downTo 0).firstOrNull {
                messages[it].role == "user"
            } ?: return listOf(current)
            return messages.subList(previousUser, currentIndex + 1)
        }

        val currentTerms = meaningfulTerms(normalizedCurrent)
        if (currentTerms.isEmpty()) return listOf(current)

        val selected = BooleanArray(currentIndex + 1)
        selected[currentIndex] = true
        var index = 0
        while (index < currentIndex) {
            if (messages[index].role != "user") {
                index++
                continue
            }
            val nextUser = ((index + 1) until currentIndex).firstOrNull {
                messages[it].role == "user"
            } ?: currentIndex
            if (meaningfulTerms(messages[index].content.lowercase()).any(currentTerms::contains)) {
                for (turnIndex in index until nextUser) selected[turnIndex] = true
            }
            index = nextUser
        }

        return messages.filterIndexed { messageIndex, _ -> selected[messageIndex] }
    }

    private fun isDirectFollowUp(text: String): Boolean {
        if (followUpPhrases.any(text::contains)) return true
        val words = wordPattern.findAll(text).map { it.value }.toList()
        return words.size <= 12 && words.any(followUpWords::contains)
    }

    private fun meaningfulTerms(text: String): Set<String> = wordPattern.findAll(text)
        .map { it.value }
        .filter { it.length >= 3 && it !in stopWords }
        .map { if (it.length > 4 && it.endsWith('s')) it.dropLast(1) else it }
        .toSet()
}
