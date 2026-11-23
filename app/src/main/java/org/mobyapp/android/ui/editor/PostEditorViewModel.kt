package org.mobyapp.android.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mobyapp.android.MobyApplication
import org.mobyapp.android.data.ApiException
import org.mobyapp.android.data.Discovery
import org.mobyapp.android.data.Hashtags
import org.mobyapp.android.data.Location
import org.mobyapp.android.data.Media
import org.mobyapp.android.data.PostDraft
import org.mobyapp.android.data.PostMediaItem
import org.mobyapp.android.data.PostRequests
import org.mobyapp.android.data.ThumbnailImage
import org.mobyapp.android.ui.userMessage
import org.mobyapp.android.util.HtmlText
import org.mobyapp.android.util.IsoDates
import java.time.ZonedDateTime
import java.util.UUID

private const val MAX_COMPRESS_ATTEMPTS = 3

/** A photo or video in the editor, uploaded or being uploaded. */
class EditorMedia(val type: String, val localUri: Uri?, existing: Media? = null) {
    val key: String = UUID.randomUUID().toString()
    var media by mutableStateOf(existing)
    /** Upload progress from 0 to 1, while uploading. */
    var progress by mutableStateOf(0f)
    var error by mutableStateOf<String?>(null)
    var alt by mutableStateOf(existing?.alt ?: "")
    internal var device: DeviceMedia? = null
    internal var job: Job? = null
    /** Whether a video is being compressed, which [progress] then reports. */
    var compressing by mutableStateOf(false)
    /** An offer to compress a large video that the user has not answered yet. */
    var compressionOffer by mutableStateOf<VideoCompression.Decision.Offer?>(null)
    internal var offerAnswer: CompletableDeferred<Boolean>? = null
    /** Set when the user declines compression, so retrying doesn't ask again. */
    internal var keepOriginal = false
    /** The video thumbnail, kept so a retried upload doesn't make it again. */
    internal var thumbnail: ThumbnailImage? = null

    val isUploading get() = media == null && error == null
    /**
     * The image to show: the local file while uploading, then the site's
     * thumbnail, or a photo without one at its full size.
     */
    val previewModel: Any? get() = localUri ?: media?.thumbnail?.url ?: if (type == "image") media?.url else null
}

/**
 * Creates a new post, or edits an existing one.
 *
 * @param postId The post to edit, or null to create a post.
 * @param sharedMedia Media shared from another app, to add once loaded.
 */
