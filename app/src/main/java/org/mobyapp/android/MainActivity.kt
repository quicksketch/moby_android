package org.mobyapp.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.mobyapp.android.ui.MobyNavHost
import org.mobyapp.android.ui.SharedMedia
import org.mobyapp.android.ui.theme.MobyTheme

class MainActivity : ComponentActivity() {

    /** Media shared from another app that has not been opened in an editor yet. */
    private var pendingShare by mutableStateOf(emptyList<Uri>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // When restored, the editor has already been given the shared media.
        pendingShare = when {
            savedInstanceState == null -> SharedMedia.fromIntent(intent)
            else -> savedInstanceState.getParcelableArrayList<Uri>(PENDING_SHARE).orEmpty()
        }
        setContent {
            MobyTheme {
                // Screens fade between each other over this, rather than over
                // the window, so there is no flash of another color.
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    MobyNavHost(
                        sharedMedia = pendingShare,
                        onSharedMediaOpened = { pendingShare = emptyList() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SharedMedia.fromIntent(intent).takeIf { it.isNotEmpty() }?.let { pendingShare = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(PENDING_SHARE, ArrayList(pendingShare))
    }

    private companion object {
        const val PENDING_SHARE = "pendingShare"
    }
}
