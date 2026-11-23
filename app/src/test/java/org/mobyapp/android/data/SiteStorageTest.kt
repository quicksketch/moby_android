package org.mobyapp.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteStorageTest {

    @Test
    fun readsSitesSavedByEarlierVersions() {
        // As kotlinx-serialization saved them, with explicit nulls.
        val saved = """[{"id":"a","url":"https://example.com","name":"Example","userName":"nate","token":"enc",
            "favicon":null,"customTitle":null},{"id":"b","url":"http://other.test"}]"""
        val sites = SiteStore.decode(saved)
        assertEquals(2, sites.size)
        assertEquals("Example", sites[0].name)
        assertEquals(null, sites[0].favicon)
        assertEquals(null, sites[1].token)
    }

    @Test
    fun roundTrips() {
        val sites = listOf(SavedSite("a", "https://example.com", "Example", "nate", "enc", "https://example.com/f.ico", "Mine"))
        assertEquals(sites, SiteStore.decode(SiteStore.encode(sites)))
    }
}
