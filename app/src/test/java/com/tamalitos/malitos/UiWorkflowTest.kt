package com.tamalitos.malitos

import android.app.AlertDialog
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/** Real native controls -> real SQLite, not a mocked UI/service response. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UiWorkflowTest {
    @Before fun reset() {
        val context = RuntimeEnvironment.getApplication()
        context.databaseList().forEach { context.deleteDatabase(it) }
        context.getSharedPreferences("ui_local_backup", 0).edit().clear().commit()
    }

    @Test fun customerCanBeAddedAndEditedUsingNativeForm() {
        val activity = activity()
        activity.customerForm()
        fill(activity, "Nombre del cliente *", "Rosa")
        fill(activity, "Teléfono", "5551234")
        save(activity)
        val original = activity.store.customers().single()
        assertEquals("Rosa", original.name)
        activity.customerForm(original.id)
        fill(activity, "Nombre del cliente *", "Rosa Martínez")
        save(activity)
        assertEquals(original.id, activity.store.customers().single().id)
        assertEquals("Rosa Martínez", activity.store.customers().single().name)
    }

    @Test fun multipleCustomItemsAndHalfPaymentAreSavedAtomically() {
        val activity = activity()
        val customer = activity.store.saveCustomer(Customer(name = "Rosa", address = "Mercado central"))
        activity.orderForm(customer)
        fill(activity, "Descripción del artículo *", "Tamal verde")
        fill(activity, "Cantidad *", "3")
        fill(activity, "Precio unitario *", "25.00")
        formButton(activity, "Agregar otro artículo").performClick()
        fill(activity, "Descripción del artículo *", "Atole", 1)
        fill(activity, "Cantidad *", "2", 1)
        fill(activity, "Precio unitario *", "20", 1)
        controls(activity).filterIsInstance<Spinner>().first { it.contentDescription == "Anticipo al crear el pedido" }.setSelection(1)
        save(activity)
        val order = activity.store.orders().single()
        assertEquals(2, order.items.size)
        assertEquals(11500L, order.totalCents)
        assertEquals(5750L, order.paidCents)
        assertEquals("Mercado central", order.deliveryAddress)
        assertEquals(customer, order.customerId)
    }

    @Test fun excessivePaymentDoesNotDismissOrChangeHistory() {
        val activity = activity()
        val customer = activity.store.saveCustomer(Customer(name = "Rosa"))
        val id = activity.store.createOrder(customer, "2026-10-09", "Mercado", "", listOf(OrderItem(null, "Tamal", 1, 2500)), InitialPayment.UNPAID)
        activity.paymentForm(id)
        fill(activity, "Importe del abono *", "25.01")
        save(activity)
        assertTrue(activity.dialog!!.isShowing)
        assertTrue(activity.store.order(id)!!.payments.isEmpty())
        fill(activity, "Importe del abono *", "10.00")
        save(activity)
        assertNull(activity.dialog)
        assertEquals(1000L, activity.store.order(id)!!.paidCents)
        assertEquals(1500L, activity.store.order(id)!!.balanceCents)
    }

    @Test fun expenseCanBeAddedAndEditedWithCategoryAndDate() {
        val activity = activity()
        activity.expenseForm()
        fill(activity, "Concepto del gasto *", "Empaques")
        fill(activity, "Importe del gasto *", "70.50")
        fill(activity, "Categoría del gasto", "Materiales")
        save(activity)
        val expense = activity.store.expenses().single()
        assertEquals(7050L, expense.amountCents)
        assertEquals("Materiales", expense.category)
        activity.expenseForm(expense.id)
        fill(activity, "Importe del gasto *", "75")
        save(activity)
        assertEquals(7500L, activity.store.expenses().single().amountCents)
    }

    @Test fun zeroProductPriceKeepsEditorOpen() {
        val activity = activity()
        activity.productForm()
        fill(activity, "Nombre del producto *", "Tamal verde")
        fill(activity, "Precio unitario *", "0")
        save(activity)
        assertTrue(activity.dialog!!.isShowing)
        assertTrue(activity.store.products(true).isEmpty())
    }

    @Test fun cancelledDeletionConfirmationLeavesCustomerUntouched() {
        val activity = activity()
        val id = activity.store.saveCustomer(Customer(name = "Rosa"))
        activity.navigate("Clientes", id)
        all(activity.window.decorView).filterIsInstance<Button>().first { it.text == "Eliminar cliente" }.performClick()
        val confirmation = ShadowAlertDialog.getLatestAlertDialog()
        confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(id, activity.store.customers().single().id)
    }

    @Test fun localExportUsesNativeCreateDocumentWithoutCloud() {
        val activity = activity()
        activity.navigate("Respaldo")
        all(activity.window.decorView).filterIsInstance<Button>().first { it.text == "Exportar respaldo local" }.performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        assertEquals("application/json", request.intent.type)
        assertFalse(DriveController.isConnected(activity))
    }

    private fun activity() = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    private fun controls(activity: MainActivity): List<View> {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return all(activity.dialog!!.window!!.decorView)
    }
    private fun fill(activity: MainActivity, label: String, value: String, index: Int = 0) {
        controls(activity).filterIsInstance<EditText>().filter { it.contentDescription == label }[index].setText(value)
    }
    private fun save(activity: MainActivity) {
        shadowOf(android.os.Looper.getMainLooper()).idle() // Deliver Dialog.onShow before pressing save.
        activity.dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle() // Deliver dismissal and callbacks.
    }
    private fun formButton(activity: MainActivity, label: String) = controls(activity).filterIsInstance<Button>().first { it.text == label }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
}
