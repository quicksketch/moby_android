package org.mobyapp.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import org.mobyapp.android.data.AppSettings
import org.mobyapp.android.data.Discovery
import org.mobyapp.android.data.HttpImageFetcher
import org.mobyapp.android.data.HttpSettings
import org.mobyapp.android.data.MobyApi
import org.mobyapp.android.data.SavedSite
import org.mobyapp.android.data.SiteStore
import org.mobyapp.android.data.User

/** Holds the objects shared by all screens. */
class MobyApplication : Application(), SingletonImageLoader.Factory {

    /** Timeouts for the API and for loading images. */
    val http = HttpSettings()

    val sites: SiteStore by lazy { SiteStore(this) }

    private val siteSettings = mutableMapOf<String, AppSettings>()

    /** The open site, or null if none is open. */
    val currentSite: SavedSite? get() = sites.get(sites.currentId)

    private fun requireSite(): SavedSite = checkNotNull(currentSite) { "No site is open." }

    /** The open site's settings. */
    val settings: AppSettings get() = settingsFor(requireSite().id)

    fun settingsFor(siteId: String): AppSettings = siteSettings.getOrPut(siteId) { AppSettings(this, siteId) }

    /** The open site's discovery document, once loaded. */
    var discovery: Discovery? = null

    /** The user logged in to the open site, once loaded. */
    var user: User? = null

    /** Opens a site, so the other screens work with it. */
    fun openSite(id: String) {
        if (sites.currentId != id) {
            discovery = null
            user = null
            sites.currentId = id
        }
    }

    /** An API client for the open site. */
    fun api(): MobyApi {
        val id = requireSite().id
        return MobyApi(requireSite().url, http) { sites.token(id) }
    }

    /** An API client for a site that isn't open, or not saved yet. */
    fun api(siteUrl: String, siteId: String? = null): MobyApi = MobyApi(siteUrl, http) { sites.token(siteId) }

    /** Loads the discovery document if it has not been loaded yet. */
    suspend fun requireDiscovery(): Discovery = discovery ?: api().discover().also {
        discovery = it
        // Keep the name and favicon in the list of sites up to date.
        sites.currentId?.let { id -> sites.updateInfo(id, it) }
    }

    /** Loads the logged-in user if they have not been loaded yet. */
    suspend fun requireUser(): User = user ?: api().me().also { user = it }

    /** Logs out of the open site locally, keeping it in the list of sites. */
    fun logOut() {
        currentSite?.let { sites.logOut(it.id) }
        discovery = null
        user = null
    }

    /**
     * Logs out of a site, revoking its token on the site. It is logged out
     * locally even if that fails.
     */
    suspend fun logOut(site: SavedSite) {
        runCatching { api(site.url, site.id).logout() }
        sites.logOut(site.id)
        if (site.id == sites.currentId) {
            discovery = null
            user = null
        }
    }

    /** Removes a site, logging out of it first. */
    suspend fun removeSite(site: SavedSite) {
        if (site.isLoggedIn) logOut(site)
        sites.remove(site.id)
        siteSettings.remove(site.id)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(HttpImageFetcher.Factory(http))
                add(VideoFrameDecoder.Factory())
                // Site favicons can be SVG.
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
