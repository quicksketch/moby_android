package org.mobyapp.android.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage

/**
 * Shows a photo or plays a video full screen, over the editor. Closing it
 * returns to the editor as it was.
 */
@Composable
fun MediaViewer(item: EditorMedia, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("mediaViewer")) {
            // The file on the device while it is still there, so the original
            // is shown rather than a smaller copy.
            val source: Any? = item.localUri ?: item.media?.url
            when {
                source == null -> Text("This item can't be shown.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                item.type == "video" -> VideoPlayer(source.toString(), Modifier.fillMaxSize().safeDrawingPadding())
                else -> AsyncImage(
                    model = source,
                    contentDescription = item.alt.ifEmpty { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .background(Color(0x66000000), CircleShape)
                    .testTag("closeViewer"),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoPlayer(uri: String, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    // Pause when the app goes to the background.
    LifecycleStartEffect(player) { onStopOrDispose { player.pause() } }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = modifier,
    )
}
