package org.mobyapp.android.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import org.mobyapp.android.data.Hashtags
import org.mobyapp.android.ui.MobyTopBar
import org.mobyapp.android.ui.appViewModel
import org.mobyapp.android.ui.theme.MobyButton
import org.mobyapp.android.ui.theme.MobyIcons
import org.mobyapp.android.ui.theme.mobySwitchColors
import org.mobyapp.android.util.IsoDates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditorScreen(postId: Long?, backLabel: String, sharedMedia: List<Uri> = emptyList(), onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel(key = "editor_${postId ?: "new"}") { PostEditorViewModel(it, postId, sharedMedia) }
    val snackbar = remember { SnackbarHostState() }
    var altItem by remember { mutableStateOf<EditorMedia?>(null) }
    var viewItem by remember { mutableStateOf<EditorMedia?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it) } }

    vm.media.firstOrNull { it.compressionOffer != null }?.let { item ->
        val offer = item.compressionOffer ?: return@let
        AlertDialog(
            // A choice is needed for the upload to continue.
            onDismissRequest = {},
            title = { Text("Compress this video?") },
            text = {
                Text(
                    "This video is ${DeviceMediaReader.formatSize(offer.originalSize)}. Compressing it to ${offer.plan.tier.label} " +
                        "should make it about ${DeviceMediaReader.formatSize(offer.plan.estimatedSize)}, so it uploads faster, with some loss of quality.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.answerCompressionOffer(item, true) }) { Text("Compress") }
            },
            dismissButton = {
                TextButton(onClick = { vm.answerCompressionOffer(item, false) }) { Text("Upload original") }
            },
        )
    }

    Scaffold(
        topBar = {
            MobyTopBar(
                title = if (vm.isEditing) "Edit post" else "New post",
                backLabel = backLabel,
                onBack = onBack,
                actions = {
                    MobyButton(
                        onClick = { vm.save(onSaved) },
                        enabled = vm.canSave,
                        modifier = Modifier.padding(end = 8.dp).testTag("save"),
                    ) {
                        if (vm.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (vm.isEditing) "Save" else "Post")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (vm.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        vm.loadError?.let { message ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (vm.isEditing) "This post couldn't be loaded." else "The editor couldn't be loaded.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(message, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                Button(onClick = { vm.load() }) { Text("Try again") }
            }
            return@Scaffold
        }
        val discovery = vm.discovery ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text("What's happening?") },
                minLines = 4,
                isError = vm.errorField == "description",
                supportingText = if (vm.descriptionIsRaw) {
                    { Text("This post has formatting, so it is edited as HTML.") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().testTag("description"),
            )

            MediaSection(vm, onEditAlt = { altItem = it }, onView = { viewItem = it })

            if (discovery.features.tags) {
                TagsSection(vm)
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Text(IsoDates.display(vm.date), modifier = Modifier.weight(1f).padding(start = 12.dp))
                TextButton(onClick = { pickingDate = true }) { Text("Change") }
            }

            if (discovery.features.location) {
                LocationSection(vm, showName = discovery.features.locationName)
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (vm.canPublish) "Published" else "Save as draft", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            vm.statusLocked -> "A site administrator needs to publish this post."
                            vm.canPublish -> "Turn off to save as a draft."
                            else -> "Drafts are not shown on the site."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = if (vm.canPublish) vm.published else !vm.published,
                    onCheckedChange = { vm.published = if (vm.canPublish) it else !it },
                    enabled = !vm.statusLocked,
                    colors = mobySwitchColors(),
                    modifier = Modifier.testTag("status"),
                )
            }
        }
    }


    viewItem?.let { item ->
        // Close the viewer if the item is removed meanwhile.
        if (item in vm.media) MediaViewer(item, onClose = { viewItem = null }) else viewItem = null
    }

    altItem?.let { item ->
        AltTextDialog(
            initial = item.alt,
            maxLength = vm.discovery?.limits?.maxAltLength ?: 2000,
            onDismiss = { altItem = null },
            onSave = { item.alt = it; altItem = null },
        )
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = vm.date.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.date = vm.date.with(day)
                    }
                    pickingDate = false
                    pickingTime = true
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state)
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = vm.date.hour, initialMinute = vm.date.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.date = vm.date.withHour(state.hour).withMinute(state.minute).withSecond(0)
                    pickingTime = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun MediaSection(vm: PostEditorViewModel, onEditAlt: (EditorMedia) -> Unit, onView: (EditorMedia) -> Unit) {
    val room = vm.maxMedia - vm.media.size
    // The multiple-item picker requires a limit of at least 2.
    val pickMany = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, vm.maxMedia)),
    ) { uris -> if (uris.isNotEmpty()) vm.addMedia(uris) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addMedia(listOf(uri))
    }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState) { from, to -> vm.moveMedia(from, to) }
    val haptics = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.media.isNotEmpty()) {
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.media, key = { _, item -> item.key }) { index, item ->
                    val dragging = reorder.draggingKey == item.key
                    EditorMediaTile(
                        item = item,
                        dragging = dragging,
                        onRemove = { vm.removeMedia(item) },
                        onRetry = { vm.retryUpload(item) },
                        onEditAlt = { onEditAlt(item) },
                        onView = { if (!reorder.ignoreTap) onView(item) },
                        modifier = Modifier
                            .then(
                                if (dragging) {
                                    Modifier.zIndex(1f).graphicsLayer { translationX = reorder.offset }
                                } else {
                                    Modifier.animateItem()
                                },
                            )
                            .then(
                                if (vm.media.size > 1) {
                                    Modifier.pointerInput(item.key) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                reorder.start(item.key)
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                reorder.drag(amount.x)
                                            },
                                            onDragEnd = reorder::end,
                                            onDragCancel = reorder::end,
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            // Dragging can't be done with a screen reader, so
                            // offer moving as actions instead.
                            .semantics {
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction("Move earlier") { vm.moveMedia(item, -1); true })
                                    if (index < vm.media.lastIndex) add(CustomAccessibilityAction("Move later") { vm.moveMedia(item, 1); true })
                                }
                            },
                    )
                }
            }
        }
        if (vm.maxMedia > 0) {
            OutlinedButton(
                onClick = {
                    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    if (room > 1) pickMany.launch(request) else pickOne.launch(request)
                },
                enabled = room > 0,
                modifier = Modifier.testTag("addMedia"),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add photos or videos", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                "${vm.media.size} of ${vm.maxMedia}." + if (vm.media.size > 1) " Touch and hold an item to drag it into place." else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EditorMediaTile(
    item: EditorMedia,
    dragging: Boolean,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onEditAlt: () -> Unit,
    onView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .then(if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(8.dp)) else Modifier)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClickLabel = if (item.type == "video") "Play video" else "View photo", onClick = onView)
                .testTag("media_${item.key}"),
            contentAlignment = Alignment.Center,
        ) {
            val preview = item.previewModel
            if (preview != null) {
                AsyncImage(
                    model = preview,
                    contentDescription = item.alt.ifEmpty { null },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (item.type == "video") {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp).background(Color(0x66000000), CircleShape),
                )
            }
            when {
                item.error != null -> Box(
                    Modifier.fillMaxSize().background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                            Text("Retry", color = Color.White)
                        }
                    }
                }
                item.isUploading -> Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)) {
                    if (item.compressing) {
                        Text(
                            "Compressing",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            AltButton(hasAlt = item.alt.isNotBlank(), onClick = onEditAlt, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp).background(Color(0x66000000), CircleShape),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White)
            }
        }
        item.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(140.dp))
        }
    }
}

