package com.tamalitos.malitos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end SQLite tests on Android, without a Google account or network. */
@RunWith(AndroidJUnit4::class)
class BusinessFlowInstrumentedTest {
    @Test fun customerHalfPaymentInstallmentExpenseAndReopenPersistCorrectly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val store = BusinessStore(context)
        val customer = store.saveCustomer(Customer(name = "Prueba $suffix", phone = "5551234567", address = "Punto de encuentro"))
        val id = store.createOrder(customer, "2099-10-07", "Punto de encuentro", "Prueba instrumental",
            listOf(OrderItem(null, "Tamales de prueba", 3, 2500)), InitialPayment.HALF)
        assertEquals(7500L, store.order(id)!!.totalCents)
        assertEquals(3750L, store.order(id)!!.paidCents)
        assertEquals(3750L, store.order(id)!!.balanceCents)
        store.addPayment(id, 3750, "Liquidación")
        store.setOrderStatus(id, OrderStatus.DELIVERED)
        val expenseId = store.saveExpense(Expense(description = "Prueba $suffix", category = "Ingredientes", amountCents = 1500, date = "2099-10-07"))
        store.close()
        BusinessStore(context).use { reopened ->
            assertEquals(0L, reopened.order(id)!!.balanceCents)
            assertEquals(2, reopened.order(id)!!.payments.size)
            assertEquals(OrderStatus.DELIVERED, reopened.order(id)!!.status)
            assertTrue(reopened.customers(suffix).any { it.id == customer })
            assertTrue(reopened.expenses("2099-10-07", "2099-10-07").any { it.id == expenseId })
            try {
                reopened.addPayment(id, 1)
                fail("No se debe permitir un sobrepago")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun unpaidOrderRemainsDebtAfterDelivery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        BusinessStore(context).use { store ->
            val customer = store.saveCustomer(Customer(name = "Adeudo " + System.nanoTime()))
            val id = store.createOrder(customer, "2099-10-08", "", "",
                listOf(OrderItem(null, "Encargo", 1, 9901)), InitialPayment.UNPAID)
            store.setOrderStatus(id, OrderStatus.DELIVERED)
            assertEquals(9901L, store.order(id)!!.balanceCents)
            assertEquals(0L, store.order(id)!!.paidCents)
        }
    }
}
