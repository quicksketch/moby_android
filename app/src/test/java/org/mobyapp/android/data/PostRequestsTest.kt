package org.mobyapp.android.data

import org.json.JSONObject
import org.junit.Test

class PostRequestsTest {

    private fun json(text: String) = JSONObject(text)

    private fun assertEquals(expected: JSONObject, actual: JSONObject) = assertJsonEquals(expected, actual)

    @Test
    fun createIncludesClientIdAndOmitsEmptyValues() {
        val draft = PostDraft(
            description = "",
            date = "2026-10-27T18:30:00-06:00",
            location = null,
            media = listOf(PostMediaItem(5, "Red rectangle")),
            published = null,
        )
        assertEquals(
            json("""{"client_id":"abc","date":"2026-10-27T18:30:00-06:00","media":[{"id":5,"alt":"Red rectangle"}]}"""),
            PostRequests.create("abc", draft),
        )
    }

    @Test
    fun createIncludesLocationAndStatus() {
        val draft = PostDraft(
            description = "Hike &amp; picnic",
            date = "2026-10-27T18:30:00-06:00",
            location = Location("Boulder", 40.01499, -105.27055),
            media = emptyList(),
            published = false,
        )
        assertEquals(
            json(
                """{"client_id":"abc","description":"Hike &amp; picnic","date":"2026-10-27T18:30:00-06:00",
                "location":{"name":"Boulder","latitude":40.01499,"longitude":-105.27055},"media":[],"status":"draft"}""",
            ),
            PostRequests.create("abc", draft),
        )
    }

    @Test
    fun updateClearsRemovedValues() {
        val draft = PostDraft(
            description = "",
            date = "2026-10-27T18:30:00-06:00",
            location = null,
            media = emptyList(),
            published = true,
        )
        assertEquals(
            json("""{"description":"","date":"2026-10-27T18:30:00-06:00","location":null,"media":[],"status":"published"}"""),
            PostRequests.update(draft),
        )
    }
}