/** A small "Alt" button, with a check once alt text has been added. */
@Composable
private fun AltButton(hasAlt: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x99000000))
            .clickable(onClickLabel = if (hasAlt) "Edit alt text" else "Add alt text", onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (hasAlt) "Edit alt text" else "Add alt text"
            }
            .testTag("alt"),
    ) {
        Text("Alt", color = Color.White, style = MaterialTheme.typography.labelMedium)
        if (hasAlt) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(start = 2.dp).size(14.dp))
        }
    }
}

/**
 * The post's tags: chips for the tags added, which can be removed, and for
 * the description's hashtags, then a field with suggestions to add more.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(vm: PostEditorViewModel) {
    val hashtags = vm.hashtags
    val extraHashtags = hashtags.filter { tag -> vm.tags.none { Hashtags.same(it, tag) } }
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Material's core icons have no tag icon.
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Text("#", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("Tags", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
        }
        if (vm.tags.isNotEmpty() || extraHashtags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("tags"),
            ) {
                vm.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removeTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.semantics { contentDescription = "Remove tag $tag" },
                    )
                }
                // Hashtags are removed by editing the description.
                extraHashtags.forEach { tag ->
                    SuggestionChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("#$tag") },
                        modifier = Modifier.semantics { contentDescription = "Tag $tag, from a hashtag in the description" },
                    )
                }
            }
        }
        TagField(vm)
        Text(
            "Hashtags in the description, like #hiking, are added as tags too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A field for adding a tag, with tags from the site suggested below it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagField(vm: PostEditorViewModel) {
    var focused by remember { mutableStateOf(false) }
    val expanded = focused && vm.tagSuggestions.isNotEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {}) {
        OutlinedTextField(
            value = vm.tagInput,
            onValueChange = vm::onTagInput,
            label = { Text("Add a tag") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.None),
            keyboardActions = KeyboardActions(onDone = { vm.addTag(vm.tagInput) }),
            trailingIcon = if (vm.tagInput.isNotBlank()) {
                { IconButton(onClick = { vm.addTag(vm.tagInput) }) { Icon(Icons.Default.Add, contentDescription = "Add tag") } }
            } else {
                null
            },
            isError = vm.errorField?.startsWith("tags") == true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .onFocusChanged { focused = it.isFocused }
                .testTag("tagInput"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { focused = false }) {
            vm.tagSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = { vm.addTag(suggestion) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun LocationSection(vm: PostEditorViewModel, showName: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }

    // Whether the current lookup was started automatically for a new post,
    // rather than by the user. Automatic lookups fail silently.
    var automatic by remember { mutableStateOf(false) }
    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val hasPermission = {
        permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun locate() {
        locating = true
        scope.launch {
            val location = runCatching { DeviceLocation.current(context) }.getOrNull()
            locating = false
            when {
                // Don't replace a location the user typed while waiting.
                automatic && (vm.locationName.isNotBlank() || vm.latitude.isNotBlank()) -> {}
                location != null -> vm.setLocation(location)
                !automatic -> vm.error = "Your location could not be found."
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        when {
            granted.values.any { it } -> locate()
            !automatic -> vm.error = "Location permission was not granted."
        }
    }

    // The "Include Location" setting: fill in new posts' location on open.
    LaunchedEffect(Unit) {
        if (!vm.autoLocate) return@LaunchedEffect
        vm.autoLocate = false
        automatic = true
        if (hasPermission()) locate() else permission.launch(permissions)
    }

    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Text("Location", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 12.dp))
            TextButton(onClick = vm::clearLocation) { Text("Clear") }
        }
        if (showName) {
            OutlinedTextField(
                value = vm.locationName,
                onValueChange = { vm.locationName = it },
                label = { Text("Place name") },
                singleLine = true,
                isError = vm.errorField == "location.name",
                modifier = Modifier.fillMaxWidth().testTag("locationName"),
            )
        }
        // Coordinates come only from the device's location, so they aren't
        // shown. They are still sent with the post, and Clear removes them.
        OutlinedButton(
            onClick = {
                automatic = false
                if (hasPermission()) locate() else permission.launch(permissions)
            },
            enabled = !locating,
        ) {
            // An arrow, like the iOS app's, and as "Add photos or videos" has a plus.
            if (locating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Finding location…", modifier = Modifier.padding(start = 8.dp))
            } else {
                Icon(MobyIcons.NearMe, contentDescription = null)
                Text("Use current location", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AltTextDialog(initial: String, maxLength: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    // The keyboard opens straight away.
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        // Tapping outside keeps the text, like Done.
        onDismissRequest = { onSave(text) },
        title = { Text("Alt text") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= maxLength) text = it },
                label = { Text("Describe this for people who can't see it") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("altText"),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
