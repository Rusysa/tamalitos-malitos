package com.tamalitos.malitos

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.DatePickerDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReviewGenerationTest {
    @Before fun clear() {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("tamalitos.db")
        DriveState.preferences(context).edit().clear().commit()
        java.io.File(context.filesDir, "drive-pre-restore").deleteRecursively()
    }
    private fun views(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Test fun stalePaymentFormCannotPayRestoredOrderWithReusedId() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val customer = a.store.saveCustomer(Customer(name = "Original"))
        val id = a.store.createOrder(customer, "2026-10-08", "", "", listOf(OrderItem(null, "Tamal", 1, 100)), InitialPayment.UNPAID)
        a.paymentForm(id)
        views(a.dialog!!.window!!.decorView).filterIsInstance<EditText>().first { it.contentDescription == "Importe del abono *" }.setText("1")
        shadowOf(Looper.getMainLooper()).idle()
        a.store.importBackup(a.store.exportBackup().replace("Original", "Restored"))
        a.dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(a.store.order(id)!!.payments.isEmpty()); assertEquals("Restored", a.store.order(id)!!.customerName)
        c.pause().stop().destroy()
    }
    @Test fun staleStatusDialogCannotChangeRestoredOrderWithReusedId() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val customer = a.store.saveCustomer(Customer(name = "Original"))
        val id = a.store.createOrder(customer, "2026-10-08", "", "", listOf(OrderItem(null, "Tamal", 1, 100)), InitialPayment.UNPAID)
        a.navigate("Pedidos", id)
        views(a.body).filterIsInstance<android.widget.Button>().first { it.text == "Cambiar estado" }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        shadowOf(dialog).clickOnItem(1); shadowOf(Looper.getMainLooper()).idle()
        a.store.importBackup(a.store.exportBackup().replace("Original", "Restored"))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(OrderStatus.PENDING, a.store.order(id)!!.status)
        c.pause().stop().destroy()
    }
    @Test fun calendarCallbackCapturedBeforeRestoreCannotChangeStaleDraft() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        a.store.saveCustomer(Customer(name = "Original"))
        val button = a.dateButton(a.column(), "Fecha", "2026-10-08"); button.performClick(); shadowOf(Looper.getMainLooper()).idle()
        val picker = ShadowAlertDialog.getLatestAlertDialog() as DatePickerDialog
        a.store.importBackup(a.store.exportBackup().replace("Original", "Restored"))
        picker.updateDate(2026, 11, 20); picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("2026-10-08", button.tag)
        c.pause().stop().destroy()
    }
    @Test fun successfulReplacementInvalidatesOtherStoreWorkButFailedReplacementDoesNot() {
        val context = RuntimeEnvironment.getApplication()
        BusinessStore(context).use { a -> BusinessStore(context).use { b ->
            a.saveCustomer(Customer(name = "Original")); val before = a.generation
            try { b.importBackup("{}"); fail() } catch (_: IllegalArgumentException) {}
            assertEquals(before, b.generation)
            a.withGeneration(before) { a.saveCustomer(Customer(a.customers().single().id, "Original")) }
            b.importBackup(b.exportBackup().replace("Original", "Restored"))
            assertNotEquals(before, a.generation)
            try { a.withGeneration(before) { a.deleteCustomer(a.customers().single().id) }; fail() } catch (_: IllegalArgumentException) {}
            assertEquals("Restored", b.customers().single().name)
        } }
    }
    private fun confirm(controller: DriveController, a: Activity, json: String) {
        DriveController::class.java.getDeclaredMethod("confirmRestore", java.lang.Long.TYPE, DriveRestoreResult::class.java).apply { isAccessible = true }
            .invoke(controller, DriveState.epoch(a), DriveRestoreResult("review-only", "2026-01-01T00:00:00Z", json))
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun driveConfirmationCannotReplaceDataRestoredLocallyWhileDialogWasOpen() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        val original = BusinessStore(a).use { it.saveCustomer(Customer(name = "Original")); it.exportBackup() }
        var restored = false
        val controller = DriveController(a, {}, { restored = true }); confirm(controller, a, original.replace("Original", "Drive"))
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        BusinessStore(a).use { it.restoreBackupWithSafety(original.replace("Original", "Local")) { json -> UiBackupFiles.safetyCopy(java.io.File(a.filesDir, "respaldos-seguridad"), json) } }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        DriveRuntime.io.submit {}.get(30, TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idle()
        assertFalse(restored); assertEquals("Local", BusinessStore(a).use { it.customers().single().name })
        assertFalse(java.io.File(a.filesDir, "drive-pre-restore").exists())
        c.pause().stop().destroy()
    }
    @Test fun resetInvalidatesAlreadyDownloadedDriveRestore() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        val original = BusinessStore(a).use { it.saveCustomer(Customer(name = "Original")); it.exportBackup() }
        val controller = DriveController(a, {}, { fail("reset must invalidate restore") }); confirm(controller, a, original.replace("Original", "Drive"))
        val dialog = ShadowAlertDialog.getLatestAlertDialog(); DriveState.disconnect(a)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        DriveRuntime.io.submit {}.get(30, TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Original", BusinessStore(a).use { it.customers().single().name })
        assertFalse(java.io.File(a.filesDir, "drive-pre-restore").exists())
        c.pause().stop().destroy()
    }
    @Test fun authorizationRequestCodesAreNotReusedAfterReset() {
        val context = RuntimeEnvironment.getApplication()
        val first = DriveState.nextAuthorizationRequest(context, DriveState.epoch(context))
        DriveState.disconnect(context)
        val second = DriveState.nextAuthorizationRequest(context, DriveState.epoch(context))
        assertTrue(second > first)
    }
}
