package org.mobyapp.android.data

import org.json.JSONArray
import org.json.JSONObject

/** A media item in a post being saved, by its uploaded media ID. */
data class PostMediaItem(val id: Long, val alt: String)

/** The values of a post being created or edited. */
data class PostDraft(
    /** The description, already encoded for the site's text format. */
    val description: String,
    /** An ISO 8601 date and time with a UTC offset. */
    val date: String,
    val location: Location?,
    val media: List<PostMediaItem>,
    val published: Boolean?,
    /**
     * The tags added in the app, without those from the description's
     * hashtags, which the site adds itself. Null if the site has no tags.
     */
    val tags: List<String>? = null,
)

/** Builds the JSON bodies for creating and updating posts. */
object PostRequests {

    /**
     * The body of POST /posts.
     *
     * @param clientId A UUID that stays the same when the request is retried,
     *   so a post is never created twice.
     */
    fun create(clientId: String, draft: PostDraft): JSONObject = JSONObject().apply {
        put("client_id", clientId)
        putDraft(draft, isUpdate = false)
    }

    /**
     * The body of PATCH /posts/{id}.
     *
     * Every editable property is sent. The media list replaces the post's
     * media, and a missing location clears it.
     */
    fun update(draft: PostDraft): JSONObject = JSONObject().apply {
        putDraft(draft, isUpdate = true)
    }

    private fun JSONObject.putDraft(draft: PostDraft, isUpdate: Boolean) {
        // An empty description is only sent when updating, to clear it.
        if (draft.description.isNotEmpty() || isUpdate) {
            put("description", draft.description)
        }
        put("date", draft.date)
        val location = draft.location?.takeIf { it.name != null || it.latitude != null }
        when {
            location != null -> put("location", JSONObject().apply {
                location.name?.let { put("name", it) }
                if (location.latitude != null && location.longitude != null) {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                }
            })
            isUpdate -> put("location", JSONObject.NULL)
        }
        put("media", JSONArray().apply {
            draft.media.forEach { item -> put(JSONObject().put("id", item.id).put("alt", item.alt)) }
        })
        draft.published?.let { put("status", if (it) "published" else "draft") }
        draft.tags?.let { put("tags", jsonArrayOf(it)) }
    }
}
