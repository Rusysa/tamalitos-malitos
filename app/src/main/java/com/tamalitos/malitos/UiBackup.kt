package com.tamalitos.malitos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import java.io.File
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val EXPORT_LOCAL = 3101
private const val IMPORT_LOCAL = 3102
private const val EXPORT_SAFETY = 3103
private const val LOCAL_PREFS = "ui_local_backup"

/** The operation survives Activity recreation without retaining an Activity. */
internal object UiBackupJobs {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var active = WeakReference<MainActivity>(null)
    val isRunning get() = running.get()
    fun attach(activity: MainActivity) { active = WeakReference(activity) }
    fun detach(activity: MainActivity) { if (active.get() === activity) active.clear() }
    fun submit(activity: MainActivity, restoring: Boolean = false, job: (Context) -> String) {
        require(running.compareAndSet(false, true)) { "Hay una operación de respaldo en curso. Espera a que termine." }
        val context = activity.applicationContext
        activity.message("Procesando respaldo… Puedes esperar aquí.")
        executor.execute {
            val result = runCatching { job(context) }
            val value = result.getOrElse { "No se completó la operación: ${it.message ?: "error de lectura o escritura"}" }
            context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE).edit().putString("status", value).commit()
            running.set(false)
            Handler(Looper.getMainLooper()).post {
                active.get()?.takeUnless { it.isDestroyed || it.isFinishing }?.let { current ->
                    if (result.isSuccess) {
                        current.message(value)
                        if (restoring) current.onBusinessRestored() else if (current.screen == "Respaldo") current.render()
                    } else {
                        current.error(value)
                        if (current.screen == "Respaldo") current.render()
                    }
                }
            }
        }
        if (activity.screen == "Respaldo") activity.render()
    }
}

private fun safetyDirectory(context: Context) = File(context.filesDir, "respaldos-seguridad")
private fun safetyCopies(context: Context): List<File> =
    listOf(safetyDirectory(context), File(context.filesDir, "drive-pre-restore")).flatMap { directory ->
        directory.listFiles()?.filter { it.isFile && it.extension == "json" }.orEmpty()
    }.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })

@androidx.compose.runtime.Composable internal fun Backup(a: MainActivity) {
    a.backupRevision // observe status changes after retained jobs
    Page {
        Heading("Respaldo y restauración", "Copias de seguridad, no sincronización entre dispositivos")
        InfoCard {
            androidx.compose.material3.Text("Respaldo local", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            androidx.compose.material3.Text("Archivo JSON sin internet ni cuenta de Google. Contiene datos personales y financieros: guárdalo en un lugar seguro.")
            androidx.compose.material3.Text(a.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE).getString("status", "Aún no se ha completado un respaldo local.").orEmpty())
            if(UiBackupJobs.isRunning) androidx.compose.material3.Text("Operación de respaldo en curso…")
            Action("Exportar respaldo local", true, !UiBackupJobs.isRunning) { a.selectExport(false) }
            Action("Importar respaldo local", enabled = !UiBackupJobs.isRunning) { a.selectImport() }
            if(safetyCopies(a).isNotEmpty()) {
                androidx.compose.material3.Text("Copias previas locales y de Drive. Expórtalas antes de desinstalar la app.")
                Action("Exportar copia de seguridad previa", enabled = !UiBackupJobs.isRunning) { a.selectExport(true) }
            }
            androidx.compose.material3.Text("Importar reemplaza todos los datos. Antes se guarda una copia privada del estado actual. Si falla la validación, no cambian los registros.")
        }
        InfoCard {
            androidx.compose.material3.Text("Google Drive", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            androidx.compose.material3.Text(DriveController.status(a))
            androidx.compose.material3.Text("Requiere internet, Play services y OAuth real (paquete y SHA-1) en Google Cloud. No está configurada una cuenta real en este proyecto.")
            val connected = DriveController.isConnected(a)
            Action(if(connected) "Respaldar ahora en Google Drive" else "Autorizar Google Drive y respaldar", true) { a.drive.connectAndBackup() }
            androidx.compose.material3.Text("Respaldo automático cada 12 horas")
            androidx.compose.material3.Switch(DriveController.isAutomatic(a), { enabled -> a.guarded {
                DriveController.setAutomatic(a, enabled)
                require(DriveController.isAutomatic(a) == enabled) { "No se pudo confirmar el cambio del respaldo automático." }
                a.render()
            } }, modifier = Modifier.semantics { contentDescription = "Respaldo automático cada 12 horas" }, enabled = connected)
            if(!connected) androidx.compose.material3.Text("Completa un respaldo verificado para activar el respaldo automático.")
            Action("Restaurar último respaldo de Drive") { a.drive.restoreLatest() }
            Action(if(connected) "Desconectar Google Drive" else "Restablecer autorización de Google Drive") {
                a.confirm("¿Desconectar Google Drive?", "Se detienen respaldos automáticos y se borra la conexión local. Tus datos y copias remotas no se eliminan.", "Desconectar") { a.drive.disconnect(); a.render() }
            }
        }
    }
}
internal fun MainActivity.selectExport(safety: Boolean) {
    if(safety) { safetyChoices = safetyCopies(this); require(safetyChoices.isNotEmpty()) { "No hay copias previas para exportar." } }
    else selectExportDocument(false)
}

internal fun MainActivity.selectExportDocument(safety: Boolean) {
    require(!UiBackupJobs.isRunning) { "Espera a que termine el respaldo en curso." }
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        putExtra(Intent.EXTRA_TITLE, "tamalitos-${if (safety) "seguridad-" else ""}${LocalDate.now()}.json")
    }, if (safety) EXPORT_SAFETY else EXPORT_LOCAL)
}

