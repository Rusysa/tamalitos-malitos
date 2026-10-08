package com.tamalitos.malitos

import android.os.ParcelFileDescriptor
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercises actual available window constraints, not an isTablet/orientation flag. */
@RunWith(AndroidJUnit4::class)
class ComposeAdaptiveInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
    @Test fun saveCanActuallyBeTappedWithKeyboardInShortLandscapeWindow() {
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val oldDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        try {
            shell("wm size 1280x720"); shell("wm density 320")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { !compose.activity.model.state.value.busy }
            val unique = "Teclado QA " + System.nanoTime()
            compose.runOnIdle { compose.activity.navigate("Clientes") }
            compose.waitUntil(15_000) { !compose.activity.model.state.value.busy }
            compose.onNodeWithText("Agregar cliente").performClick()
            compose.onNodeWithTag("name").performClick().performTextInput(unique)
            compose.waitUntil(5_000) { shell("dumpsys input_method").contains("mIsInputViewShown=true") }
            // Do not hide the keyboard or call the save callback directly: prove the
            // pointer reaches the real Material button rather than the overlaid IME.
            compose.onNodeWithTag("save-editor").performClick()
            compose.waitUntil(5_000) { compose.activity.draftKind == null && !compose.activity.model.state.value.busy }
            BusinessStore(compose.activity).use { assertEquals(unique, it.customers(unique).single().name) }
        } finally {
            shell("wm size ${oldSize ?: "reset"}"); shell("wm density ${oldDensity ?: "reset"}")
        }
    }
    @Test fun actualWideWindowShowsClientAndOrderListsBesideDetails() {
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val oldDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        try {
            shell("wm size 1600x1000"); shell("wm density 160")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { !compose.activity.model.state.value.busy }
            val unique = "Tableta QA " + System.nanoTime()
            val ids = BusinessStore(compose.activity).use { store ->
                val customer = store.saveCustomer(Customer(name = unique))
                val order = store.createOrder(customer, "2026-10-09", "Mercado QA", "", listOf(OrderItem(null, "Tamal", 1, 2500)), InitialPayment.UNPAID)
                customer to order
            }
            compose.runOnIdle { compose.activity.navigate("Clientes", ids.first) }
            compose.waitUntil(15_000) { !compose.activity.model.state.value.busy }; compose.waitForIdle()
            compose.onNodeWithTag("navigation-rail").assertIsDisplayed()
            compose.onNodeWithTag("navigation-bottom").assertDoesNotExist()
            compose.onNodeWithTag("list-pane").assertIsDisplayed(); compose.onNodeWithTag("detail-pane").assertIsDisplayed()
            val image = compose.onRoot().captureToImage()
            val dark = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            assertEquals("Unused space below the rail must use the Material background, not the black window", if(dark) Color(0xFF121B16) else Color(0xFFFFF9EE), image.toPixelMap()[2, image.height - 40])
            compose.onNodeWithText("Editar cliente").assertExists()
            compose.runOnIdle { compose.activity.navigate("Pedidos", ids.second) }
            compose.waitUntil(15_000) { !compose.activity.model.state.value.busy }; compose.waitForIdle()
            compose.onNodeWithTag("list-pane").assertIsDisplayed(); compose.onNodeWithTag("detail-pane").assertIsDisplayed()
            compose.onNodeWithText("Saldo pendiente").assertExists()
            assertEquals(ids.second, compose.activity.selectedId)
        } finally {
            shell("wm size ${oldSize ?: "reset"}"); shell("wm density ${oldDensity ?: "reset"}")
        }
    }
}
