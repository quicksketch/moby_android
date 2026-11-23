package org.mobyapp.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mobyapp.android.ui.login.LoginViewModel

class LoginUrlTest {

    @Test
    fun normalizesSiteAddresses() {
        assertEquals("https://example.com", LoginViewModel.normalizeUrl(" example.com/ "))
        assertEquals("http://quicksketch.ddev.site", LoginViewModel.normalizeUrl("http://quicksketch.ddev.site"))
        assertNull(LoginViewModel.normalizeUrl(""))
        assertNull(LoginViewModel.normalizeUrl("not a url"))
    }
}
