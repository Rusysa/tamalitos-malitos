package com.tamalitos.malitos

import androidx.compose.ui.test.*
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
internal class ReviewGenerationTest : ComposeUiHarness() {
    @Before fun clear() {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("tamalitos.db")
        DriveState.preferences(context).edit().clear().commit()
        java.io.File(context.filesDir, "drive-pre-restore").deleteRecursively()
    }
    @Test fun stalePaymentFormCannotPayRestoredOrderWithReusedId() {
        launch(); val customer = activity.store.saveCustomer(Customer(name = "Original"))
        val id = activity.store.createOrder(customer, "2026-10-08", "", "", listOf(OrderItem(null, "Tamal", 1, 100)), InitialPayment.UNPAID)
        refresh(); compose.runOnIdle { activity.paymentForm(id) }; field("amount", "1")
        activity.store.importBackup(activity.store.exportBackup().replace("Original", "Restored")); save()
        assertTrue(activity.store.order(id)!!.payments.isEmpty()); assertEquals("Restored", activity.store.order(id)!!.customerName)
    }
    @Test fun staleStatusDialogCannotChangeRestoredOrderWithReusedId() {
        launch(); val customer = activity.store.saveCustomer(Customer(name = "Original"))
        val id = activity.store.createOrder(customer, "2026-10-08", "", "", listOf(OrderItem(null, "Tamal", 1, 100)), InitialPayment.UNPAID)
        navigate("Pedidos", id); click("Cambiar estado")
        compose.onNode(hasTestTag("Estado del pedido") and hasAnyAncestor(hasTestTag("editor"))).activate(); compose.onNodeWithText("En preparación").activate()
        activity.store.importBackup(activity.store.exportBackup().replace("Original", "Restored")); save()
        assertEquals(OrderStatus.PENDING, activity.store.order(id)!!.status)
    }
    @Test fun calendarCallbackCapturedBeforeRestoreCannotChangeStaleDraft() {
        launch(); activity.store.saveCustomer(Customer(name = "Original")); refresh()
        compose.runOnIdle { activity.orderForm() }; field("date", "2026-10-08")
        compose.onNodeWithTag("calendar-date").performScrollTo().performClick(); synchronizeCompose()
        compose.onNodeWithTag("material-date-picker").assertExists()
        compose.runOnIdle { activity.updateDraft("date", "2026-10-09") }
        activity.store.importBackup(activity.store.exportBackup().replace("Original", "Restored"))
        compose.onNodeWithText("Usar fecha").performClick(); synchronizeCompose()
        assertEquals("2026-10-09", activity.draft.string("date"))
        assertNotNull(activity.problem)
        compose.onNodeWithTag("material-date-picker").assertDoesNotExist()
    }
    @Test fun calendarOpenedBeforeRestoreCannotRebindToNewEditorGeneration() {
        launch(); activity.store.saveCustomer(Customer(name = "Original")); refresh()
        compose.runOnIdle { activity.orderForm() }; field("date", "2026-10-08")
        compose.onNodeWithTag("calendar-date").performScrollTo().performClick(); synchronizeCompose()
        activity.store.importBackup(activity.store.exportBackup().replace("Original", "Restored"))
        compose.runOnIdle { activity.orderForm(saved = android.os.Bundle(activity.draft).apply { putString("date", "2026-12-20") }) }
        synchronizeCompose()
        assertEquals(activity.store.generation, activity.draftGeneration)
        compose.onNodeWithTag("material-date-picker").assertExists()
        compose.onNodeWithText("Usar fecha").performClick(); synchronizeCompose()
        assertEquals("2026-12-20", activity.draft.string("date"))
        assertNotNull(activity.problem)
    }
    @Test fun calendarAcceptUpdatesTheActiveDraftAndCancelLeavesItUnchanged() {
        launch(); compose.runOnIdle { activity.expenseForm() }; field("date", "2026-10-08")
        compose.onNodeWithTag("calendar-date").performScrollTo().performClick(); synchronizeCompose()
        compose.runOnIdle { activity.updateDraft("date", "2026-10-09") }
        compose.onNodeWithText("Usar fecha").performClick(); synchronizeCompose()
        assertEquals("2026-10-08", activity.draft.string("date"))
        compose.onNodeWithTag("calendar-date").performScrollTo().performClick(); synchronizeCompose()
        compose.runOnIdle { activity.updateDraft("date", "2026-10-10") }
        compose.onNodeWithText("Volver al formulario").performClick(); synchronizeCompose()
        assertEquals("2026-10-10", activity.draft.string("date"))
        assertNull(activity.problem)
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
