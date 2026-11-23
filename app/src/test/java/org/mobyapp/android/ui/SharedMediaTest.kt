package org.mobyapp.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaTest {

    @Test
    fun onlyAllowsContentUris() {
        assertTrue(SharedMedia.isAllowedScheme("content"))
        assertTrue(SharedMedia.isAllowedScheme("CONTENT"))
        assertFalse(SharedMedia.isAllowedScheme("file"))
        assertFalse(SharedMedia.isAllowedScheme(null))
    }
}
