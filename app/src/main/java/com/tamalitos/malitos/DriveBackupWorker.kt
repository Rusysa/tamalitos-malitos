package com.tamalitos.malitos

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** No UI, no refresh tokens: acquire an ephemeral token after earlier user consent. */
class DriveBackupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val context = applicationContext
        if (!DriveState.connected(context) || !DriveState.automatic(context)) return Result.success()
        val epoch = DriveState.epoch(context)
        if (!DriveRuntime.operations.tryLock(30, TimeUnit.SECONDS)) return Result.retry()
        try {
            DriveRuntime.checkCurrent(context, epoch)
            if (isStopped || !DriveState.automatic(context)) return Result.success()
            val client = Identity.getAuthorizationClient(context)
            val outcome = DriveBackgroundOperation(context).run(epoch,
                authorize = {
                    val authorization = Tasks.await(client.authorize(DriveRuntime.authorizationRequest()), 30, TimeUnit.SECONDS)
                    DriveBackgroundGrant(authorization.hasResolution(), authorization.accessToken,
                        authorization.grantedScopes.contains(DriveRuntime.SCOPE))
                },
                backup = { token ->
                    try {
                        if (isStopped || !DriveState.automatic(context)) throw DriveCancelledException()
                        val now = System.currentTimeMillis()
                        val snapshot = BusinessStore(context).use { DriveSnapshot.encode(it.exportBackup(), now) }
                        val result = DriveRuntime.api(context, epoch) { isStopped || !DriveState.automatic(context) }.backup(token, snapshot, now)
                        if (isStopped) throw DriveCancelledException()
                        result
                    } catch (e: DriveHttpException) {
                        if (e.code == 401) DriveRuntime.clearRejectedToken(context, token)
                        throw e
                    }
                })
            if (outcome == DriveBackgroundOutcome.RECONNECT) {
                DriveRuntime.schedule(context)
                return Result.failure()
            }
            return Result.success()
        } catch (_: DriveCancelledException) {
            return Result.failure()
        } catch (e: Exception) {
            val cause = (e as? java.util.concurrent.ExecutionException)?.cause ?: e
            val apiCode = (cause as? com.google.android.gms.common.api.ApiException)?.statusCode
            if (cause is DriveHttpException && cause.code == 401 || apiCode == 4 || apiCode == 16) {
                DriveState.reconnect(context, epoch)
                DriveRuntime.schedule(context)
                return Result.failure()
            }
            DriveState.notice(context, epoch, DriveRuntime.failureMessage(e))
            // Do not blindly retry consent/configuration failures or permanent HTTP failures.
            val retryable = e is TimeoutException || e is java.io.IOException &&
                (e !is DriveHttpException || e.code == 429 || e.code >= 500)
            return if (retryable && runAttemptCount < 3 && !isStopped) Result.retry() else Result.failure()
        } finally {
            DriveRuntime.operations.unlock()
        }
    }
}
