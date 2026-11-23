package org.mobyapp.android.util

/**
 * Converts post descriptions between what the user types and what the site
 * stores.
 *
 * When the site's description format is HTML (discovery's `description.html`),
 * descriptions are sent HTML-encoded, as the node form saves them. The app
 * never renders descriptions as HTML; it shows them as plain text, so markup
 * from other users' posts cannot run in the app.
 */
object HtmlText {

    private val tag = Regex("<[a-zA-Z/!][^>]*>")
    private val blockBreak = Regex("(?i)<br\\s*/?>|</(p|div|li|h[1-6]|blockquote)>")
    private val entity = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    )

    /** Escapes text for an HTML description. */
    fun encode(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    /** Decodes HTML entities, such as "&amp;" and "&#39;". */
    fun decodeEntities(text: String): String = entity.replace(text) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") ->
                name.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            name.startsWith("#") -> name.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[name]
        } ?: match.value
    }

    /** Whether a description contains markup, rather than only escaped text. */
    fun containsMarkup(html: String): Boolean = tag.containsMatchIn(html)

    /** Text for display, with markup removed. */
    fun toPlainText(description: String, isHtml: Boolean): String {
        if (!isHtml) return description
        val withBreaks = blockBreak.replace(description, "\n")
        return decodeEntities(tag.replace(withBreaks, "")).replace(" ", " ").trim()
    }

    /**
     * A stored description prepared for editing.
     *
     * @property raw Whether the text is edited as HTML. Descriptions written
     *   on the site may contain markup, which would be lost if converted to
     *   plain text, so they are edited as is.
     */
    data class Editable(val text: String, val raw: Boolean)

    fun toEditable(description: String, isHtml: Boolean): Editable = when {
        !isHtml -> Editable(description, raw = false)
        containsMarkup(description) -> Editable(description, raw = true)
        else -> Editable(decodeEntities(description), raw = false)
    }

    /** Encodes edited text for saving. */
    fun fromEditable(text: String, raw: Boolean, isHtml: Boolean): String =
        if (isHtml && !raw) encode(text) else text
}
