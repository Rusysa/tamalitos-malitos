package com.tamalitos.malitos

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** Production never accepts HTTP, alternate hosts, redirects or custom trust managers. */
internal class DriveHttpsTransport : DriveTransport {
    override fun execute(request: DriveRequest): DriveResponse {
        val url = URL(request.url)
        require(url.protocol == "https" && url.host == "www.googleapis.com" &&
            url.userInfo == null && (url.port == -1 || url.port == 443)) { "Destino HTTPS de Drive inválido" }
        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = request.method
        connection.useCaches = false
        request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        val expired = AtomicBoolean(false)
        // Includes writes and slow/trickling reads, not just socket inactivity.
        val watchdog = deadlines.schedule({ expired.set(true); connection.disconnect() }, 90, TimeUnit.SECONDS)
        try {
            request.body?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val limit = if (status in 200..299) DriveSnapshot.MAX_BYTES else 64 * 1024
            val output = ByteArrayOutputStream()
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            input?.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    if (expired.get() || Thread.currentThread().isInterrupted) throw IOException("Tiempo de espera de Drive agotado")
                    val count = stream.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > limit) throw IOException("Respuesta de Drive demasiado grande")
                    output.write(buffer, 0, count)
                }
            }
            if (expired.get()) throw IOException("Tiempo de espera de Drive agotado")
            return DriveResponse(status, output.toByteArray())
        } finally {
            watchdog.cancel(false)
            connection.disconnect()
        }
    }

    companion object {
        private val deadlines = Executors.newScheduledThreadPool(2) { runnable ->
            Thread(runnable, "drive-deadline").apply { isDaemon = true }
        }
    }
}
