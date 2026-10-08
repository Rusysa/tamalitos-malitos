package com.tamalitos.malitos

import android.app.Application
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.*
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

/** Semantics actions exercise Material fields and retained IO-backed real SQLite. */
internal open class ComposeUiHarness {
    @get:Rule val compose = createComposeRule()
    lateinit var controller: ActivityController<MainActivity>
    val activity get() = controller.get()
    @Before fun resetUiStorage() {
        val context = RuntimeEnvironment.getApplication()
        context.databaseList().forEach { context.deleteDatabase(it) }
        context.getSharedPreferences("ui_local_backup", 0).edit().clear().commit()
    }
    fun launch(saved: Bundle? = null) {
        controller = if(saved == null) Robolectric.buildActivity(MainActivity::class.java).setup() else Robolectric.buildActivity(MainActivity::class.java).create(saved).start().resume().visible()
        settled()
    }
    fun synchronizeCompose() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }
    fun settled() { compose.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !activity.model.state.value.busy }; synchronizeCompose() }
    fun refresh() { compose.runOnIdle { activity.render() }; settled() }
    fun field(tag: String, value: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value); synchronizeCompose() }
    fun click(label: String) { compose.onNodeWithText(label).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); synchronizeCompose() }
    fun SemanticsNodeInteraction.activate(): SemanticsNodeInteraction { performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); synchronizeCompose(); return this }
    fun save() { compose.onNodeWithTag("save-editor").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settled() }
    fun navigate(label: String, id: Long = 0) { compose.runOnIdle { activity.navigate(label, id) }; settled() }
    fun rotate(): Bundle {
        val saved = Bundle(); compose.runOnIdle { controller.saveInstanceState(saved).pause().stop().destroy() }
        launch(saved); return saved
    }
    @After fun closeUi() { if(::controller.isInitialized && !activity.isDestroyed) { compose.runOnIdle { controller.pause().stop().destroy() } } }
}
