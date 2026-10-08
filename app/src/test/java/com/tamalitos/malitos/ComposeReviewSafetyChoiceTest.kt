package com.tamalitos.malitos

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ComposeReviewSafetyChoiceTest : ComposeUiHarness() {
    @Before fun clearCopies() {
        val app = RuntimeEnvironment.getApplication()
        File(app.filesDir, "respaldos-seguridad").deleteRecursively()
        File(app.filesDir, "drive-pre-restore").deleteRecursively()
    }
    @Test fun missingDocumentHandlerShowsErrorAndKeepsSafetyChoiceForRetry() {
        launch()
        val file = UiBackupFiles.safetyCopy(File(activity.filesDir, "respaldos-seguridad"), activity.store.exportBackup())
        navigate("Respaldo"); click("Exportar copia de seguridad previa")
        val label = "${file.parentFile!!.name}/${file.name}"
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        assertNull(activity.packageManager.resolveActivity(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE) }, 0))
        compose.onNodeWithText(label).performClick(); synchronizeCompose()
        assertNotNull("Picker launch errors must be shown, not escape the Compose callback", activity.problem)
        assertEquals(file.canonicalPath, activity.pendingSafetyPath)
        assertEquals(listOf(file), activity.safetyChoices)
        assertTrue(file.isFile)
        compose.onNodeWithText("Entendido").performClick(); synchronizeCompose()
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(false)
        compose.onNodeWithText(label).performClick(); synchronizeCompose()
        val launch = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, launch.intent.action)
        assertEquals(3103, launch.requestCode)
        assertEquals(file.canonicalPath, activity.pendingSafetyPath)
        assertTrue(activity.safetyChoices.isEmpty())
    }
}
