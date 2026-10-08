package com.tamalitos.malitos

import android.app.Activity
import android.app.Application
import android.content.DialogInterface
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class DriveControllerTest {
    private val activity get() = Robolectric.buildActivity(Activity::class.java).setup().get()
    @Before fun clearState() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        DriveState.preferences(context).edit().clear().commit()
        context.deleteDatabase("tamalitos.db")
        File(context.filesDir, "drive-pre-restore").deleteRecursively()
    }

    // Exercise the already downloaded/verified restore seam, without authorizing any real account.
    private fun confirm(controller: DriveController, context: android.content.Context, json: String) {
        val method = DriveController::class.java.getDeclaredMethod("confirmRestore", java.lang.Long.TYPE, DriveRestoreResult::class.java)
        method.isAccessible = true
        method.invoke(controller, DriveState.epoch(context), DriveRestoreResult("unit-test-only", "2026-01-01T00:00:00Z", json))
    }

    private fun withoutExportTime(json: String): String = org.json.JSONObject(json).apply { remove("exportedAt") }.toString()

    @Test fun cancellationDoesNotReplaceBusinessDataOrCreateSafetySnapshot() {
        val activity = activity
        val before = BusinessStore(activity).use { store -> store.saveCustomer(Customer(name = "Local")); store.exportBackup() }
        val controller = DriveController(activity, {}, { fail("Cancelar no debe restaurar") })
        confirm(controller, activity, before)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(withoutExportTime(before), withoutExportTime(BusinessStore(activity).use { it.exportBackup() }))
        assertFalse(File(activity.filesDir, "drive-pre-restore").exists())
    }

    @Test fun safetyCopyFailurePreventsDestructiveImport() {
        val activity = activity
        val before = BusinessStore(activity).use { store -> store.saveCustomer(Customer(name = "No perder")); store.exportBackup() }
        File(activity.filesDir, "drive-pre-restore").writeText("bloqueo de prueba")
        var restored = false
        val controller = DriveController(activity, {}, { restored = true })
        confirm(controller, activity, before)
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        DriveRuntime.io.submit {}.get(30, TimeUnit.SECONDS)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(restored)
        assertEquals(withoutExportTime(before), withoutExportTime(BusinessStore(activity).use { it.exportBackup() }))
    }

    @Test fun invalidBusinessSnapshotLeavesDatabaseIntactAndKeepsSafetyCopy() {
        val activity = activity
        val before = BusinessStore(activity).use { store -> store.saveCustomer(Customer(name = "Conservar")); store.exportBackup() }
        var restored = false
        val controller = DriveController(activity, {}, { restored = true })
        confirm(controller, activity, "{\"schemaVersion\":999}")
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        DriveRuntime.io.submit {}.get(30, TimeUnit.SECONDS)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(restored)
        assertEquals(withoutExportTime(before), withoutExportTime(BusinessStore(activity).use { it.exportBackup() }))
        assertEquals(1, File(activity.filesDir, "drive-pre-restore").listFiles()!!.size)
    }

    @Test fun confirmingRestorePreservesPreviousBusinessDataBeforeReplacement() {
        val activity = activity
        val before = BusinessStore(activity).use { store -> store.saveCustomer(Customer(name = "Anterior")); store.exportBackup() }
        val remote = BusinessStore(activity).use { store -> store.deleteCustomer(store.customers().single().id); store.saveCustomer(Customer(name = "Restaurado")); store.exportBackup() }
        BusinessStore(activity).use { it.importBackup(before) }
        var restored = false
        val controller = DriveController(activity, {}, { restored = true })
        confirm(controller, activity, remote)
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        DriveRuntime.io.submit {}.get(30, TimeUnit.SECONDS)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(restored)
        assertEquals("Restaurado", BusinessStore(activity).use { it.customers().single().name })
        val safety = File(activity.filesDir, "drive-pre-restore").listFiles()!!.single()
        assertEquals(withoutExportTime(before), withoutExportTime(safety.readText()))
        assertFalse(DriveController.isConnected(activity)) // Restore alone does not pretend to verify a new upload.
    }
}
