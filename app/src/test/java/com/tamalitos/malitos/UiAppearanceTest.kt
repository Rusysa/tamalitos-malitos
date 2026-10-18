package com.tamalitos.malitos

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class UiAppearanceTest : ComposeUiHarness() {
    @Before fun resetAppearance() {
        RuntimeEnvironment.getApplication().getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun clearAppearance() = resetAppearance()

    @Test fun selectedDarkModeSurvivesActivityRecreation() {
        launch()
        assertEquals(AppearanceMode.SYSTEM, activity.appearance)
        compose.onNodeWithContentDescription("Cambiar apariencia").activate()
        compose.onNodeWithText("Modo oscuro").activate()
        assertEquals(AppearanceMode.DARK, activity.appearance)
        assertEquals(AppearanceMode.DARK, AppearancePreferences.read(activity))
        compose.onNodeWithText("Listo").activate()
        rotate()
        assertEquals(AppearanceMode.DARK, activity.appearance)
        compose.onNodeWithContentDescription("Cambiar apariencia").activate()
        compose.onNodeWithText("Seguir el sistema").activate()
        assertEquals(AppearanceMode.SYSTEM, AppearancePreferences.read(activity))
    }

    @Test fun unknownPreferenceFallsBackToSystem() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", "obsolete").commit()
        assertEquals(AppearanceMode.SYSTEM, AppearancePreferences.read(context))
    }
}
