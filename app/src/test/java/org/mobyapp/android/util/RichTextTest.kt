package org.mobyapp.android.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared cases in rich-text.json, a copy of
 * moby_docs/fixtures/rich-text.json, which the iOS app's RichTextTests runs
 * too.
 */
class RichTextTest {

    private class Case(val name: String, val html: String, val tags: RichTags, val result: String?)

    private fun cases(group: String): List<Case> {
        val json = JSONObject(javaClass.classLoader!!.getResource("rich-text.json")!!.readText())
        val array = json.getJSONArray(group)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val tags = o.getJSONArray("tags").let { t -> (0 until t.length()).map(t::getString) }
            Case(o.getString("name"), o.getString("html"), RichTags(tags), if (o.isNull("result")) null else o.getString("result"))
        }
    }

    @Test
    fun editableCases() {
        for (c in cases("editable")) {
            assertEquals(c.name, c.result, RichText.editable(c.html, c.tags)?.let { RichText.write(it, c.tags) })
        }
    }

    @Test
    fun displayCases() {
        for (c in cases("display")) {
            assertEquals(c.name, c.result, RichText.write(RichText.display(c.html, c.tags), c.tags))
        }
    }

    @Test
    fun readsSpans() {
        val document = RichText.editable(
            "<p><strong>a <em>b</em></strong> <a href=\"https://x.org\">c</a></p><p>d</p>",
            RichTags(listOf("p", "strong", "em", "a")),
        )
        assertEquals(
            RichDocument(
                listOf(
                    listOf(
                        RichSpan("a ", setOf(TextStyle.BOLD)),
                        RichSpan("b", setOf(TextStyle.BOLD, TextStyle.ITALIC)),
                        RichSpan(" "),
                        RichSpan("c", link = "https://x.org"),
                    ),
                    listOf(RichSpan("d")),
                ),
            ),
            document,
        )
        assertEquals("a b c\nd", document?.plainText)
    }

    @Test
    fun tags() {
        val tags = RichTags(listOf("p", "b", "del", "a"))
        assertEquals("b", tags.tagFor(TextStyle.BOLD))
        assertEquals("del", tags.tagFor(TextStyle.STRIKETHROUGH))
        assertEquals(listOf(TextStyle.BOLD, TextStyle.STRIKETHROUGH), tags.styles)
        assertTrue(tags.offersLinks)
        assertTrue(tags.offersFormatting)
        assertFalse(RichTags(listOf("p", "br")).offersFormatting)
        assertTrue(RichTags(listOf("a")).offersFormatting)
    }

    @Test
    fun normalizesLinks() {
        assertEquals("https://example.com/a?b=1", RichText.normalizeLink(" example.com/a?b=1 "))
        assertEquals("http://example.com", RichText.normalizeLink("http://example.com"))
        assertEquals("HTTPS://Example.com", RichText.normalizeLink("HTTPS://Example.com"))
        assertEquals("mailto:me@example.com", RichText.normalizeLink("mailto:me@example.com"))
        for (bad in listOf("", "javascript://alert(1)", "ftp://example.com", "two words", "https://", "mailto:")) {
            assertEquals(bad, null, RichText.normalizeLink(bad))
        }
    }
}
