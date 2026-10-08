package com.tamalitos.malitos

import org.json.JSONObject

internal class DriveRequest(val method: String, val url: String, val headers: Map<String, String>, val body: ByteArray? = null)
internal data class DriveResponse(val code: Int, val body: ByteArray)
internal fun interface DriveTransport { fun execute(request: DriveRequest): DriveResponse }
internal data class DriveBackupResult(val id: String, val warning: String? = null)
internal data class DriveRestoreResult(val id: String, val createdTime: String, val businessJson: String)
internal class DriveApi(private val transport: DriveTransport) {
    private val base = "https://www.googleapis.com/drive/v3/files"
    private val fields = "id,name,mimeType,spaces,size,md5Checksum,createdTime,appProperties"

    fun backup(token: String, bytes: ByteArray, timestamp: Long): DriveBackupResult {
        DriveSnapshot.decode(bytes)
        val name = "tamalitos-v1-$timestamp-${java.util.UUID.randomUUID()}.json"
        val properties = JSONObject().put("format", DriveSnapshot.FORMAT).put("version", "1")
            .put("sha256", digest(bytes, "SHA-256")).put("verified", "false")
        val metadata = JSONObject().put("name", name).put("mimeType", "application/json")
            .put("parents", org.json.JSONArray().put("appDataFolder")).put("appProperties", properties)
        val boundary = "tamalitos_${java.util.UUID.randomUUID()}"
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray()
        val body = prefix + bytes + "\r\n--$boundary--\r\n".toByteArray()
        val upload = json(call(token, "POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", body, "multipart/related; boundary=$boundary"))
        val id = upload.getString("id")
        val target = "$base/${encode(id)}"
        val remote = json(call(token, "GET", "$target?fields=${encode(fields)}"))
        check(remote.getString("id") == id && remote.getString("name") == name) { "Drive devolvió otro archivo" }
        check(remote.getString("mimeType") == "application/json" && remote.getLong("size") == bytes.size.toLong()) { "Metadatos de respaldo incorrectos" }
        val checksum = remote.optString("md5Checksum")
        check(checksum.isEmpty() || checksum.equals(digest(bytes, "MD5"), true)) { "Checksum remoto incorrecto" }
        val media = call(token, "GET", "$target?alt=media")
        check(media.contentEquals(bytes)) { "El respaldo leído desde Drive no coincide" }
        val patch = JSONObject().put("appProperties", properties.put("verified", "true")).toString().toByteArray()
        call(token, "PATCH", "$target?fields=id", patch)
        val committed = json(call(token, "GET", "$target?fields=${encode(fields)}"))
        check(owned(committed) && committed.getString("id") == id && committed.getString("name") == name &&
            committed.getLong("size") == bytes.size.toLong() && committed.getJSONObject("appProperties").getString("sha256") == digest(bytes, "SHA-256") &&
            (committed.optString("md5Checksum").isEmpty() || committed.optString("md5Checksum").equals(digest(bytes, "MD5"), true))) {
            "Drive no confirmó la verificación del archivo exacto"
        }
        val warning = try {
            val older = listOwned(token).filter { it.getString("id") != id }
            older.drop(9).forEach { candidate ->
                val oldId = candidate.getString("id")
                // Read the exact target again; never delete a file that changed ownership.
                val exact = json(call(token, "GET", "$base/${encode(oldId)}?fields=${encode(fields)}"))
                check(owned(exact) && exact.getString("id") == oldId) { "Archivo de retención cambió" }
                call(token, "DELETE", "$base/${encode(oldId)}")
            }
            null
        } catch (_: Exception) { "Respaldo verificado; no se pudo completar la limpieza de copias antiguas" }
        return DriveBackupResult(id, warning)
    }

    fun restoreLatest(token: String): DriveRestoreResult {
        val latest = listOwned(token).firstOrNull() ?: error("No hay respaldos verificados de esta aplicación en la cuenta seleccionada")
        val id = latest.getString("id")
        val target = "$base/${encode(id)}"
        val exact = json(call(token, "GET", "$target?fields=${encode(fields)}"))
        check(owned(exact) && exact.getString("id") == id) { "El respaldo ya no está disponible o no es reconocido" }
        val bytes = call(token, "GET", "$target?alt=media")
        check(exact.getLong("size") == bytes.size.toLong()) { "Tamaño de respaldo incorrecto" }
        check(exact.getJSONObject("appProperties").getString("sha256") == digest(bytes, "SHA-256")) { "Checksum de respaldo incorrecto" }
        val md5 = exact.optString("md5Checksum")
        check(md5.isEmpty() || md5.equals(digest(bytes, "MD5"), true)) { "Checksum de Drive incorrecto" }
        return DriveRestoreResult(id, exact.getString("createdTime"), DriveSnapshot.decode(bytes))
    }

    private fun listOwned(token: String): List<JSONObject> {
        val all = linkedMapOf<String, JSONObject>()
        var page = ""
        val seen = mutableSetOf<String>()
        repeat(20) {
            val url = "$base?spaces=appDataFolder&pageSize=100&orderBy=${encode("createdTime desc")}&q=${encode("trashed = false")}&fields=${encode("nextPageToken,files($fields)")}" +
                if (page.isEmpty()) "" else "&pageToken=${encode(page)}"
            val response = json(call(token, "GET", url))
            val files = response.getJSONArray("files")
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                if (owned(file)) all[file.getString("id")] = file
            }
            page = response.optString("nextPageToken")
            if (page.isEmpty()) return all.values.sortedByDescending { java.time.Instant.parse(it.getString("createdTime")) }
            check(seen.add(page)) { "Paginación repetida de Drive" }
        }
        error("Demasiados archivos en Drive; no se realizó limpieza ni restauración")
    }

    private fun owned(file: JSONObject): Boolean {
        val props = file.optJSONObject("appProperties") ?: return false
        val spaces = file.optJSONArray("spaces") ?: return false
        return file.optString("id").isNotEmpty() && file.optString("mimeType") == "application/json" &&
            Regex("tamalitos-v1-[0-9]+-[a-f0-9-]{36}\\.json").matches(file.optString("name")) &&
            (0 until spaces.length()).any { spaces.optString(it) == "appDataFolder" } &&
            props.optString("format") == DriveSnapshot.FORMAT && props.optString("version") == "1" &&
            props.optString("verified") == "true" && Regex("[a-f0-9]{64}").matches(props.optString("sha256")) &&
            runCatching { java.time.Instant.parse(file.getString("createdTime")) }.isSuccess
    }

    private fun call(token: String, method: String, url: String, body: ByteArray? = null, contentType: String = "application/json; charset=UTF-8"): ByteArray {
        val response = transport.execute(DriveRequest(method, url, mapOf("Authorization" to "Bearer $token", "Content-Type" to contentType), body))
        if (response.code !in 200..299) throw DriveHttpException(response.code)
        check(response.body.size <= DriveSnapshot.MAX_BYTES) { "Respuesta de Drive demasiado grande" }
        return response.body
    }

    private fun json(bytes: ByteArray) = JSONObject(bytes.toString(Charsets.UTF_8))
    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
    private fun digest(bytes: ByteArray, algorithm: String) = java.security.MessageDigest.getInstance(algorithm)
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
internal class DriveHttpException(val code: Int) : java.io.IOException("Drive respondió HTTP $code")
