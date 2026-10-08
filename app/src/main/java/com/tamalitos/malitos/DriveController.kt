package com.tamalitos.malitos

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import java.util.concurrent.TimeUnit

class DriveController(
    private val activity: Activity,
    private val onStatus: (String) -> Unit,
    private val onRestored: () -> Unit,
    private val composeConfirmation: ((String, String, () -> Unit, () -> Unit) -> Unit)? = null
) {
    private val context = activity.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val client = Identity.getAuthorizationClient(context)
    private var busy = false
    private enum class Action { BACKUP, RESTORE }

    fun connectAndBackup() = authorize(Action.BACKUP)
    fun restoreLatest() = authorize(Action.RESTORE)

    fun disconnect() {
        DriveState.disconnect(context)
        busy = false
        try { DriveRuntime.schedule(context) } catch (_: Exception) {
            DriveState.notice(context, DriveState.epoch(context), "Autorización restablecida. No se pudo actualizar el programador; vuelve a abrir la aplicación.")
        }
        publish()
    }

    private fun authorize(action: Action) {
        if (busy || DriveState.preferences(context).contains("pending_action")) {
            show("Ya hay una operación de Drive en curso. Completa o cancela la autorización.")
            return
        }
        busy = true
        val (epoch, requestCode) = try { DriveState.beginAuthorization(context, action.name) } catch (e: Exception) {
            busy = false; show(e.message ?: "No se pudo iniciar la autorización."); return
        }
        DriveState.notice(context, epoch, "Solicitando autorización a Google; aún no se ha completado esta operación.")
        publish()
        var completed = false
        val timeout = Runnable {
            if (!completed && DriveState.epoch(context) == epoch) {
                completed = true
                fail(epoch, "Google no respondió a tiempo. Revisa Play services y vuelve a intentar.")
            }
        }
        main.postDelayed(timeout, 30_000)
        client.authorize(DriveRuntime.authorizationRequest())
            .addOnSuccessListener { result ->
                if (completed || DriveState.epoch(context) != epoch) return@addOnSuccessListener
                completed = true
                main.removeCallbacks(timeout)
                if (result.hasResolution()) {
                    if (!alive()) { busy = false; return@addOnSuccessListener }
                    val pending = result.pendingIntent
                    if (pending == null) { fail(epoch, "Google requiere autorización; vuelve a conectar."); return@addOnSuccessListener }
                    try {
                        activity.startIntentSenderForResult(pending.intentSender, requestCode, null, 0, 0, 0)
                    } catch (_: Exception) {
                        clearPending()
                        fail(epoch, "No se pudo abrir la autorización de Google. Revisa Play services y la configuración de Google Cloud.")
                    }
                } else authorized(action, epoch, result)
            }
            .addOnFailureListener { error ->
                if (completed || DriveState.epoch(context) != epoch) return@addOnFailureListener
                completed = true
                main.removeCallbacks(timeout)
                val code = (error as? ApiException)?.statusCode
                fail(epoch, "No se pudo autorizar Google Drive${code?.let { " (código $it)" }.orEmpty()}. Configura el cliente Android para com.tamalitos.malitos y el SHA-1 de este APK; revisa Play services y vuelve a conectar.")
            }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode !in REQUEST_AUTHORIZATION..65534) return false
        val prefs = DriveState.preferences(context)
        if (!prefs.contains("pending_action") || requestCode != prefs.getInt("pending_request", REQUEST_AUTHORIZATION)) {
            show("Resultado de una autorización anterior ignorado. La operación actual no cambió.")
            return true
        }
        val actionName = prefs.getString("pending_action", null)
        val epoch = prefs.getLong("pending_epoch", -1)
        clearPending()
        if (epoch != DriveState.epoch(context) || actionName == null) {
            busy = false
            show("La operación de autorización ya no está activa. Vuelve a pulsar la acción deseada.")
            return true
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            fail(epoch, "Autorización cancelada. No se realizó ningún respaldo ni restauración.")
            return true
        }
        try {
            authorized(Action.valueOf(actionName), epoch, client.getAuthorizationResultFromIntent(data))
        } catch (_: Exception) {
            fail(epoch, "Google no devolvió una autorización válida. Vuelve a conectar Google Drive.")
        }
        return true
    }

    private fun clearPending(expectedEpoch: Long = DriveState.epoch(context)) {
        DriveState.withCurrent(context, expectedEpoch) {
            val prefs = DriveState.preferences(context)
            if (prefs.getLong("pending_epoch", -1) == expectedEpoch)
                prefs.edit().remove("pending_action").remove("pending_epoch").remove("pending_request").commit()
        }
    }

    private fun authorized(action: Action, epoch: Long, result: AuthorizationResult) {
        if (DriveState.epoch(context) != epoch) return
        clearPending(epoch)
        val token = result.accessToken
        if (result.hasResolution() || token.isNullOrBlank() || !result.grantedScopes.contains(DriveRuntime.SCOPE)) {
            DriveState.reconnect(context, epoch)
            DriveRuntime.schedule(context)
            busy = false
            publish()
            return
        }
        busy = true
        DriveState.notice(context, epoch, if (action == Action.BACKUP) "Subiendo respaldo; pendiente de verificación." else "Buscando y verificando el respaldo de Drive; todavía no se cambiaron datos locales.")
        publish()
        DriveRuntime.io.execute {
            if (!DriveRuntime.operations.tryLock(30, TimeUnit.SECONDS)) {
                main.post { fail(epoch, "Otra operación de Drive sigue en curso. Intenta nuevamente.") }
                return@execute
            }
            try {
                DriveRuntime.checkCurrent(context, epoch)
                val api = DriveRuntime.api(context, epoch)
                if (action == Action.BACKUP) {
                    // SQLite is closed before any HTTPS call; business operations never depend on the network.
                    val now = System.currentTimeMillis()
                    val bytes = BusinessStore(context).use { DriveSnapshot.encode(it.exportBackup(), now) }
                    val backup = api.backup(token, bytes, now)
                    if (DriveState.verified(context, epoch, System.currentTimeMillis(), backup.warning)) {
                        try { DriveRuntime.schedule(context) } catch (_: Exception) {
                            DriveState.notice(context, epoch, "Respaldo verificado; no se pudo programar WorkManager. Reabre la aplicación y revisa respaldo automático.")
                        }
                    }
                    main.post { busy = false; publish() }
                } else {
                    val restore = api.restoreLatest(token)
                    main.post { confirmRestore(epoch, restore) }
                }
            } catch (_: DriveCancelledException) {
                main.post { busy = false; publish() }
            } catch (error: Exception) {
                if (error is DriveHttpException && error.code == 401) {
                    DriveRuntime.clearRejectedToken(context, token)
                    DriveState.reconnect(context, epoch)
                    DriveRuntime.schedule(context)
                }
                main.post { fail(epoch, DriveRuntime.failureMessage(error)) }
            } finally {
                DriveRuntime.operations.unlock()
            }
        }
    }

    private fun confirmRestore(epoch: Long, restore: DriveRestoreResult) {
        if (!alive() || epoch != DriveState.epoch(context)) { busy = false; return }
        val expectedGeneration = BusinessStore(context).use { it.generation }
        var accepted = false
        composeConfirmation?.let { show ->
            show("¿Reemplazar todos los datos locales?",
                "Respaldo de Drive: ${restore.createdTime}. Se reemplazan clientes, productos, pedidos, pagos y gastos; no se fusionan datos. Los cambios posteriores se perderán. Antes se guarda una copia JSON local de seguridad, disponible en Exportar copia de seguridad previa.",
                { applyRestore(epoch, restore, expectedGeneration) },
                { busy = false; DriveState.notice(context, epoch, "Restauración cancelada; los datos locales no se cambiaron."); publish() })
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("¿Reemplazar todos los datos locales?")
            .setMessage("Respaldo de Drive: ${restore.createdTime}.\n\nSe reemplazarán clientes, productos, pedidos, pagos y gastos. No se fusionan datos. Los cambios posteriores a ese respaldo se perderán.\n\nAntes de aplicar se guardará una copia JSON local de seguridad. Para conservarla fuera de esta app, cancela y exporta tus datos locales primero.")
            .setNegativeButton("Cancelar") { _, _ -> }
            .setPositiveButton("Restaurar y reemplazar") { _, _ -> accepted = true; applyRestore(epoch, restore, expectedGeneration) }
            .setOnDismissListener {
                if (!accepted) { busy = false; DriveState.notice(context, epoch, "Restauración cancelada; los datos locales no se cambiaron."); publish() }
            }
            .show()
    }

    private fun applyRestore(epoch: Long, restore: DriveRestoreResult, expectedGeneration: Long) {
        DriveRuntime.io.execute {
            if (!DriveRuntime.operations.tryLock(30, TimeUnit.SECONDS)) {
                main.post { fail(epoch, "Otra operación de Drive sigue en curso. Intenta nuevamente.") }
                return@execute
            }
            try {
                DriveRuntime.checkCurrent(context, epoch)
                val safety = BusinessStore(context).use { store ->
                    var file: java.io.File? = null
                    // Same lock order as UI actions: business fence, then Drive epoch fence.
                    store.withGeneration(expectedGeneration) { DriveState.withCurrent(context, epoch) {
                        store.restoreBackupWithSafety(restore.businessJson, expectedGeneration) { json ->
                            file = DriveLocalSafety.save(context, json)
                        }
                    } }
                    checkNotNull(file)
                }
                DriveState.notice(context, epoch, "Datos restaurados desde Drive. Copia local anterior: drive-pre-restore/${safety.name}.")
                main.post { busy = false; publish(); if (alive()) onRestored() }
            } catch (error: Exception) {
                main.post { fail(epoch, DriveRuntime.failureMessage(error)) }
            } finally { DriveRuntime.operations.unlock() }
        }
    }

    private fun alive() = !activity.isFinishing && !activity.isDestroyed
    private fun show(message: String) { if (alive()) onStatus(message) }
    private fun publish() = show(status(context))
    private fun fail(epoch: Long, message: String) {
        if (DriveState.epoch(context) != epoch) return
        clearPending(epoch)
        busy = false
        DriveState.notice(context, epoch, message)
        publish()
    }

    companion object {
        private const val REQUEST_AUTHORIZATION = 9041
        fun status(context: Context): String = DriveState.status(context)
        fun isConnected(context: Context): Boolean = DriveState.connected(context)
        fun setAutomatic(context: Context, enabled: Boolean) {
            DriveState.preferences(context).edit().putBoolean("automatic", enabled).commit()
            DriveRuntime.schedule(context)
        }
        fun isAutomatic(context: Context): Boolean = DriveState.automatic(context)
    }
}
