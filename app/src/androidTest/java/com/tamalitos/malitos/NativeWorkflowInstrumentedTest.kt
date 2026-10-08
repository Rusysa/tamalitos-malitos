package com.tamalitos.malitos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Material semantics -> actual touch events -> real SQLite and real Application. */
@RunWith(AndroidJUnit4::class)
class NativeWorkflowInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun settled() { compose.waitUntil(15_000) { !compose.activity.model.state.value.busy }; compose.waitForIdle() }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick(); compose.waitForIdle() }
    private fun fill(tag: String, text: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text) }
    private fun save() {
        compose.onNodeWithTag("save-editor").performClick(); settled()
        // A Snackbar intentionally overlays the bottom of a scrolling page. Dismiss it
        // through its accessibility action before the next real pointer click.
        val snackbar = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss) and hasAnyDescendant(hasText("Registro guardado.")))
        if(snackbar.fetchSemanticsNodes().isNotEmpty()) snackbar[0].performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
    }
    @Test fun registerCustomerEditOrderHalfThenLiquidateAndRegisterExpenseThroughScreens() {
        settled(); val unique = "Cliente QA " + System.nanoTime()
        click("Agregar cliente"); fill("name", unique); fill("phone", "5551234567"); fill("address", "Mercado QA"); save()
        click("Editar cliente")
        compose.onNodeWithTag("editor").assertIsDisplayed()
        fill("notes", "Cliente editado en Compose"); save()
        BusinessStore(compose.activity).use { assertEquals("Cliente editado en Compose", it.customers(unique).single().notes) }
        click("Nuevo pedido para este cliente"); fill("item-0-description", "Tamal QA"); fill("item-0-quantity", "3"); fill("item-0-price", "25")
        compose.onNodeWithTag("Anticipo al crear el pedido").performScrollTo().performClick(); compose.onNodeWithText("La mitad (50 %)").performClick(); save()
        BusinessStore(compose.activity).use { store -> val order = store.orders(unique).single(); assertEquals(7500L, order.totalCents); assertEquals(3750L, order.paidCents); assertEquals(3750L, order.balanceCents) }
        click("Registrar abono"); fill("amount", "37.50"); save()
        BusinessStore(compose.activity).use { assertEquals(0L, it.orders(unique).single().balanceCents) }
        click("Cambiar estado"); compose.onNode(hasTestTag("Estado del pedido") and hasAnyAncestor(hasTestTag("editor"))).performClick(); compose.onNodeWithText("Entregado").performClick(); save()
        BusinessStore(compose.activity).use { assertEquals(OrderStatus.DELIVERED, it.orders(unique).single().status) }
        // All seven destinations remain reachable on phone and rail; exercise the phone's overflow.
        if(compose.onAllNodesWithTag("navigation-bottom").fetchSemanticsNodes().isNotEmpty()) { compose.onNodeWithText("Más").performClick(); compose.onNodeWithText("Gastos").performClick() }
        else compose.onNodeWithText("Gastos").performClick()
        settled(); click("Registrar gasto"); fill("description", "Empaque $unique"); fill("amount", "15"); save()
        BusinessStore(compose.activity).use { store -> assertTrue(store.expenses().any { it.description == "Empaque $unique" && it.amountCents == 1500L }) }
    }
    @Test fun unsavedMultiLineDraftSurvivesActivityRecreation() {
        settled(); val unique = "Borrador QA " + System.nanoTime()
        click("Agregar cliente"); fill("name", unique); fill("address", "Dirección de prueba sin guardar")
        compose.activityRule.scenario.recreate(); settled()
        compose.onNodeWithTag("name").assertTextContains(unique)
        compose.onNodeWithTag("address").assertTextContains("Dirección de prueba sin guardar")
        assertEquals("customer", compose.activity.draftKind)
        BusinessStore(compose.activity).use { assertTrue(it.customers(unique).isEmpty()) }
    }
}
