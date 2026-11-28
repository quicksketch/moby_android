package org.mobyapp.android.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import org.mobyapp.android.util.RichDocument
import org.mobyapp.android.util.RichSpan
import org.mobyapp.android.util.RichTags
import org.mobyapp.android.util.RichText
import org.mobyapp.android.util.TextStyle

/**
 * The description field when the site keeps formatting. Selecting text adds
 * Bold, Italic, Underline, Strikethrough (the styles the site offers) and Link
 * to the floating text toolbar. See SPECIFICATIONS.md section 8, "Formatting".
 *
 * It looks like the other outlined fields: an [EditText] inside Material's
 * outlined decoration.
 *
 * @param initial The description when the editor opens. Afterwards the field
 *   holds it, and reports each change to [onChange].
 */
@Composable
fun FormattedTextField(
    initial: RichDocument,
    tags: RichTags,
    isError: Boolean,
    onChange: (RichDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    var plainText by remember { mutableStateOf(initial.plainText) }
    var linkRequest by remember { mutableStateOf<LinkRequest?>(null) }
    val currentOnChange by rememberUpdatedState(onChange)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val bodyLarge = MaterialTheme.typography.bodyLarge

    OutlinedTextFieldDefaults.DecorationBox(
        value = plainText,
        innerTextField = {
            AndroidView(
                factory = { context ->
                    FormattedEditText(context, tags, linkColor).apply {
                        setTextColor(textColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, bodyLarge.fontSize.value)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            lineHeight = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP, bodyLarge.lineHeight.value, resources.displayMetrics,
                            ).toInt()
                        }
                        setDocument(initial)
                        onDocumentChange = { document ->
                            plainText = document.plainText
                            currentOnChange(document)
                        }
                        onLinkRequest = { linkRequest = it }
                        var focus: FocusInteraction.Focus? = null
                        setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                focus = FocusInteraction.Focus().also { interaction.tryEmit(it) }
                            } else {
                                focus?.let { interaction.tryEmit(FocusInteraction.Unfocus(it)) }
                                focus = null
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("description"),
            )
        },
        enabled = true,
        singleLine = false,
        visualTransformation = VisualTransformation.None,
        interactionSource = interaction,
        isError = isError,
        label = { Text("What's happening?") },
        supportingText = { Text("Select text to format it.") },
    )

    linkRequest?.let { request ->
        LinkDialog(request, onDismiss = { linkRequest = null })
    }
}

/** Asks for a link's address. */
class LinkRequest(
    /** The link being changed, or "" for a new one. */
    val url: String,
    val canRemove: Boolean,
    val apply: (String) -> Unit,
    val remove: () -> Unit,
)

@Composable
private fun LinkDialog(request: LinkRequest, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(request.url) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    fun done() {
        val link = RichText.normalizeLink(url)
        if (link == null) {
            error = "Enter a web address."
        } else {
            request.apply(link)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("URL") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { done() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("linkUrl"),
                )
                if (request.canRemove) {
                    TextButton(onClick = { request.remove(); onDismiss() }) {
                        Text("Remove link", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::done) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Marks text with a style, and draws it. */
private class StyleMark(val style: TextStyle) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = apply(paint)
    override fun updateMeasureState(paint: TextPaint) = apply(paint)

    private fun apply(paint: TextPaint) {
        when (style) {
            TextStyle.BOLD, TextStyle.ITALIC -> {
                val old = paint.typeface?.style ?: Typeface.NORMAL
                val add = if (style == TextStyle.BOLD) Typeface.BOLD else Typeface.ITALIC
                paint.typeface = Typeface.create(paint.typeface, old or add)
            }
            TextStyle.UNDERLINE -> paint.isUnderlineText = true
            TextStyle.STRIKETHROUGH -> paint.isStrikeThruText = true
        }
    }
}

/** Marks text as a link to [url], and draws it underlined in [color]. */
private class LinkMark(val url: String, private val color: Int) : CharacterStyle() {
    override fun updateDrawState(paint: TextPaint) {
        paint.color = color
        paint.isUnderlineText = true
    }
}

/**
 * The text field. Styles are spans of their own, which continue into text
 * typed at their end, as in other editors. Links don't.
 */
@SuppressLint("ViewConstructor")
class FormattedEditText(context: Context, private val tags: RichTags, private val linkColor: Int) : EditText(context) {
    var onDocumentChange: (RichDocument) -> Unit = {}
    var onLinkRequest: (LinkRequest) -> Unit = {}
    private var updating = false

    init {
        background = null
        setPadding(0, 0, 0, 0)
        gravity = Gravity.TOP or Gravity.START
        minLines = 4
        inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
            EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
        // The editor's view model keeps the description, so the view doesn't
        // restore its own text, without its styles, over it.
        isSaveEnabled = false
        contentDescription = "What's happening?"
        customSelectionActionModeCallback = SelectionMenu()
        customInsertionActionModeCallback = InsertionMenu()
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (!updating) onDocumentChange(document())
            }
        })
    }

    fun setDocument(document: RichDocument) {
        val text = SpannableStringBuilder()
        document.paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) text.append('\n')
            for (span in paragraph) {
                val start = text.length
                text.append(span.text)
                for (style in span.styles) text.setSpan(StyleMark(style), start, text.length, STYLE_FLAGS)
                span.link?.let { text.setSpan(LinkMark(it, linkColor), start, text.length, LINK_FLAGS) }
            }
        }
        updating = true
        setText(text, BufferType.EDITABLE)
        updating = false
    }

    /** The text as paragraphs of styled spans. */
    fun document(): RichDocument {
        val text = this.text ?: return RichDocument()
        val styles = text.getSpans(0, text.length, StyleMark::class.java)
        val links = text.getSpans(0, text.length, LinkMark::class.java)
        val bounds = sortedSetOf(0, text.length)
        for (span in styles.asList() + links.asList()) {
            bounds += text.getSpanStart(span)
            bounds += text.getSpanEnd(span)
        }
        val paragraphs = mutableListOf(mutableListOf<RichSpan>())
        bounds.zipWithNext().forEach { (start, end) ->
            if (start >= end) return@forEach
            val on = styles.filter { text.getSpanStart(it) <= start && text.getSpanEnd(it) >= end }.map { it.style }.toSet()
            val link = links.lastOrNull { text.getSpanStart(it) <= start && text.getSpanEnd(it) >= end }?.url
            text.substring(start, end).split('\n').forEachIndexed { index, line ->
                if (index > 0) paragraphs += mutableListOf<RichSpan>()
                if (line.isEmpty()) return@forEachIndexed
                val current = paragraphs.last()
                val last = current.lastOrNull()
                if (last != null && last.sameFormat(RichSpan(line, on, link))) {
                    current[current.size - 1] = last.copy(text = last.text + line)
                } else {
                    current += RichSpan(line, on, link)
                }
            }
        }
        return RichDocument(paragraphs.filter { it.isNotEmpty() })
    }

    // Styles

    /** Whether all of a range has a style. */
    private fun has(style: TextStyle, start: Int, end: Int): Boolean {
        val text = text ?: return false
        if (start >= end) return false
        val spans = text.getSpans(start, end, StyleMark::class.java).filter { it.style == style }
        var covered = start
        for (span in spans.sortedBy { text.getSpanStart(it) }) {
            if (text.getSpanStart(span) > covered) return false
            covered = maxOf(covered, text.getSpanEnd(span))
        }
        return covered >= end
    }

    /** Adds a style to the selection, or removes it if all of it has it. */
    fun toggle(style: TextStyle) {
        val text = text ?: return
        val start = selectionStart
        val end = selectionEnd
        if (!tags.offers(style) || start < 0 || start >= end) return
        val on = !has(style, start, end)
        var from = start
        var to = end
        // Spans of the style that overlap or touch the selection are joined
        // into it, or cut around it.
        for (span in text.getSpans(start, end, StyleMark::class.java).filter { it.style == style }) {
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            text.removeSpan(span)
            if (on) {
                from = minOf(from, spanStart)
                to = maxOf(to, spanEnd)
            } else {
                if (spanStart < start) text.setSpan(StyleMark(style), spanStart, start, STYLE_FLAGS)
                if (spanEnd > end) text.setSpan(StyleMark(style), end, spanEnd, STYLE_FLAGS)
            }
        }
        if (on) text.setSpan(StyleMark(style), from, to, STYLE_FLAGS)
        changed()
    }

    /** Asks for a link for the selection, or for the link the cursor is in. */
    fun requestLink() {
        val text = text ?: return
        if (!tags.offersLinks) return
        var start = selectionStart
        var end = selectionEnd
        var existing: String? = null
        if (start < end) {
            val links = text.getSpans(start, end, LinkMark::class.java)
            val urls = links.map { it.url }.toSet()
            val covered = links.any { text.getSpanStart(it) <= start && text.getSpanEnd(it) >= end }
            if (urls.size == 1 && covered) existing = urls.first()
        } else {
            val link = linkAt(start) ?: return
            existing = link.url
            start = text.getSpanStart(link)
            end = text.getSpanEnd(link)
        }
        onLinkRequest(
            LinkRequest(
                url = existing.orEmpty(),
                canRemove = existing != null,
                apply = { url -> setLink(url, start, end) },
                remove = { setLink(null, start, end) },
            ),
        )
    }

    /** The link around a cursor position, including right after its end. */
    private fun linkAt(position: Int): LinkMark? {
        val text = text ?: return null
        return text.getSpans(maxOf(0, position - 1), position, LinkMark::class.java)
            .firstOrNull { text.getSpanStart(it) < position && text.getSpanEnd(it) >= position }
    }

    private fun setLink(url: String?, start: Int, end: Int) {
        val text = text ?: return
        if (start >= end || end > text.length) return
        for (span in text.getSpans(start, end, LinkMark::class.java)) {
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            text.removeSpan(span)
            if (spanStart < start) text.setSpan(LinkMark(span.url, linkColor), spanStart, start, LINK_FLAGS)
            if (spanEnd > end) text.setSpan(LinkMark(span.url, linkColor), end, spanEnd, LINK_FLAGS)
        }
        if (url != null) text.setSpan(LinkMark(url, linkColor), start, end, LINK_FLAGS)
        requestFocus()
        setSelection(start, end)
        changed()
    }

    private fun changed() {
        onDocumentChange(document())
        // Redraws, since changing spans' styles doesn't always.
        invalidate()
        requestLayout()
    }

    // Menus and keys

    /**
     * Closes the keyboard if the field leaves the screen while it has it, as
     * Compose's own fields do. Otherwise it would stay open on the next
     * screen.
     */
    override fun onDetachedFromWindow() {
        if (hasFocus()) {
            context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
        }
        super.onDetachedFromWindow()
    }

    /** Pasted text takes the styles at the cursor. */
    override fun onTextContextMenuItem(id: Int): Boolean =
        super.onTextContextMenuItem(if (id == android.R.id.paste) android.R.id.pasteAsPlainText else id)

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_B -> { toggle(TextStyle.BOLD); return true }
                KeyEvent.KEYCODE_I -> { toggle(TextStyle.ITALIC); return true }
                KeyEvent.KEYCODE_U -> { toggle(TextStyle.UNDERLINE); return true }
                KeyEvent.KEYCODE_K -> { requestLink(); return true }
            }
        }
        return super.onKeyShortcut(keyCode, event)
    }

    /** Adds the styles the site offers, and Link, after the standard commands. */
    private inner class SelectionMenu : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            tags.styles.forEachIndexed { index, style ->
                menu.add(Menu.NONE, MENU_STYLE + style.ordinal, MENU_ORDER + index, label(style))
            }
            if (tags.offersLinks) menu.add(Menu.NONE, MENU_LINK, MENU_ORDER + TextStyle.entries.size, "Link")
            return true
        }

        // The floating toolbar can't show check marks, so the items don't
        // show which styles the selection has.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val style = TextStyle.entries.firstOrNull { item.itemId == MENU_STYLE + it.ordinal }
            when {
                style != null -> { toggle(style); mode.invalidate() }
                // Asked for before the menu closes, which ends the selection.
                item.itemId == MENU_LINK -> { requestLink(); mode.finish() }
                else -> return false
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

    /** With the cursor in a link, adds Edit link. */
    private inner class InsertionMenu : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (tags.offersLinks && linkAt(selectionStart) != null) menu.add(Menu.NONE, MENU_LINK, MENU_ORDER, "Edit link")
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != MENU_LINK) return false
            requestLink()
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

    private companion object {
        // Styles continue into text typed at their end; links don't.
        const val STYLE_FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE
        const val LINK_FLAGS = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        const val MENU_STYLE = 0x4d0b0
        const val MENU_LINK = 0x4d0bf
        const val MENU_ORDER = 100

        fun label(style: TextStyle) = when (style) {
            TextStyle.BOLD -> "Bold"
            TextStyle.ITALIC -> "Italic"
            TextStyle.UNDERLINE -> "Underline"
            TextStyle.STRIKETHROUGH -> "Strikethrough"
        }
    }
}
