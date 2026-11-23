package org.mobyapp.android.ui.editor

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Decides whether and how to compress a video, from estimates alone. */
object VideoCompression {

    /** Videos over this size may be compressed by choice, even if they'd fit. */
    const val OFFER_SIZE = 200L * 1024 * 1024

    /** Audio bitrate to allow for. Audio is often copied, so it isn't exact. */
    private const val AUDIO_BITRATE = 192_000L

    /** Keeps estimates below the limit, as encoders don't hit bitrates exactly. */
    private const val SAFETY_MARGIN = 0.93

    /** MP4 structure, as a share of the media data. */
    private const val CONTAINER_OVERHEAD = 1.02

    /** An offer must save at least this much to be worth the lost quality. */
    private const val WORTHWHILE_RATIO = 0.8

    /**
     * A target resolution.
     *
     * @param shortSide The length of the video's shorter side, for portrait too.
     * @param bitrate The H.264 bitrate to use when the site's limit allows.
     * @param minBitrate The lowest H.264 bitrate that still looks acceptable.
     */
    enum class Tier(val label: String, val shortSide: Int, val bitrate: Long, val minBitrate: Long) {
        P1080("1080p", 1080, 8_000_000, 3_000_000),
        P720("720p", 720, 5_000_000, 1_500_000),
    }

    /** A video's properties, with its width and height as displayed. */
    data class Info(val durationSeconds: Double, val width: Int, val height: Int)

    /**
     * How to compress a video.
     *
     * @param width The output width. Videos are never scaled up.
     * @param height The output height.
     * @param scale Whether the output is smaller than the original.
     */
    data class Plan(
        val tier: Tier,
        val width: Int,
        val height: Int,
        val scale: Boolean,
        val videoBitrate: Long,
        val estimatedSize: Long,
    )

    sealed interface Decision {
        data object UploadOriginal : Decision
        data class Compress(val plan: Plan) : Decision
        data class Offer(val originalSize: Long, val plan: Plan) : Decision
        /** @param triedCompressing Whether no compression would be enough. */
        data class Reject(val triedCompressing: Boolean) : Decision
    }

    /**
     * Decides what to do with a video.
     *
     * @param size The video's size in bytes.
     * @param info The video's properties, or null if it can't be compressed.
     * @param maxSize The most bytes the site accepts for a video.
     */
    fun decide(size: Long, info: Info?, maxSize: Long): Decision {
        if (size > maxSize) {
            if (info == null) return Decision.Reject(triedCompressing = false)
            return plan(info, maxSize)?.let { Decision.Compress(it) } ?: Decision.Reject(triedCompressing = true)
        }
        if (size <= OFFER_SIZE || info == null) return Decision.UploadOriginal
        val plan = plan(info, maxSize)
        return if (plan != null && plan.estimatedSize < size * WORTHWHILE_RATIO) Decision.Offer(size, plan) else Decision.UploadOriginal
    }

    /**
     * Finds the largest resolution that can fit within a size.
     *
     * @param overshoot How much larger than estimated earlier results were.
     * @return The plan, or null if even the smallest resolution wouldn't fit.
     */
    fun plan(info: Info, maxSize: Long, overshoot: Double = 1.0): Plan? {
        if (info.durationSeconds <= 0 || info.width <= 0 || info.height <= 0) return null
        val available = maxSize * SAFETY_MARGIN / overshoot / CONTAINER_OVERHEAD * 8 / info.durationSeconds - AUDIO_BITRATE
        val sourceShortSide = min(info.width, info.height)
        // A video smaller than a tier's next one only needs that next tier.
        val tiers = Tier.entries.filter { it == Tier.entries.last() || sourceShortSide > Tier.entries[it.ordinal + 1].shortSide }
        for (tier in tiers) {
            if (available < tier.minBitrate) continue
            val bitrate = min(tier.bitrate, available.toLong())
            val (width, height) = outputSize(info.width, info.height, tier.shortSide)
            val estimate = (bitrate + AUDIO_BITRATE) * info.durationSeconds / 8 * CONTAINER_OVERHEAD * overshoot
            return Plan(tier, width, height, width != info.width || height != info.height, bitrate, estimate.roundToLong())
        }
        return null
    }

    /** Scales dimensions down to a short side, keeping them even for encoders. */
    internal fun outputSize(width: Int, height: Int, shortSide: Int): Pair<Int, Int> {
        val shorter = min(width, height)
        if (shorter <= shortSide) return even(width) to even(height)
        val scale = shortSide.toDouble() / shorter
        return even((width * scale).roundToInt()) to even((height * scale).roundToInt())
    }

    private fun even(value: Int) = value - value % 2
}
