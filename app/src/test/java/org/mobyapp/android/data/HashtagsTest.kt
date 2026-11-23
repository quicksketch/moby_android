package org.mobyapp.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HashtagsTest {

    @Test
    fun findsHashtags() {
        assertEquals(
            listOf("backdrop", "mobile", "new-tag_1"),
            Hashtags.find("Testing #backdrop and #mobile, then #new-tag_1."),
        )
    }

    @Test
    fun findsHashtagsAtTheStartAndAfterBrackets() {
        assertEquals(listOf("first", "inside"), Hashtags.find("#first line\n(#inside)"))
    }

    @Test
    fun ignoresLinksEntitiesAndNumbers() {
        assertEquals(emptyList<String>(), Hashtags.find("a#b http://x.com/#frag &#39; #123 # alone"))
    }

    @Test
    fun findsLettersInOtherLanguages() {
        assertEquals(listOf("café", "東京"), Hashtags.find("#café #東京"))
    }

    @Test
    fun removesDuplicatesIgnoringCase() {
        assertEquals(listOf("Backdrop"), Hashtags.find("#Backdrop #backdrop"))
    }

    @Test
    fun cleansTags() {
        assertEquals(listOf("Hiking", "Rocky Mountains"), Hashtags.clean(listOf("  #Hiking ", "hiking", "Rocky   Mountains", " ", "#")))
    }
}
