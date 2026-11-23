package org.mobyapp.android.ui.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads videos' properties and compresses them on the device. */
object VideoCompressor {

    /** Devices have few video encoders, so videos are compressed one at a time. */
    private val mutex = Mutex()

    private const val PROGRESS_INTERVAL_MS = 300L
    private const val TAG = "VideoCompressor"

    /** Reads a video's properties, or returns null if they aren't available. */
    suspend fun readInfo(context: Context, uri: Uri, size: Long): VideoCompression.Info? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun read(key: Int) = retriever.extractMetadata(key)?.toIntOrNull()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val width = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (durationMs == null || width == null || height == null || size < 0) return@withContext null
            val rotation = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            val sideways = rotation % 180 != 0
            VideoCompression.Info(durationMs / 1000.0, if (sideways) height else width, if (sideways) width else height)
        } catch (e: RuntimeException) {
            // Thrown for files the device can't read.
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Compresses a video to an H.264 and AAC MP4 in the app's cache.
     *
     * @return The file, which the caller must delete.
     * @throws IllegalStateException With a message for the user, on failure.
     */
    @OptIn(UnstableApi::class)
    suspend fun compress(context: Context, uri: Uri, plan: VideoCompression.Plan, onProgress: (Float) -> Unit): File = mutex.withLock {
        val directory = File(context.cacheDir, "converted").apply { mkdirs() }
        if (directory.usableSpace < plan.estimatedSize * 1.1) {
            error("There isn't enough free storage to compress this video.")
        }
        val file = File(directory, "${UUID.randomUUID()}.mp4")
        // Transformer must be used from the thread that created it.
        withContext(Dispatchers.Main) {
            val presentation = if (plan.scale) {
                listOf(Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT))
            } else {
                emptyList()
            }
            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(emptyList(), presentation))
                .build()
            val composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build())
                // H.264 in browsers is SDR.
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()
            // Requesting a bitrate also makes Transformer re-encode videos
            // already in H.264 at their original size, instead of copying them.
            val encoders = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(plan.videoBitrate.toInt()).build())
                .build()

            // The emulator's hardware decoder fails when given a video's first
            // buffers, so it uses its software decoder. Devices don't have this one.
            val decoders = DefaultDecoderFactory.Builder(context)
                .setMediaCodecSelector { mimeType, secure, tunneling ->
                    MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling).filterNot { "goldfish" in it.name }
                }
                .build()

            lateinit var transformer: Transformer
            val poller = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    delay(PROGRESS_INTERVAL_MS)
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                }
            }
            try {
                suspendCancellableCoroutine { continuation ->
                    transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoders)
                        .setAssetLoaderFactory(DefaultAssetLoaderFactory(context, decoders, Clock.DEFAULT, DefaultMediaSourceFactory(context), DataSourceBitmapLoader.Builder(context).build()))
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                continuation.resume(file)
                            }
    
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                Log.w(TAG, "Compression failed", exportException)
                                file.delete()
                                continuation.resumeWithException(IllegalStateException("This video couldn't be compressed on this device.", exportException))
                            }
                        })
                        .build()
                    continuation.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post {
                            transformer.cancel()
                            file.delete()
                        }
                    }
                    transformer.start(composition, file.absolutePath)
                }
            } finally {
                poller.cancel()
            }
        }
    }
}
