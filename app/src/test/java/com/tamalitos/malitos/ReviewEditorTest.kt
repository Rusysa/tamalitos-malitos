package com.tamalitos.malitos

import android.app.Application
import android.os.Bundle
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ReviewEditorTest : ComposeUiHarness() {
    private fun replace(): Long {
        val id = activity.store.customers().single().id
        activity.store.importBackup(activity.store.exportBackup().replace("Original", "Restored"))
        assertEquals(id, activity.store.customers().single().id); return id
    }
    @Test fun openEditorCannotWriteToReusedCustomerIdAfterRestore() {
        launch(); val id = activity.store.saveCustomer(Customer(name = "Original")); refresh()
        compose.runOnIdle { activity.customerForm(id) }; field("name", "Stale draft"); replace(); save()
        assertEquals("Restored", activity.store.customers().single().name)
        compose.onNodeWithTag("editor").assertExists(); assertTrue(activity.draftError.isNotBlank())
    }
    @Test fun rotationCannotRebindAnOldDraftToRestoredCustomer() {
        launch(); val id = activity.store.saveCustomer(Customer(name = "Original")); refresh()
        compose.runOnIdle { activity.customerForm(id) }; field("name", "Old draft")
        val state = Bundle(); compose.runOnIdle { controller.saveInstanceState(state).pause().stop().destroy() }
        BusinessStore(RuntimeEnvironment.getApplication()).use { it.importBackup(it.exportBackup().replace("Original", "Restored")) }
        launch(state)
        compose.onNodeWithTag("editor").assertDoesNotExist()
        assertEquals("Restored", activity.store.customers().single().name); assertNull(activity.draftKind)
    }
    @Test fun destructiveConfirmationCannotDeleteReusedId() {
        launch(); val id = activity.store.saveCustomer(Customer(name = "Original")); refresh()
        compose.runOnIdle { activity.confirm("Delete", "Original", "Delete") { activity.store.deleteCustomer(id) } }
        replace(); compose.onNodeWithTag("confirm-action").activate(); settled()
        assertEquals("Restored", activity.store.customers().single().name)
    }
    @Test fun queuedSaveFromAnOldEditorCannotSaveANewlyOpenedEditor() {
        launch(); val id = activity.store.saveCustomer(Customer(name = "Original")); refresh()
        compose.runOnIdle { activity.customerForm(id) }; field("name", "Old draft")
        val queuedSave = compose.onNodeWithTag("save-editor").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        replace(); compose.runOnIdle { activity.onBusinessRestored() }; settled()
        compose.runOnIdle { activity.customerForm(id) }; field("name", "New unsaved draft")
        compose.runOnIdle { queuedSave() }; settled()
        assertEquals("Restored", activity.store.customers().single().name)
        assertEquals("New unsaved draft", activity.draft.string("name"))
        compose.onNodeWithTag("editor").assertExists()
    }
    @Test fun restoreCompletionDismissesEditorsAndImportConfirmation() {
        launch(); val id = activity.store.saveCustomer(Customer(name = "Original")); refresh()
        compose.runOnIdle { activity.customerForm(id); activity.confirmLocalImport(android.net.Uri.parse("content://review/backup")) }
        compose.onNodeWithTag("editor").assertExists(); compose.onNodeWithTag("confirm-action").assertExists()
        compose.runOnIdle { activity.onBusinessRestored() }; settled()
        compose.onNodeWithTag("editor").assertDoesNotExist(); compose.onNodeWithTag("confirm-action").assertDoesNotExist()
        assertNull(activity.draftKind); assertNull(activity.pendingImportUri)
    }
}
