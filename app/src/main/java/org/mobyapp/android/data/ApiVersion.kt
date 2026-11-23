package org.mobyapp.android.data

/**
 * The API version this app is built for, and what it does on sites with an
 * older one. See "Versioning" in moby_docs/API.md and specifications.md.
 *
 * Every API change bumps the version. When the app starts using a change,
 * raise [CURRENT] to its version and add it to [CHANGES], with a label if
 * users would notice it, and make the code that uses it check
 * [Discovery.supports] first.
 */
object ApiVersion {

    /** The version the app is built for, sent as ?api= on every request. */
    const val CURRENT = 1

    /** The oldest site version the app can work with at all. */
    const val MIN_SITE = 1

    /**
     * A change to the API after version 1.
     *
     * @property label What users gain from it, such as "Tags", or null if
     *   they wouldn't notice it.
     * @property fallback What the app does on sites without it, for
     *   developers.
     */
    data class Change(val version: Int, val label: String?, val fallback: String)

    /** The changes the app uses, oldest first. Version 1 is the baseline. */
    val CHANGES: List<Change> = emptyList()
}

/** Whether the app can use a site, from its discovery document. */
sealed interface Compatibility {
    /** The site has every change the app uses. */
    data object Current : Compatibility

    /**
     * The site's module is older than the app. The app works, without the
     * changes the site lacks.
     *
     * @property missing Labels of the missing changes users would notice.
     *   May be empty when only unnoticeable changes are missing.
     */
    data class Behind(val siteVersion: Int, val missing: List<String>) : Compatibility

    /** The site's module is too old for the app to use at all. */
    data object SiteTooOld : Compatibility

    /** The app is older than the oldest version the site serves. */
    data object AppTooOld : Compatibility

    val isUnsupported: Boolean get() = this == SiteTooOld || this == AppTooOld

    /** A message for the user when the site can't be used, or null. */
    val blockingMessage: String?
        get() = when (this) {
            SiteTooOld -> "This site's Moby module needs updating before the app can use it. Ask the site's administrator to update it."
            AppTooOld -> "Update the Moby app to post to this site."
            else -> null
        }

    companion object {
        /**
         * Works out the app's compatibility with a site.
         *
         * @param appVersion The app's API version, [ApiVersion.CURRENT].
         * @param changes The changes the app uses, [ApiVersion.CHANGES].
         */
        fun of(
            api: ApiInfo?,
            appVersion: Int = ApiVersion.CURRENT,
            minSite: Int = ApiVersion.MIN_SITE,
            changes: List<ApiVersion.Change> = ApiVersion.CHANGES,
        ): Compatibility {
            // A site without "api_version" predates versioning: version 1.
            val siteVersion = api?.version ?: 1
            val siteMin = api?.minVersion ?: 1
            return when {
                appVersion < siteMin -> AppTooOld
                siteVersion < minSite -> SiteTooOld
                siteVersion >= appVersion -> Current
                else -> Behind(siteVersion, changes.filter { it.version in (siteVersion + 1)..appVersion }.mapNotNull { it.label })
            }
        }
    }
}

/** Whether the site has an API change, by the version that added it. */
fun Discovery.supports(version: Int): Boolean = (api?.version ?: 1) >= version

/** The app's compatibility with this site. */
val Discovery.compatibility: Compatibility get() = Compatibility.of(api)
