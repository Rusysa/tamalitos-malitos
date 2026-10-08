package com.tamalitos.malitos

import android.app.Application
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class UiWorkflowTest : ComposeUiHarness() {
    @Test fun customerCanBeAddedAndEditedUsingMaterialForm() {
        launch(); click("Agregar cliente"); field("name", "Rosa"); field("phone", "5551234"); save()
        val original = activity.store.customers().single(); assertEquals("Rosa", original.name)
        click("Editar cliente"); field("name", "Rosa Martínez"); save()
        assertEquals(original.id, activity.store.customers().single().id); assertEquals("Rosa Martínez", activity.store.customers().single().name)
    }
    @Test fun multipleCustomItemsAndHalfPaymentAreSavedAtomically() {
        launch(); val customer = activity.store.saveCustomer(Customer(name = "Rosa", address = "Mercado central")); refresh()
        click("Nuevo pedido"); field("item-0-description", "Tamal verde"); field("item-0-quantity", "3"); field("item-0-price", "25.00")
        click("Agregar otro artículo"); field("item-1-description", "Atole"); field("item-1-quantity", "2"); field("item-1-price", "20")
        compose.onNodeWithTag("Anticipo al crear el pedido").performScrollTo().activate()
        compose.onNodeWithText("La mitad (50 %)").activate(); save()
        val order = activity.store.orders().single()
        assertEquals(2, order.items.size); assertEquals(11500L, order.totalCents); assertEquals(5750L, order.paidCents)
        assertEquals("Mercado central", order.deliveryAddress); assertEquals(customer, order.customerId)
    }
    @Test fun excessivePaymentDoesNotDismissOrChangeHistory() {
        launch(); val customer = activity.store.saveCustomer(Customer(name = "Rosa"))
        val id = activity.store.createOrder(customer, "2026-10-09", "Mercado", "", listOf(OrderItem(null, "Tamal", 1, 2500)), InitialPayment.UNPAID)
        navigate("Pedidos", id); click("Registrar abono"); field("amount", "25.01"); save()
        compose.onNodeWithTag("editor").assertExists(); assertTrue(activity.store.order(id)!!.payments.isEmpty())
        field("amount", "10.00"); save(); compose.onNodeWithTag("editor").assertDoesNotExist()
        assertEquals(1000L, activity.store.order(id)!!.paidCents); assertEquals(1500L, activity.store.order(id)!!.balanceCents)
    }
    @Test fun expenseCanBeAddedAndEditedWithCategoryAndDate() {
        launch(); click("Registrar gasto"); field("description", "Empaques"); field("amount", "70.50"); field("category", "Materiales"); field("date", "2026-10-08"); save()
        val expense = activity.store.expenses().single(); assertEquals(7050L, expense.amountCents); assertEquals("Materiales", expense.category); assertEquals("2026-10-08", expense.date)
        click("Editar gasto: Empaques"); field("amount", "75"); save(); assertEquals(7500L, activity.store.expenses().single().amountCents)
    }
    @Test fun zeroProductPriceKeepsEditorOpen() {
        launch(); navigate("Productos"); click("Agregar producto"); compose.onNodeWithContentDescription("Producto activo para pedidos nuevos").assertExists(); field("name", "Tamal verde"); field("price", "0"); save()
        compose.onNodeWithTag("editor").assertExists(); assertTrue(activity.store.products(true).isEmpty())
    }
    @Test fun cancelledDeletionConfirmationLeavesCustomerUntouched() {
        launch(); val id = activity.store.saveCustomer(Customer(name = "Rosa")); navigate("Clientes", id)
        click("Eliminar cliente"); compose.onNodeWithText("Volver").activate()
        assertEquals(id, activity.store.customers().single().id)
    }
    @Test fun localExportUsesNativeCreateDocumentWithoutCloud() {
        launch(); navigate("Respaldo"); click("Exportar respaldo local")
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, request.intent.action); assertEquals("application/json", request.intent.type)
        assertFalse(DriveController.isConnected(activity))
    }
}
