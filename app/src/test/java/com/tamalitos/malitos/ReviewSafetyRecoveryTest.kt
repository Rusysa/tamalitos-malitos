package com.tamalitos.malitos

import android.app.Application
import android.os.Bundle
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ReviewSafetyRecoveryTest : ComposeUiHarness() {
    @Before fun clearSafety() {
        val context = RuntimeEnvironment.getApplication()
        listOf("respaldos-seguridad", "drive-pre-restore").forEach { File(context.filesDir, it).deleteRecursively() }
    }
    @Test fun existingSafetyExportActionLetsUserChooseLocalOrDriveCopyAndRetainsExactSelection() {
        val context = RuntimeEnvironment.getApplication()
        val local = UiBackupFiles.safetyCopy(File(context.filesDir, "respaldos-seguridad"), "local safety")
        val drive = DriveLocalSafety.save(context, "drive safety")
        launch(); navigate("Respaldo"); click("Exportar copia de seguridad previa")
        compose.onNodeWithText("respaldos-seguridad/${local.name}").assertExists()
        compose.onNodeWithText("drive-pre-restore/${drive.name}").activate()
        val state = Bundle(); compose.runOnIdle { controller.saveInstanceState(state) }
        assertEquals(drive.canonicalPath, state.getString("pendingSafetyPath"))
        val picker = shadowOf(activity).nextStartedActivityForResult
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, picker.intent.action); assertEquals(3103, picker.requestCode)
        rotate(); assertEquals(drive.canonicalPath, activity.pendingSafetyPath)
    }
    @Test fun driveOnlySafetyCopyIsRecoverableEvenWithoutLocalImportHistory() {
        DriveLocalSafety.save(RuntimeEnvironment.getApplication(), "drive safety")
        launch(); navigate("Respaldo"); compose.onNodeWithText("Exportar copia de seguridad previa").assertExists()
    }
}
