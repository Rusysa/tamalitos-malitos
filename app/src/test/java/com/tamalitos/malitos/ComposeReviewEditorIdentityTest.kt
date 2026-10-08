package com.tamalitos.malitos

import android.app.Application
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ComposeReviewEditorIdentityTest : ComposeUiHarness() {
    private class ControlledIo : CoroutineDispatcher() {
        val tasks = mutableListOf<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun finish() {
            val task = tasks.removeAt(0)
            val worker = Thread { task.run() }
            worker.start(); worker.join(10_000); check(!worker.isAlive)
            shadowOf(Looper.getMainLooper()).idle()
        }
        fun drain() { while(tasks.isNotEmpty()) finish() }
    }
    private lateinit var io: ControlledIo

    private fun launchControlled() {
        launch()
        io = ControlledIo()
        compose.runOnIdle {
            val controlled = BusinessViewModel(activity.application, io)
            activity.viewModelStore.put("editor-review", controlled)
            activity.model = controlled
            controlled.session.initialized = true
            activity.setContent { TamalitosTheme { TamalitosApp(activity) } }
            activity.render()
        }
        io.drain(); synchronizeCompose()
    }
    private fun queueCustomerSave(name: String) {
        compose.runOnIdle { activity.customerForm() }
        field("name", name)
        compose.onNodeWithTag("save-editor").activate()
        assertTrue(activity.model.state.value.busy)
        assertEquals(1, io.tasks.size)
    }
    private fun replaceWithUnsavedEditor() {
        compose.onNodeWithText("Volver").activate()
        compose.runOnIdle { activity.navigate("Productos"); activity.productForm() }
        field("name", "Unsaved editor B")
        field("price", "8.50")
        compose.runOnIdle { activity.notice = "B notice" }
    }
    private fun assertNewEditorUnchanged() {
        assertEquals("product", activity.draftKind)
        assertEquals("Unsaved editor B", activity.draft.string("name"))
        assertEquals("8.50", activity.draft.string("price"))
        assertEquals("", activity.draftError)
        assertEquals("Productos", activity.screen)
        assertEquals(0L, activity.selectedId)
        assertEquals("B notice", activity.notice)
        compose.onNodeWithTag("editor").assertExists()
        assertFalse(activity.model.state.value.busy)
        assertTrue(activity.store.products().isEmpty())
    }

    @Test fun queuedSaveCompletionDoesNotCloseOrNavigateAwayFromReopenedEditor() {
        launchControlled(); queueCustomerSave("Saved editor A")
        replaceWithUnsavedEditor()
        io.drain(); synchronizeCompose()
        assertNewEditorUnchanged()
        assertEquals("Saved editor A", activity.store.customers().single().name)
    }
}
