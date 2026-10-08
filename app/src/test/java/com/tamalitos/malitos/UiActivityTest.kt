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
internal class UiActivityTest : ComposeUiHarness() {
    @Test fun emptyDashboardOffersRealWorkWithoutDemoData() {
        launch()
        compose.onNodeWithText("Resumen del negocio").assertExists()
        compose.onNodeWithText("Nuevo pedido").assertExists()
        compose.onNodeWithText("Registrar gasto").assertExists()
        BusinessStore(activity).use { assertTrue(it.customers().isEmpty()); assertTrue(it.orders().isEmpty()) }
    }
    @Test fun directorySearchFindsSavedCustomerAndDetailShowsDebt() {
        launch(); val id = activity.store.saveCustomer(Customer(name = "Ana Pérez", phone = "555100200"))
        navigate("Clientes"); field("Buscar cliente por nombre o teléfono", "Ana")
        compose.onNodeWithText("Ana Pérez").assertExists()
        click("Ver cliente: Ana Pérez"); compose.onNodeWithText("Saldo por cobrar").assertExists()
        compose.onNodeWithText("Nuevo pedido para este cliente").assertExists()
        navigate("Clientes"); field("Buscar cliente por nombre o teléfono", "No existe"); compose.onNodeWithText("Sin coincidencias").assertExists()
        assertEquals(id, activity.store.customers().single().id)
    }
    @Test fun customerEditorKeepsInvalidDraftOpen() {
        launch(); compose.runOnIdle { activity.customerForm() }; save()
        compose.onNodeWithTag("editor").assertExists(); assertTrue(activity.store.customers().isEmpty())
        compose.onNodeWithText("Escribe el nombre del cliente.").assertExists()
    }
    @Test fun orderDetailDistinguishesTotalPaidBalanceAndHistory() {
        launch(); val customer = activity.store.saveCustomer(Customer(name = "Luz"))
        val id = activity.store.createOrder(customer, "2026-10-09", "Punto de encuentro", "", listOf(OrderItem(null, "Tamal", 3, 2500)), InitialPayment.HALF)
        navigate("Pedidos", id)
        listOf("Total del pedido", "Cobrado", "Saldo pendiente", "Historial de pagos", "Registrar abono").forEach { compose.onNodeWithText(it).assertExists() }
    }
    @Test fun orderDraftRestoresCustomerAndFreeformItems() {
        launch(); val customer = activity.store.saveCustomer(Customer(name = "Luz")); refresh()
        compose.runOnIdle { activity.orderForm(customer) }
        field("address", "Mercado"); field("item-0-description", "Tamal verde"); field("item-0-quantity", "3"); field("item-0-price", "25.00")
        rotate()
        assertEquals("order", activity.draftKind)
        val captured = activity.captureDraft!!.invoke()
        assertEquals("Mercado", captured.getString("address")); assertEquals(customer, captured.getLong("customerId"))
        assertEquals("Tamal verde", captured.items().single().getString("description"))
        compose.onNodeWithTag("item-0-description").assertTextContains("Tamal verde")
    }
    @Test fun reportsExplainCashAndDeliveryDateSemantics() {
        launch(); navigate("Informes")
        listOf("Ventas por fecha de entrega", "Cobros por fecha real de pago", "Gastos del periodo", "Saldo por cobrar de esos pedidos").forEach { compose.onNodeWithText(it).assertExists() }
    }
    @Test fun expensesAndCatalogShowPersistedRecords() {
        launch(); activity.store.saveExpense(Expense(description = "Maíz", category = "Ingredientes", amountCents = 45000, date = "2026-10-02"))
        activity.store.saveProduct(Product(name = "Tamal verde", priceCents = 2500, stock = 12))
        navigate("Gastos"); compose.onNodeWithText("Maíz").assertExists()
        navigate("Productos"); compose.onNodeWithText("Tamal verde").assertExists(); compose.onNodeWithText("Existencias: 12").assertExists()
    }
    @Test fun backupScreenOffersLocalFilesWithoutGoogleSetup() {
        launch(); navigate("Respaldo")
        compose.onNodeWithText("Exportar respaldo local").assertExists(); compose.onNodeWithText("Importar respaldo local").assertExists()
        assertFalse(DriveController.isConnected(activity))
    }
}
