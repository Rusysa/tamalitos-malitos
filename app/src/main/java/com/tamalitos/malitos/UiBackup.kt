package com.tamalitos.malitos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Switch
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

internal fun MainActivity.backupScreen() {
    title("Respaldo y restauración", "Un dispositivo. Copias de seguridad, no sincronización simultánea.")
    body.addView(card().apply {
        addView(text("Respaldo local", 21, MainActivity.GREEN, true))
        addView(text("Exporta un archivo JSON donde elijas. Funciona sin internet y sin cuenta de Google. Contiene datos personales y financieros: guárdalo en un lugar seguro.", 15))
        addView(text(getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE).getString("status", "Aún no se ha completado un respaldo local.").orEmpty(), 14))
        if (UiBackupJobs.isRunning) addView(text("Operación de respaldo en curso…", 16, MainActivity.GREEN, true))
        addView(button("Exportar respaldo local", true) { selectExport(false) }.apply { isEnabled = !UiBackupJobs.isRunning })
        addView(button("Importar respaldo local") { selectImport() }.apply { isEnabled = !UiBackupJobs.isRunning })
        safetyCopies(this@backupScreen).firstOrNull()?.let { file ->
            addView(text("Hay copias previas a restauraciones locales o de Drive. Más reciente: ${file.name}. Elige cuál exportar; se conservan dentro de la app, expórtalas antes de desinstalar.", 14))
            addView(button("Exportar copia de seguridad previa") { selectExport(true) }.apply { isEnabled = !UiBackupJobs.isRunning })
        }
        addView(text("Importar reemplaza todos los datos. Antes se crea una copia privada del estado actual. Si la validación falla, no se cambian los registros.", 15))
    })
    body.addView(card().apply {
        addView(text("Google Drive", 21, MainActivity.GREEN, true))
        addView(text(DriveController.status(this@backupScreen), 15))
        addView(text("Requiere internet y configuración OAuth real (paquete y SHA-1) en Google Cloud. Sin esa configuración no se puede autorizar ni afirmar que hubo un respaldo.", 14))
        val connected = DriveController.isConnected(this@backupScreen)
        addView(button(if (connected) "Respaldar ahora en Google Drive" else "Autorizar Google Drive y respaldar", true) { drive.connectAndBackup() })
        val automatic = Switch(this@backupScreen).apply {
            text = "Respaldo automático cada 12 horas"
            contentDescription = text
            setTextColor(MainActivity.GREEN); minHeight = dp(48)
            isChecked = DriveController.isAutomatic(this@backupScreen)
            isEnabled = connected
            setOnCheckedChangeListener { _, enabled -> guarded {
                DriveController.setAutomatic(this@backupScreen, enabled)
                require(DriveController.isAutomatic(this@backupScreen) == enabled) { "No se pudo confirmar el cambio del respaldo automático." }
                message(if (enabled) "Respaldo automático activado. Requiere conexión de red y autorización vigente." else "Respaldo automático desactivado.")
            } }
        }
        addView(automatic)
        if (!connected) addView(text("Primero autoriza y completa un respaldo verificado para activar el respaldo automático.", 14))
        addView(button("Restaurar último respaldo de Drive") { drive.restoreLatest() })
        addView(button(if (connected) "Desconectar Google Drive" else "Restablecer autorización de Google Drive") {
            confirm("¿Desconectar Google Drive?", "Se detienen los respaldos automáticos y se borra la conexión local. Tus datos del dispositivo y las copias remotas no se eliminan.", "Desconectar") {
                drive.disconnect(); render()
            }
        })
    })
}

private fun MainActivity.selectExport(safety: Boolean) {
    if (safety) {
        val copies = safetyCopies(this)
        require(copies.isNotEmpty()) { "No hay copias previas para exportar." }
        val chooser = android.app.AlertDialog.Builder(this).setTitle("Elegir copia de seguridad previa")
            .setItems(copies.map { "${it.parentFile!!.name}/${it.name}" }.toTypedArray()) { _, index -> guarded {
                pendingSafetyPath = copies[index].canonicalPath
                selectExportDocument(true)
            } }.setNegativeButton("Volver", null).create()
        transientDialogs.add(chooser)
        chooser.setOnDismissListener { transientDialogs.remove(chooser) }
        chooser.show()
    } else selectExportDocument(false)
}

private fun MainActivity.selectExportDocument(safety: Boolean) {
    require(!UiBackupJobs.isRunning) { "Espera a que termine el respaldo en curso." }
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        putExtra(Intent.EXTRA_TITLE, "tamalitos-${if (safety) "seguridad-" else ""}${LocalDate.now()}.json")
    }, if (safety) EXPORT_SAFETY else EXPORT_LOCAL)
}

private fun MainActivity.selectImport() {
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
                confirmLocalImport(uri)
            }
        }
    }
}

internal fun MainActivity.confirmLocalImport(uri: Uri) {
    val expectedGeneration = store.generation
    pendingImportGeneration = expectedGeneration
    pendingImportUri = uri.toString()
    val created = android.app.AlertDialog.Builder(this).setTitle("¿Reemplazar todos los datos?")
        .setMessage("El archivo elegido reemplazará clientes, pedidos, pagos, gastos y catálogo. Antes se guardará una copia privada del estado actual. Si el archivo es inválido, no se cambiará ningún dato. Esto NO combina dos dispositivos.")
        .setNegativeButton("Volver") { _, _ -> pendingImportUri = null }
        .setPositiveButton("Restaurar archivo") { _, _ ->
            pendingImportUri = null
            guarded {
                store.withGeneration(expectedGeneration) { UiBackupJobs.submit(this, restoring = true) { context ->
                    val json = context.contentResolver.openInputStream(uri)?.use { UiBackupFiles.readBounded(it) }
                        ?: throw IllegalArgumentException("No se pudo abrir el respaldo seleccionado.")
                    BusinessStore(context).use { database ->
                        var safety: File? = null
                        database.restoreBackupWithSafety(json, expectedGeneration) { prior -> safety = UiBackupFiles.safetyCopy(safetyDirectory(context), prior) }
                        "Restauración completada. Copia previa guardada: ${checkNotNull(safety).name}."
                    }
                } }
            }
        }.create()
    created.setOnCancelListener { pendingImportUri = null }
    importDialog = created
    created.show()
}
