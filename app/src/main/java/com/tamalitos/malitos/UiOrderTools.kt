package com.tamalitos.malitos

import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class DeliveryFilter(val label: String) {
    ALL("Todas las fechas"), TODAY("Por entregar hoy"), TOMORROW("Por entregar mañana"), OVERDUE("Entregas vencidas")
}
internal enum class OrderSort(val label: String) {
    RECENT("Más recientes"), DELIVERY("Fecha de entrega"), BALANCE("Mayor saldo")
}
internal fun Order.awaitingDelivery() = status == OrderStatus.PENDING || status == OrderStatus.PREPARING

internal fun filterOrders(
    orders: List<Order>, query: String = "", status: OrderStatus? = null,
    delivery: DeliveryFilter = DeliveryFilter.ALL, owingOnly: Boolean = false,
    sort: OrderSort = OrderSort.RECENT, today: LocalDate = LocalDate.now()
): List<Order> {
    val term = query.trim()
    val rows = orders.filter { order ->
        (status == null || order.status == status) &&
            listOf(order.id.toString(), order.customerName, order.deliveryAddress, order.notes)
                .any { it.contains(term, ignoreCase = true) } &&
            (!owingOnly || (order.status != OrderStatus.CANCELLED && order.balanceCents > 0)) &&
            (delivery == DeliveryFilter.ALL || (order.awaitingDelivery() && when (delivery) {
                DeliveryFilter.TODAY -> order.deliveryDate == today.toString()
                DeliveryFilter.TOMORROW -> order.deliveryDate == today.plusDays(1).toString()
                DeliveryFilter.OVERDUE -> order.deliveryDate < today.toString()
                DeliveryFilter.ALL -> true
            }))
    }
    return when (sort) {
        OrderSort.RECENT -> rows.sortedByDescending { it.id }
        OrderSort.DELIVERY -> rows.sortedWith(compareBy<Order> { it.deliveryDate }.thenBy { it.id })
        OrderSort.BALANCE -> rows.sortedWith(compareByDescending<Order> { it.balanceCents }.thenBy { it.id })
    }
}

/** Customer-facing summary: private notes and payment references stay in the app. */
internal fun orderSummary(order: Order, statusLabel: String): String = buildString {
    appendLine("Tamalitos Malitos · Pedido #${order.id}")
    appendLine("Cliente: ${order.customerName}")
    appendLine("Estado: $statusLabel")
    appendLine("Entrega: ${LocalDate.parse(order.deliveryDate).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
    appendLine("Dirección / encuentro: ${order.deliveryAddress}")
    appendLine()
    order.items.forEach { appendLine("${it.quantity} × ${it.description} · ${Money.format(it.unitPriceCents)} c/u = ${Money.format(it.totalCents)}") }
    appendLine()
    appendLine("Total: ${Money.format(order.totalCents)} MXN")
    appendLine("Pagos registrados: ${Money.format(order.paidCents)} MXN")
    if (order.status == OrderStatus.CANCELLED) append("Cancelado: sin saldo exigible.")
    else append("Saldo pendiente: ${Money.format(order.balanceCents)} MXN")
}
