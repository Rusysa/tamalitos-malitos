package com.tamalitos.malitos

import org.json.JSONObject

/** Versioned transport envelope; business schema is validated atomically by BusinessStore. */
internal object DriveSnapshot {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val FORMAT = "com.tamalitos.malitos.drive"

    fun encode(businessJson: String, createdAt: Long): ByteArray {
        require(createdAt >= 0) { "Fecha de respaldo inválida" }
        JSONObject(businessJson)
        val bytes = JSONObject().put("format", FORMAT).put("version", 1)
            .put("createdAt", createdAt).put("businessJson", businessJson)
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Respaldo mayor de 20 MiB" }
        return bytes
    }

    fun decode(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "Respaldo mayor de 20 MiB" }
        try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            val envelope = JSONObject(text)
            require(envelope.get("format") == FORMAT && envelope.get("version") == 1) {
                "Formato o versión de respaldo no compatible"
            }
            require(envelope.getLong("createdAt") >= 0) { "Fecha de respaldo inválida" }
            val business = envelope.get("businessJson")
            require(business is String) { "Contenido de respaldo inválido" }
            JSONObject(business)
            return business
        } catch (e: Exception) {
            throw IllegalArgumentException("Respaldo inválido o incompatible", e)
        }
    }
}
