package com.tamalitos.malitos

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, manifest = Config.NONE)
class DriveApiTest {
    private fun digest(bytes: ByteArray, type: String) = MessageDigest.getInstance(type).digest(bytes).joinToString("") { "%02x".format(it) }
    private fun metadata(bytes: ByteArray, name: String) = JSONObject()
        .put("id", "exact_uploaded_id").put("name", name).put("mimeType", "application/json")
        .put("spaces", org.json.JSONArray().put("appDataFolder"))
        .put("createdTime", "2026-01-01T00:00:00Z").put("size", bytes.size.toString())
        .put("md5Checksum", digest(bytes, "MD5"))
        .put("appProperties", JSONObject().put("format", DriveSnapshot.FORMAT).put("version", "1")
            .put("sha256", digest(bytes, "SHA-256")).put("verified", "false"))

    @Test fun retentionKeepsTenAfterVerificationAndNeverDeletesUnknownFiles() {
        val bytes = DriveSnapshot.encode("{\"version\":1}", 1)
        var meta: JSONObject? = null
        val deleted = mutableListOf<String>()
        var verified = false
        val transport = DriveTransport { req ->
            when {
                req.method == "POST" -> {
                    val name = Regex("tamalitos-v1-[0-9]+-[a-f0-9-]+\\.json").find(req.body!!.toString(Charsets.UTF_8))!!.value
                    meta = metadata(bytes, name)
                    DriveResponse(200, "{\"id\":\"exact_uploaded_id\"}".toByteArray())
                }
                req.method == "DELETE" -> { assertTrue(verified); deleted.add(req.url.substringAfterLast('/')); DriveResponse(204, byteArrayOf()) }
                req.method == "PATCH" -> { verified = true; meta!!.getJSONObject("appProperties").put("verified", "true"); DriveResponse(200, byteArrayOf()) }
                req.url.contains("alt=media") -> DriveResponse(200, bytes)
                req.url.contains("exact_uploaded_id") -> DriveResponse(200, meta!!.toString().toByteArray())
                req.url.contains("/old") -> {
                    val id = req.url.substringAfterLast('/').substringBefore('?')
                    val old = metadata(bytes, "tamalitos-v1-1-00000000-0000-0000-0000-000000000000.json").put("id", id)
                    old.getJSONObject("appProperties").put("verified", "true")
                    DriveResponse(200, old.toString().toByteArray())
                }
                else -> {
                    val files = org.json.JSONArray()
                    for (i in 0..11) {
                        val old = metadata(bytes, "tamalitos-v1-$i-00000000-0000-0000-0000-000000000000.json")
                            .put("id", "old$i").put("createdTime", "2025-01-%02dT00:00:00Z".format(31-i))
                        old.getJSONObject("appProperties").put("verified", "true")
                        files.put(old)
                    }
                    files.put(metadata(bytes, "unrelated.json").put("id", "unknown"))
                    DriveResponse(200, JSONObject().put("files", files).toString().toByteArray())
                }
            }
        }
        DriveApi(transport).backup("ephemeral", bytes, 1)
        assertEquals(listOf("old9", "old10", "old11"), deleted)
    }

    @Test fun restoresOnlyNewestVerifiedOwnedSnapshotAfterCheckingDownloadedChecksum() {
        val bytes = DriveSnapshot.encode("{\"version\":1,\"customers\":[]}", 1)
        val name = "tamalitos-v1-1-00000000-0000-0000-0000-000000000000.json"
        val meta = metadata(bytes, name)
        meta.getJSONObject("appProperties").put("verified", "true")
        val requests = mutableListOf<DriveRequest>()
        val api = DriveApi(DriveTransport { req ->
            requests.add(req)
            when {
                req.url.contains("alt=media") -> DriveResponse(200, bytes)
                req.url.contains("exact_uploaded_id") -> DriveResponse(200, meta.toString().toByteArray())
                else -> DriveResponse(200, JSONObject().put("files", org.json.JSONArray()
                    .put(metadata(bytes, "unrelated.json").put("createdTime", "2027-01-01T00:00:00Z"))
                    .put(meta)).toString().toByteArray())
            }
        })
        val restored = api.restoreLatest("ephemeral")
        assertEquals("{\"version\":1,\"customers\":[]}", restored.businessJson)
        assertEquals("exact_uploaded_id", restored.id)
        assertTrue(requests.all { it.method == "GET" })
    }

    @Test fun rejectsCommitMetadataThatNoLongerMatchesUploadedBytes() {
        val bytes = DriveSnapshot.encode("{\"version\":1}", 1)
        var meta: JSONObject? = null
        var committed = false
        val api = DriveApi(DriveTransport { req ->
            when {
                req.method == "POST" -> {
                    val name = Regex("tamalitos-v1-[0-9]+-[a-f0-9-]+\\.json").find(req.body!!.toString(Charsets.UTF_8))!!.value
                    meta = metadata(bytes, name)
                    DriveResponse(200, "{\"id\":\"exact_uploaded_id\"}".toByteArray())
                }
                req.url.contains("alt=media") -> DriveResponse(200, bytes)
                req.method == "PATCH" -> { committed = true; meta!!.getJSONObject("appProperties").put("verified", "true"); DriveResponse(200, byteArrayOf()) }
                req.url.contains("exact_uploaded_id") -> {
                    if (committed) meta!!.put("size", "9999")
                    DriveResponse(200, meta!!.toString().toByteArray())
                }
                else -> DriveResponse(200, "{\"files\":[]}".toByteArray())
            }
        })
        assertThrows(IllegalStateException::class.java) { api.backup("ephemeral", bytes, 1) }
    }

