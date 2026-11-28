package org.mobyapp.android.util

/** A text style the formatted description editor supports. Links are kept separately, since they have a URL. */
enum class TextStyle(
    /** The tags for the style, preferred first. A site's tags pick the one it uses. */
    val tags: List<String>,
) {
    BOLD(listOf("strong", "b")),
    ITALIC(listOf("em", "i")),
    UNDERLINE(listOf("u")),
    STRIKETHROUGH(listOf("s", "del", "strike"));

    companion object {
        fun of(tag: String): TextStyle? = entries.firstOrNull { tag in it.tags }
    }
}

/** A piece of text with the same styles and link throughout. */
data class RichSpan(val text: String, val styles: Set<TextStyle> = emptySet(), val link: String? = null) {
    fun sameFormat(other: RichSpan) = styles == other.styles && link == other.link
}

/** A description as paragraphs of styled text. */
data class RichDocument(val paragraphs: List<List<RichSpan>> = emptyList()) {
    /** The text without formatting, with paragraphs on separate lines. */
    val plainText: String get() = paragraphs.joinToString("\n") { p -> p.joinToString("") { it.text } }
}

/**
 * The formatting a site's description format keeps: discovery's
 * `description.tags`. See SPECIFICATIONS.md section 8.
 */
data class RichTags(val tags: List<String>) {
    enum class Paragraphs { P, BR, NEWLINES }

    /** The tag a style is written with, or null if the site doesn't keep it. */
    fun tagFor(style: TextStyle): String? = style.tags.firstOrNull { it in tags }

    fun offers(style: TextStyle) = tagFor(style) != null
    val offersLinks: Boolean get() = "a" in tags
    /** The styles the site keeps, in toolbar order. */
    val styles: List<TextStyle> get() = TextStyle.entries.filter(::offers)
    /** Whether descriptions are edited as formatted text. */
    val offersFormatting: Boolean get() = styles.isNotEmpty() || offersLinks

    val paragraphs: Paragraphs
        get() = when {
            "p" in tags -> Paragraphs.P
            "br" in tags -> Paragraphs.BR
            else -> Paragraphs.NEWLINES
        }
}

/**
 * Reads and writes formatted descriptions. See SPECIFICATIONS.md section 8,
 * and the shared cases in moby_docs/fixtures/rich-text.json.
 */
object RichText {

    /** The tags the editor reads. */
    internal val knownTags: Set<String> = (listOf("p", "br", "a") + TextStyle.entries.flatMap { it.tags }).toSet()
    /** Tags whose ends break lines when displaying. */
    private val blockTags = setOf("p", "div", "li", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote")

    private val token = Regex(
        "<!--[\\s\\S]*?(?:-->|$)|<![^>]*>|<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>",
    )
    private val attribute = Regex("([^\\s=/>\"']+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?")

    /**
     * Reads a description for the formatted editor, or returns null if it has
     * something the editor can't keep, so it's edited as raw HTML.
     */
    fun editable(html: String, tags: RichTags): RichDocument? {
        val document = parse(html, strict = true) ?: return null
        for (span in document.paragraphs.flatten()) {
            if (span.link != null && !tags.offersLinks) return null
            if (span.styles.any { !tags.offers(it) }) return null
        }
        return document
    }

    /** Reads a description for display, keeping only the formatting the site keeps. */
    fun display(html: String, tags: RichTags): RichDocument {
        val document = parse(html, strict = false) ?: RichDocument()
        return RichDocument(
            document.paragraphs.map { spans ->
                merged(
                    spans.map { span ->
                        span.copy(
                            styles = span.styles.filter(tags::offers).toSet(),
                            link = span.link.takeIf { tags.offersLinks },
                        )
                    },
                )
            },
        )
    }

    /** Writes a document as HTML, using only the site's tags. */
    fun write(document: RichDocument, tags: RichTags): String {
        val paragraphs = document.paragraphs.map { writeParagraph(it, tags) }.filter { it.isNotEmpty() }
        val html = when (tags.paragraphs) {
            RichTags.Paragraphs.P -> paragraphs.joinToString("\n") { "<p>$it</p>" }
            RichTags.Paragraphs.BR -> paragraphs.joinToString("<br>")
            RichTags.Paragraphs.NEWLINES -> paragraphs.joinToString("\n\n")
        }
        return html.trim()
    }

    /**
     * A link's address as typed, made ready to save: `https://` is added when
     * there's no scheme. Returns null unless it's an http, https or mailto
     * address.
     */
    fun normalizeLink(typed: String): String? {
        var text = typed.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val lower = text.lowercase()
        if (!lower.startsWith("mailto:") && "://" !in lower) text = "https://$text"
        val uri = try {
            java.net.URI(text)
        } catch (e: java.net.URISyntaxException) {
            return null
        }
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> text.takeIf { !uri.host.isNullOrEmpty() }
            "mailto" -> text.takeIf { it.length > "mailto:".length }
            else -> null
        }
    }

    // Writing

    private sealed interface Mark {
        data class Link(val href: String) : Mark
        data class Style(val tag: String) : Mark
    }

