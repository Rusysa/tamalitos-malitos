package com.tamalitos.malitos

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
class ReviewDriveResetTest {
    @Before fun clear() { DriveState.preferences(RuntimeEnvironment.getApplication()).edit().clear().commit() }
    private fun views(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Test fun coldDisconnectedPendingAuthorizationHasAVisibleResetAndCanRecover() {
        val context = RuntimeEnvironment.getApplication()
        DriveState.preferences(context).edit().putString("pending_action", "RESTORE").putLong("pending_epoch", 0).commit()
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); a.navigate("Respaldo")
        assertFalse(DriveController.isConnected(a))
        val reset = views(a.body).filterIsInstance<Button>().firstOrNull { it.text.contains("Restablecer") || it.text.contains("Desconectar") }
        assertNotNull("first-time interrupted auth must not hide the only reset", reset)
        reset!!.performClick(); shadowOf(android.os.Looper.getMainLooper()).idle()
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(DriveState.preferences(a).contains("pending_action")); assertEquals(1L, DriveState.epoch(a))
        assertTrue(a.drive.handleActivityResult(9041, Activity.RESULT_OK, Intent()))
        assertFalse(DriveController.isConnected(a))
        a.drive.connectAndBackup()
        assertTrue("recovery must launch authorization rather than remain pending", DriveState.status(a).contains("Solicitando autorización"))
        c.pause().stop().destroy()
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
