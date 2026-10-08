package com.tamalitos.malitos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

internal object DriveRuntime {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val WORK_NAME = "tamalitos-drive-backup"
    val operations = ReentrantLock()
    val io = Executors.newSingleThreadExecutor { task -> Thread(task, "drive-io").apply { isDaemon = true } }
    fun authorizationRequest(): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun periodicRequest(): androidx.work.PeriodicWorkRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(12, TimeUnit.HOURS)
        .setInitialDelay(12, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!DriveState.connected(context) || !DriveState.automatic(context)) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = periodicRequest()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun api(context: Context, epoch: Long, cancelled: () -> Boolean = { false }): DriveApi {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4)
        val https = DriveHttpsTransport()
        return DriveApi(DriveTransport { request ->
            checkCurrent(context, epoch)
            if (cancelled() || Thread.currentThread().isInterrupted) throw DriveCancelledException()
            if (System.nanoTime() >= deadline) throw IOException("Tiempo total de la operación de Drive agotado")
            val response = https.execute(request)
            checkCurrent(context, epoch)
            if (cancelled() || System.nanoTime() >= deadline) throw IOException("Operación de Drive detenida o agotada")
            response
        })
    }

    fun checkCurrent(context: Context, epoch: Long) {
        if (DriveState.epoch(context) != epoch) throw DriveCancelledException()
    }

    fun clearRejectedToken(context: Context, token: String) {
        // AuthorizationClient.clearToken is absent in the contract's auth 21.3.0.
        val task = java.util.concurrent.FutureTask<Unit> { com.google.android.gms.auth.GoogleAuthUtil.clearToken(context, token) }
        Thread(task, "drive-token-cache").apply { isDaemon = true }.start()
        try { task.get(30, TimeUnit.SECONDS) } catch (_: Exception) { task.cancel(true) }
    }

    fun failureMessage(error: Exception): String = when (error) {
        is DriveHttpException -> when (error.code) {
            401 -> "Drive rechazó la autorización (HTTP 401). Reconecta Google Drive."
            403 -> "Drive no permite esta operación (HTTP 403). Revisa permiso appdata, cuota y configuración de Google Cloud."
            429 -> "Drive limitó las solicitudes (HTTP 429). Intenta más tarde."
            else -> "No se completó la operación: Drive respondió HTTP ${error.code}."
        }
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "Respaldo inválido o no disponible."
        else -> "No se completó la operación de Drive. Revisa internet y la configuración de Google Cloud; los datos locales siguen disponibles."
    }
}

internal class DriveCancelledException : IOException("Operación cancelada al desconectar")
