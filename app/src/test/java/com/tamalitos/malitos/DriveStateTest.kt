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
class DriveStateTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { context.getSharedPreferences("drive_state", 0).edit().clear().commit() }

    @Test fun permissionAloneDoesNotFabricateConnectedState() {
        assertFalse(DriveController.isConnected(context))
        assertFalse(DriveController.isAutomatic(context))
        assertTrue(DriveController.status(context).contains("Sin conectar"))
        assertFalse(context.getSharedPreferences("drive_state", 0).contains("token"))
    }

    @Test fun reconnectNeededPreservesAutomaticIntentButDisconnectClearsIt() {
        DriveState.preferences(context).edit().putBoolean("automatic", true).commit()
        val epoch = DriveState.epoch(context)
        DriveState.verified(context, epoch, 100, null)
        assertTrue(DriveController.isConnected(context))
        DriveState.reconnect(context, epoch)
        assertFalse(DriveController.isConnected(context))
        assertTrue(DriveController.isAutomatic(context))
        assertTrue(DriveController.status(context).contains("Reconecta"))
        DriveState.disconnect(context)
        assertFalse(DriveController.isConnected(context))
        assertFalse(DriveController.isAutomatic(context))
        assertFalse(DriveState.verified(context, epoch, 200, null))
    }

    @Test fun localSafetyCopyIsDurableExactBusinessJsonBeforeRestore() {
        val json = "{\"version\":1,\"customers\":[]}"
        val file = DriveLocalSafety.save(context, json)
        assertEquals(json, file.readText(Charsets.UTF_8))
        assertEquals(context.filesDir.canonicalFile, file.parentFile!!.parentFile!!.canonicalFile)
    }
}
