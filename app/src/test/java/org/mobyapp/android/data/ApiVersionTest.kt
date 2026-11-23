package org.mobyapp.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiVersionTest {

    private val changes = listOf(
        ApiVersion.Change(2, "Tags", "Hides tags"),
        ApiVersion.Change(3, null, "Uses the old request"),
        ApiVersion.Change(4, "Post order", "Uses the default order"),
    )

    private fun of(siteVersion: Int?, siteMin: Int = 1, appVersion: Int = 4, minSite: Int = 1) =
        Compatibility.of(siteVersion?.let { ApiInfo(it, siteMin) }, appVersion, minSite, changes)

    @Test
    fun sameOrNewerSitesAreCurrent() {
        assertEquals(Compatibility.Current, of(4))
        assertEquals(Compatibility.Current, of(9))
    }

    @Test
    fun olderSitesListTheChangesUsersWouldNotice() {
        assertEquals(Compatibility.Behind(1, listOf("Tags", "Post order")), of(1))
        assertEquals(Compatibility.Behind(2, listOf("Post order")), of(2))
        assertEquals(Compatibility.Behind(3, listOf("Post order")), of(3))
        // Only an unnoticeable change missing: behind, with nothing to show.
        assertEquals(Compatibility.Behind(2, emptyList()), of(2, appVersion = 3))
    }

    @Test
    fun sitesFromBeforeVersioningAreVersionOne() {
        assertEquals(Compatibility.Behind(1, listOf("Tags", "Post order")), of(null))
    }

    @Test
    fun tooOldEitherWayIsUnsupported() {
        assertEquals(Compatibility.SiteTooOld, of(1, minSite = 2))
        assertEquals(Compatibility.AppTooOld, of(9, siteMin = 5))
        assertTrue(of(9, siteMin = 5).isUnsupported)
    }

    @Test
    fun parsesTheSitesVersions() {
        val discovery = JsonModels.discovery(
            org.json.JSONObject(
                """{"api_version":3,"api_min_version":2,"module":{"name":"moby","platform":"backdrop","version":null},
                "site":{"name":"S","url":"u"},
                "limits":{"max_upload_size":1,"max_media_per_post":1},
                "media":{"image":{"extensions":[],"max_size":1},"video":{"extensions":[],"max_size":1}}}""",
            ),
        )
        assertEquals(ApiInfo(3, 2), discovery.api)
        assertEquals("backdrop", discovery.module?.platform)
        assertTrue(discovery.supports(3))
        assertTrue(!discovery.supports(4))
    }
}
