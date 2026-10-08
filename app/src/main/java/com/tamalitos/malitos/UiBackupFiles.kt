package com.tamalitos.malitos

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** No Android dependency: byte limit and atomic private safety-file publication. */
internal object UiBackupFiles {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun readBounded(input: InputStream, limit: Int = MAX_BYTES): String {
        require(limit > 0)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "El respaldo supera el límite de ${limit / 1024 / 1024} MB. No se modificó ningún dato." }
            output.write(buffer, 0, count)
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
    }

    fun safetyCopy(directory: File, json: String): File {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "La copia de seguridad actual supera el límite de 16 MB. No se restaurará el respaldo; exporta tus datos antes de continuar." }
        require(directory.isDirectory || directory.mkdirs()) { "No se pudo crear la carpeta de seguridad. No se restaurará el respaldo." }
        val id = "seguridad-${System.currentTimeMillis()}-${UUID.randomUUID()}"
        val pending = File(directory, "$id.tmp")
        val published = File(directory, "$id.json")
        try {
            FileOutputStream(pending).use { stream -> stream.write(bytes); stream.fd.sync() }
            require(pending.renameTo(published)) { "No se pudo guardar la copia de seguridad. No se restaurará el respaldo." }
            return published
        } finally { pending.delete() }
    }
}
