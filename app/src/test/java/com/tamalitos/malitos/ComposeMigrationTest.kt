package com.tamalitos.malitos

import android.app.Application
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeMigrationTest {
    @Test fun launcherRendersComposeInsteadOfNativeWidgetScreens() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            assertTrue("The launcher must render a real Jetpack Compose UI", descendants(controller.get().window.decorView).any { it is ComposeView })
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