internal fun MainActivity.selectImport() {
    pendingDocumentGeneration = store.generation
    require(!UiBackupJobs.isRunning) { "Espera a que termine el respaldo en curso." }
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }, IMPORT_LOCAL)
}

internal fun MainActivity.handleLocalBackupResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode !in listOf(EXPORT_LOCAL, IMPORT_LOCAL, EXPORT_SAFETY)) return
    if (resultCode != Activity.RESULT_OK) { if (requestCode == EXPORT_SAFETY) pendingSafetyPath = null; message("Operación de respaldo cancelada. Tus datos no cambiaron."); return }
    val uri = data?.data ?: run { error("No se recibió un archivo válido."); return }
    val selectedSafetyPath = pendingSafetyPath
    if (requestCode == EXPORT_SAFETY) pendingSafetyPath = null
    guarded {
        when (requestCode) {
            EXPORT_LOCAL, EXPORT_SAFETY -> UiBackupJobs.submit(this) { context ->
                val json = if (requestCode == EXPORT_SAFETY) {
                    val file = selectedSafetyPath?.let { File(it).canonicalFile }
                        ?: throw IllegalArgumentException("No se eligió una copia previa para exportar.")
                    require(safetyCopies(context).any { it.canonicalFile == file }) { "La copia previa elegida ya no está disponible." }
                    file.inputStream().use { UiBackupFiles.readBounded(it) }
                } else BusinessStore(context).use { it.exportBackup() }
                val bytes = json.toByteArray(Charsets.UTF_8)
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream -> stream.write(bytes); stream.flush() }
                    ?: throw IllegalArgumentException("No se pudo abrir el archivo para escribir.")
                // Never report success solely from an OutputStream: read back the exact target.
                val written = context.contentResolver.openInputStream(uri)?.use { UiBackupFiles.readBounded(it) }
                    ?: throw IllegalArgumentException("No fue posible verificar el archivo exportado.")
                require(written == json) { "El archivo exportado no coincide con el respaldo. Intenta guardarlo de nuevo." }
                "Respaldo local exportado y verificado (${LocalDate.now()})."
            }
            IMPORT_LOCAL -> {
                if (((data?.flags ?: 0) and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                    try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { /* transient grant remains valid */ }
                }
                store.withGeneration(pendingDocumentGeneration) { confirmLocalImport(uri, pendingDocumentGeneration) }
            }
        }
    }
}

internal fun MainActivity.confirmLocalImport(uri: Uri, expectedGeneration: Long = store.generation) {
    pendingImportGeneration = expectedGeneration
    pendingImportUri = uri.toString()
    confirmation = UiConfirmation("¿Reemplazar todos los datos?",
        "El archivo reemplazará clientes, pedidos, pagos, gastos y catálogo. Antes se guarda una copia privada. Un archivo inválido no cambia ningún dato. Esto NO combina dispositivos.",
        "Restaurar archivo", expectedGeneration, {
            pendingImportUri = null
            UiBackupJobs.submit(this, restoring = true) { context ->
                val json = context.contentResolver.openInputStream(uri)?.use { UiBackupFiles.readBounded(it) }
                    ?: throw IllegalArgumentException("No se pudo abrir el respaldo seleccionado.")
                BusinessStore(context).use { database ->
                    var safety: File? = null
                    database.restoreBackupWithSafety(json, expectedGeneration) { prior -> safety = UiBackupFiles.safetyCopy(safetyDirectory(context), prior) }
                    "Restauración completada. Copia previa guardada: ${checkNotNull(safety).name}."
                }
            }
        }, { pendingImportUri = null })
}
