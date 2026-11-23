package org.mobyapp.android.ui.editor

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import org.mobyapp.android.data.Discovery
import org.mobyapp.android.data.UploadSource
import java.io.File

/**
 * A photo or video picked on the device.
 *
 * @param original The original file, or null if it can't be uploaded as it is.
 * @param jpegFilename For HEIC photos, the name to upload as once converted.
 */
class DeviceMedia(val uri: Uri, val type: String, private val original: UploadSource?, val jpegFilename: String? = null) {
    /** What to upload, or null if a HEIC photo has not been converted yet. */
    var source: UploadSource? = original
        private set
    /** The original's size, even when a converted file will be uploaded. */
    val originalSize: Long get() = original?.size ?: -1
    private var converted: File? = null
    val isConverted get() = converted != null

    /** Uploads a converted file in place of the original. */
    fun useConverted(file: File, filename: String, mimeType: String) {
        converted = file
        source = UploadSource(filename, mimeType, file.length()) { file.inputStream() }
    }

    /** Deletes the converted file, if any. Another upload makes it again. */
    fun deleteConverted() {
        converted?.delete() ?: return
        converted = null
        source = original
    }
}

/** Reads picked photos and videos, and checks them against the site's limits. */
object DeviceMediaReader {

    /**
     * Prepares a picked file for upload.
     *
     * @return The media, or an error message for the user.
     */
    fun read(resolver: ContentResolver, uri: Uri, discovery: Discovery): Result<DeviceMedia> = runCatching {
        var displayName: String? = null
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0)
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        val mimeType = resolver.getType(uri) ?: error("This file's type could not be determined.")
        val type = mimeType.substringBefore('/')
        if (type != "image" && type != "video") error("Only photos and videos can be added.")
        val baseName = displayName?.substringBeforeLast('.')?.ifBlank { null } ?: "upload"

        // HEIC photos are converted when they are uploaded, and only the JPEG's
        // type and size need to suit the site.
        if (type == "image" && isHeic(mimeType, displayName)) {
            val jpegExtension = jpegExtension(discovery.media.image.extensions)
                ?: error("This photo needs to be converted to JPEG, but this site does not accept JPEG files.")
            return@runCatching DeviceMedia(uri, type, original = null, jpegFilename = "$baseName.$jpegExtension")
        }

        // The site checks the extension, so name the file after its actual type.
        val extension = (MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: displayName?.substringAfterLast('.', ""))?.lowercase()?.ifEmpty { null }
            ?: error("This file's type could not be determined.")
        val allowed = if (type == "image") discovery.media.image.extensions else discovery.media.video.extensions
        val normalized = if (extension == "jpeg" && "jpeg" !in allowed) "jpg" else extension
        if (normalized !in allowed) {
            error("This site does not accept .$extension files. Allowed: ${allowed.joinToString(", ")}.")
        }

        if (size < 0) error("This file's size could not be determined.")
        // Videos that are too large may be compressed when they are uploaded.
        if (type == "image" && size > discovery.media.image.maxSize) {
            error("This photo is ${formatSize(size)}. The site accepts up to ${formatSize(discovery.media.image.maxSize)}.")
        }

        DeviceMedia(
            uri = uri,
            type = type,
            original = UploadSource(
                filename = "$baseName.$normalized",
                mimeType = mimeType,
                size = size,
                open = { resolver.openInputStream(uri) ?: error("The file could not be opened.") },
            ),
        )
    }

    /** Whether a photo is HEIC, by its MIME type or else its filename. */
    internal fun isHeic(mimeType: String, filename: String?): Boolean =
        mimeType.lowercase() in HEIC_MIME_TYPES ||
            filename?.substringAfterLast('.', "")?.lowercase() in HEIC_EXTENSIONS

    /** The extension to give a JPEG on a site, or null if it accepts neither. */
    internal fun jpegExtension(allowed: List<String>): String? =
        listOf("jpg", "jpeg").firstOrNull { it in allowed }

    private val HEIC_MIME_TYPES = setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence")
    private val HEIC_EXTENSIONS = setOf("heic", "heif")

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes bytes"
    }
}
