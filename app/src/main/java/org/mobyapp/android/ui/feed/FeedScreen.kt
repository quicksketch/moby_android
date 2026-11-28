package org.mobyapp.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.mobyapp.android.data.DescriptionInfo
import org.mobyapp.android.data.Media
import org.mobyapp.android.data.Post
import org.mobyapp.android.ui.FormattedText
import org.mobyapp.android.ui.MobyTopBar
import org.mobyapp.android.ui.POST_DELETED
import org.mobyapp.android.ui.POST_EDITED
import org.mobyapp.android.ui.POST_SAVED
import org.mobyapp.android.ui.appViewModel
import org.mobyapp.android.ui.theme.MobyFloatingButton
import org.mobyapp.android.util.IsoDates

private const val NO_POST = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    savedStateHandle: SavedStateHandle,
    backLabel: String,
    onBack: () -> Unit,
    onNewPost: () -> Unit,
    onOpenPost: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val vm = appViewModel { FeedViewModel(it) }
    val snackbar = remember { SnackbarHostState() }

    // Reload after a post is created.
    val saved by savedStateHandle.getStateFlow(POST_SAVED, false).collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            savedStateHandle[POST_SAVED] = false
            vm.refresh(showTop = true)
        }
    }
    // Remove a post deleted in the editor, staying at the same place in the feed.
    val deleted by savedStateHandle.getStateFlow(POST_DELETED, NO_POST).collectAsStateWithLifecycle()
    LaunchedEffect(deleted) {
        if (deleted != NO_POST) {
            savedStateHandle[POST_DELETED] = NO_POST
            vm.removeDeleted(deleted)
        }
    }
    // Show the changes to an edited post.
    val edited by savedStateHandle.getStateFlow(POST_EDITED, NO_POST).collectAsStateWithLifecycle()
    LaunchedEffect(edited) {
        if (edited != NO_POST) {
            savedStateHandle[POST_EDITED] = NO_POST
            vm.reloadPost(edited)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.scrollToTop) { if (vm.scrollToTop > 0) listState.scrollToItem(0) }
    LaunchedEffect(vm.loggedOut) { if (vm.loggedOut) onLoggedOut() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it) } }
    var confirmLogOut by rememberSaveable { mutableStateOf(false) }

    if (confirmLogOut) {
        AlertDialog(
            onDismissRequest = { confirmLogOut = false },
            title = { Text("Log out of ${vm.siteName ?: "this site"}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogOut = false
                        vm.logOut()
                    },
                    modifier = Modifier.testTag("confirmLogOut"),
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogOut = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            MobyTopBar(
                title = vm.siteName ?: "Posts",
                backLabel = backLabel,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings")) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { confirmLogOut = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log out")
                    }
                },
            )
        },
        floatingActionButton = {
            MobyFloatingButton(
                onClick = onNewPost,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New post") },
                modifier = Modifier.testTag("newPost"),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().testTag("feed"),
            ) {
                vm.updateNotice?.let { missing ->
                    item(key = "updateNotice") { UpdateNotice(missing, onDismiss = vm::dismissUpdateNotice) }
                }
                if (vm.posts.isEmpty() && !vm.refreshing) {
                    item {
                        Text(
                            "No posts yet. Tap New post to share photos or videos.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                }
                items(vm.posts, key = { it.id }) { post ->
                    PostCard(post, vm.descriptionInfo, vm.postOrder, onClick = { onOpenPost(post.id) })
                }
                if (vm.hasMore) {
                    item {
                        TextButton(onClick = vm::loadMore, enabled = !vm.loadingMore, modifier = Modifier.fillMaxWidth()) {
                            Text(if (vm.loadingMore) "Loading…" else "Load more")
                        }
                    }
                }
            }
        }
    }
}

/**
 * A post in the feed. Its date, photos and videos, description and location
 * are shown in [order], the order the site shows them in.
 */
@Composable
private fun PostCard(post: Post, descriptionInfo: DescriptionInfo, order: List<String>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("post_${post.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            order.forEach { part ->
                when (part) {
                    "date" -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(IsoDates.display(post.date), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (post.status == "draft") {
                            AssistChip(onClick = {}, label = { Text("Draft") })
                        }
                    }
                    "location" -> post.location?.name?.let { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    "description" -> {
                        val linkColor = MaterialTheme.colorScheme.primary
                        val text = remember(post.description, descriptionInfo, linkColor) {
                            FormattedText.annotated(post.description, descriptionInfo, linkColor)
                        }
                        if (text.isNotEmpty()) {
                            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    "media" -> if (post.media.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(post.media, key = { it.id }) { MediaThumbnail(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnail(media: Media) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (media.type == "video") {
            val thumbnail = media.thumbnail
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail.url,
                    contentDescription = media.alt.ifEmpty { null },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Without a thumbnail, remote videos are not downloaded just to
            // show a frame.
            val onImage = thumbnail != null
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = if (onImage) Color.White else LocalContentColor.current,
                    modifier = Modifier
                        .size(40.dp)
                        .then(if (onImage) Modifier.background(Color(0x66000000), CircleShape) else Modifier),
                )
                media.duration?.let {
                    Text(
                        formatDuration(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (onImage) Color.White else LocalContentColor.current,
                        modifier = if (onImage) Modifier.padding(top = 4.dp).background(Color(0x66000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp) else Modifier,
                    )
                }
            }
        } else {
            AsyncImage(
                // Not every photo has a thumbnail, such as HEIC from other apps.
                model = media.thumbnail?.url ?: media.url,
                contentDescription = media.alt.ifEmpty { null },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun formatDuration(seconds: Double): String {
    val total = seconds.toLong()
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Tells the user the site's module is older than the app, and what updating it
 * would add. Users can't update the site themselves, so it points them to the
 * administrator.
 */
@Composable
private fun UpdateNotice(missing: List<String>, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("updateNotice")) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            Text("This site's Moby module is out of date.", style = MaterialTheme.typography.titleSmall)
            Text(
                "Ask the site's administrator to update it to use: ${missing.joinToString(", ")}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Dismiss") }
        }
    }
}
