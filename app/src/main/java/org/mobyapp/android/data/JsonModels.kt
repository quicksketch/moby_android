package org.mobyapp.android.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the API's JSON into the models in Models.kt, with Android's built-in
 * org.json.
 *
 * Required properties throw org.json.JSONException when missing or of the
 * wrong type, which the app reports as a response it didn't understand.
 * Optional properties, and properties older sites leave out, fall back to the
 * models' defaults. Unknown properties are ignored, so newer sites work with
 * older apps.
 */
internal object JsonModels {

    fun discovery(o: JSONObject) = Discovery(
        api = o.intOrNull("api_version")?.let { ApiInfo(version = it, minVersion = o.intOrNull("api_min_version") ?: 1) },
        module = o.objOrNull("module")?.let {
            ModuleInfo(name = it.getString("name"), platform = it.strOrNull("platform"), version = it.strOrNull("version"))
        },
        site = site(o.getJSONObject("site")),
        setupIssues = o.list("setup_issues") { setupIssue(it) },
        authMechanisms = o.list("auth_mechanisms") { authInfo(it) },
        limits = limits(o.getJSONObject("limits")),
        media = mediaTypes(o.getJSONObject("media")),
        description = o.objOrNull("description")?.let(::descriptionInfo) ?: DescriptionInfo(),
        features = o.objOrNull("features")?.let(::features) ?: Features(),
        display = o.objOrNull("display")?.let(::displayInfo) ?: DisplayInfo(),
    )

    fun site(o: JSONObject) = Site(
        name = o.getString("name"),
        url = o.getString("url"),
        favicon = o.strOrNull("favicon"),
    )

    private fun setupIssue(o: JSONObject) = SetupIssue(
        code = o.getString("code"),
        severity = o.getString("severity"),
        message = o.getString("message"),
    )

    private fun authInfo(o: JSONObject) = AuthInfo(
        mechanism = o.getString("mechanism"),
        label = o.getString("label"),
        fields = o.list("fields") { authField(it) },
        passwordResetUrl = o.strOrNull("password_reset_url"),
        description = o.strOrNull("description"),
        submitLabel = o.strOrNull("submit_label"),
    )

    private fun authField(o: JSONObject) = AuthField(
        name = o.getString("name"),
        label = o.getString("label"),
        type = o.getString("type"),
        required = o.bool("required", true),
        placeholder = o.strOrNull("placeholder"),
    )

    private fun limits(o: JSONObject) = Limits(
        maxUploadSize = o.getLong("max_upload_size"),
        maxMediaPerPost = o.getInt("max_media_per_post"),
        maxDescriptionLength = o.intOrNull("max_description_length"),
        maxAltLength = o.intOrNull("max_alt_length") ?: 2000,
        maxTagsPerPost = o.intOrNull("max_tags_per_post") ?: 50,
        maxTagLength = o.intOrNull("max_tag_length") ?: 255,
    )

    private fun mediaTypes(o: JSONObject): MediaTypes {
        val image = o.getJSONObject("image")
        val video = o.getJSONObject("video")
        return MediaTypes(
            image = ImageTypeInfo(extensions = image.strings("extensions"), maxSize = image.getLong("max_size")),
            video = VideoTypeInfo(
                extensions = video.strings("extensions"),
                maxSize = video.getLong("max_size"),
                thumbnail = video.objOrNull("thumbnail")?.let {
                    ThumbnailInfo(extensions = it.strings("extensions"))
                },
            ),
        )
    }

    private fun descriptionInfo(o: JSONObject) = DescriptionInfo(
        format = o.strOrNull("format"),
        html = o.bool("html", false),
        tags = if (o.isNull("tags")) emptyList() else o.strings("tags"),
    )

    private fun displayInfo(o: JSONObject) = DisplayInfo(
        order = if (o.isNull("order")) DisplayInfo.DEFAULT_ORDER else o.strings("order"),
    )

    private fun features(o: JSONObject) = Features(
        location = o.bool("location", false),
        locationName = o.bool("location_name", false),
        locationCoordinates = o.bool("location_coordinates", false),
        drafts = o.bool("drafts", false),
        tags = o.bool("tags", false),
    )

    fun tokenResponse(o: JSONObject) = TokenResponse(
        token = o.getString("token"),
        tokenType = o.getString("token_type"),
        user = user(o.getJSONObject("user")),
    )

