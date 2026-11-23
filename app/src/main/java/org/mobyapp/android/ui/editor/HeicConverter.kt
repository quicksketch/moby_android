package org.mobyapp.android.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Converts HEIC photos to JPEG before they are uploaded, as many browsers can't
 * display HEIC and servers usually can't make image styles from it.
 *
 * Bitmaps can't carry metadata, so the JPEG has none. That includes the photo's
 * GPS position. Its rotation is applied to the pixels.
 */
object HeicConverter {

    /** Full-size bitmaps are large, so photos are converted one at a time. */
    private val mutex = Mutex()

    private val QUALITIES = intArrayOf(90, 75, 60)

    /**
     * Saves a HEIC photo as a JPEG in the app's cache.
     *
     * The conversion always finishes once started, even if the upload is
     * cancelled, so the caller receives the file and can delete it.
     *
     * @param maxSize The most bytes the site accepts. Quality is lowered to fit.
     * @return The JPEG file, which the caller must delete.
     * @throws IllegalStateException With a message for the user, if the photo
     *   can't be converted.
     */
    suspend fun toJpeg(context: Context, uri: Uri, maxSize: Long): File = mutex.withLock {
        withContext(Dispatchers.Default + NonCancellable) {
            val bitmap = decode(context, uri)
            try {
                val directory = File(context.cacheDir, "converted").apply { mkdirs() }
                val file = File(directory, "${UUID.randomUUID()}.jpg")
                for (quality in QUALITIES) {
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                    if (file.length() <= maxSize) return@withContext file
                }
                val size = file.length()
                file.delete()
                error("This photo is ${DeviceMediaReader.formatSize(size)} as a JPEG. The site accepts up to ${DeviceMediaReader.formatSize(maxSize)}.")
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Decodes a photo as shown, rotated, in standard colors for the JPEG. */
    private fun decode(context: Context, uri: Uri): Bitmap {
        // Android 8 can't read HEIC.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) error(FAILED)
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // Hardware bitmaps can't be read back to be compressed.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
        } catch (e: java.io.IOException) {
            error(FAILED)
        } catch (e: OutOfMemoryError) {
            error(FAILED)
        }
    }

    private const val FAILED = "This HEIC photo could not be converted to JPEG on this device."
}
