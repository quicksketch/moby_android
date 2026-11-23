package org.mobyapp.android.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMediaReaderTest {

    @Test
    fun detectsHeicByMimeTypeOrFilename() {
        assertTrue(DeviceMediaReader.isHeic("image/heic", "a.jpg"))
        assertTrue(DeviceMediaReader.isHeic("IMAGE/HEIF", null))
        assertTrue(DeviceMediaReader.isHeic("image/heic-sequence", null))
        assertTrue(DeviceMediaReader.isHeic("image/*", "IMG_1.HEIC"))
        assertFalse(DeviceMediaReader.isHeic("image/jpeg", "a.jpg"))
        assertFalse(DeviceMediaReader.isHeic("image/jpeg", null))
    }

    @Test
    fun choosesAnExtensionTheSiteAccepts() {
        assertEquals("jpg", DeviceMediaReader.jpegExtension(listOf("png", "jpeg", "jpg")))
        assertEquals("jpeg", DeviceMediaReader.jpegExtension(listOf("jpeg")))
        assertNull(DeviceMediaReader.jpegExtension(listOf("png", "heic")))
    }
}
