package org.mobyapp.android.data

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads images over http and https for Coil, with Android's built-in
 * HttpURLConnection, in place of Coil's OkHttp network module.
 *
 * Images are kept in Coil's memory cache, but not on disk, so thumbnails are
 * downloaded again after the app restarts.
 */
class HttpImageFetcher(
    private val url: String,
    private val options: Options,
    private val http: HttpSettings,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = http.connectTimeoutMs
            connection.readTimeout = http.readTimeoutMs
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status for $url")
            // Thumbnails and photos are small enough to read whole.
            val buffer = Buffer().write(connection.inputStream.use { it.readBytes() })
            SourceFetchResult(
                source = ImageSource(buffer, options.fileSystem),
                mimeType = connection.contentType?.substringBefore(';')?.trim(),
                dataSource = DataSource.NETWORK,
            )
        } finally {
            connection.disconnect()
        }
    }

    class Factory(private val http: HttpSettings = HttpSettings()) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "http" || data.scheme == "https") HttpImageFetcher(data.toString(), options, http) else null
    }
}
