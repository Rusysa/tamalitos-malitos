package com.tamalitos.malitos

import android.app.Application
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ComposeRetentionTest : ComposeUiHarness() {
    @Test fun retainedRotationDiscardsEditorFromAReplacedGeneration() {
        launch(); val id = activity.store.saveCustomer(Customer(name = "Original")); refresh()
        compose.runOnIdle { activity.customerForm(id) }; field("name", "Old draft")
        activity.store.importBackup(activity.store.exportBackup().replace("Original", "Restored"))
        compose.runOnIdle { controller.configurationChange(android.content.res.Configuration(activity.resources.configuration).apply { orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE }).visible() }
        settled()
        assertEquals("Restored", activity.store.customers().single().name)
        assertNull("A retained ViewModel must not rebind an old editor to reused IDs", activity.draftKind)
        assertEquals(0L, activity.selectedId)
        compose.onNodeWithTag("editor").assertDoesNotExist()
    }
    @Test fun pendingLocalImportAndSafetySelectionSurviveRetainedRotation() {
        launch()
        compose.runOnIdle {
            activity.pendingSafetyPath = "/private/selected-safety.json"
            activity.confirmLocalImport(android.net.Uri.parse("content://review/pending"))
            controller.configurationChange(android.content.res.Configuration(activity.resources.configuration).apply { orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE }).visible()
        }
        settled()
        assertEquals("/private/selected-safety.json", activity.pendingSafetyPath)
        assertEquals("content://review/pending", activity.pendingImportUri)
        compose.onNodeWithTag("confirm-action").assertExists()
    }
    @Test fun completedSaveAfterRotationClosesRetainedEditorAndSelectsSavedCustomer() {
        launch(); click("Agregar cliente"); field("name", "Rotación")
        val acquired = CountDownLatch(1); val release = CountDownLatch(1)
        val context = activity.applicationContext
        val fence = thread {
            BusinessStore(context).use { database -> database.withCurrentData { acquired.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }
        }
        assertTrue(acquired.await(10, TimeUnit.SECONDS))
        try {
            compose.onNodeWithTag("save-editor").activate()
            assertTrue(activity.model.state.value.busy)
            compose.runOnIdle { controller.configurationChange(android.content.res.Configuration(activity.resources.configuration).apply { orientation = if(orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE }) }
        } finally { release.countDown(); fence.join(10_000) }
        settled()
        assertEquals("Rotación", activity.store.customers().single().name)
        compose.onNodeWithTag("editor").assertDoesNotExist()
        assertEquals("Clientes", activity.screen)
        assertEquals(activity.store.customers().single().id, activity.selectedId)
    }
}
