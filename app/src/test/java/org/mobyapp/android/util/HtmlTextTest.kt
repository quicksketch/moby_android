package org.mobyapp.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {

    @Test
    fun encodesSpecialCharacters() {
        assertEquals("Tom &amp; Jerry &lt;3 &quot;hi&quot;", HtmlText.encode("Tom & Jerry <3 \"hi\""))
    }

    @Test
    fun decodesNamedAndNumericEntities() {
        assertEquals("a & b < c ' é  ", HtmlText.decodeEntities("a &amp; b &lt; c &#39; &#xE9; &nbsp;"))
        assertEquals("&unknown;", HtmlText.decodeEntities("&unknown;"))
    }

    @Test
    fun escapedTextRoundTripsForEditing() {
        val typed = "Sunset & hike <3\nSecond line"
        val stored = HtmlText.fromEditable(typed, raw = false, isHtml = true)
        assertEquals("Sunset &amp; hike &lt;3\nSecond line", stored)
        val editable = HtmlText.toEditable(stored, isHtml = true)
        assertFalse(editable.raw)
        assertEquals(typed, editable.text)
    }

    @Test
    fun markupIsEditedAsIs() {
        val stored = "<p>Written in the <em>editor</em> &amp; saved.</p>"
        val editable = HtmlText.toEditable(stored, isHtml = true)
        assertTrue(editable.raw)
        assertEquals(stored, editable.text)
        assertEquals(stored, HtmlText.fromEditable(editable.text, editable.raw, isHtml = true))
    }

    @Test
    fun plainTextFormatsAreNotEncoded() {
        assertEquals("a & b", HtmlText.fromEditable("a & b", raw = false, isHtml = false))
        assertEquals("a &amp; b", HtmlText.toEditable("a &amp; b", isHtml = false).text)
        assertEquals("<3", HtmlText.toPlainText("<3", isHtml = false))
    }

    @Test
    fun displayTextRemovesMarkup() {
        assertEquals(
            "First & best\nSecond",
            HtmlText.toPlainText("<p>First &amp; <strong>best</strong></p><p>Second</p>", isHtml = true),
        )
        assertEquals("Line one\nline two", HtmlText.toPlainText("Line one<br />line two", isHtml = true))
        assertEquals("<script> stays text", HtmlText.toPlainText("&lt;script&gt; stays text", isHtml = true))
    }
}
