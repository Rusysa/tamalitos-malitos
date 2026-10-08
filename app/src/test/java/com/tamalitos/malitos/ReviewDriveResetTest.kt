package com.tamalitos.malitos

import androidx.compose.ui.test.*
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ReviewDriveResetTest : ComposeUiHarness() {
    @Before fun clear() { DriveState.preferences(RuntimeEnvironment.getApplication()).edit().clear().commit() }
    @Test fun coldDisconnectedPendingAuthorizationHasAVisibleResetAndCanRecover() {
        val context = RuntimeEnvironment.getApplication()
        DriveState.preferences(context).edit().putString("pending_action", "RESTORE").putLong("pending_epoch", 0).commit()
        launch(); navigate("Respaldo")
        assertFalse(DriveController.isConnected(activity))
        click("Restablecer autorización de Google Drive"); compose.onNodeWithTag("confirm-action").activate(); settled()
        assertFalse(DriveState.preferences(activity).contains("pending_action")); assertEquals(1L, DriveState.epoch(activity))
        assertTrue(activity.drive.handleActivityResult(9041, Activity.RESULT_OK, Intent()))
        assertFalse(DriveController.isConnected(activity))
        activity.drive.connectAndBackup()
        assertTrue("recovery must launch authorization rather than remain pending", DriveState.status(activity).contains("Solicitando autorización"))
    }
    @Test fun staleResultWithoutAPendingResolutionCannotReleaseAnActiveOperation() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        DriveState.disconnect(a)
        val controller = DriveController(a, {}, {})
        val busy = DriveController::class.java.getDeclaredField("busy").apply { isAccessible = true }
        busy.setBoolean(controller, true)
        assertTrue(controller.handleActivityResult(9041, Activity.RESULT_OK, Intent()))
        assertTrue("a stale result must not release a newer in-flight operation", busy.getBoolean(controller))
        c.pause().stop().destroy()
    }
    @Test fun authorizationIsRecoverablyPendingBeforeGoogleResponds() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        val controller = DriveController(a, {}, {})
        controller.connectAndBackup()
        assertEquals("BACKUP", DriveState.preferences(a).getString("pending_action", null))
        assertEquals(DriveState.epoch(a), DriveState.preferences(a).getLong("pending_epoch", -1))
        assertTrue(DriveState.preferences(a).getInt("pending_request", -1) >= 9041)
        DriveState.disconnect(a)
        c.pause().stop().destroy()
    }
    @Test fun staleResultCannotConsumeANewerPendingAuthorization() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        DriveState.disconnect(a)
        DriveState.preferences(a).edit().putString("pending_action", "RESTORE").putLong("pending_epoch", DriveState.epoch(a))
            .putInt("pending_request", 9042).putInt("last_auth_request", 9042).commit()
        val controller = DriveController(a, {}, { fail("stale authorization must never restore") })
        assertTrue(controller.handleActivityResult(9041, Activity.RESULT_OK, Intent()))
        assertEquals("RESTORE", DriveState.preferences(a).getString("pending_action", null))
        assertEquals(9042, DriveState.preferences(a).getInt("pending_request", -1))
        assertFalse(DriveController.isConnected(a))
        c.pause().stop().destroy()
    }
}
