package org.mobyapp.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.mobyapp.android.data.DescriptionInfo
import org.mobyapp.android.util.RichSpan
import org.mobyapp.android.util.RichText
import org.mobyapp.android.util.TextStyle

/** Descriptions with their formatting, for display. See SPECIFICATIONS.md section 8, "Displaying". */
object FormattedText {

    /**
     * A description as styled text, with only the formatting the site keeps.
     * Links are shown in [linkColor], but aren't tappable.
     */
    fun annotated(description: String, info: DescriptionInfo, linkColor: Color): AnnotatedString {
        if (!info.html) return AnnotatedString(description)
        val document = RichText.display(description, info.richTags)
        return buildAnnotatedString {
            document.paragraphs.forEachIndexed { index, paragraph ->
                if (index > 0) append('\n')
                paragraph.forEach { span -> withStyle(spanStyle(span, linkColor)) { append(span.text) } }
            }
        }
    }

    fun spanStyle(span: RichSpan, linkColor: Color): SpanStyle {
        val decorations = buildList {
            if (TextStyle.UNDERLINE in span.styles || span.link != null) add(TextDecoration.Underline)
            if (TextStyle.STRIKETHROUGH in span.styles) add(TextDecoration.LineThrough)
        }
        return SpanStyle(
            fontWeight = if (TextStyle.BOLD in span.styles) FontWeight.Bold else null,
            fontStyle = if (TextStyle.ITALIC in span.styles) FontStyle.Italic else null,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
            color = if (span.link != null) linkColor else Color.Unspecified,
        )
    }
}
