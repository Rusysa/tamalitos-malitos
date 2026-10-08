package com.tamalitos.malitos

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun OrderRow(a: MainActivity, order: Order) {
    InfoCard {
        Text("Pedido #${order.id} · ${order.customerName}", style = MaterialTheme.typography.titleMedium)
        Text("${a.dateLabel(order.deliveryDate)} · ${a.statusLabel(order.status)}")
        Text("Total ${Money.format(order.totalCents)} · Saldo ${Money.format(order.balanceCents)}")
        Action("Ver pedido #${order.id}") { a.navigate("Pedidos", order.id) }
    }
}
@Composable internal fun Orders(a: MainActivity, state: BusinessUiState) {
    val statuses = listOf(null, OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.DELIVERED, OrderStatus.CANCELLED)
    val rows = state.orders.filter { (a.orderFilter == 0 || it.status == statuses[a.orderFilter.coerceIn(0, 4)]) && listOf(it.id.toString(), it.customerName, it.deliveryAddress, it.notes).any { text -> text.contains(a.orderQuery.trim(), true) } }.sortedByDescending { it.id }
    LazyColumn(Modifier.fillMaxSize().testTag("order-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("Pedidos", "Entregas, anticipos y abonos") }
        item { Action("Nuevo pedido", true) { a.orderForm() } }
        item { Input("Buscar pedido por cliente o referencia", a.orderQuery) { a.orderQuery = it } }
        item { Choice("Estado del pedido", listOf("Todos", "Pendiente", "En preparación", "Entregado", "Cancelado"), a.orderFilter) { a.orderFilter = it } }
        if(rows.isEmpty()) item { Empty("No hay pedidos para esta búsqueda", "Crea un pedido o cambia los filtros.") }
        items(rows, key = { it.id }) { OrderRow(a, it) }
    }
}
@Composable internal fun OrderDetail(a: MainActivity, state: BusinessUiState) {
    val order = state.orders.firstOrNull { it.id == a.selectedId }
    Page {
        if(order == null) { Empty("Pedido no disponible", "Vuelve a la lista."); return@Page }
        Heading("Pedido #${order.id}", order.customerName)
        Action("Volver a pedidos") { a.navigate("Pedidos") }
        Action("Ver cuenta de ${order.customerName}") { a.navigate("Clientes", order.customerId) }
        InfoCard { Text(a.statusLabel(order.status)); Text("Entrega: ${a.dateLabel(order.deliveryDate)}"); Text("Dirección / encuentro: ${order.deliveryAddress}"); Text(order.notes) }
        Metric("Total del pedido", order.totalCents); Metric("Cobrado", order.paidCents); Metric("Saldo pendiente", order.balanceCents)
        Text(if(order.status == OrderStatus.CANCELLED) "Pedido cancelado: no suma a ventas ni deuda." else if(order.balanceCents == 0L) "Pagado" else if(order.paidCents == 0L) "Sin pagar" else "Pago parcial")
        Heading("Artículos")
        order.items.forEach { item -> InfoCard { Text(item.description); Text("${item.quantity} × ${Money.format(item.unitPriceCents)} = ${Money.format(item.totalCents)}") } }
        if(order.status != OrderStatus.CANCELLED) {
            if(order.balanceCents > 0) Action("Registrar abono", true) { a.paymentForm(order.id) }
            Action("Cambiar estado") { a.orderStatusConfirmation(order.id, order.status) }
            Action("Cancelar pedido") { a.confirm("¿Cancelar el pedido #${order.id}?", "La cancelación es definitiva y libera el stock reservado. No se puede cancelar con abonos: los reembolsos no están implementados.", "Cancelar pedido") {
                a.write(state.generation, { it.setOrderStatus(order.id, OrderStatus.CANCELLED) })
            } }
        }
        Heading("Historial de pagos")
        if(order.payments.isEmpty()) Empty("Sin pagos registrados", "Los anticipos y abonos aparecerán aquí.")
        val format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
        order.payments.sortedByDescending { it.timestamp }.forEach { payment -> InfoCard { Text(Money.format(payment.amountCents)); Text(format.format(Instant.ofEpochMilli(payment.timestamp))); Text(payment.note) } }
    }
}
internal fun MainActivity.orderForm(customerId: Long = 0, saved: Bundle? = null) {
    val customers = model.state.value.customers
    if(customers.isEmpty()) { confirm("Primero agrega un cliente", "Cada pedido necesita un cliente para conservar su historial y saldo.", "Agregar cliente") { customerForm() }; return }
    val customer = customers.firstOrNull { it.id == customerId } ?: customers.first()
    openEditor("order", customerId, saved ?: Bundle().apply {
        putLong("customerId", customer.id); putString("date", LocalDate.now().toString()); putString("address", customer.address)
        putString("mode", "2"); putParcelableArrayList("items", arrayListOf(Bundle().apply { putString("quantity", "1") }))
    }.let { input -> Bundle(input).apply { if(input.containsKey("mode") && input.get("mode") is Int) putString("mode", input.getInt("mode").toString()) } })
}
internal fun MainActivity.updateItem(index: Int, key: String, value: String, product: Product? = null) {
    guarded { store.withGeneration(draftGeneration) {
        val rows = draft.items().map { Bundle(it) }.toMutableList()
        val row = rows[index]; row.putString(key, value)
        if(product != null) { row.putLong("productId", product.id); row.putString("description", product.name); row.putString("price", centsInput(product.priceCents)) }
        draft = Bundle(draft).apply { putParcelableArrayList("items", ArrayList(rows)) }
    } }
}
@Composable internal fun OrderFields(a: MainActivity, state: BusinessUiState) {
    Text("Importes en MXN. El catálogo reserva existencias al guardar.")
    val customer = state.customers.indexOfFirst { it.id == a.draft.getLong("customerId") }.coerceAtLeast(0)
    Choice("Cliente *", state.customers.map { "${it.name} (#${it.id})" }, customer) { position -> a.guarded { a.store.withGeneration(a.draftGeneration) {
        val next = state.customers[position]; val previous = state.customers.getOrNull(customer)
        a.draft = Bundle(a.draft).apply { putLong("customerId", next.id); if(string("address").isBlank() || string("address") == previous?.address) putString("address", next.address) }
    } } }
    FormField(a, "Fecha de entrega o reunión * (AAAA-MM-DD)", "date")
    FormField(a, "Dirección de entrega / punto de encuentro *", "address", multiline = true)
    FormField(a, "Notas del pedido", "notes", multiline = true)
    val products = state.products.filter { it.active }
    a.draft.items().forEachIndexed { index, row -> InfoCard {
        Text("Artículo ${index + 1}", style = MaterialTheme.typography.titleMedium)
        Choice("Artículo del catálogo o personalizado ${index + 1}", listOf("Artículo personalizado") + products.map { "${it.name} · ${Money.format(it.priceCents)}${it.stock?.let { stock -> " · Stock: $stock" }.orEmpty()}" }, products.indexOfFirst { it.id == row.getLong("productId") } + 1) { position ->
            if(position == 0) { a.guarded { a.store.withGeneration(a.draftGeneration) { a.draft = Bundle(a.draft).apply { putParcelableArrayList("items", ArrayList(a.draft.items().mapIndexed { i, item -> Bundle(item).apply { if(i == index) putLong("productId", 0) } })) } } } }
            else a.updateItem(index, "description", products[position - 1].name, products[position - 1])
        }
        Input("Descripción del artículo *", row.string("description"), "item-$index-description") { a.updateItem(index, "description", it) }
        Input("Cantidad *", row.string("quantity", "1"), "item-$index-quantity", numeric = true) { a.updateItem(index, "quantity", it) }
        Input("Precio unitario *", row.string("price"), "item-$index-price", numeric = true) { a.updateItem(index, "price", it) }
        Action("Quitar artículo ${index + 1}") { a.store.withGeneration(a.draftGeneration) { a.draft = Bundle(a.draft).apply { putParcelableArrayList("items", ArrayList(a.draft.items().filterIndexed { i, _ -> i != index })) } } }
    } }
    Action("Agregar otro artículo", enabled = a.draft.items().size < 100) { a.store.withGeneration(a.draftGeneration) { a.draft = Bundle(a.draft).apply { putParcelableArrayList("items", ArrayList(a.draft.items() + Bundle().apply { putString("quantity", "1") })) } } }
    val mode = a.draft.string("mode", "2").toIntOrNull() ?: 2
    Choice("Anticipo al crear el pedido", listOf("Pago completo", "La mitad (50 %)", "Sin anticipo", "Otro importe"), mode) { a.updateDraft("mode", it.toString()) }
    if(mode == 3) FormField(a, "Importe del anticipo personalizado", "custom", true)
    val preview = runCatching {
        val total = a.draft.items().fold(0L) { sum, item -> Math.addExact(sum, OrderItem(null, item.string("description"), item.string("quantity").toInt(), Money.parse(item.string("price"))).totalCents) }
        val payment = BusinessRules.initialPayment(total, InitialPayment.entries[mode], if(mode == 3) Money.parse(a.draft.string("custom")) else 0)
        "Total: ${Money.format(total)} · Anticipo: ${Money.format(payment)} · Saldo: ${Money.format(total - payment)}"
    }.getOrDefault("Completa los artículos y el anticipo con importes válidos.")
    Text(preview, color = MaterialTheme.colorScheme.primary)
}
internal fun MainActivity.paymentForm(orderId: Long, saved: Bundle? = null) = openEditor("payment", orderId, saved ?: Bundle())
@Composable internal fun PaymentFields(a: MainActivity, state: BusinessUiState) {
    Text("Saldo actual: ${Money.format(state.orders.firstOrNull { it.id == a.draftId }?.balanceCents ?: 0)}")
    Text("El abono se registra con fecha y hora actuales y no se puede deshacer.")
    FormField(a, "Importe del abono *", "amount", true); FormField(a, "Nota / referencia del pago", "note", multiline = true)
}
