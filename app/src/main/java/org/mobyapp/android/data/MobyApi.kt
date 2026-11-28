package org.mobyapp.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * A file to upload, read from a stream so large videos are never held in
 * memory.
 */
class UploadSource(
    val filename: String,
    val mimeType: String,
    val size: Long,
    val open: () -> InputStream,
)

/** An encoded image to upload as a video's thumbnail. */
class ThumbnailImage(val filename: String, val mimeType: String, val bytes: ByteArray)

/**
 * Timeouts for the app's requests. See SPECIFICATIONS.md section 2.
 *
 * Android's HttpURLConnection has no write timeout; the read timeout limits
 * how long the site may take to answer once an upload is sent.
 */
class HttpSettings(val connectTimeoutMs: Int = 20_000, val readTimeoutMs: Int = 60_000)

/**
 * A client for one site's Moby API, using Android's built-in HttpURLConnection
 * and org.json.
 *
 * @param siteUrl The site's base URL, such as "https://example.com".
 * @param token Returns the current API token, or null when logged out.
 */
class MobyApi(
    siteUrl: String,
    private val http: HttpSettings = HttpSettings(),
    private val token: () -> String? = { null },
) {
    val baseUrl = siteUrl.trimEnd('/') + "/api/moby"

    suspend fun discover(): Discovery = get("/meta", JsonModels::discovery)

    /**
     * Logs in with a mechanism's credentials, or sends a later step of a
     * login. Some mechanisms answer with another step instead of a token.
     *
     * @param mechanism The mechanism's name, from [Discovery.authMechanisms].
     * @param requestId For a later step, its [LoginStep.requestId].
     */
    suspend fun login(mechanism: String, credentials: Map<String, String>, deviceName: String, requestId: String? = null): LoginResult {
        val body = JSONObject().put("mechanism", mechanism)
        requestId?.let { body.put("request_id", it) }
        credentials.forEach { (name, value) -> body.put(name, value) }
        body.put("device_name", deviceName)
        return send("POST", "/auth/token", body, JsonModels::loginResult)
    }

    suspend fun logout() {
        execute("DELETE", "/auth/token")
    }

    suspend fun me(): User = get("/me", JsonModels::user)

    /**
     * Uploads a photo or video as multipart/form-data, streaming the file.
     *
     * @param thumbnail For videos, an image to use as the thumbnail, if the
     *   site stores them.
     */
    suspend fun uploadMedia(
        source: UploadSource,
        alt: String? = null,
        thumbnail: ThumbnailImage? = null,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): Media {
        val body = Multipart(source, alt?.takeIf { it.isNotBlank() }, thumbnail)
        val text = execute("POST", "/media", body.contentType, streamLength = body.length) { out -> body.writeTo(out, onProgress) }
        return parse(text, JsonModels::media)
    }

    suspend fun deleteMedia(id: Long) {
        execute("DELETE", "/media/$id")
    }

    suspend fun listPosts(page: Int = 0, limit: Int = 20): PostList =
        get("/posts?page=$page&limit=$limit", JsonModels::postList)

    suspend fun getPost(id: Long): Post = get("/posts/$id", JsonModels::post)

    suspend fun createPost(body: JSONObject): Post = send("POST", "/posts", body, JsonModels::post)

    /**
     * Saves changes to a post. HttpURLConnection can't send PATCH, so this
     * sends POST /posts/{id}, which the site treats the same.
     */
    suspend fun updatePost(id: Long, body: JSONObject): Post = send("POST", "/posts/$id", body, JsonModels::post)

    suspend fun deletePost(id: Long) {
        execute("DELETE", "/posts/$id")
    }

    /** Tags starting with or containing what the user typed, most used first. */
    suspend fun suggestTags(search: String, limit: Int = 8): List<String> =
        get("/tags?search=${URLEncoder.encode(search, "UTF-8")}&limit=$limit", JsonModels::tagSuggestions).tags

    private suspend fun <T> get(path: String, read: (JSONObject) -> T): T = parse(execute("GET", path), read)

    private suspend fun <T> send(method: String, path: String, body: JSONObject, read: (JSONObject) -> T): T {
        val bytes = body.toString().toByteArray()
        val text = execute(method, path, "application/json") { it.write(bytes) }
        return parse(text, read)
    }

    private fun <T> parse(text: String, read: (JSONObject) -> T): T = read(JSONObject(text))

    /**
     * Sends a request and returns the response body.
     *
     * @param streamLength For large bodies, such as uploads, their length, so
     *   they are streamed rather than held in memory. Small bodies are
     *   buffered, which also keeps the error details of a 401 response
     *   readable.
     *
     * @throws ApiException For an error response.
     * @throws java.io.IOException If the site can't be reached.
     */
    private suspend fun execute(
        method: String,
        path: String,
        contentType: String? = null,
        streamLength: Long? = null,
        writeBody: ((OutputStream) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
        // Every request says which API version the app is built for.
        val versioned = path + (if ('?' in path) "&" else "?") + "api=${ApiVersion.CURRENT}"
        val connection = URL(baseUrl + versioned).openConnection() as HttpURLConnection
        // Blocking reads and writes don't notice coroutine cancellation, such
        // as when the user removes an uploading photo, so close the connection
        // to stop them.
        val cancel = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }
        try {
            connection.connectTimeout = http.connectTimeoutMs
            connection.readTimeout = http.readTimeoutMs
            connection.useCaches = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            token()?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (writeBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                streamLength?.let(connection::setFixedLengthStreamingMode)
                connection.outputStream.use(writeBody)
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status !in 200..299) throw parseError(status, body)
            body
        } finally {
            cancel?.dispose()
        }
    }

    private fun parseError(status: Int, body: String): ApiException {
        val detail = JsonModels.errorDetail(body)
            ?: ErrorDetail("http_$status", "The site returned an unexpected error ($status).")
        return ApiException(status, detail.code, detail.message, detail.field, detail.restart)
    }
}

