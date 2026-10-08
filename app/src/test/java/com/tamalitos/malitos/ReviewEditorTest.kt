package com.tamalitos.malitos

import android.app.Application
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReviewEditorTest {
    @Before fun reset() { RuntimeEnvironment.getApplication().deleteDatabase("tamalitos.db") }
    private fun views(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun edit(a: MainActivity) = views(a.dialog!!.window!!.decorView).filterIsInstance<EditText>().first { it.contentDescription == "Nombre del cliente *" }
    private fun replace(a: MainActivity): Long {
        val id = a.store.customers().single().id
        a.store.importBackup(a.store.exportBackup().replace("Original", "Restored"))
        assertEquals(id, a.store.customers().single().id)
        return id
    }
    @Test fun openEditorCannotWriteToReusedCustomerIdAfterRestore() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val id = a.store.saveCustomer(Customer(name = "Original")); a.customerForm(id); edit(a).setText("Stale draft")
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        replace(a)
        a.dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals("Restored", a.store.customers().single().name)
        c.pause().stop().destroy()
    }
    @Test fun rotationCannotRebindAnOldDraftToRestoredCustomer() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val id = a.store.saveCustomer(Customer(name = "Original")); a.customerForm(id); edit(a).setText("Old draft")
        val state = Bundle(); c.saveInstanceState(state); c.pause().stop().destroy()
        BusinessStore(RuntimeEnvironment.getApplication()).use { it.importBackup(it.exportBackup().replace("Original", "Restored")) }
        val recreated = Robolectric.buildActivity(MainActivity::class.java).create(state).start().resume().visible(); val b = recreated.get()
        b.dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        assertEquals("Restored", b.store.customers().single().name)
        assertNull("stale draft must be discarded, not rebound", b.draftKind)
        recreated.pause().stop().destroy()
    }
    @Test fun destructiveConfirmationCannotDeleteReusedId() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val id = a.store.saveCustomer(Customer(name = "Original"))
        a.confirm("Delete", "Original", "Delete") { a.store.deleteCustomer(id) }
        val confirmation = ShadowAlertDialog.getLatestAlertDialog()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        replace(a); confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Restored", a.store.customers().single().name)
        c.pause().stop().destroy()
    }
    @Test fun restoreCompletionDismissesEditorsAndImportConfirmation() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val id = a.store.saveCustomer(Customer(name = "Original")); a.customerForm(id)
        val editor = a.dialog!!
        a.confirmLocalImport(android.net.Uri.parse("content://review/backup")); val importing = a.importDialog!!
        a.onBusinessRestored()
        assertFalse("restore must close editors", editor.isShowing)
        assertFalse("restore must close import confirmations", importing.isShowing)
        assertNull(a.draftKind); assertNull(a.pendingImportUri)
        c.pause().stop().destroy()
    }
}
