package org.mobyapp.android.ui

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Reads photos and videos shared to the app from other apps. */
object SharedMedia {

    /**
     * Returns the content shared by an intent.
     *
     * Any app can send these intents, so only content:// URIs are used. Other
     * apps must not be able to get the app to upload its own private files
     * with a file:// URI.
     *
     * @return The URIs in the order shared, or an empty list if the intent is
     *   not a share.
     */
    fun fromIntent(intent: Intent?): List<Uri> {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        return uris.filter { isAllowedScheme(it.scheme) }.distinct()
    }

    internal fun isAllowedScheme(scheme: String?): Boolean =
        scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)

    /** Encodes URIs for a navigation argument, which restores them if needed. */
    internal fun encode(uris: List<Uri>): String = Uri.encode(uris.joinToString("\n"))

    /** Decodes a navigation argument, which navigation has already URL-decoded. */
    internal fun decode(argument: String?): List<Uri> =
        argument?.split("\n")?.filter { it.isNotEmpty() }?.map(Uri::parse).orEmpty()
}