/** A multipart/form-data body: the file, its alt text, and a thumbnail. */
private class Multipart(
    private val source: UploadSource,
    alt: String?,
    private val thumbnail: ThumbnailImage?,
) {
    private val boundary = "moby-${UUID.randomUUID()}"
    val contentType = "multipart/form-data; boundary=$boundary"

    private val fileHeader = header("file", source.filename, source.mimeType)
    private val altPart = alt?.let { header("alt") + it.toByteArray() + CRLF }
    private val thumbnailHeader = thumbnail?.let { header("thumbnail", it.filename, it.mimeType) }
    private val end = "--$boundary--\r\n".toByteArray()

    /** The body's size, which fixed-length streaming needs up front. */
    val length: Long = fileHeader.size + source.size + CRLF.size +
        (altPart?.size ?: 0) +
        (if (thumbnail != null && thumbnailHeader != null) thumbnailHeader.size + thumbnail.bytes.size + CRLF.size else 0) +
        end.size

    fun writeTo(out: OutputStream, onProgress: (Long, Long) -> Unit) {
        out.write(fileHeader)
        source.open().use { input ->
            val buffer = ByteArray(SEGMENT_SIZE)
            var sent = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                sent += read
                onProgress(sent, source.size)
            }
        }
        out.write(CRLF)
        altPart?.let(out::write)
        if (thumbnail != null && thumbnailHeader != null) {
            out.write(thumbnailHeader)
            out.write(thumbnail.bytes)
            out.write(CRLF)
        }
        out.write(end)
    }

    private fun header(name: String, filename: String? = null, type: String? = null): ByteArray = buildString {
        append("--").append(boundary).append("\r\n")
        append("Content-Disposition: form-data; name=\"").append(name).append('"')
        // Quotes and line breaks would break the header.
        if (filename != null) append("; filename=\"").append(filename.replace(Regex("[\"\r\n]"), "_")).append('"')
        append("\r\n")
        if (type != null) append("Content-Type: ").append(type).append("\r\n")
        append("\r\n")
    }.toByteArray()

    private companion object {
        val CRLF = "\r\n".toByteArray()
        const val SEGMENT_SIZE = 64 * 1024
    }
}
