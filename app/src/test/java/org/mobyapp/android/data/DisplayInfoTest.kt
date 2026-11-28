package org.mobyapp.android.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayInfoTest {

    private fun discovery(extra: String) = JsonModels.discovery(
        JSONObject(
            """{"api_version":1,"site":{"name":"S","url":"u"},
            "limits":{"max_upload_size":1,"max_media_per_post":1},
            "media":{"image":{"extensions":[],"max_size":1},"video":{"extensions":[],"max_size":1}}$extra}""",
        ),
    )

    @Test
    fun usesTheSitesOrder() {
        val info = discovery(""","display":{"order":["date","description","location","media"]}""").display
        assertEquals(listOf("date", "description", "location", "media"), info.parts)
    }

    @Test
    fun olderSitesGetTheDefaultOrder() {
        assertEquals(listOf("date", "media", "description", "location"), discovery("").display.parts)
    }

    @Test
    fun ignoresUnknownPartsAndAddsMissingOnes() {
        val info = DisplayInfo(listOf("description", "tags", "media"))
        assertEquals(listOf("description", "media", "date", "location"), info.parts)
    }

    @Test
    fun olderSitesGetDefaultsForEverythingNewer() {
        val d = discovery("")
        assertEquals(emptyList<SetupIssue>(), d.setupIssues)
        assertEquals(2000, d.limits.maxAltLength)
        assertEquals(50, d.limits.maxTagsPerPost)
        assertEquals(255, d.limits.maxTagLength)
        assertEquals(false, d.features.tags)
        assertEquals(false, d.description.html)
        assertEquals(emptyList<String>(), d.description.tags)
        assertEquals(null, d.site.favicon)
    }
}