    /** A 201 token response, or a 202 login step. */
    fun loginResult(o: JSONObject): LoginResult = o.objOrNull("step")?.let {
        LoginResult.Step(
            LoginStep(
                requestId = it.getString("request_id"),
                title = it.strOrNull("title"),
                description = it.strOrNull("description"),
                submitLabel = it.strOrNull("submit_label") ?: "Continue",
                fields = it.list("fields") { field -> authField(field) },
            ),
        )
    } ?: LoginResult.LoggedIn(tokenResponse(o))

    fun user(o: JSONObject) = User(
        id = o.getLong("id"),
        name = o.getString("name"),
        url = o.getString("url"),
        permissions = o.objOrNull("permissions")?.let {
            Permissions(create = it.bool("create", false), publish = it.bool("publish", false))
        } ?: Permissions(),
    )

    fun media(o: JSONObject) = Media(
        id = o.getLong("id"),
        type = o.strOrNull("type"),
        status = o.getString("status"),
        filename = o.getString("filename"),
        mimeType = o.getString("mime_type"),
        size = o.getLong("size"),
        width = o.intOrNull("width"),
        height = o.intOrNull("height"),
        duration = o.doubleOrNull("duration"),
        url = o.getString("url"),
        thumbnail = o.objOrNull("thumbnail")?.let {
            Thumbnail(url = it.getString("url"), width = it.intOrNull("width"), height = it.intOrNull("height"))
        },
        alt = o.strOrNull("alt") ?: "",
    )

    private fun location(o: JSONObject) = Location(
        name = o.strOrNull("name"),
        latitude = o.doubleOrNull("latitude"),
        longitude = o.doubleOrNull("longitude"),
    )

    fun post(o: JSONObject) = Post(
        id = o.getLong("id"),
        url = o.getString("url"),
        apiUrl = o.getString("api_url"),
        status = o.getString("status"),
        description = o.strOrNull("description") ?: "",
        date = o.getString("date"),
        location = o.objOrNull("location")?.let(::location),
        media = o.list("media") { media(it) },
        tags = if (o.isNull("tags")) emptyList() else o.strings("tags"),
        created = o.getString("created"),
        changed = o.getString("changed"),
        permissions = o.objOrNull("permissions")?.let {
            PostPermissions(edit = it.bool("edit", false), delete = it.bool("delete", false))
        } ?: PostPermissions(),
    )

    fun postList(o: JSONObject) = PostList(
        posts = o.list("posts") { post(it) },
        page = o.getInt("page"),
        limit = o.getInt("limit"),
        total = o.getInt("total"),
    )

    fun tagSuggestions(o: JSONObject) = TagSuggestions(
        tags = if (o.isNull("tags")) emptyList() else o.strings("tags"),
    )

    /** An error response's details, or null if the body isn't one. */
    fun errorDetail(body: String): ErrorDetail? = runCatching {
        val error = JSONObject(body).getJSONObject("error")
        ErrorDetail(
            code = error.getString("code"),
            message = error.getString("message"),
            field = error.strOrNull("field"),
            restart = error.bool("restart", false),
        )
    }.getOrNull()
}

// Small helpers for optional values. org.json's own opt* methods turn a
// missing value into "" or 0, which would hide the difference between "absent"
// and a real value.

internal fun JSONObject.strOrNull(key: String): String? = if (isNull(key)) null else getString(key)

internal fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

internal fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)

internal fun JSONObject.bool(key: String, default: Boolean): Boolean = if (isNull(key)) default else getBoolean(key)

internal fun JSONObject.objOrNull(key: String): JSONObject? = if (isNull(key)) null else getJSONObject(key)

/** The strings in an array property. */
internal fun JSONObject.strings(key: String): List<String> = getJSONArray(key).let { a -> List(a.length()) { a.getString(it) } }

/** The objects in an array property, read with [read], or an empty list if it's missing. */
internal fun <T> JSONObject.list(key: String, read: (JSONObject) -> T): List<T> =
    if (isNull(key)) emptyList() else getJSONArray(key).let { a -> List(a.length()) { read(a.getJSONObject(it)) } }

/** A JSON array of strings. */
internal fun jsonArrayOf(values: List<String>): JSONArray = JSONArray().apply { values.forEach { put(it) } }
