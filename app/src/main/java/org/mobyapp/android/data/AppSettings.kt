package org.mobyapp.android.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * A site's settings in the app. They are kept when the user logs out of the
 * site, and deleted when the site is removed.
 */
class AppSettings(context: Context, siteId: String) {

    private val prefs = context.getSharedPreferences(prefsName(siteId), Context.MODE_PRIVATE)

    private var _includeLocation by mutableStateOf(prefs.getBoolean(KEY_INCLUDE_LOCATION, true))
    private var _publishByDefault by mutableStateOf(prefs.getBoolean(KEY_PUBLISH_BY_DEFAULT, true))
    private var _dismissedUpdateNotice by mutableStateOf(prefs.getString(KEY_DISMISSED_UPDATE_NOTICE, null))

    /**
     * The update notice the user dismissed, as "{site version}:{app version}",
     * so it comes back when the gap between them changes.
     */
    var dismissedUpdateNotice: String?
        get() = _dismissedUpdateNotice
        set(value) {
            _dismissedUpdateNotice = value
            prefs.edit { putString(KEY_DISMISSED_UPDATE_NOTICE, value) }
        }

    /** Whether new posts are filled in with the device's current location. */
    var includeLocation: Boolean
        get() = _includeLocation
        set(value) {
            _includeLocation = value
            prefs.edit { putBoolean(KEY_INCLUDE_LOCATION, value) }
        }

    /**
     * Whether new posts start out published. When off, they start as drafts.
     */
    var publishByDefault: Boolean
        get() = _publishByDefault
        set(value) {
            _publishByDefault = value
            prefs.edit { putBoolean(KEY_PUBLISH_BY_DEFAULT, value) }
        }

    companion object {
        private const val KEY_INCLUDE_LOCATION = "include_location"
        private const val KEY_PUBLISH_BY_DEFAULT = "publish_by_default"
        private const val KEY_DISMISSED_UPDATE_NOTICE = "dismissed_update_notice"

        private fun prefsName(siteId: String) = "moby_settings_$siteId"

        fun delete(context: Context, siteId: String) {
            context.deleteSharedPreferences(prefsName(siteId))
        }
    }
}