class PostEditorViewModel(
    private val app: MobyApplication,
    val postId: Long?,
    sharedMedia: List<Uri> = emptyList(),
) : ViewModel() {

    /** Shared media not added yet. It waits for a failed load to be retried. */
    private var pendingMedia = sharedMedia

    var discovery by mutableStateOf<Discovery?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    /** The API property an error applies to, such as "location.latitude". */
    var errorField by mutableStateOf<String?>(null)
        private set

    var description by mutableStateOf("")
    /** Whether the description contains markup and is edited as HTML. */
    var descriptionIsRaw by mutableStateOf(false)
        private set
    var date by mutableStateOf(ZonedDateTime.now())
    var locationName by mutableStateOf("")
    var latitude by mutableStateOf("")
    var longitude by mutableStateOf("")
    /**
     * For users who may publish, whether the post is published. For other
     * users, false means "save as a draft", and true leaves the status to the
     * site, which uses the content type's default.
     */
    var published by mutableStateOf(postId != null || app.settings.publishByDefault)
    var canPublish by mutableStateOf(false)
        private set
    /**
     * Whether the user is editing a draft they may not publish, which only a
     * site administrator can publish.
     */
    var statusLocked by mutableStateOf(false)
        private set
    val media = mutableStateListOf<EditorMedia>()

    /**
     * Tags the user added. Hashtags in the description are tags too (see
     * [hashtags]), and the site adds them itself.
     */
    val tags = mutableStateListOf<String>()

    /** What the user is typing in the tag field. */
    var tagInput by mutableStateOf("")
        private set

    /** Tags the site suggests for [tagInput]. */
    var tagSuggestions by mutableStateOf<List<String>>(emptyList())
        private set
    private var suggestJob: Job? = null

    /** The hashtags in the description, which are saved as tags. */
    val hashtags: List<String> get() = Hashtags.find(description)

    val maxTags get() = discovery?.limits?.maxTagsPerPost ?: 0

    /** Stays the same across retries, so the post is never created twice. */
    private val clientId = UUID.randomUUID().toString()

    val isEditing get() = postId != null

    /**
     * Whether to fill in the device's location once the editor has loaded:
     * only for new posts, when the user's setting is on. Cleared when used, so
     * it happens at most once per post.
     */
    var autoLocate by mutableStateOf(postId == null && app.settings.includeLocation)
    val maxMedia get() = discovery?.limits?.maxMediaPerPost ?: 0

    /**
     * Why the editor couldn't load, or null. While set, the form is not shown
     * and can't be saved, so a failed load never saves a blank post over an
     * existing one.
     */
    var loadError by mutableStateOf<String?>(null)
        private set

    val canSave get() = !loading && !saving && loadError == null && discovery != null

    init {
        load()
    }

    fun load() {
        loading = true
        loadError = null
        viewModelScope.launch {
            try {
                discovery = app.requireDiscovery()
                canPublish = app.requireUser().permissions.publish
                if (postId != null) loadPost(postId)
                addMedia(pendingMedia.also { pendingMedia = emptyList() })
            } catch (e: Exception) {
                loadError = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    private suspend fun loadPost(id: Long) {
        val post = app.api().getPost(id)
        val editable = HtmlText.toEditable(post.description, discovery?.description?.html == true)
        description = editable.text
        descriptionIsRaw = editable.raw
        date = IsoDates.parse(post.date) ?: date
        locationName = post.location?.name ?: ""
        latitude = post.location?.latitude?.toString() ?: ""
        longitude = post.location?.longitude?.toString() ?: ""
        published = post.status == "published"
        statusLocked = !published && !canPublish
        // Tags from the description's hashtags are shown from the description,
        // so removing a hashtag there removes its tag.
        val fromDescription = Hashtags.find(description)
        tags.clear()
        tags.addAll(post.tags.filter { tag -> fromDescription.none { Hashtags.same(it, tag) } })
        media.clear()
        media.addAll(post.media.map { EditorMedia(it.type ?: "image", localUri = null, existing = it) })
    }

    /** Adds picked photos and videos, uploading each one immediately. */
    fun addMedia(uris: List<Uri>) {
        val discovery = discovery ?: return
        val room = maxMedia - media.size
        if (uris.size > room) {
            error = when {
                maxMedia == 0 -> "This site doesn't accept photos or videos from the app yet."
                else -> "A post may have up to $maxMedia photos and videos. Only the first ${room.coerceAtLeast(0)} were added."
            }
        }
        uris.take(room.coerceAtLeast(0)).forEach { uri ->
            DeviceMediaReader.read(app.contentResolver, uri, discovery)
                .onSuccess { device ->
                    val item = EditorMedia(device.type, uri).apply { this.device = device }
                    media.add(item)
                    upload(item)
                }
                .onFailure { error = if (it is SecurityException) "This file could not be opened." else it.message }
        }
    }

    override fun onCleared() {
        media.forEach { it.device?.deleteConverted() }
    }

    fun retryUpload(item: EditorMedia) {
        item.error = null
        upload(item)
    }

    private fun upload(item: EditorMedia) {
        val device = item.device ?: return
        item.progress = 0f
        val thumbnailInfo = discovery?.media?.video?.thumbnail
        val maxImageSize = discovery?.media?.image?.maxSize ?: return
        item.job = viewModelScope.launch {
            try {
                if (device.source == null) {
                    device.useConverted(HeicConverter.toJpeg(app, device.uri, maxImageSize), device.jpegFilename!!, "image/jpeg")
                }
                if (item.type == "video") prepareVideo(item, device)
                item.progress = 0f
                val source = device.source ?: return@launch
                if (item.type == "video" && thumbnailInfo != null && item.thumbnail == null) {
                    val baseName = source.filename.substringBeforeLast('.')
                    item.thumbnail = VideoThumbnails.create(app, device.uri, baseName, thumbnailInfo, maxImageSize)
                }
                item.media = app.api().uploadMedia(source, item.alt, item.thumbnail) { sent, total ->
                    item.progress = if (total > 0) sent.toFloat() / total else 0f
                }
            } catch (e: Exception) {
                item.error = e.userMessage()
            } finally {
                item.compressionOffer = null
                // Keep a converted file only for retrying this upload.
                if (item.media != null || item !in media) device.deleteConverted()
            }
        }
    }

    /**
     * Compresses a video if it is too large for the site, or if it is large and
     * the user chooses to.
     *
     * @throws IllegalStateException With a message for the user, if the video
     *   is too large and can't be compressed enough.
     */
    private suspend fun prepareVideo(item: EditorMedia, device: DeviceMedia) {
        val discovery = discovery ?: return
        if (device.isConverted || item.keepOriginal) return
        val size = device.originalSize
        val maxSize = discovery.media.video.maxSize
        val info = VideoCompressor.readInfo(app, device.uri, size)
        // Compressed videos are always MP4.
        val decision = VideoCompression.decide(size, info.takeIf { "mp4" in discovery.media.video.extensions }, maxSize)
        val plan = when (decision) {
            is VideoCompression.Decision.UploadOriginal -> return
            is VideoCompression.Decision.Compress -> decision.plan
            is VideoCompression.Decision.Reject -> error(
                "This video is ${DeviceMediaReader.formatSize(size)}. The site accepts up to " +
                    DeviceMediaReader.formatSize(maxSize) + (if (decision.triedCompressing) ", and compressing it to 720p wouldn't be enough." else "."),
            )
            is VideoCompression.Decision.Offer -> {
                val answer = CompletableDeferred<Boolean>()
                item.offerAnswer = answer
                item.compressionOffer = decision
                val compress = answer.await()
                item.compressionOffer = null
                if (!compress) {
                    item.keepOriginal = true
                    return
                }
                decision.plan
            }
        }

        val filename = device.source!!.filename.substringBeforeLast('.') + ".mp4"
        item.compressing = true
        try {
            // The estimate is close but not exact, so check the result. If it's
            // too large, try again, allowing for how far off the estimate was.
            var attempt: VideoCompression.Plan? = plan
            var overshoot = 1.0
            var tries = 0
            while (attempt != null && tries++ < MAX_COMPRESS_ATTEMPTS) {
                item.progress = 0f
                val file = VideoCompressor.compress(app, device.uri, attempt) { item.progress = it }
                if (file.length() <= maxSize) {
                    device.useConverted(file, filename, "video/mp4")
                    return
                }
                overshoot *= file.length().toDouble() / attempt.estimatedSize
                file.delete()
                attempt = VideoCompression.plan(requireNotNull(info), maxSize, overshoot)
            }
            error("This video couldn't be compressed to fit the site's limit of ${DeviceMediaReader.formatSize(maxSize)}.")
        } finally {
            item.compressing = false
        }
    }

    /** Answers a [EditorMedia.compressionOffer]. */
    fun answerCompressionOffer(item: EditorMedia, compress: Boolean) {
        item.offerAnswer?.complete(compress)
    }

    fun removeMedia(item: EditorMedia) {
        item.job?.cancel()
        media.remove(item)
        item.device?.deleteConverted()
        // Delete uploads that are not attached to a post yet. Attached media is
        // removed from the post when it is saved.
        val uploaded = item.media
        if (uploaded != null && uploaded.status == "pending") {
            viewModelScope.launch { runCatching { app.api().deleteMedia(uploaded.id) } }
        }
    }

    /** Moves the item at [from] to [to], as when dragging it. */
    fun moveMedia(from: Int, to: Int) {
        if (from !in media.indices || to !in media.indices || from == to) return
        media.add(to, media.removeAt(from))
    }

    fun moveMedia(item: EditorMedia, offset: Int) {
        val from = media.indexOf(item)
        val to = from + offset
        if (from < 0 || to !in media.indices) return
        media.removeAt(from)
        media.add(to, item)
    }

    fun setLocation(location: Location) {
        val features = discovery?.features
        if (features?.locationName == true && features.locationCoordinates == false && location.name == null) {
            error = "A place name could not be found for your location."
            return
        }
        locationName = location.name ?: locationName
        if (features?.locationCoordinates != true) return
        latitude = location.latitude?.toString() ?: ""
        longitude = location.longitude?.toString() ?: ""
    }

    fun clearLocation() {
        locationName = ""
        latitude = ""
        longitude = ""
    }

    fun save(onSaved: () -> Unit) {
        val discovery = discovery ?: return
        if (!canSave) return
        error = null
        errorField = null
        when {
            media.any { it.isUploading } -> {
                error = "Wait for uploads to finish."
                return
            }
            media.any { it.error != null } -> {
                error = "Some uploads failed. Retry or remove them."
                return
            }
            description.isBlank() && media.isEmpty() -> {
                error = "Add a description, or a photo or video."
                errorField = "description"
                return
            }
        }
        val location = buildLocation() ?: return
        // A tag still being typed is kept, as if the user had finished it.
        if (tagInput.isNotBlank()) {
            addTag(tagInput)
            if (error != null) return
        }

        val draft = PostDraft(
            description = HtmlText.fromEditable(description.trim(), descriptionIsRaw, discovery.description.html),
            date = IsoDates.format(date),
            location = location.takeIf { it.name != null || it.latitude != null },
            media = media.map { PostMediaItem(requireNotNull(it.media).id, it.alt.trim()) },
            published = if (canPublish || !published) published else null,
            tags = tags.toList().takeIf { discovery.features.tags },
        )
        saving = true
        viewModelScope.launch {
            try {
                if (postId == null) {
                    app.api().createPost(PostRequests.create(clientId, draft))
                } else {
                    app.api().updatePost(postId, PostRequests.update(draft))
                }
                onSaved()
            } catch (e: Exception) {
                error = e.userMessage()
                errorField = (e as? ApiException)?.field
            } finally {
                saving = false
            }
        }
    }

    /**
     * Updates the tag being typed. A comma ends it and adds it as a tag.
     * Suggestions are looked up once the user pauses typing.
     */
    fun onTagInput(text: String) {
        if (',' in text) {
            text.split(',').dropLast(1).forEach(::addTag)
            tagInput = text.substringAfterLast(',').trimStart()
        } else {
            tagInput = text
        }
        suggestJob?.cancel()
        val search = tagInput.trim().trimStart('#')
        if (search.isEmpty()) {
            tagSuggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            val found = runCatching { app.api().suggestTags(search) }.getOrDefault(emptyList())
            tagSuggestions = found.filter { suggestion -> (tags + hashtags).none { Hashtags.same(it, suggestion) } }
        }
    }

    /** Adds a tag, unless the post already has it. Clears the tag field. */
    fun addTag(name: String) {
        val tag = Hashtags.clean(listOf(name)).firstOrNull()
        tagInput = ""
        tagSuggestions = emptyList()
        suggestJob?.cancel()
        if (tag == null || (tags + hashtags).any { Hashtags.same(it, tag) }) return
        val maxLength = discovery?.limits?.maxTagLength ?: Int.MAX_VALUE
        when {
            tag.length > maxLength -> error = "Tags can be at most $maxLength characters."
            tags.size + hashtags.size >= maxTags -> error = "A post can have at most $maxTags tags."
            else -> tags.add(tag)
        }
    }

    fun removeTag(tag: String) {
        tags.remove(tag)
    }

    /** Validates the location fields, or returns null after setting an error. */
    private fun buildLocation(): Location? {
        val features = discovery?.features
        val name = locationName.trim().ifEmpty { null }.takeIf { features?.locationName == true }
        if (features?.locationCoordinates != true) return Location(name, null, null)
        val lat = latitude.trim()
        val lng = longitude.trim()
        if (lat.isEmpty() != lng.isEmpty()) {
            error = "Enter both a latitude and a longitude, or neither."
            errorField = if (lat.isEmpty()) "location.latitude" else "location.longitude"
            return null
        }
        val latValue = lat.toDoubleOrNull()
        val lngValue = lng.toDoubleOrNull()
        if (lat.isNotEmpty() && (latValue == null || latValue !in -90.0..90.0)) {
            error = "The latitude must be a number between -90 and 90."
            errorField = "location.latitude"
            return null
        }
        if (lng.isNotEmpty() && (lngValue == null || lngValue !in -180.0..180.0)) {
            error = "The longitude must be a number between -180 and 180."
            errorField = "location.longitude"
            return null
        }
        return Location(name, latValue, lngValue)
    }
}
