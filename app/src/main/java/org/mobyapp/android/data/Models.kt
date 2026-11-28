package org.mobyapp.android.data

import org.mobyapp.android.util.RichTags

// Models for the Moby REST API (v1). See moby_docs/API.md for the meaning
// of each property. They are read from JSON
// in JsonModels.kt, with the defaults given here for properties older sites
// leave out.

data class Discovery(
    /** The API versions the site serves, from the root "api_version" and "api_min_version" properties. Null on sites from before versioning. */
    val api: ApiInfo? = null,
    /** The site's module, for showing and support. */
    val module: ModuleInfo? = null,
    val site: Site,
    /** Problems with the site's setup, for the user to pass on to an administrator. */
    val setupIssues: List<SetupIssue> = emptyList(),
    /** Every mechanism the app can log in with, the default first. */
    val authMechanisms: List<AuthInfo> = emptyList(),
    val limits: Limits,
    val media: MediaTypes,
    val description: DescriptionInfo = DescriptionInfo(),
    val features: Features = Features(),
    /** How the site orders a post's parts. Missing from older sites. */
    val display: DisplayInfo = DisplayInfo(),
)

/**
 * The API versions a site serves.
 *
 * @property version The newest version the site implements.
 * @property minVersion The oldest version it still answers.
 */
data class ApiInfo(val version: Int, val minVersion: Int)

/** The site's Moby module. Apps never compare its version. */
data class ModuleInfo(val name: String, val platform: String?, val version: String?)

data class Site(
    val name: String,
    val url: String,
    /** Missing from sites with older versions of the module. */
    val favicon: String? = null,
)

data class SetupIssue(val code: String, val severity: String, val message: String) {
    /** Whether the issue stops posting from the app, rather than limiting it. */
    val isError get() = severity == "error"
}

data class AuthInfo(
    val mechanism: String,
    val label: String,
    val fields: List<AuthField>,
    val passwordResetUrl: String? = null,
    /** A sentence to show above the fields. */
    val description: String? = null,
    /** The submit button's label. The app uses "Log in" without one. */
    val submitLabel: String? = null,
)

data class AuthField(
    val name: String,
    val label: String,
    val type: String,
    val required: Boolean = true,
    /** Example text shown in the empty field, such as "name@example.com". */
    val placeholder: String? = null,
)

data class Limits(
    /** The most bytes a single request may carry. */
    val maxUploadSize: Long,
    val maxMediaPerPost: Int,
    val maxDescriptionLength: Int? = null,
    val maxAltLength: Int = 2000,
    val maxTagsPerPost: Int = 50,
    val maxTagLength: Int = 255,
)

data class MediaTypes(val image: ImageTypeInfo, val video: VideoTypeInfo)

data class ImageTypeInfo(
    val extensions: List<String>,
    /** The most bytes a photo or a video's thumbnail may have. */
    val maxSize: Long,
)

data class VideoTypeInfo(
    val extensions: List<String>,
    /** The most bytes a video may have. */
    val maxSize: Long,
    /** How to send video thumbnails, or null if the site doesn't store them. */
    val thumbnail: ThumbnailInfo? = null,
)

/** Thumbnails may be at most [ImageTypeInfo.maxSize] bytes. */
data class ThumbnailInfo(val extensions: List<String>)

data class DescriptionInfo(
    val format: String? = null,
    val html: Boolean = false,
    /** The formatting tags the site keeps. See SPECIFICATIONS.md section 8. */
    val tags: List<String> = emptyList(),
) {
    /** The site's formatting, none unless descriptions are HTML. */
    val richTags: RichTags get() = RichTags(if (html) tags else emptyList())
}

/**
 * How posts show their parts on the site.
 *
 * @property order "date", "media", "description" and "location", in the order
 *   the site shows them, so the app's list of posts matches.
 */
data class DisplayInfo(val order: List<String> = DEFAULT_ORDER) {
    /** The order with any unknown parts left out and missing parts added. */
    val parts: List<String>
        get() = (order.filter { it in DEFAULT_ORDER } + DEFAULT_ORDER).distinct()

    companion object {
        val DEFAULT_ORDER = listOf("date", "media", "description", "location")
    }
}

data class Features(
    val location: Boolean = false,
    val locationName: Boolean = false,
    val locationCoordinates: Boolean = false,
    val drafts: Boolean = false,
    /** Whether posts can have tags, including from hashtags. */
    val tags: Boolean = false,
)

data class TokenResponse(
    val token: String,
    val tokenType: String,
    val user: User,
)

/** The answer to a login request: a token, or a login waiting for the user. */
sealed interface LoginResult {
    data class LoggedIn(val response: TokenResponse) : LoginResult
    data class Step(val step: LoginStep) : LoginResult
}

/**
 * The next form of a login with more than one step, such as for an emailed
 * code. The app sends its fields with [requestId]. See "Login steps" in
 * API.md.
 *
 * @property requestId A secret: anyone with it can continue the login. Kept
 *   only in memory.
 * @property title The step's heading, or null for the usual one.
 */
data class LoginStep(
    val requestId: String,
    val title: String? = null,
    val description: String? = null,
    val submitLabel: String,
    val fields: List<AuthField>,
)

data class User(
    val id: Long,
    val name: String,
    val url: String,
    val permissions: Permissions = Permissions(),
)

data class Permissions(val create: Boolean = false, val publish: Boolean = false)

data class Media(
    val id: Long,
    val type: String? = null,
    val status: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val url: String,
    /** A smaller image for lists and previews, made by the site's image style. */
    val thumbnail: Thumbnail? = null,
    val alt: String = "",
)

data class Thumbnail(val url: String, val width: Int? = null, val height: Int? = null)

data class Location(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class Post(
    val id: Long,
    val url: String,
    val apiUrl: String,
    val status: String,
    val description: String = "",
    val date: String,
    val location: Location? = null,
    val media: List<Media> = emptyList(),
    /** The post's tags, including those from hashtags in the description. */
    val tags: List<String> = emptyList(),
    val created: String,
    val changed: String,
    val permissions: PostPermissions = PostPermissions(),
)

/** What the current user may do with a post. */
data class PostPermissions(val edit: Boolean = false, val delete: Boolean = false)

/** Tags suggested as the user types, from GET /tags. */
data class TagSuggestions(val tags: List<String> = emptyList())

data class PostList(
    val posts: List<Post>,
    val page: Int,
    val limit: Int,
    val total: Int,
)

/**
 * @property restart Whether a login must start again from its first form.
 */
data class ErrorDetail(val code: String, val message: String, val field: String? = null, val restart: Boolean = false)

/** An error response from the API. */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val field: String? = null,
    /** During a login, whether it must start again from the first form. */
    val restart: Boolean = false,
) : Exception(message)
