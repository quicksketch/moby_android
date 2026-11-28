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
import org.mobyapp.android.data.AuthField
import org.mobyapp.android.data.AuthInfo
import org.mobyapp.android.data.Discovery
import org.mobyapp.android.data.LoginResult
import org.mobyapp.android.data.LoginStep
import org.mobyapp.android.data.SetupIssue
import org.mobyapp.android.data.TokenResponse
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

    /** The mechanism whose form is shown. The site's default at first. */
    var mechanism by mutableStateOf<AuthInfo?>(null)
        private set

    /** The site's other mechanisms, listed under "Log in a different way". */
    val otherMechanisms: List<AuthInfo>
        get() = discovery?.authMechanisms.orEmpty().filter { it.mechanism != mechanism?.mechanism }

    /** Credential values, keyed by the auth field names from discovery. */
    val credentials = mutableStateMapOf<String, String>()

    /**
     * A later step of the login, such as for an emailed code, or null on the
     * mechanism's own form.
     */
    var step by mutableStateOf<LoginStep?>(null)
        private set

    /** The fields of the form shown: the step's, or the mechanism's. */
    val fields: List<AuthField>
        get() = step?.fields ?: mechanism?.fields.orEmpty()

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
            val blocked = result.authMechanisms.isEmpty() || result.setupIssues.any { it.isError }
            when {
                !blocked -> {
                    discovery = result
                    mechanism = result.authMechanisms.first()
                }
                // Sites with older versions of the module don't list issues.
                result.setupIssues.isNotEmpty() -> Unit
                result.authMechanisms.isEmpty() -> error = "This site does not allow logging in from the app."
                else -> error = "This site has not finished setting up posting from the app."
            }
        }
    }

    fun changeSite() {
        step = null
        discovery = null
        mechanism = null
        setupIssues = emptyList()
        credentials.clear()
        error = null
        errorField = null
    }

    /** Shows another mechanism's form, from "Log in a different way". */
    fun selectMechanism(selected: AuthInfo) {
        mechanism = selected
        step = null
        error = null
        errorField = null
    }

    fun logIn(onLoggedIn: () -> Unit) {
        val auth = mechanism ?: return
        val shown = fields
        val missing = shown.firstOrNull { it.required && credentials[it.name].isNullOrBlank() }
        if (missing != null) {
            error = missingMessage(missing)
            errorField = missing.name
            return
        }
        val current = step
        run(shown) {
            val values = shown.associate { it.name to (credentials[it.name]?.trim() ?: "") }
            when (val result = app.api(siteUrl).login(auth.mechanism, values, deviceName(), current?.requestId)) {
                is LoginResult.LoggedIn -> finish(result.response, onLoggedIn)
                is LoginResult.Step -> showStep(result.step)
            }
        }
    }

    /** Leaves the login's later steps, and shows the mechanism's form again. */
    fun startOver() {
        val current = step ?: return
        current.fields.forEach { credentials.remove(it.name) }
        // Passwords are typed again, as for any failed login.
        mechanism?.fields?.filter { it.type == "password" }?.forEach { credentials.remove(it.name) }
        step = null
        error = null
        errorField = null
    }

    private fun showStep(next: LoginStep) {
        // A field of a new step starts empty, even if an earlier form had one
        // with the same name.
        next.fields.forEach { credentials.remove(it.name) }
        step = next
    }

    private fun finish(response: TokenResponse, onLoggedIn: () -> Unit) {
        val discovery = discovery ?: return
        val id = app.sites.saveLogin(siteUrl, discovery.site, response.token, response.user.name, discovery.api?.version ?: 1)
        app.openSite(id)
        app.discovery = discovery
        app.user = response.user
        onLoggedIn()
    }

    /**
     * Runs a request, showing its error, if any.
     *
     * @param shown For a login form, its fields, so an error can mark one.
     */
    private fun run(shown: List<AuthField> = emptyList(), block: suspend () -> Unit) {
        error = null
        errorField = null
        loading = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                val api = e as? ApiException
                if (api?.restart == true) {
                    // The login can't go on, such as after too many wrong
                    // codes: back to the first form, with the site's message.
                    startOver()
                    error = e.userMessage()
                } else {
                    error = e.userMessage()
                    errorField = api?.field?.takeIf { field -> shown.isEmpty() || shown.any { it.name == field } }
                }
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
        /** The message for a required field left empty. */
        fun missingMessage(field: AuthField): String =
            // Labels can be questions, such as for security questions.
            if (field.label.endsWith("?")) "Answer \u201c${field.label}\u201d" else "Enter your ${field.label.lowercase()}."

        /** Adds "https://" if no scheme was entered, and removes any trailing slash. */
        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.contains(' ')) return null
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