    private fun writeParagraph(spans: List<RichSpan>, tags: RichTags): String {
        val out = StringBuilder()
        val open = ArrayList<Mark>()
        fun closeTo(count: Int) {
            while (open.size > count) {
                when (val mark = open.removeAt(open.size - 1)) {
                    is Mark.Link -> out.append("</a>")
                    is Mark.Style -> out.append("</").append(mark.tag).append('>')
                }
            }
        }
        for (span in spans) {
            if (span.text.isEmpty()) continue
            val marks = ArrayList<Mark>()
            if (span.link != null && tags.offersLinks) marks += Mark.Link(span.link)
            for (style in TextStyle.entries) {
                if (style in span.styles) tags.tagFor(style)?.let { marks += Mark.Style(it) }
            }
            var shared = 0
            while (shared < open.size && shared < marks.size && open[shared] == marks[shared]) shared++
            closeTo(shared)
            for (mark in marks.drop(shared)) {
                when (mark) {
                    is Mark.Link -> out.append("<a href=\"").append(HtmlText.encode(mark.href)).append("\">")
                    is Mark.Style -> out.append('<').append(mark.tag).append('>')
                }
                open += mark
            }
            out.append(HtmlText.encode(span.text))
        }
        closeTo(0)
        return out.toString()
    }

    // Reading

    private class Element(val name: String, val style: TextStyle?, val link: String?)

    /**
     * Reads HTML into paragraphs. When [strict], returns null for anything the
     * editor can't keep: other tags, attributes, or comments. Otherwise other
     * tags are removed, keeping their text, and block tags break paragraphs.
     */
    internal fun parse(html: String, strict: Boolean): RichDocument? {
        val reader = Reader()
        val stack = ArrayList<Element>()
        var pDepth = 0
        var last = 0

        fun addText(raw: String) {
            val styles = stack.mapNotNull { it.style }.toSet()
            val link = stack.lastOrNull { it.link != null }?.link
            // Outside <p>, newlines end paragraphs.
            val lines = if (pDepth > 0) listOf(raw) else raw.split('\n')
            lines.forEachIndexed { i, line ->
                if (i > 0) reader.endParagraph()
                reader.add(line, styles, link)
            }
        }

        for (match in token.findAll(html)) {
            addText(html.substring(last, match.range.first))
            last = match.range.last + 1
            val nameGroup = match.groups[2]
            if (nameGroup == null) {
                // A comment or declaration.
                if (strict) return null
                continue
            }
            val closing = match.groupValues[1].isNotEmpty()
            val name = nameGroup.value.lowercase()
            val attributes = parseAttributes(match.groupValues[3])
            val known = name in knownTags
            if (strict) {
                if (!known) return null
                if (name == "a" && !closing) {
                    if (attributes.size != 1 || "href" !in attributes) return null
                } else if (attributes.isNotEmpty()) {
                    return null
                }
            }
            when {
                name == "br" -> reader.endParagraph()
                name == "p" || (!strict && name in blockTags) -> {
                    reader.endParagraph()
                    if (name == "p") pDepth = maxOf(0, pDepth + if (closing) -1 else 1)
                }
                known -> if (closing) {
                    val index = stack.indexOfLast { it.name == name }
                    if (index >= 0) while (stack.size > index) stack.removeAt(stack.size - 1)
                } else {
                    val href = if (name == "a") attributes["href"]?.let(HtmlText::decodeEntities) else null
                    stack += Element(name, TextStyle.of(name), href)
                }
            }
        }
        addText(html.substring(last))
        reader.endParagraph()
        return RichDocument(reader.paragraphs)
    }

    private fun parseAttributes(text: String): Map<String, String> =
        attribute.findAll(text).associate { match ->
            val value = (2..4).firstNotNullOfOrNull { match.groups[it]?.value } ?: ""
            match.groupValues[1].lowercase() to value
        }

    /** Collects paragraphs, collapsing whitespace as HTML does. */
    private class Reader {
        val paragraphs = ArrayList<List<RichSpan>>()
        private val current = ArrayList<RichSpan>()

        fun add(raw: String, styles: Set<TextStyle>, link: String?) {
            var text = HtmlText.decodeEntities(collapse(raw)).replace('\u00A0', ' ')
            // One space where two pieces of text meet, and none at the start.
            val previous = current.lastOrNull()?.text
            if (text.startsWith(' ') && (previous == null || previous.endsWith(' '))) text = text.substring(1)
            if (text.isEmpty()) return
            val span = RichSpan(text, styles, link)
            val lastSpan = current.lastOrNull()
            if (lastSpan != null && lastSpan.sameFormat(span)) {
                current[current.size - 1] = lastSpan.copy(text = lastSpan.text + text)
            } else {
                current += span
            }
        }

        fun endParagraph() {
            val spans = ArrayList(current)
            current.clear()
            // Removes spaces at the end.
            while (spans.isNotEmpty()) {
                val trimmed = spans.last().text.trimEnd(' ')
                if (trimmed.isEmpty()) {
                    spans.removeAt(spans.size - 1)
                } else {
                    spans[spans.size - 1] = spans.last().copy(text = trimmed)
                    break
                }
            }
            if (spans.isNotEmpty()) paragraphs += spans
        }

        /** Collapses runs of HTML's whitespace, not other spaces, to one space. */
        private fun collapse(text: String): String = buildString(text.length) {
            var space = false
            for (c in text) {
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C') {
                    if (!space) append(' ')
                    space = true
                } else {
                    append(c)
                    space = false
                }
            }
        }
    }

    private fun merged(spans: List<RichSpan>): List<RichSpan> {
        val out = ArrayList<RichSpan>()
        for (span in spans) {
            val last = out.lastOrNull()
            if (last != null && last.sameFormat(span)) out[out.size - 1] = last.copy(text = last.text + span.text) else out += span
        }
        return out
    }
}
