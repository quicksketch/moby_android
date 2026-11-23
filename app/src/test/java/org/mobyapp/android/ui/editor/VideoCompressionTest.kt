package org.mobyapp.android.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mobyapp.android.ui.editor.VideoCompression.Decision
import org.mobyapp.android.ui.editor.VideoCompression.Info
import org.mobyapp.android.ui.editor.VideoCompression.Tier

private const val MB = 1024L * 1024

class VideoCompressionTest {

    private val uhd = Info(60.0, 3840, 2160)

    @Test
    fun uploadsVideosWithinTheLimitAndOfferSize() {
        assertEquals(Decision.UploadOriginal, VideoCompression.decide(50 * MB, uhd, 100 * MB))
        assertEquals(Decision.UploadOriginal, VideoCompression.decide(200 * MB, uhd, 1024 * MB))
    }

    @Test
    fun compressesTooLargeVideosTo1080pWhenTheyFit() {
        val decision = VideoCompression.decide(400 * MB, uhd, 100 * MB) as Decision.Compress
        assertEquals(Tier.P1080, decision.plan.tier)
        assertEquals(1920 to 1080, decision.plan.width to decision.plan.height)
        assertTrue(decision.plan.estimatedSize <= 100 * MB)
    }

    @Test
    fun usesLessThanTheFullBitrateToFit() {
        val plan = VideoCompression.plan(Info(180.0, 3840, 2160), 100 * MB)!!
        assertEquals(Tier.P1080, plan.tier)
        assertTrue(plan.videoBitrate in Tier.P1080.minBitrate until Tier.P1080.bitrate)
        assertTrue(plan.estimatedSize <= 100 * MB)
    }

    @Test
    fun fallsBackTo720pAndThenRejects() {
        assertEquals(Tier.P720, VideoCompression.plan(Info(300.0, 3840, 2160), 100 * MB)!!.tier)
        assertEquals(Decision.Reject(triedCompressing = true), VideoCompression.decide(2000 * MB, Info(900.0, 3840, 2160), 100 * MB))
    }

    @Test
    fun rejectsTooLargeVideosThatCantBeRead() {
        assertEquals(Decision.Reject(triedCompressing = false), VideoCompression.decide(400 * MB, null, 100 * MB))
    }

    @Test
    fun offersCompressionForLargeVideosOnlyWhenItHelps() {
        val offer = VideoCompression.decide(800 * MB, uhd, 2048 * MB) as Decision.Offer
        assertEquals(Tier.P1080, offer.plan.tier)
        // At 8 Mbps, this is larger than the original.
        assertEquals(Decision.UploadOriginal, VideoCompression.decide(300 * MB, Info(600.0, 1920, 1080), 2048 * MB))
        assertEquals(Decision.UploadOriginal, VideoCompression.decide(800 * MB, null, 2048 * MB))
    }

    @Test
    fun neverScalesUpAndHandlesPortraitAndOddSizes() {
        val small = VideoCompression.plan(Info(60.0, 1280, 720), 20 * MB)!!
        assertEquals(Tier.P720, small.tier)
        assertEquals(false, small.scale)
        assertEquals(1080 to 1920, VideoCompression.outputSize(2160, 3840, 1080))
        assertEquals(1078 to 1438, VideoCompression.outputSize(1079, 1439, 1080))
    }

    @Test
    fun allowsForEarlierOvershoot() {
        val first = VideoCompression.plan(uhd, 60 * MB)!!
        val second = VideoCompression.plan(uhd, 60 * MB, overshoot = 1.5)!!
        assertTrue(second.videoBitrate < first.videoBitrate)
        assertNull(VideoCompression.plan(uhd, 60 * MB, overshoot = 10.0))
    }
}
