package com.averycorp.prismtask.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null
) {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val style = MaterialTheme.typography.bodyLarge

    val annotated = if (query.isBlank()) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            append(text)
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            var startIndex = 0
            while (true) {
                val index = lowerText.indexOf(lowerQuery, startIndex)
                if (index == -1) break
                addStyle(
                    SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold),
                    start = index,
                    end = index + query.length
                )
                startIndex = index + query.length
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        fontSize = fontSize,
        fontWeight = fontWeight
    )
}
