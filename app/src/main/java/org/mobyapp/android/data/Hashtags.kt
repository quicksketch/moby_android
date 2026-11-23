package org.mobyapp.android.data

/**
 * Finds hashtags in descriptions, by the same rules as the site, so the app
 * can show them as tags while the user types. See "Tags" in
 * moby_docs/API.md.
 */
object Hashtags {

    /**
     * A "#" and a letter, then letters, numbers, "_", and "-". It must start
     * the text or follow a space, a line break, or an opening bracket, so the
     * "#" in a link or an HTML entity isn't one.
     */
    private val pattern = Regex("""(?:^|(?<=[\s(\[]))#(\p{L}[\p{L}\p{M}\p{N}_-]*)""")

    /** The hashtags in a text, without their "#", each once, in order. */
    fun find(text: String): List<String> =
        clean(pattern.findAll(text).map { it.groupValues[1].trimEnd('-') }.toList())

    /**
     * Tidies tags as the site does: trims them, removes a leading "#",
     * collapses spaces, and removes empty tags and duplicates, which differ
     * only in letter case.
     */
    fun clean(tags: List<String>): List<String> =
        tags.map { it.trim().trimStart('#').trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    /** Whether two tags are the same tag, which ignores letter case. */
    fun same(a: String, b: String) = a.equals(b, ignoreCase = true)
}
