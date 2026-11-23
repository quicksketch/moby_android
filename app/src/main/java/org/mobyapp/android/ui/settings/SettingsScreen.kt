package org.mobyapp.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mobyapp.android.MobyApplication
import org.mobyapp.android.ui.MobyTopBar
import org.mobyapp.android.ui.theme.mobySwitchColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(backLabel: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MobyApplication
    val site = app.currentSite ?: return
    val settings = app.settingsFor(site.id)

    Scaffold(
        topBar = {
            MobyTopBar(title = "Settings", backLabel = backLabel, onBack = onBack)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "These settings apply to ${site.displayName}. Each site has its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            SectionHeader("Site")
            SiteTitleField(
                siteName = site.name,
                customTitle = site.customTitle,
                onChange = { app.sites.setCustomTitle(site.id, it) },
            )
            SectionHeader("Defaults")
            ToggleItem(
                title = "Include Location",
                summary = "Fill in your current location on new posts.",
                checked = settings.includeLocation,
                onCheckedChange = { settings.includeLocation = it },
                tag = "includeLocation",
            )
            ToggleItem(
                title = "Publish by Default",
                summary = "Start new posts as published. When off, they start as drafts.",
                checked = settings.publishByDefault,
                onCheckedChange = { settings.publishByDefault = it },
                tag = "publishByDefault",
            )
        }
    }
}

/**
 * The site's title in the app. It starts as the site's own name, and the user
 * can change it or go back to the site's name.
 */
@Composable
private fun SiteTitleField(siteName: String?, customTitle: String?, onChange: (String?) -> Unit) {
    // Kept here while typing, so clearing the field doesn't refill it.
    var text by rememberSaveable { mutableStateOf(customTitle ?: siteName ?: "") }
    val custom = customTitle != null
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text("Site title") },
        placeholder = siteName?.let { { Text(it) } },
        supportingText = {
            Text(
                when {
                    custom -> "Your own title, shown in the app instead of the site's."
                    else -> "The site's own title. Change it to show something else in the app."
                },
            )
        },
        trailingIcon = if (custom && siteName != null) {
            {
                IconButton(onClick = {
                    text = siteName
                    onChange(null)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Use the site's title")
                }
            }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("siteTitle"),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ToggleItem(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        // The whole row toggles, and the switch is read as part of it.
        trailingContent = { Switch(checked = checked, onCheckedChange = null, colors = mobySwitchColors()) },
        modifier = Modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(tag),
    )
}
