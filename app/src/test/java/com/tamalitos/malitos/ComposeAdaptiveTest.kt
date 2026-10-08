package com.tamalitos.malitos

import android.app.Application
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ComposeAdaptiveTest : ComposeUiHarness() {
    @Test fun compactNavigationOffersEveryDestinationInThreeTabsAndOverflow() {
        launch(); compose.onNodeWithTag("navigation-bottom").assertExists()
        listOf("Gastos", "Informes", "Productos", "Respaldo").forEach { label ->
            compose.onNodeWithText("Más").activate(); compose.onNodeWithTag("navigation-drawer").assertExists()
            compose.onNodeWithText(label).activate(); settled(); assertEquals(label, activity.screen)
        }
        listOf("Inicio", "Clientes", "Pedidos").forEach { label -> compose.onNodeWithText(label).activate(); settled(); assertEquals(label, activity.screen) }
    }
    @Test @Config(qualifiers = "w1000dp-h600dp-land")
    fun wideClientsAndOrdersKeepListAndDetailVisible() {
        launch(); val customer = activity.store.saveCustomer(Customer(name = "Cliente tableta"))
        val order = activity.store.createOrder(customer, "2026-10-09", "Mercado", "", listOf(OrderItem(null, "Tamal", 1, 2500)), InitialPayment.UNPAID)
        navigate("Clientes", customer)
        compose.onNodeWithTag("navigation-rail").assertExists(); compose.onNodeWithTag("navigation-bottom").assertDoesNotExist()
        compose.onNodeWithTag("list-pane").assertIsDisplayed(); compose.onNodeWithTag("detail-pane").assertIsDisplayed()
        compose.onNodeWithText("Ver cliente: Cliente tableta").assertExists(); compose.onNodeWithText("Editar cliente").assertExists()
        navigate("Pedidos", order)
        compose.onNodeWithTag("list-pane").assertIsDisplayed(); compose.onNodeWithTag("detail-pane").assertIsDisplayed()
        compose.onNodeWithText("Ver pedido #$order").assertExists(); compose.onNodeWithText("Saldo pendiente").assertExists()
        assertEquals(order, activity.selectedId)
    }
    @Test @Config(qualifiers = "w700dp-h360dp-land")
    fun shortMediumWindowUsesScrollableRailAndOnePane() {
        launch(); navigate("Respaldo")
        compose.onNodeWithTag("navigation-rail").assertExists()
        compose.onNodeWithTag("detail-pane").assertDoesNotExist()
        compose.onNodeWithText("Exportar respaldo local").assertExists()
    }
    @Test fun largeDirectoryLoadsVisibleRowsAndCanReachLastCustomer() {
        launch()
        (1..80).forEach { activity.store.saveCustomer(Customer(name = "Cliente %03d".format(it))) }
        navigate("Clientes")
        compose.onNodeWithText("Cliente 080").assertDoesNotExist()
        compose.onNodeWithTag("customer-list").performScrollToNode(hasText("Cliente 080"))
        compose.onNodeWithText("Cliente 080").assertIsDisplayed()
    }
    @Test fun materialCalendarIsAvailableWithoutLosingTypedDate() {
        launch(); click("Registrar gasto"); field("date", "2026-10-08")
        compose.onNodeWithTag("calendar-date").performScrollTo().activate()
        compose.onNodeWithTag("material-date-picker").assertExists()
        compose.onNodeWithText("Volver al formulario").activate()
        compose.onNodeWithTag("date").assertTextContains("2026-10-08")
    }
}
