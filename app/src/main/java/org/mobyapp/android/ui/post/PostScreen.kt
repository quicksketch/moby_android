package org.mobyapp.android.ui.post

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobyapp.android.ui.MobyTopBar
import org.mobyapp.android.ui.POST_EDITED
import org.mobyapp.android.ui.appViewModel

/** Shows a post's real page, as rendered by the site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    postId: Long,
    savedStateHandle: SavedStateHandle,
    backLabel: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val vm = appViewModel(key = "post_$postId") { PostViewModel(it, postId) }
    val snackbar = remember { SnackbarHostState() }
    var pageLoading by remember { mutableStateOf(true) }
    var pageFailed by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Show the changes after the post is edited.
    val edited by savedStateHandle.getStateFlow(POST_EDITED, false).collectAsStateWithLifecycle()
    LaunchedEffect(edited) {
        if (edited) {
            savedStateHandle[POST_EDITED] = false
            vm.load()
        }
    }
    LaunchedEffect(vm.loggedOut) { if (vm.loggedOut) onLoggedOut() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            MobyTopBar(title = "Post", backLabel = backLabel, onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val post = vm.post
            val message = vm.loadError ?: if (pageFailed) "The page couldn't be loaded." else null
            if (post != null) {
                PostPage(
                    url = post.url,
                    version = vm.pageVersion,
                    headers = vm::pageHeaders,
                    onLoading = { loading, failed ->
                        pageLoading = loading
                        pageFailed = failed
                    },
                )
            }
            if (message != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("This post couldn't be shown.", style = MaterialTheme.typography.titleMedium)
                    Text(message, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                    Button(onClick = vm::load) { Text("Try again") }
                }
            } else if (post == null || pageLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            if (post != null && (post.permissions.edit || post.permissions.delete)) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("postToolbar"),
                ) {
                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (post.permissions.edit) {
                            TextButton(
                                onClick = onEdit,
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Edit post" }.testTag("edit"),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Edit", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        if (post.permissions.edit && post.permissions.delete) {
                            // Keeps the two apart.
                            VerticalDivider(Modifier.height(28.dp))
                        }
                        if (post.permissions.delete) {
                            TextButton(
                                onClick = { confirmingDelete = true },
                                enabled = !vm.deleting,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Delete post" }.testTag("delete"),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Delete", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this post?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    vm.delete(onDeleted)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * A web view showing a post's page. Only that page is shown here. Links on it
 * open in the user's browser, where they are logged in or not as usual.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PostPage(
    url: String,
    version: Int,
    headers: (Uri) -> Map<String, String>,
    onLoading: (loading: Boolean, failed: Boolean) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize().testTag("postPage"),
        factory = { context ->
            WebView(context).apply {
                // Compose gives views wrap_content layout params, and a WebView
                // with a wrap_content height makes CSS viewport units (vh) 0.
                // Pages that size things in vh, such as the photo carousel's
                // maximum height, would then draw them 0 pixels tall.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // Show the app's background until the page draws its own,
                // rather than a white flash.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // Always show the current version of the post.
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = PostPageClient(headers, onLoading)
            }
        },
        update = { view ->
            // Compose may call this at any time, so only load for a new version.
            if (view.tag != version) {
                view.tag = version
                // The site's page cache answers before tokens are checked. A
                // unique URL gets the page rendered for this user instead.
                val page = Uri.parse(url).buildUpon()
                    .appendQueryParameter("moby_view", System.currentTimeMillis().toString())
                    .build()
                view.loadUrl(page.toString(), headers(page))
            }
        },
        onRelease = { it.destroy() },
    )
}

private class PostPageClient(
    private val headers: (Uri) -> Map<String, String>,
    private val onLoading: (loading: Boolean, failed: Boolean) -> Unit,
) : WebViewClient() {
    private var failed = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        if (request.isRedirect) {
            // Redirects lose the request's headers, so send them again. This
            // does nothing for other hosts, which never get the token.
            val redirectHeaders = headers(request.url)
            if (redirectHeaders.isEmpty()) return false
            view.loadUrl(request.url.toString(), redirectHeaders)
            return true
        }
        // A link on the same page.
        val current = view.url?.let(Uri::parse)
        if (current != null && request.url.buildUpon().fragment(null).build() == current.buildUpon().fragment(null).build()) {
            return false
        }
        openExternally(view.context, request.url)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        failed = false
        onLoading(true, false)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onLoading(false, failed)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame) failed = true
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) failed = true
    }

    private fun openExternally(context: Context, url: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: ActivityNotFoundException) {
            // Nothing on the device can open this link.
        }
    }
}
