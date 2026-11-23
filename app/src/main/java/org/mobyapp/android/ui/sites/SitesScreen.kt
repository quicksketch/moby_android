package org.mobyapp.android.ui.sites

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mobyapp.android.MobyApplication
import org.mobyapp.android.data.ApiInfo
import org.mobyapp.android.data.Compatibility
import org.mobyapp.android.data.SavedSite
import org.mobyapp.android.ui.MobyTopBar
import org.mobyapp.android.ui.theme.MobyButton
import org.mobyapp.android.ui.theme.MobyFloatingButton

private val LoggedInColor = Color(0xFF2E9E4F)

/**
 * Lists the sites the user has added, and whether they are logged in to each.
 *
 * @param sharing Whether media shared from another app is waiting for a site
 *   to be chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    sharing: Boolean,
    onOpenSite: (SavedSite) -> Unit,
    onOpenSettings: (SavedSite) -> Unit,
    onAddSite: () -> Unit,
    onCancelShare: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as MobyApplication
    val sites = app.sites.sites
    val scope = rememberCoroutineScope()
    // Dialogs, by site ID so they survive rotation.
    var confirmLogOut by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Keep each site's name and favicon up to date. Sites that can't be
    // reached keep what was saved.
    LaunchedEffect(Unit) {
        app.sites.sites.forEach { site ->
            launch {
                runCatching { app.api(site.url, site.id).discover() }.onSuccess { app.sites.updateInfo(site.id, it) }
            }
        }
    }

    Scaffold(
        topBar = { MobyTopBar(title = "Sites") },
        floatingActionButton = {
            if (sites.isNotEmpty()) {
                MobyFloatingButton(
                    onClick = onAddSite,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add site") },
                    modifier = Modifier.testTag("addSite"),
                )
            }
        },
    ) { padding ->
        if (sites.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No sites connected yet.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                MobyButton(onClick = onAddSite, modifier = Modifier.padding(top = 16.dp).testTag("addSite")) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add site", modifier = Modifier.padding(start = 8.dp))
                }
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("sites"),
        ) {
            if (sharing) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Choose a site to post the shared photos and videos to.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onCancelShare) { Text("Cancel") }
                        }
                    }
                }
            }
            items(sites, key = { it.id }) { site ->
                SiteCard(
                    site = site,
                    enabled = !busy,
                    onClick = { onOpenSite(site) },
                    onSettings = { onOpenSettings(site) },
                    onLogOut = { confirmLogOut = site.id },
                    onRemove = { confirmRemove = site.id },
                )
            }
        }
    }

    app.sites.get(confirmLogOut)?.let { site ->
        AlertDialog(
            onDismissRequest = { confirmLogOut = null },
            title = { Text("Log out of ${site.displayName}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogOut = null
                    busy = true
                    scope.launch {
                        app.logOut(site)
                        busy = false
                    }
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogOut = null }) { Text("Cancel") } },
        )
    }

    app.sites.get(confirmRemove)?.let { site ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${site.displayName}?") },
            text = {
                Text(
                    if (site.isLoggedIn) {
                        "You'll be logged out, and the app's settings for this site will be deleted. Your posts stay on the site."
                    } else {
                        "The app's settings for this site will be deleted. Your posts stay on the site."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    busy = true
                    scope.launch {
                        app.removeSite(site)
                        busy = false
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SiteCard(
    site: SavedSite,
    enabled: Boolean,
    onClick: () -> Unit,
    onSettings: () -> Unit,
    onLogOut: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("site_${site.id}")) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SiteIcon(site)
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(site.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    site.url.substringAfter("://"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SessionIndicator(site)
                if (site.needsUpdate) {
                    Text(
                        "Site update available",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options for ${site.displayName}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onSettings() })
                    if (site.isLoggedIn) {
                        DropdownMenuItem(text = { Text("Log out") }, onClick = { menuOpen = false; onLogOut() })
                    }
                    DropdownMenuItem(text = { Text("Remove site") }, onClick = { menuOpen = false; onRemove() })
                }
            }
        }
    }
}

/**
 * The site's favicon. Until it loads, or if the site has none that can be
 * shown, the first letter of the site's name.
 */
@Composable
private fun SiteIcon(site: SavedSite) {
    var loaded by remember(site.favicon) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (loaded) Color.Transparent else MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                site.displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (site.favicon != null) {
            AsyncImage(
                model = site.favicon,
                // Decorative: the site's name is next to it.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** A dot and a label: green when logged in, gray when logged out. */
@Composable
private fun SessionIndicator(site: SavedSite) {
    val label = when {
        !site.isLoggedIn -> "Logged out"
        site.userName != null -> "Logged in as ${site.userName}"
        else -> "Logged in"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (site.isLoggedIn) LoggedInColor else MaterialTheme.colorScheme.outline, CircleShape),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (site.isLoggedIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Whether the site's module lacks changes the app uses that users would
 * notice, from its API version when last checked.
 */
private val SavedSite.needsUpdate: Boolean
    get() = apiVersion?.let { (Compatibility.of(ApiInfo(it, 1)) as? Compatibility.Behind)?.missing?.isNotEmpty() } == true
