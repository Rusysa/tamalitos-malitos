package com.tamalitos.malitos

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UiActivityTest {
    @Before fun cleanDatabase() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        context.databaseList().forEach { context.deleteDatabase(it) }
    }

    @Test fun emptyDashboardOffersRealWorkWithoutDemoData() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val labels = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(labels.contains("Resumen del negocio"))
        assertTrue(labels.contains("Nuevo pedido"))
        assertTrue(labels.contains("Registrar gasto"))
        BusinessStore(activity).use { assertTrue(it.customers().isEmpty()); assertTrue(it.orders().isEmpty()) }
    }

    @Test fun directorySearchFindsSavedCustomerAndDetailShowsDebt() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val id = activity.store.saveCustomer(Customer(name = "Ana Pérez", phone = "555100200"))
        activity.customerQuery = "Ana"
        activity.navigate("Clientes")
        assertTrue(labels(activity).contains("Ana Pérez"))
        activity.navigate("Clientes", id)
        assertTrue(labels(activity).contains("Saldo por cobrar"))
        assertTrue(labels(activity).contains("Nuevo pedido para este cliente"))
        activity.customerQuery = "No existe"
        activity.navigate("Clientes")
        assertTrue(labels(activity).contains("Sin coincidencias"))
    }

    @Test fun customerEditorKeepsInvalidDraftOpen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.customerForm()
        val dialog = activity.dialog!!
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertTrue(activity.store.customers().isEmpty())
    }

    @Test fun orderDetailDistinguishesTotalPaidBalanceAndHistory() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val customer = activity.store.saveCustomer(Customer(name = "Luz"))
        val id = activity.store.createOrder(customer, "2026-10-09", "Punto de encuentro", "", listOf(OrderItem(null, "Tamal", 3, 2500)), InitialPayment.HALF)
        activity.navigate("Pedidos", id)
        val labels = labels(activity)
        assertTrue(labels.contains("Total del pedido"))
        assertTrue(labels.contains("Cobrado"))
        assertTrue(labels.contains("Saldo pendiente"))
        assertTrue(labels.contains("Historial de pagos"))
        assertTrue(labels.contains("Registrar abono"))
    }

    @Test fun orderDraftRestoresCustomerAndFreeformItems() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val customer = activity.store.saveCustomer(Customer(name = "Luz"))
        val item = android.os.Bundle().apply {
            putLong("productId", 0); putString("description", "Tamal verde")
            putString("quantity", "3"); putString("price", "25.00")
        }
        val draft = android.os.Bundle().apply {
            putLong("customerId", customer); putString("date", "2026-10-09"); putString("address", "Mercado")
            putParcelableArrayList("items", arrayListOf(item)); putInt("mode", 2)
        }
        activity.orderForm(customer, draft)
        val state = android.os.Bundle()
        controller.saveInstanceState(state).pause().stop().destroy()
        val restored = Robolectric.buildActivity(MainActivity::class.java).create(state).start().resume().visible().get()
        assertEquals("order", restored.draftKind)
        val capture = restored.captureDraft!!.invoke()
        assertEquals("Mercado", capture.getString("address"))
        assertEquals(customer, capture.getLong("customerId"))
        assertEquals("Tamal verde", capture.getParcelableArrayList<android.os.Bundle>("items")!![0].getString("description"))
    }

    @Test fun reportsExplainCashAndDeliveryDateSemantics() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.navigate("Informes")
        val labels = labels(activity)
        assertTrue(labels.contains("Ventas por fecha de entrega"))
        assertTrue(labels.contains("Cobros por fecha real de pago"))
        assertTrue(labels.contains("Gastos del periodo"))
        assertTrue(labels.contains("Saldo por cobrar de esos pedidos"))
    }

    @Test fun expensesAndCatalogShowPersistedRecords() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.store.saveExpense(Expense(description = "Maíz", category = "Ingredientes", amountCents = 45000, date = "2026-10-02"))
        activity.store.saveProduct(Product(name = "Tamal verde", priceCents = 2500, stock = 12))
        activity.navigate("Gastos")
        assertTrue(labels(activity).contains("Maíz"))
        activity.navigate("Productos")
        assertTrue(labels(activity).contains("Tamal verde"))
        assertTrue(labels(activity).contains("Existencias: 12"))
    }

    @Test fun backupScreenOffersLocalFilesWithoutGoogleSetup() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.navigate("Respaldo")
        assertTrue(labels(activity).contains("Exportar respaldo local"))
        assertTrue(labels(activity).contains("Importar respaldo local"))
        assertFalse(DriveController.isConnected(activity))
    }

    private fun labels(activity: MainActivity) = descendants(activity.window.decorView)
        .filterIsInstance<TextView>().map { it.text.toString() }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
