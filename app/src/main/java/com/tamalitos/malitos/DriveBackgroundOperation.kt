package com.tamalitos.malitos

import android.content.Context

internal class DriveBackgroundGrant(val resolutionNeeded: Boolean, val token: String?, val scopeGranted: Boolean)
internal enum class DriveBackgroundOutcome { COMPLETE, RECONNECT, INACTIVE }
internal class DriveBackgroundOperation(private val context: Context) {
    fun run(epoch: Long, authorize: () -> DriveBackgroundGrant, backup: (String) -> DriveBackupResult): DriveBackgroundOutcome {
        DriveRuntime.checkCurrent(context, epoch)
        if (!DriveState.connected(context) || !DriveState.automatic(context)) return DriveBackgroundOutcome.INACTIVE
        val grant = authorize()
        DriveRuntime.checkCurrent(context, epoch)
        val token = grant.token
        if (grant.resolutionNeeded || token.isNullOrBlank() || !grant.scopeGranted) {
            DriveState.reconnect(context, epoch)
            return DriveBackgroundOutcome.RECONNECT
        }
        val result = backup(token)
        DriveRuntime.checkCurrent(context, epoch)
        return if (DriveState.verified(context, epoch, System.currentTimeMillis(), result.warning))
            DriveBackgroundOutcome.COMPLETE else DriveBackgroundOutcome.INACTIVE
    }
}
