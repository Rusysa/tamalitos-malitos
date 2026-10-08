package com.tamalitos.malitos

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

/** Preferences contain state only: never access/refresh tokens, auth codes or account claims. */
internal object DriveState {
    private val lock = Any()
    fun preferences(context: Context) = context.applicationContext.getSharedPreferences("drive_state", Context.MODE_PRIVATE)
    fun epoch(context: Context) = preferences(context).getLong("epoch", 0)
    fun nextAuthorizationRequest(context: Context, expectedEpoch: Long): Int = synchronized(lock) {
        check(epoch(context) == expectedEpoch) { "Autorización cancelada." }
        val prefs = preferences(context)
        val next = maxOf(prefs.getInt("last_auth_request", 9040), prefs.getInt("pending_request", 9040)) + 1
        require(next <= 65534) { "Se agotaron los intentos de autorización de esta instalación. Exporta tus datos antes de reinstalar." }
        check(prefs.edit().putInt("last_auth_request", next).commit()) { "No se pudo registrar el intento de autorización." }
        next
    }
    fun beginAuthorization(context: Context, action: String): Pair<Long, Int> = synchronized(lock) {
        val prefs = preferences(context)
        require(!prefs.contains("pending_action")) { "Hay una autorización pendiente; complétala o restablécela." }
        val previousEpoch = epoch(context)
        val request = nextAuthorizationRequest(context, previousEpoch)
        val nextEpoch = previousEpoch + 1
        check(prefs.edit().putLong("epoch", nextEpoch).putString("pending_action", action)
            .putLong("pending_epoch", nextEpoch).putInt("pending_request", request).commit()) { "No se pudo registrar la autorización pendiente." }
        nextEpoch to request
    }
    fun <T> withCurrent(context: Context, expectedEpoch: Long, block: () -> T): T = synchronized(lock) {
        if (epoch(context) != expectedEpoch) throw DriveCancelledException()
        block()
    }
    fun connected(context: Context) = preferences(context).getBoolean("connected", false)
    fun automatic(context: Context) = preferences(context).getBoolean("automatic", false)
    fun status(context: Context): String {
        val prefs = preferences(context)
        val state = when {
            prefs.contains("pending_action") -> "Autorización pendiente o interrumpida. Complétala o pulsa Restablecer autorización en Respaldo para volver a intentar."
            prefs.getBoolean("reconnect", false) -> "Reconecta Google Drive desde Respaldo; se necesita tu autorización."
            !connected(context) -> "Sin conectar a Google Drive. Configura Google Cloud y autoriza desde Respaldo."
            else -> "Google Drive conectado. " + if (automatic(context)) "Automático cada 12 h (aproximado, con red)." else "Respaldo automático desactivado."
        }
        val last = prefs.getLong("last_verified", 0)
        val time = if (last > 0) "\nÚltimo respaldo verificado: ${DateFormat.getDateTimeInstance().format(Date(last))}." else "\nTodavía no hay un respaldo verificado en este dispositivo."
        val notice = prefs.getString("notice", "").orEmpty()
        return state + time + if (notice.isEmpty()) "" else "\n$notice"
    }
    fun verified(context: Context, expectedEpoch: Long, timestamp: Long, warning: String?): Boolean = synchronized(lock) {
        if (epoch(context) != expectedEpoch) return false
        preferences(context).edit().putBoolean("connected", true).putBoolean("reconnect", false)
            .putLong("last_verified", timestamp).putString("notice", warning ?: "Respaldo subido y leído de vuelta; contenido verificado.")
            .remove("pending_action").remove("pending_epoch").commit()
    }
    fun notice(context: Context, expectedEpoch: Long, message: String) = synchronized(lock) {
        if (epoch(context) == expectedEpoch) preferences(context).edit().putString("notice", message).commit()
    }
    fun reconnect(context: Context, expectedEpoch: Long) = synchronized(lock) {
        if (epoch(context) == expectedEpoch) preferences(context).edit().putBoolean("connected", false)
            .putBoolean("reconnect", true).putString("notice", "No se completó el respaldo. Los datos locales siguen disponibles.").commit()
    }
    fun disconnect(context: Context) = synchronized(lock) {
        val nextEpoch = epoch(context) + 1
        // Last verified time refers to the formerly selected Drive and is intentionally cleared.
        val prefs = preferences(context)
        // Never reuse an Activity request code: an old result must not consume a new pending request.
        val lastRequest = maxOf(prefs.getInt("last_auth_request", 9041), prefs.getInt("pending_request", 9041))
        check(prefs.edit().clear().putLong("epoch", nextEpoch).putInt("last_auth_request", lastRequest).commit()) { "No se pudo restablecer Drive." }
    }
}

internal object DriveLocalSafety {
    fun save(context: Context, businessJson: String): File {
        val folder = File(context.filesDir, "drive-pre-restore")
        check(folder.isDirectory || folder.mkdirs()) { "No se pudo crear la copia local de seguridad" }
        val file = File(folder, "antes-restaurar-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json")
        val bytes = businessJson.toByteArray(Charsets.UTF_8)
        FileOutputStream(file).use { stream -> stream.write(bytes); stream.fd.sync() }
        check(file.readBytes().contentEquals(bytes)) { "No se pudo verificar la copia local previa" }
        return file
    }
}
