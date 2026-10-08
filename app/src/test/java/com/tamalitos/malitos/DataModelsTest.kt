package com.tamalitos.malitos

import org.junit.Assert.*
import org.junit.Test

class DataModelsTest {
    @Test fun totalsBalancesAndLabelsAreDerivedFromCents() {
        val item = OrderItem(null, "Tamal", 3, 101)
        assertEquals(303L, item.totalCents)
        val order = Order(1, 1, "Cliente", "2026-10-08", "", "", OrderStatus.PENDING, listOf(item), emptyList())
        assertEquals(303L, order.totalCents)
        assertEquals(303L, order.balanceCents)
        assertEquals("Sin pagar", order.paymentLabel)
        val partial = order.copy(payments = listOf(Payment(1, 1, 100, 0)))
        assertEquals(203L, partial.balanceCents)
        assertEquals("Abonado", partial.paymentLabel)
        assertEquals("Pagado", order.copy(payments = listOf(Payment(1, 1, 303, 0))).paymentLabel)
        assertEquals(-20L, Report(10, 30, 50, 0, 0, 0).cashFlowCents)
    }

    @Test fun invalidOrderItemCannotProducePlausibleMoney() {
        val invalid = listOf<() -> OrderItem>(
            { OrderItem(null, "Tamal", 0, 100) },
            { OrderItem(null, "Tamal", -1, 100) },
            { OrderItem(null, "Tamal", 1, 0) },
            { OrderItem(null, "Tamal", 1, -100) },
            { OrderItem(null, " ", 1, 100) }
        )
        invalid.forEach { construct ->
            try { construct(); fail("Expected invalid item rejection") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun subtractionOverflowIsRejectedInsteadOfWrapping() {
        try { Report(0, 0, Long.MIN_VALUE, 0, 0, 0).cashFlowCents; fail("Expected subtraction overflow") } catch (_: IllegalArgumentException) { }
        val order = Order(1, 1, "Cliente", "2026-10-08", "", "", OrderStatus.PENDING,
            listOf(OrderItem(null, "Tamal", 1, 100)), listOf(Payment(1, 1, Long.MIN_VALUE, 0)))
        try { order.balanceCents; fail("Expected balance overflow") } catch (_: IllegalArgumentException) { }
    }

    @Test fun overflowIsNotSilentlyWrapped() {
        try { OrderItem(null, "Tamal", 2, Long.MAX_VALUE).totalCents; fail("Expected overflow") } catch (_: IllegalArgumentException) { }
    }
}