    private class TestOnlyDrive(private val bytes: ByteArray) : DriveTransport {
        var media = bytes
        var status = 200
        var wrongChecksum = false
        var wrongTarget = false
        var patches = 0
        var deletes = 0
        private var metadata: JSONObject? = null
        override fun execute(request: DriveRequest): DriveResponse {
            if (status != 200) return DriveResponse(status, "HTTP failure".toByteArray())
            return when {
                request.method == "POST" -> {
                    val name = Regex("tamalitos-v1-[0-9]+-[a-f0-9-]+\\.json").find(request.body!!.toString(Charsets.UTF_8))!!.value
                    metadata = JSONObject().put("id", if (wrongTarget) "other_id" else "target")
                        .put("name", name).put("size", bytes.size.toString()).put("mimeType", "application/json")
                        .put("spaces", org.json.JSONArray().put("appDataFolder")).put("createdTime", "2026-01-01T00:00:00Z")
                        .put("md5Checksum", if (wrongChecksum) "bad" else digestOf(bytes, "MD5"))
                        .put("appProperties", JSONObject().put("format", DriveSnapshot.FORMAT).put("version", "1")
                            .put("verified", "false").put("sha256", digestOf(bytes, "SHA-256")))
                    DriveResponse(200, "{\"id\":\"target\"}".toByteArray())
                }
                request.url.contains("alt=media") -> DriveResponse(200, media)
                request.method == "PATCH" -> { patches++; metadata!!.getJSONObject("appProperties").put("verified", "true"); DriveResponse(200, byteArrayOf()) }
                request.method == "DELETE" -> { deletes++; DriveResponse(204, byteArrayOf()) }
                request.url.contains("/target?") -> DriveResponse(200, metadata!!.toString().toByteArray())
                else -> DriveResponse(200, "{\"files\":[]}".toByteArray())
            }
        }
        private fun digestOf(bytes: ByteArray, algorithm: String) = MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Test fun httpFailuresCannotBecomeBackupSuccessOrPruneFiles() {
        val bytes = DriveSnapshot.encode("{\"version\":1}", 1)
        for (code in listOf(401, 403, 429, 500)) {
            val fake = TestOnlyDrive(bytes).apply { status = code }
            val failure = assertThrows(DriveHttpException::class.java) { DriveApi(fake).backup("test-memory-only", bytes, 1) }
            assertEquals(code, failure.code)
            assertEquals(0, fake.deletes)
        }
    }

    @Test fun corruptMediaChecksumOrWrongTargetCannotMarkVerifiedOrPrune() {
        val bytes = DriveSnapshot.encode("{\"version\":1}", 1)
        for (mode in 0..2) {
            val fake = TestOnlyDrive(bytes).apply {
                if (mode == 0) media = "corrupted".toByteArray()
                if (mode == 1) wrongChecksum = true
                if (mode == 2) wrongTarget = true
            }
            assertThrows(IllegalStateException::class.java) { DriveApi(fake).backup("test-memory-only", bytes, 1) }
            assertEquals(0, fake.patches)
            assertEquals(0, fake.deletes)
        }
    }

    @Test fun noRecognizedSnapshotCannotRestoreArbitraryJson() {
        val fake = TestOnlyDrive(DriveSnapshot.encode("{\"version\":1}", 1))
        assertThrows(IllegalStateException::class.java) { DriveApi(fake).restoreLatest("test-memory-only") }
    }

    @Test fun uploadReadsBackExactTargetBeforeReturningSuccess() {
        val bytes = DriveSnapshot.encode("{\"version\":1}", 1)
        val requests = mutableListOf<DriveRequest>()
        var meta: JSONObject? = null
        val transport = DriveTransport { req ->
            requests.add(req)
            assertTrue(req.url.startsWith("https://www.googleapis.com/"))
            assertEquals("Bearer ephemeral", req.headers["Authorization"])
            when {
                req.method == "POST" -> {
                    val multipart = req.body!!.toString(Charsets.UTF_8)
                    val name = Regex("tamalitos-v1-[0-9]+-[a-f0-9-]+\\.json").find(multipart)!!.value
                    assertTrue(multipart.contains("appDataFolder"))
                    assertTrue(multipart.contains(bytes.toString(Charsets.UTF_8)))
                    meta = metadata(bytes, name)
                    DriveResponse(200, "{\"id\":\"exact_uploaded_id\"}".toByteArray())
                }
                req.url.contains("alt=media") -> DriveResponse(200, bytes)
                req.method == "PATCH" -> {
                    meta!!.getJSONObject("appProperties").put("verified", "true")
                    DriveResponse(200, meta!!.toString().toByteArray())
                }
                req.url.contains("exact_uploaded_id") -> DriveResponse(200, meta!!.toString().toByteArray())
                else -> DriveResponse(200, "{\"files\":[]}".toByteArray())
            }
        }
        val result = DriveApi(transport).backup("ephemeral", bytes, 1)
        assertEquals("exact_uploaded_id", result.id)
        assertTrue(requests.any { it.url.contains("/exact_uploaded_id?alt=media") })
        assertEquals("true", meta!!.getJSONObject("appProperties").getString("verified"))
    }
}
