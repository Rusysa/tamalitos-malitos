package com.tamalitos.malitos

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class DriveBackgroundTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { DriveState.preferences(context).edit().clear().commit() }
    @Test fun automaticRequestIsTwelveHoursNetworkConstrainedAndContainsNoSecrets() {
        val request = DriveRuntime.periodicRequest()
        assertEquals(java.util.concurrent.TimeUnit.HOURS.toMillis(12), request.workSpec.intervalDuration)
        assertEquals(androidx.work.NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.input.keyValueMap.isEmpty())
        assertEquals(listOf(DriveRuntime.SCOPE), DriveRuntime.authorizationRequest().requestedScopes.map { it.scopeUri })
    }

    @Test fun disabledAutomaticNeverAuthorizesOrTouchesBusinessData() {
        val outcome = DriveBackgroundOperation(context).run(DriveState.epoch(context),
            authorize = { throw AssertionError("No debe pedir autorización") },
            backup = { throw AssertionError("No debe exportar datos") })
        assertEquals(DriveBackgroundOutcome.INACTIVE, outcome)
    }

    @Test fun consentResolutionInBackgroundRequiresReconnectWithoutBackupOrAnyUi() {
        DriveState.preferences(context).edit().putBoolean("automatic", true).commit()
        val epoch = DriveState.epoch(context)
        DriveState.verified(context, epoch, 100, null)
        var backups = 0
        val outcome = DriveBackgroundOperation(context).run(epoch,
            authorize = { DriveBackgroundGrant(true, null, false) },
            backup = { backups++; DriveBackupResult("never") })
        assertEquals(DriveBackgroundOutcome.RECONNECT, outcome)
        assertEquals(0, backups)
        assertFalse(DriveState.connected(context))
        assertTrue(DriveState.automatic(context))
        assertTrue(DriveState.status(context).contains("Reconecta"))
    }
}
