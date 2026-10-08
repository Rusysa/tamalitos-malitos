package com.tamalitos.malitos

import android.app.Application
import android.content.res.Configuration
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ComposeReviewDriveConfirmationTest : ComposeUiHarness() {
    @Before fun clearDrive() {
        val app = RuntimeEnvironment.getApplication()
        DriveState.preferences(app).edit().clear().commit()
        File(app.filesDir, "drive-pre-restore").deleteRecursively()
    }
    private fun downloadedConfirmation(): String {
        launch()
        activity.store.saveCustomer(Customer(name = "Local fixture")); refresh()
        val original = activity.store.exportBackup()
        compose.runOnIdle {
            // Match a download awaiting approval, without OAuth or network access.
            DriveController::class.java.getDeclaredField("busy").apply { isAccessible = true }.setBoolean(activity.drive, true)
            DriveState.notice(activity, DriveState.epoch(activity), "Download fixture awaiting confirmation")
            DriveController::class.java.getDeclaredMethod("confirmRestore", java.lang.Long.TYPE, DriveRestoreResult::class.java).apply { isAccessible = true }
                .invoke(activity.drive, DriveState.epoch(activity), DriveRestoreResult("compose-review-fixture", "2026-10-08T00:00:00Z", original.replace("Local fixture", "Drive fixture")))
        }
        synchronizeCompose()
        compose.onNodeWithTag("confirm-action").assertExists()
        return original
    }
    private fun drainDrive() {
        DriveRuntime.io.submit {}.get(30, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle(); settled()
    }
    private fun businessPayload(json: String) = org.json.JSONObject(json).apply { remove("exportedAt") }.toString()
    private fun noReplacement(original: String) {
        assertEquals(businessPayload(original), businessPayload(activity.store.exportBackup()))
        assertFalse(File(activity.filesDir, "drive-pre-restore").exists())
    }
    @Test fun launcherComposeAcceptRestoresAndPublishesVerifiedSafetyCopy() {
        val original = downloadedConfirmation()
        compose.onNodeWithTag("confirm-action").performClick(); synchronizeCompose(); drainDrive()
        assertEquals("Drive fixture", activity.store.customers().single().name)
        val files = File(activity.filesDir, "drive-pre-restore").listFiles()!!.filter { it.extension == "json" }
        assertEquals(1, files.size); assertEquals(businessPayload(original), businessPayload(files.single().readText()))
        assertTrue(DriveController.status(activity).contains("Datos restaurados desde Drive"))
        assertNull(activity.confirmation)
        assertEquals("Inicio", activity.screen)
    }
    @Test fun launcherComposeCancelDoesNotReplaceOrPublishSafetyCopy() {
        val original = downloadedConfirmation()
        compose.onNodeWithText("Volver").performClick(); synchronizeCompose(); drainDrive()
        noReplacement(original)
        assertTrue(DriveController.status(activity).contains("Restauración cancelada"))
        assertNull(activity.confirmation)
    }
    @Test fun launcherComposeDismissCancelsDownloadedRestore() {
        val original = downloadedConfirmation()
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog()
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        synchronizeCompose(); drainDrive(); noReplacement(original)
        assertNull(activity.confirmation)
        assertTrue(DriveController.status(activity).contains("Restauración cancelada"))
    }
    @Test fun launcherComposeStaleGenerationCannotReplaceLocallyRestoredData() {
        val original = downloadedConfirmation()
        val local = original.replace("Local fixture", "New local fixture")
        activity.store.importBackup(local)
        compose.onNodeWithTag("confirm-action").performClick(); synchronizeCompose(); drainDrive()
        noReplacement(local)
        assertNotNull(activity.problem)
        assertTrue(DriveController.status(activity).contains("Restauración cancelada"))
    }
    @Test fun rotationExplicitlyCancelsDownloadedConfirmationAndRejectsOldCallback() {
        val original = downloadedConfirmation()
        val old = activity; val captured = old.confirmation!!
        compose.runOnIdle {
            controller.configurationChange(Configuration(activity.resources.configuration).apply {
                orientation = if(orientation == Configuration.ORIENTATION_LANDSCAPE) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            }).visible()
        }
        settled()
        assertTrue("Rotation must explicitly cancel, not silently lose the downloaded confirmation", DriveController.status(activity).contains("Restauración cancelada"))
        assertNull(old.confirmation)
        compose.onNodeWithTag("confirm-action").assertDoesNotExist()
        compose.runOnIdle { old.acceptConfirmation(captured) }; drainDrive()
        noReplacement(original)
        assertNull(activity.confirmation)
    }
}
