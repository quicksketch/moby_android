package org.mobyapp.android.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mobyapp.android.data.ThumbnailImage
import org.mobyapp.android.data.ThumbnailInfo
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Makes thumbnails for videos from one of their frames. */
object VideoThumbnails {

    /** The longest side of a thumbnail, in pixels. */
    private const val MAX_SIDE = 1280

    /** How far into the video to take the frame, skipping a black first frame. */
    private const val FRAME_TIME_US = 1_000_000L

    /**
     * Encodes a frame of a video as a thumbnail the site accepts.
     *
     * @param baseName The video's filename without its extension.
     * @param maxSize The most bytes the site accepts for an image.
     * @return The thumbnail, or null if no frame could be read or the site
     *   accepts no format the device can encode.
     */
    suspend fun create(context: Context, uri: Uri, baseName: String, info: ThumbnailInfo, maxSize: Long): ThumbnailImage? =
        withContext(Dispatchers.Default) {
            val format = Format.entries.firstOrNull { it.extension in info.extensions } ?: return@withContext null
            val frame = readFrame(context, uri) ?: return@withContext null
            try {
                // Lower the quality until it fits, for sites with a small limit.
                for (quality in intArrayOf(85, 70, 50)) {
                    val bytes = ByteArrayOutputStream().also { frame.compress(format.compressFormat, quality, it) }.toByteArray()
                    if (bytes.size <= maxSize) {
                        return@withContext ThumbnailImage("$baseName.${format.extension}", format.mimeType, bytes)
                    }
                    if (format == Format.PNG) break
                }
                null
            } finally {
                frame.recycle()
            }
        }

    /** Reads a frame scaled to fit [MAX_SIDE], rotated as the video is shown. */
    private fun readFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            // Short videos use their midpoint.
            val timeUs = if (durationUs in 1 until FRAME_TIME_US * 2) durationUs / 2 else FRAME_TIME_US
            val option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(timeUs, option, MAX_SIDE, MAX_SIDE)
            } else {
                retriever.getFrameAtTime(timeUs, option)
            } ?: return null
            scaleToFit(frame)
        } catch (e: RuntimeException) {
            // Thrown for files the device can't decode.
            null
        } finally {
            retriever.release()
        }
    }

    private fun scaleToFit(frame: Bitmap): Bitmap {
        val longest = max(frame.width, frame.height)
        if (longest <= MAX_SIDE) return frame
        val scale = MAX_SIDE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(frame, (frame.width * scale).roundToInt(), (frame.height * scale).roundToInt(), true)
        frame.recycle()
        return scaled
    }

    /** Formats in order of preference. */
    private enum class Format(val extension: String, val mimeType: String, val compressFormat: Bitmap.CompressFormat) {
        JPG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
        JPEG("jpeg", "image/jpeg", Bitmap.CompressFormat.JPEG),
        @Suppress("DEPRECATION")
        WEBP("webp", "image/webp", Bitmap.CompressFormat.WEBP),
        PNG("png", "image/png", Bitmap.CompressFormat.PNG),
    }
}
