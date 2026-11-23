package org.mobyapp.android.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A site the user has added. Logging out keeps the site, without its token.
 *
 * @property token The API token, encrypted with a key held in the Android
 *   Keystore, so it cannot be read from the app's files. Null when logged out.
 */
data class SavedSite(
    val id: String,
    val url: String,
    val name: String? = null,
    val userName: String? = null,
    val token: String? = null,
    /** The URL of the site's favicon, if known. */
    val favicon: String? = null,
    /** A title the user chose for the site, shown instead of [name]. */
    val customTitle: String? = null,
    /** The site's API version when last checked, or null if not known yet. */
    val apiVersion: Int? = null,
) {
    val isLoggedIn: Boolean get() = token != null

    /**
     * The title to show: the user's own, or else the site's name, or its host
     * until the name is known.
     */
    val displayName: String get() = customTitle ?: name ?: Uri.parse(url).host ?: url
}

/**
 * Stores the sites the user has added, each with its own session, and which
 * one is open.
 */
class SiteStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("moby_sites", Context.MODE_PRIVATE)

    /** The sites, in the order they were added. */
    var sites by mutableStateOf(load())
        private set

    /** The ID of the site that is open, kept so it survives the app being stopped. */
    var currentId: String?
        get() = prefs.getString(KEY_CURRENT, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_CURRENT) else putString(KEY_CURRENT, value) }

    fun get(id: String?): SavedSite? = sites.firstOrNull { it.id == id }

    fun findByUrl(url: String): SavedSite? = sites.firstOrNull { it.url.equals(url, ignoreCase = true) }

    /** The site's decrypted API token, or null when logged out. */
    fun token(id: String?): String? = get(id)?.token?.let { runCatching { decrypt(it) }.getOrNull() }

    /**
     * Saves a login, adding the site or updating it if it was added before.
     *
     * @return The site's ID.
     */
    fun saveLogin(url: String, info: Site, token: String, userName: String, apiVersion: Int? = null): String {
        val existing = findByUrl(url)
        val site = SavedSite(
            id = existing?.id ?: UUID.randomUUID().toString(),
            url = url,
            name = info.name,
            userName = userName,
            token = encrypt(token),
            favicon = info.favicon,
            customTitle = existing?.customTitle,
            apiVersion = apiVersion,
        )
        save(if (existing == null) sites + site else sites.map { if (it.id == site.id) site else it })
        return site.id
    }

    /** Updates a site's name and favicon from its discovery document. */
    fun updateInfo(id: String, discovery: Discovery) {
        val site = get(id) ?: return
        val info = discovery.site
        val apiVersion = discovery.api?.version ?: 1
        if (site.name == info.name && site.favicon == info.favicon && site.apiVersion == apiVersion) return
        save(sites.map { if (it.id == id) it.copy(name = info.name, favicon = info.favicon, apiVersion = apiVersion) else it })
    }

    /**
     * Sets the title the user chose for the site. A blank title, or the
     * site's own name, removes it, so the site's name is used again.
     */
    fun setCustomTitle(id: String, title: String?) {
        val site = get(id) ?: return
        val custom = title?.trim()?.takeIf { it.isNotEmpty() && it != site.name }
        if (site.customTitle == custom) return
        save(sites.map { if (it.id == id) it.copy(customTitle = custom) else it })
    }

    /** Forgets the site's token, keeping the site in the list. */
    fun logOut(id: String) {
        save(sites.map { if (it.id == id) it.copy(token = null) else it })
    }

    /** Removes the site and its settings. */
    fun remove(id: String) {
        save(sites.filterNot { it.id == id })
        if (currentId == id) currentId = null
        AppSettings.delete(context, id)
    }

    private fun load(): List<SavedSite> =
        prefs.getString(KEY_SITES, null)?.let { text -> runCatching { decode(text) }.getOrNull() }.orEmpty()

    private fun save(list: List<SavedSite>) {
        sites = list
        prefs.edit { putString(KEY_SITES, encode(list)) }
    }

    internal companion object Storage {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "moby_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val KEY_SITES = "sites"
        private const val KEY_CURRENT = "current"

        /** Saves sites as a JSON array, one object per site. */
        fun encode(list: List<SavedSite>): String = JSONArray().apply {
            list.forEach { site ->
                put(JSONObject().apply {
                    put("id", site.id)
                    put("url", site.url)
                    site.name?.let { put("name", it) }
                    site.userName?.let { put("userName", it) }
                    site.token?.let { put("token", it) }
                    site.favicon?.let { put("favicon", it) }
                    site.customTitle?.let { put("customTitle", it) }
                    site.apiVersion?.let { put("apiVersion", it) }
                })
            }
        }.toString()

        fun decode(text: String): List<SavedSite> {
            val array = JSONArray(text)
            return List(array.length()) { i ->
                val o = array.getJSONObject(i)
                SavedSite(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    name = o.strOrNull("name"),
                    userName = o.strOrNull("userName"),
                    token = o.strOrNull("token"),
                    favicon = o.strOrNull("favicon"),
                    customTitle = o.strOrNull("customTitle"),
                    apiVersion = o.intOrNull("apiVersion"),
                )
            }
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }
}
