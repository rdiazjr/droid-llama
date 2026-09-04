package com.example.androidllama.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.androidllama.ui.screens.chat.models.DocumentPreviewState

@Composable
fun DocumentPreviewDialog(
    preview: DocumentPreviewState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview.displayName, maxLines = 2) },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MarkdownContent(preview.content)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun MarkdownContent(markdown: String) {
    val lines = markdown.lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trimEnd()
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim()
            val code = buildString {
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    if (isNotEmpty()) append('\n')
                    append(lines[index])
                    index++
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (language.isNotBlank()) {
                        Text(
                            text = language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        text = code.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (index < lines.size) index++
            continue
        }
        when {
            line.matches(Regex("^#{1,6}\\s+.*")) -> {
                val level = line.takeWhile { it == '#' }.length
                InlineMarkdownText(
                    text = line.drop(level).trim(),
                    style = when (level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = if (level <= 2) 8.dp else 4.dp)
                )
            }

            line.trim() in setOf("---", "***", "___") -> HorizontalDivider()

            line.trimStart().startsWith("> ") -> InlineMarkdownText(
                text = line.trimStart().removePrefix("> "),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Regex("^\\s*[-*+]\\s+.*").matches(line) -> Row {
                Text("•  ", fontWeight = FontWeight.Bold)
                InlineMarkdownText(line.replaceFirst(Regex("^\\s*[-*+]\\s+"), ""))
            }

            line.isBlank() -> Text(" ", style = MaterialTheme.typography.bodySmall)
            else -> InlineMarkdownText(line)
        }
        index++
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontWeight: FontWeight? = null
) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    Text(
        text = parseInlineMarkdown(text, codeBackground),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight
    )
}

private val INLINE_MARKDOWN = Regex(
    """(\*\*\*(.+?)\*\*\*|___(.+?)___|\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|\*([^*\n]+)\*|_([^_\n]+)_)"""
)

private fun parseInlineMarkdown(
    source: String,
    codeBackground: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    INLINE_MARKDOWN.findAll(source).forEach { match ->
        if (match.range.first > cursor) append(source.substring(cursor, match.range.first))
        val (content, style) = when {
            match.groupValues[2].isNotEmpty() -> match.groupValues[2] to SpanStyle(
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
            match.groupValues[3].isNotEmpty() -> match.groupValues[3] to SpanStyle(
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
            match.groupValues[4].isNotEmpty() -> match.groupValues[4] to SpanStyle(fontWeight = FontWeight.Bold)
            match.groupValues[5].isNotEmpty() -> match.groupValues[5] to SpanStyle(fontWeight = FontWeight.Bold)
            match.groupValues[6].isNotEmpty() -> match.groupValues[6] to SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = codeBackground
            )
            match.groupValues[7].isNotEmpty() -> match.groupValues[7] to SpanStyle(fontStyle = FontStyle.Italic)
            else -> match.groupValues[8] to SpanStyle(fontStyle = FontStyle.Italic)
        }
        withStyle(style) { append(content) }
        cursor = match.range.last + 1
    }
    if (cursor < source.length) append(source.substring(cursor))
}
