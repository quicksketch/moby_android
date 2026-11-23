package org.mobyapp.android.ui.login

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mobyapp.android.MobyApplication
import org.mobyapp.android.data.ApiException
import org.mobyapp.android.data.Discovery
import org.mobyapp.android.data.SetupIssue
import org.mobyapp.android.data.compatibility
import org.mobyapp.android.ui.userMessage

/**
 * Logs in to a site.
 *
 * @param siteId A saved site to log in to again, or null to add a site.
 */
class LoginViewModel(private val app: MobyApplication, siteId: String?) : ViewModel() {

    private val savedSite = app.sites.get(siteId)

    /** Whether this logs in to a site that was added before. */
    val isSavedSite = savedSite != null

    var siteUrl by mutableStateOf(savedSite?.url ?: "")
    var discovery by mutableStateOf<Discovery?>(null)
        private set

    /**
     * The site's setup issues. When any is an error, the site can't be used
     * and all issues are listed, so an administrator can fix them at once.
     * Otherwise these are warnings, shown with the login form.
     */
    var setupIssues by mutableStateOf<List<SetupIssue>>(emptyList())
        private set

    /** Credential values, keyed by the auth field names from discovery. */
    val credentials = mutableStateMapOf<String, String>()

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** The credential field the error applies to, if any. */
    var errorField by mutableStateOf<String?>(null)
        private set

    init {
        // The address of a saved site is known, so go straight to logging in.
        if (savedSite != null) connect()
    }

    /** Checks that the site runs Moby, and how to log in. */
    fun connect() {
        val url = normalizeUrl(siteUrl)
        if (url == null) {
            error = "Enter the site's address."
            return
        }
        siteUrl = url
        setupIssues = emptyList()
        run {
            val result = app.api(url).discover()
            setupIssues = result.setupIssues
            // A site whose module is too old for the app, or an app too old
            // for the site, can't be used.
            result.compatibility.blockingMessage?.let {
                error = it
                return@run
            }
            val blocked = result.auth == null || result.setupIssues.any { it.isError }
            when {
                !blocked -> discovery = result
                // Sites with older versions of the module don't list issues.
                result.setupIssues.isNotEmpty() -> Unit
                result.auth == null -> error = "This site does not allow logging in from the app."
                else -> error = "This site has not finished setting up posting from the app."
            }
        }
    }

    fun changeSite() {
        discovery = null
        setupIssues = emptyList()
        credentials.clear()
        error = null
        errorField = null
    }

    fun logIn(onLoggedIn: () -> Unit) {
        val discovery = discovery ?: return
        val auth = discovery.auth ?: return
        val missing = auth.fields.firstOrNull { it.required && credentials[it.name].isNullOrBlank() }
        if (missing != null) {
            error = "Enter your ${missing.label.lowercase()}."
            errorField = missing.name
            return
        }
        run {
            val values = auth.fields.associate { it.name to (credentials[it.name] ?: "") }
            val response = app.api(siteUrl).login(values, deviceName())
            val id = app.sites.saveLogin(siteUrl, discovery.site, response.token, response.user.name, discovery.api?.version ?: 1)
            app.openSite(id)
            app.discovery = discovery
            app.user = response.user
            onLoggedIn()
        }
    }

    private fun run(block: suspend () -> Unit) {
        error = null
        errorField = null
        loading = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = e.userMessage()
                errorField = (e as? ApiException)?.field
            } finally {
                loading = false
            }
        }
    }

    private fun deviceName(): String {
        val model = Build.MODEL ?: "Android"
        val manufacturer = Build.MANUFACTURER ?: ""
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }

    companion object {
        /** Adds "https://" if no scheme was entered, and removes any trailing slash. */
        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.contains(' ')) return null
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
