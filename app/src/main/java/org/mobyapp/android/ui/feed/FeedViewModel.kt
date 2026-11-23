package org.mobyapp.android.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mobyapp.android.MobyApplication
import org.mobyapp.android.data.ApiVersion
import org.mobyapp.android.data.Compatibility
import org.mobyapp.android.data.DisplayInfo
import org.mobyapp.android.data.Post
import org.mobyapp.android.data.compatibility
import org.mobyapp.android.ui.isUnauthorized
import org.mobyapp.android.ui.userMessage

class FeedViewModel(private val app: MobyApplication) : ViewModel() {

    val posts = mutableStateListOf<Post>()
    /** The site's title, which updates when the user changes it. */
    val siteName: String? get() = app.currentSite?.displayName
    /** Whether descriptions are HTML, from the site's discovery document. */
    var descriptionsAreHtml by mutableStateOf(false)
        private set

    /**
     * What the site's module lacks that the app could use, for the update
     * notice, or null when there's nothing to show or it was dismissed.
     */
    var updateNotice by mutableStateOf<List<String>?>(null)
        private set
    private var updateNoticeKey: String? = null

    /** The order the site shows a post's parts in, which the cards follow. */
    var postOrder by mutableStateOf(DisplayInfo.DEFAULT_ORDER)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    /** Set when the token was rejected, so the user must log in again. */
    var loggedOut by mutableStateOf(false)
        private set

    /** Incremented when the feed should scroll to the top, after a post is saved. */
    var scrollToTop by mutableStateOf(0)
        private set

    private var page = 0
    /** Deleted posts, so a response already on its way can't add them back. */
    private val deletedIds = mutableSetOf<Long>()
    /** Whether a post was deleted while a page was loading. */
    private var reloadPage = false
    private var scrollAfterLoad = false

    init {
        refresh()
    }

    /**
     * Reloads the first page.
     *
     * @param showTop Whether to scroll to the top afterwards, so a new post
     *   is visible.
     */
    fun refresh(showTop: Boolean = false) {
        if (showTop) scrollAfterLoad = true
        if (refreshing) return
        refreshing = true
        load(page = 0)
    }

    fun loadMore() {
        if (loadingMore || refreshing || !hasMore) return
        loadingMore = true
        load(page = page + 1)
    }

    private fun load(page: Int) {
        error = null
        viewModelScope.launch {
            try {
                val discovery = app.requireDiscovery()
                descriptionsAreHtml = discovery.description.html
                postOrder = discovery.display.parts
                showUpdateNotice(discovery.compatibility)
                val result = app.api().listPosts(page = page, limit = PAGE_SIZE)
                if (page == 0) posts.clear()
                // Skip posts already shown, in case new posts shifted the pages.
                val shown = posts.map { it.id }.toSet()
                posts.addAll(result.posts.filter { it.id !in shown && it.id !in deletedIds })
                this@FeedViewModel.page = page
                hasMore = (page + 1) * result.limit < result.total
                if (page == 0 && scrollAfterLoad) {
                    scrollAfterLoad = false
                    scrollToTop++
                }
            } catch (e: Exception) {
                if (e.isUnauthorized) {
                    app.logOut()
                    loggedOut = true
                } else {
                    error = e.userMessage()
                }
            } finally {
                refreshing = false
                loadingMore = false
                if (reloadPage) {
                    reloadPage = false
                    reloadLastPage()
                }
            }
        }
    }

    /** Shows the changes to an edited post, keeping its place in the feed. */
    fun reloadPost(id: Long) {
        viewModelScope.launch {
            runCatching { app.api().getPost(id) }.onSuccess { post ->
                val index = posts.indexOfFirst { it.id == id }
                if (index >= 0) posts[index] = post
            }
        }
    }

    /**
     * Removes a deleted post, keeping the rest of the feed in place.
     *
     * Later posts each move up one place on the site, so the first post of the
     * next page is now on the last page loaded. That page is loaded again, so
     * "Load more" doesn't skip it.
     */
    fun removeDeleted(id: Long) {
        deletedIds.add(id)
        posts.removeAll { it.id == id }
        if (loadingMore || refreshing) {
            reloadPage = true
        } else {
            reloadLastPage()
        }
    }

    private fun reloadLastPage() {
        if (!hasMore) return
        loadingMore = true
        load(page = page)
    }

    /** Shows the update notice for a site that's behind, unless dismissed. */
    private fun showUpdateNotice(compatibility: Compatibility) {
        val behind = compatibility as? Compatibility.Behind
        if (behind == null || behind.missing.isEmpty()) {
            updateNotice = null
            return
        }
        // Dismissing lasts until the site or the app changes version.
        val key = "${behind.siteVersion}:${ApiVersion.CURRENT}"
        updateNoticeKey = key
        updateNotice = behind.missing.takeIf { app.settings.dismissedUpdateNotice != key }
    }

    fun dismissUpdateNotice() {
        app.settings.dismissedUpdateNotice = updateNoticeKey
        updateNotice = null
    }

    fun logOut() {
        val site = app.currentSite ?: return
        viewModelScope.launch {
            app.logOut(site)
            loggedOut = true
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
