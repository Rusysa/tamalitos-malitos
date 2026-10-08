package com.tamalitos.malitos

import android.app.Application
import android.os.Bundle
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReviewSafetyRecoveryTest {
    private fun views(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Before fun clear() {
        val context = RuntimeEnvironment.getApplication()
        listOf("respaldos-seguridad", "drive-pre-restore").forEach { File(context.filesDir, it).deleteRecursively() }
        context.deleteDatabase("tamalitos.db")
    }
    @Test fun existingSafetyExportActionLetsUserChooseLocalOrDriveCopyAndRetainsExactSelection() {
        val context = RuntimeEnvironment.getApplication()
        val local = UiBackupFiles.safetyCopy(File(context.filesDir, "respaldos-seguridad"), "local safety")
        val drive = DriveLocalSafety.save(context, "drive safety")
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); a.navigate("Respaldo")
        val export = views(a.body).filterIsInstance<Button>().first { it.text == "Exportar copia de seguridad previa" }
        export.performClick(); shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("safety export must offer a copy selector", dialog)
        val items = shadowOf(dialog).items.map { it.toString() }
        assertTrue(items.any { it.contains(local.name) && it.contains("respaldos-seguridad") })
        assertTrue(items.any { it.contains(drive.name) && it.contains("drive-pre-restore") })
        shadowOf(dialog).clickOnItem(items.indexOfFirst { it.contains(drive.name) })
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val state = Bundle(); c.saveInstanceState(state)
        assertEquals(drive.canonicalPath, state.getString("pendingSafetyPath"))
        val picker = shadowOf(a).nextStartedActivityForResult
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, picker.intent.action)
        assertEquals(3103, picker.requestCode)
        c.pause().stop().destroy()
        val recreated = Robolectric.buildActivity(MainActivity::class.java).create(state).start().resume().visible()
        val restoredState = Bundle(); recreated.saveInstanceState(restoredState)
        assertEquals(drive.canonicalPath, restoredState.getString("pendingSafetyPath"))
        recreated.pause().stop().destroy()
    }
    @Test fun driveOnlySafetyCopyIsRecoverableEvenWithoutLocalImportHistory() {
        val context = RuntimeEnvironment.getApplication(); DriveLocalSafety.save(context, "drive safety")
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); a.navigate("Respaldo")
        assertTrue("Drive safety must not be hidden", views(a.body).filterIsInstance<Button>().any { it.text == "Exportar copia de seguridad previa" })
        c.pause().stop().destroy()
    }
}
