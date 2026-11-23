package org.mobyapp.android.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.mobyapp.android.MobyApplication
import org.mobyapp.android.ui.editor.PostEditorScreen
import org.mobyapp.android.ui.feed.FeedScreen
import org.mobyapp.android.ui.login.LoginScreen
import org.mobyapp.android.ui.post.PostScreen
import org.mobyapp.android.ui.settings.SettingsScreen
import org.mobyapp.android.ui.sites.SitesScreen

private const val SITES = "sites"
private const val LOGIN = "login"
private const val SITE_ID = "siteId"
private const val FEED = "feed"
private const val POST = "post"
private const val EDITOR = "editor"
private const val SETTINGS = "settings"
private const val POST_ID = "postId"
private const val SHARED = "shared"

/** Set on the feed's back stack entry when a new post was saved. */
const val POST_SAVED = "postSaved"

/**
 * Set when a post was edited: on the post screen's back stack entry to true, and
 * on the feed's to the post's ID.
 */
const val POST_EDITED = "postEdited"

/** Set on the feed's back stack entry to the ID of a post that was deleted. */
const val POST_DELETED = "postDeleted"

/**
 * The name of a screen, for the back button that returns to it, such as
 * "← Sites".
 */
internal fun backLabel(route: String?): String = when {
    route == null -> "Back"
    route == SITES -> "Sites"
    // The feed, which is titled with the site's name. "Site" keeps it short.
    route == FEED -> "Site"
    route.startsWith(POST) -> "Post"
    route.startsWith(SETTINGS) -> "Settings"
    route.startsWith(LOGIN) -> "Log in"
    else -> "Back"
}

/**
 * The app opens on the list of sites. Opening a site shows its feed, and the
 * screens after it work with that site.
 *
 * @param sharedMedia Media shared from another app, opened in a new post once
 *   a site is chosen and logged in to. With only one site logged in, that site
 *   is chosen.
 * @param onSharedMediaOpened Called when a new post has been opened for it, or
 *   sharing was cancelled.
 */
@Composable
fun MobyNavHost(sharedMedia: List<Uri>, onSharedMediaOpened: () -> Unit) {
    val app = LocalContext.current.applicationContext as MobyApplication
    val navController = rememberNavController()
    val share by rememberUpdatedState(sharedMedia)

    fun openShared() {
        navController.navigate("$EDITOR?$SHARED=${SharedMedia.encode(share)}")
        onSharedMediaOpened()
    }

    /** Opens a logged-in site's feed, and a new post for any shared media. */
    fun openFeed(siteId: String) {
        app.openSite(siteId)
        navController.navigate(FEED) { popUpTo(SITES) }
        if (share.isNotEmpty()) openShared()
    }

    fun backToSites() {
        navController.popBackStack(SITES, inclusive = false)
    }

    LaunchedEffect(sharedMedia) {
        if (sharedMedia.isEmpty()) return@LaunchedEffect
        val only = app.sites.sites.filter { it.isLoggedIn }.singleOrNull()
        val inSite = only != null && only.id == app.sites.currentId &&
            runCatching { navController.getBackStackEntry(FEED) }.isSuccess
        when {
            // Keep any post being edited, under the new one.
            inSite -> openShared()
            only != null -> openFeed(only.id)
            // The sites list asks which site to post to.
            else -> backToSites()
        }
    }

    /** The back label for the screen now on top, kept while it is shown. */
    @Composable
    fun backLabelFor(entry: androidx.navigation.NavBackStackEntry): String =
        androidx.compose.runtime.remember(entry) {
            backLabel(navController.previousBackStackEntry?.destination?.route)
        }

    NavHost(navController, startDestination = SITES) {
        composable(SITES) {
            SitesScreen(
                sharing = sharedMedia.isNotEmpty(),
                onOpenSite = { site ->
                    if (site.isLoggedIn) openFeed(site.id) else navController.navigate("$LOGIN?$SITE_ID=${site.id}")
                },
                onOpenSettings = { site ->
                    app.openSite(site.id)
                    navController.navigate(SETTINGS)
                },
                onAddSite = { navController.navigate(LOGIN) },
                onCancelShare = onSharedMediaOpened,
            )
        }
        composable(
            "$LOGIN?$SITE_ID={$SITE_ID}",
            arguments = listOf(navArgument(SITE_ID) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            LoginScreen(
                siteId = entry.arguments?.getString(SITE_ID),
                backLabel = backLabelFor(entry),
                onBack = { navController.popBackStack() },
                onLoggedIn = { app.sites.currentId?.let(::openFeed) },
            )
        }
        composable(FEED) { entry ->
            FeedScreen(
                savedStateHandle = entry.savedStateHandle,
                backLabel = backLabelFor(entry),
                onBack = ::backToSites,
                onNewPost = { navController.navigate(EDITOR) },
                onOpenPost = { id -> navController.navigate("$POST/$id") },
                onOpenSettings = { navController.navigate(SETTINGS) },
                onLoggedOut = ::backToSites,
            )
        }
        composable(SETTINGS) { entry ->
            SettingsScreen(backLabel = backLabelFor(entry), onBack = { navController.popBackStack() })
        }
        composable(
            "$POST/{$POST_ID}",
            arguments = listOf(navArgument(POST_ID) { type = NavType.LongType }),
        ) { entry ->
            val postId = requireNotNull(entry.arguments).getLong(POST_ID)
            PostScreen(
                postId = postId,
                savedStateHandle = entry.savedStateHandle,
                backLabel = backLabelFor(entry),
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("$EDITOR?$POST_ID=$postId") },
                onDeleted = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(POST_DELETED, postId)
                    navController.popBackStack()
                },
                onLoggedOut = ::backToSites,
            )
        }
        composable(
            "$EDITOR?$POST_ID={$POST_ID}&$SHARED={$SHARED}",
            arguments = listOf(
                navArgument(POST_ID) { type = NavType.LongType; defaultValue = -1L },
                navArgument(SHARED) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val postId = entry.arguments?.getLong(POST_ID)?.takeIf { it > 0 }
            PostEditorScreen(
                postId = postId,
                backLabel = backLabelFor(entry),
                sharedMedia = SharedMedia.decode(entry.arguments?.getString(SHARED)),
                onBack = { navController.popBackStack() },
                onSaved = {
                    if (postId == null) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(POST_SAVED, true)
                    } else {
                        navController.previousBackStackEntry?.savedStateHandle?.set(POST_EDITED, true)
                        navController.getBackStackEntry(FEED).savedStateHandle[POST_EDITED] = postId
                    }
                    navController.popBackStack()
                },
            )
        }
    }
}
