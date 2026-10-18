package com.tamalitos.malitos

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UiOrderToolsTest {
    private val today = LocalDate.of(2026, 10, 12)
    private fun order(id: Long, date: String = today.toString(), status: OrderStatus = OrderStatus.PENDING, paid: Long = 0) =
        Order(id, 1, "Ana", date, "Calle 10", "Nota privada", status,
            listOf(OrderItem(null, "Tamal verde", 2, 2500)),
            if (paid == 0L) emptyList() else listOf(Payment(1, id, paid, 0, "Referencia privada")))

    @Test fun agendaExcludesDeliveredAndCancelledEvenWhenUnpaid() {
        val rows = listOf(order(1), order(2, status = OrderStatus.PREPARING),
            order(3, status = OrderStatus.DELIVERED), order(4, status = OrderStatus.CANCELLED),
            order(5, "2026-10-11"), order(6, "2026-10-13"))
        assertEquals(listOf(2L, 1L), filterOrders(rows, delivery = DeliveryFilter.TODAY, today = today).map { it.id })
        assertEquals(listOf(5L), filterOrders(rows, delivery = DeliveryFilter.OVERDUE, today = today).map { it.id })
        assertEquals(listOf(6L), filterOrders(rows, delivery = DeliveryFilter.TOMORROW, today = today).map { it.id })
    }

    @Test fun owingIncludesDeliveredExcludesCancelledAndPaidAndCombinesWithSearch() {
        val rows = listOf(order(1, paid = 5000), order(2, status = OrderStatus.CANCELLED),
            order(3, status = OrderStatus.DELIVERED, paid = 2000), order(4).copy(customerName = "Luis"))
        assertEquals(listOf(3L), filterOrders(rows, query = " ANA ", owingOnly = true).map { it.id })
        assertEquals(listOf(4L, 3L), filterOrders(rows, owingOnly = true, sort = OrderSort.BALANCE).map { it.id })
        assertTrue(filterOrders(rows, status = OrderStatus.CANCELLED, owingOnly = true).isEmpty())
    }

    @Test fun deliverySortUsesDateThenIdAndCanFindAnAddress() {
        val rows = listOf(order(3, "2026-10-13"), order(2), order(1))
        assertEquals(listOf(1L, 2L, 3L), filterOrders(rows, query = "calle", sort = OrderSort.DELIVERY).map { it.id })
    }

    @Test fun summaryUsesActualPaymentsAndOmitsPrivateNotes() {
        val summary = orderSummary(order(1, paid = 2000), "Pendiente")
        assertTrue(summary.contains("12/10/2026"))
        assertTrue(summary.contains("2 × Tamal verde"))
        assertTrue(summary.contains("Saldo pendiente: ${Money.format(3000)} MXN"))
        assertFalse(summary.contains("Nota privada"))
        assertFalse(summary.contains("Referencia privada"))
    }

    @Test fun cancelledSummaryDoesNotAskForPayment() {
        val summary = orderSummary(order(1, status = OrderStatus.CANCELLED), "Cancelado")
        assertTrue(summary.contains("Cancelado: sin saldo exigible."))
        assertFalse(summary.contains("Saldo pendiente:"))
    }
}
