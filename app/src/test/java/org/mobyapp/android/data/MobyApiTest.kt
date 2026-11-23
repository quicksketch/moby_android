package org.mobyapp.android.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MobyApiTest {

    private lateinit var server: MockWebServer
    private var token: String? = null
    private lateinit var api: MobyApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = MobyApi(server.url("/").toString()) { token }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun respond(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test
    fun parsesDiscovery() = runTest {
        // A response from the moby module, with an extra property to check
        // that unknown properties are ignored.
        respond(
            200,
            """{"api_version":1,"site":{"name":"Example","url":"https:\/\/example.com\/"},
            "auth":{"mechanism":"password","label":"Username and password","fields":[
              {"name":"username","label":"Username","type":"text","required":true},
              {"name":"password","label":"Password","type":"password","required":true}],
              "password_reset_url":"https:\/\/example.com\/user\/password"},
            "limits":{"max_upload_size":104857600,"max_media_per_post":3,"max_description_length":null,"max_alt_length":2000},
            "media":{"image":{"extensions":["jpg","png"],"max_size":104857600},"video":{"extensions":["mp4"],"max_size":52428800}},
            "description":{"format":"filtered_html","html":true},
            "features":{"location":true,"drafts":true},"future_property":1}""",
        )
        val discovery = api.discover()
        assertEquals("/api/moby/meta?api=1", server.takeRequest().path)
        assertEquals(listOf("username", "password"), discovery.auth?.fields?.map { it.name })
        assertEquals(52428800L, discovery.media.video.maxSize)
        assertNull(discovery.limits.maxDescriptionLength)
        assertTrue(discovery.description.html)
    }

    @Test
    fun logsInWithoutATokenAndSendsTheDeviceName() = runTest {
        respond(201, """{"token":"moby_abc","token_type":"Bearer","user":{"id":1,"name":"nate","url":"u","permissions":{"create":true,"publish":true}}}""")
        val response = api.login(mapOf("username" to "nate", "password" to "secret"), "Pixel 9")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertNull(request.getHeader("Authorization"))
        assertJsonEquals(
            JSONObject("""{"username":"nate","password":"secret","device_name":"Pixel 9"}"""),
            JSONObject(request.body.readUtf8()),
        )
        assertEquals("moby_abc", response.token)
    }

    @Test
    fun errorsBecomeApiExceptions() = runTest {
        respond(401, """{"error":{"code":"invalid_credentials","message":"Sorry, incorrect password.","field":"password"}}""")
        try {
            api.login(mapOf("username" to "nate", "password" to "wrong"), "Pixel")
            fail("Expected an ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertEquals("invalid_credentials", e.code)
            assertEquals("password", e.field)
            assertEquals("Sorry, incorrect password.", e.message)
        }
    }

    @Test
    fun unexpectedErrorBodiesStillBecomeApiExceptions() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        try {
            api.discover()
            fail("Expected an ApiException")
        } catch (e: ApiException) {
            assertEquals(502, e.status)
            assertEquals("http_502", e.code)
        }
    }

    @Test
    fun uploadsMultipartWithTokenAndReportsProgress() = runTest {
        token = "moby_token"
        respond(201, """{"id":7,"type":"image","status":"pending","filename":"photo.jpg","mime_type":"image\/jpeg","size":200000,"url":"u","alt":"A"}""")
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val progress = mutableListOf<Long>()
        val media = api.uploadMedia(
            UploadSource("photo.jpg", "image/jpeg", bytes.size.toLong()) { bytes.inputStream() },
            alt = "A",
        ) { sent, _ -> progress += sent }

        val request = server.takeRequest()
        assertEquals("/api/moby/media?api=1", request.path)
        assertEquals("Bearer moby_token", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readByteArray()
        val text = String(body, Charsets.ISO_8859_1)
        assertTrue(text.contains("""name="file"; filename="photo.jpg""""))
        assertTrue(text.contains("""name="alt""""))
        assertTrue("the file is sent intact", text.contains(String(bytes, Charsets.ISO_8859_1)))
        assertEquals(bytes.size.toLong(), progress.last())
        assertEquals(7L, media.id)
        assertNull(media.thumbnail)
    }

    @Test
    fun uploadsVideoThumbnailAndParsesIt() = runTest {
        respond(201, """{"id":8,"type":"video","status":"pending","filename":"clip.mp4","mime_type":"video\/mp4","size":3,"url":"u","thumbnail":{"url":"t","width":1280,"height":720},"alt":""}""")
        val video = byteArrayOf(1, 2, 3)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x42)
        val media = api.uploadMedia(
            UploadSource("clip.mp4", "video/mp4", video.size.toLong()) { video.inputStream() },
            thumbnail = ThumbnailImage("clip.jpg", "image/jpeg", jpeg),
        )

        val text = String(server.takeRequest().body.readByteArray(), Charsets.ISO_8859_1)
        assertTrue(text.contains("""name="thumbnail"; filename="clip.jpg""""))
        assertTrue(text.contains("Content-Type: image/jpeg"))
        assertTrue("the thumbnail is sent intact", text.contains(String(jpeg, Charsets.ISO_8859_1)))
        assertEquals(Thumbnail("t", 1280, 720), media.thumbnail)
    }

    @Test
    fun parsesPhotoThumbnails() = runTest {
        respond(200, """{"id":9,"type":"image","status":"attached","filename":"a.jpg","mime_type":"image\/jpeg","size":3,"url":"u","thumbnail":{"url":"t","width":200,"height":150},"alt":""}""")
        val photo = byteArrayOf(1, 2, 3)
        val media = api.uploadMedia(UploadSource("a.jpg", "image/jpeg", photo.size.toLong()) { photo.inputStream() })
        assertEquals(Thumbnail("t", 200, 150), media.thumbnail)
    }

    @Test
    fun parsesVideoThumbnailDiscovery() = runTest {
        respond(
            200,
            """{"api_version":1,"site":{"name":"S","url":"u"},
            "limits":{"max_upload_size":4096,"max_media_per_post":1},
            "media":{"image":{"extensions":["jpg"],"max_size":2048},"video":{"extensions":["mp4"],"max_size":1,"thumbnail":{"extensions":["jpg","png"]}}}}""",
        )
        val discovery = api.discover()
        assertEquals(ThumbnailInfo(listOf("jpg", "png")), discovery.media.video.thumbnail)
        assertEquals(2048L, discovery.media.image.maxSize)
    }

    @Test
    fun parsesLocationFeatures() = runTest {
        respond(
            200,
            """{"api_version":1,"site":{"name":"S","url":"u"},
            "limits":{"max_upload_size":1,"max_media_per_post":1},
            "media":{"image":{"extensions":[],"max_size":1},"video":{"extensions":[],"max_size":1}},
            "features":{"location":true,"location_name":true,"location_coordinates":false,"drafts":true}}""",
        )
        val features = api.discover().features
        assertTrue(features.locationName)
        assertTrue(!features.locationCoordinates)
    }

    @Test
    fun parsesSetupIssues() = runTest {
        respond(
            200,
            """{"api_version":1,"site":{"name":"S","url":"u"},
            "setup_issues":[{"code":"not_configured","severity":"error","message":"Select a content type."},
                            {"code":"media_not_mapped","severity":"warning","message":"No media field."}],
            "limits":{"max_upload_size":1,"max_media_per_post":0},
            "media":{"image":{"extensions":[],"max_size":1},"video":{"extensions":[],"max_size":1}}}""",
        )
        val issues = api.discover().setupIssues
        assertEquals(listOf("not_configured", "media_not_mapped"), issues.map { it.code })
        assertEquals(listOf(true, false), issues.map { it.isError })
        assertEquals("Select a content type.", issues[0].message)
    }

    @Test
    fun deletesPostsWithNoContent() = runTest {
        respond(204)
        api.deletePost(12)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/moby/posts/12?api=1", request.path)
    }

    @Test
    fun parsesPostPermissionsDefaultingToNone() = runTest {
        val post = """"id":1,"url":"u","api_url":"a","status":"draft","date":"d","created":"c","changed":"c""""
        respond(200, "{$post,\"permissions\":{\"edit\":true,\"delete\":true}}")
        assertTrue(api.getPost(1).permissions.delete)
        respond(200, "{$post}")
        assertTrue(!api.getPost(1).permissions.delete)
    }

    @Test
    fun deletesMediaWithNoContent() = runTest {
        respond(204)
        api.deleteMedia(7)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/moby/media/7?api=1", request.path)
    }

    @Test
    fun listsAndUpdatesPosts() = runTest {
        val post = """{"id":3,"url":"u","api_url":"a","status":"published","description":"Hi","date":"2026-10-27T18:30:00-06:00",
            "location":null,"media":[],"created":"2026-10-27T18:30:00-06:00","changed":"2026-10-27T18:30:00-06:00"}"""
        respond(200, """{"posts":[$post],"page":1,"limit":20,"total":21}""")
        respond(200, post)
        val list = api.listPosts(page = 1)
        assertEquals("/api/moby/posts?page=1&limit=20&api=1", server.takeRequest().path)
        assertEquals(1, list.posts.size)
        assertNull(list.posts[0].location)

        api.updatePost(3, JSONObject().put("description", "Hi"))
        val request = server.takeRequest()
        // HttpURLConnection can't send PATCH; the site treats POST the same.
        assertEquals("POST", request.method)
        assertEquals("/api/moby/posts/3?api=1", request.path)
        assertEquals("Hi", JSONObject(request.body.readUtf8()).getString("description"))
    }

    @Test
    fun suggestsTags() = runTest {
        respond(200, """{"tags":["backdrop","BADCamp"]}""")
        assertEquals(listOf("backdrop", "BADCamp"), api.suggestTags("ba ck#"))
        assertEquals("/api/moby/tags?search=ba+ck%23&limit=8&api=1", server.takeRequest().path)
    }

    @Test
    fun sendsTagsWithPosts() {
        val draft = PostDraft("Hi #there", "2026-10-27T18:30:00-06:00", null, emptyList(), null, tags = listOf("hiking"))
        assertEquals("""["hiking"]""", PostRequests.create("id", draft).get("tags").toString())
        assertNull(PostRequests.update(draft.copy(tags = null)).opt("tags"))
    }
}
