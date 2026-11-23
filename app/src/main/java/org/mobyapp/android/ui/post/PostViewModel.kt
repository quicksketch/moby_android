package org.mobyapp.android.ui.post

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mobyapp.android.MobyApplication
import org.mobyapp.android.data.ApiException
import org.mobyapp.android.data.Post
import org.mobyapp.android.ui.isUnauthorized
import org.mobyapp.android.ui.userMessage

/** Shows one post, with options to edit and delete it. */
class PostViewModel(private val app: MobyApplication, private val postId: Long) : ViewModel() {

    var post by mutableStateOf<Post?>(null)
        private set
    /** Why the post couldn't be loaded, or null. */
    var loadError by mutableStateOf<String?>(null)
        private set
    /** Why the last action failed, or null. */
    var error by mutableStateOf<String?>(null)
    var deleting by mutableStateOf(false)
        private set
    /** Set when the token was rejected, so the user must log in again. */
    var loggedOut by mutableStateOf(false)
        private set
    /** Incremented whenever the page should be loaded again. */
    var pageVersion by mutableIntStateOf(0)
        private set

    init {
        load()
    }

    /** Loads the post, then shows its page. */
    fun load() {
        loadError = null
        viewModelScope.launch {
            try {
                post = app.api().getPost(postId)
                pageVersion++
            } catch (e: Exception) {
                if (e.isUnauthorized) logOut() else loadError = e.userMessage()
            }
        }
    }

    /**
     * The headers to load a page with. The token is only sent to the site
     * itself, so a post's URL can never pass it to another host.
     */
    fun pageHeaders(url: Uri): Map<String, String> {
        val current = app.currentSite
        val token = app.sites.token(current?.id)
        val site = current?.url?.let(Uri::parse)
        val sameSite = site != null && url.scheme == site.scheme && url.host == site.host && url.port == site.port
        return if (token != null && sameSite) mapOf("Authorization" to "Bearer $token") else emptyMap()
    }

    fun delete(onDeleted: () -> Unit) {
        error = null
        deleting = true
        viewModelScope.launch {
            try {
                try {
                    app.api().deletePost(postId)
                } catch (e: ApiException) {
                    // It's already gone, such as when retrying after a timeout.
                    if (e.status != 404) throw e
                }
                onDeleted()
            } catch (e: Exception) {
                if (e.isUnauthorized) logOut() else error = e.userMessage()
            } finally {
                deleting = false
            }
        }
    }

    private fun logOut() {
        app.logOut()
        loggedOut = true
    }
}
