package com.tamalitos.malitos

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun MainActivity.orderRow(order: Order, parent: LinearLayout = body) {
    parent.addView(card().apply {
        addView(text("Pedido #${order.id} · ${order.customerName}", 20, MainActivity.GREEN, true))
        addView(text("${dateLabel(order.deliveryDate)} · ${statusLabel(order.status)}", 15))
        addView(text("Total ${Money.format(order.totalCents)} · Saldo ${Money.format(order.balanceCents)}", 16, MainActivity.GREEN))
        addView(button("Ver pedido #${order.id}") { navigate("Pedidos", order.id) })
    })
}

internal fun MainActivity.ordersScreen() {
    title("Pedidos", "Entregas, anticipos y abonos en un solo lugar.")
    body.addView(button("Nuevo pedido", true) { orderForm() })
    val results = column()
    val statuses = listOf<OrderStatus?>(null, OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.DELIVERED, OrderStatus.CANCELLED)
    fun update() {
        results.removeAllViews()
        val orders = store.orders(orderQuery, statuses[orderFilter.coerceIn(0, statuses.lastIndex)])
        if (orders.isEmpty()) results.addView(card().apply {
            addView(text("No hay pedidos para esta búsqueda", 20, MainActivity.GREEN, true))
            addView(text("Crea un pedido o cambia los filtros."))
        })
        orders.sortedByDescending { it.id }.forEach { orderRow(it, results) }
    }
    search(body, "Buscar pedido por cliente o referencia", orderQuery) { orderQuery = it; guarded { update() } }
    val filter = spinner(body, "Estado del pedido", listOf("Todos", "Pendiente", "En preparación", "Entregado", "Cancelado"), orderFilter)
    filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { orderFilter = position; guarded { update() } }
    }
    body.addView(results)
    update()
}

private class UiItemEditor(val view: LinearLayout, val select: Spinner, val description: EditText, val quantity: EditText, val price: EditText,
                           val products: List<Product>) {
    fun item(): OrderItem {
        val quantityValue = quantity.value().toIntOrNull()
        require(quantityValue != null && quantityValue >= 1) { "La cantidad de cada artículo debe ser un entero mayor que cero." }
        val cents = Money.parse(price.value())
        require(cents > 0) { "El precio de cada artículo debe ser mayor que cero." }
        return OrderItem(products.getOrNull(select.selectedItemPosition - 1)?.id, required(description, "la descripción de cada artículo"), quantityValue, cents)
    }
    fun snapshot() = Bundle().apply {
        putLong("productId", products.getOrNull(select.selectedItemPosition - 1)?.id ?: 0)
        putString("description", description.value()); putString("quantity", quantity.value()); putString("price", price.value())
    }
}

internal fun MainActivity.orderForm(customerId: Long = 0, draft: Bundle? = null) {
    val customers = store.customers()
    if (customers.isEmpty()) {
        confirm("Primero agrega un cliente", "Cada pedido debe tener un cliente para conservar su historial y saldo.", "Agregar cliente") { customerForm() }
        return
    }
    val products = store.products().filter { it.active }
    val fields = column()
    fields.addView(text("Importes en MXN. Los artículos del catálogo reservan existencias al guardar.", 14))
    val savedCustomer = draft?.getLong("customerId")?.takeIf { it != 0L } ?: customerId
    val customer = spinner(fields, "Cliente *", customers.map { "${it.name}${if (it.phone.isBlank()) "" else " · ${it.phone}"} (#${it.id})" },
        customers.indexOfFirst { it.id == savedCustomer }.coerceAtLeast(0))
    val date = dateButton(fields, "Fecha de entrega o reunión *", draft?.string("date", LocalDate.now().toString()) ?: LocalDate.now().toString())
    val address = field(fields, "Dirección de entrega / punto de encuentro *",
        draft?.string("address") ?: customers[customer.selectedItemPosition].address, true)
    var previousDefault = customers[customer.selectedItemPosition].address
    customer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val next = customers[position].address
            if (address.value().isBlank() || (draft == null && address.value() == previousDefault)) address.setText(next)
            previousDefault = next
        }
    }
    val notes = field(fields, "Notas del pedido", draft?.string("notes").orEmpty(), true)
    fields.addView(text("Artículos del pedido", 21, MainActivity.GREEN, true))
    val lines = column()
    fields.addView(lines)
    val editors = mutableListOf<UiItemEditor>()
    val totalLabel = text("Completa los artículos para calcular el total.", 18, MainActivity.GREEN, true)
    val modes = InitialPayment.entries.toList()
    val mode = spinner(fields, "Anticipo al crear el pedido", listOf("Pago completo", "La mitad (50 %)", "Sin anticipo", "Otro importe"), draft?.getInt("mode", 2) ?: 2)
    val custom = moneyField(fields, "Importe del anticipo personalizado", draft?.string("custom").orEmpty())
    val paymentPreview = text("", 16, MainActivity.GREEN)
    fields.addView(totalLabel); fields.addView(paymentPreview)
    fun preview() {
        custom.isEnabled = modes[mode.selectedItemPosition] == InitialPayment.CUSTOM
        try {
            val total = editors.map { it.item() }.fold(0L) { sum, item -> Math.addExact(sum, item.totalCents) }
            require(editors.isNotEmpty())
            val payment = BusinessRules.initialPayment(total, modes[mode.selectedItemPosition], if (custom.isEnabled) Money.parse(custom.value()) else 0)
            totalLabel.text = "Total: ${Money.format(total)}"
            paymentPreview.text = "Anticipo: ${Money.format(payment)} · Saldo: ${Money.format(Math.subtractExact(total, payment))}"
        } catch (_: Exception) {
            totalLabel.text = "Completa artículos y anticipo con importes válidos."
            paymentPreview.text = "El total se valida antes de guardar."
        }
    }
    val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { preview() }
        override fun afterTextChanged(s: Editable?) = Unit
    }
    fun addItem(saved: Bundle? = null) {
        require(editors.size < 100) { "El pedido admite hasta 100 artículos." }
        val row = card()
        val productId = saved?.getLong("productId") ?: 0
        val selected = products.indexOfFirst { it.id == productId } + 1
        val pick = spinner(row, "Artículo del catálogo o personalizado", listOf("Artículo personalizado") + products.map {
            "${it.name} · ${Money.format(it.priceCents)}${it.stock?.let { stock -> " · Stock: $stock" }.orEmpty()}"
        }, selected)
        val description = field(row, "Descripción del artículo *", saved?.string("description").orEmpty())
        val quantity = field(row, "Cantidad *", saved?.string("quantity", "1") ?: "1", input = InputType.TYPE_CLASS_NUMBER)
        val price = moneyField(row, "Precio unitario *", saved?.string("price").orEmpty())
        val editor = UiItemEditor(row, pick, description, quantity, price, products)
        editors.add(editor)
        var firstSelection = true
        pick.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!(firstSelection && saved != null)) products.getOrNull(position - 1)?.let {
                    description.setText(it.name); price.setText(centsInput(it.priceCents))
                }
                firstSelection = false
                preview()
            }
        }
        row.addView(button("Quitar este artículo") { editors.remove(editor); lines.removeView(row); preview() })
        lines.addView(row)
        listOf(description, quantity, price).forEach { it.addTextChangedListener(watcher) }
        preview()
    }
    @Suppress("DEPRECATION")
    val restored = draft?.getParcelableArrayList<Bundle>("items")
    if (restored.isNullOrEmpty()) addItem() else restored.take(100).forEach { addItem(it) }
    fields.addView(button("Agregar otro artículo") { addItem() })
    custom.addTextChangedListener(watcher)
    mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { preview() }
    }
    preview()
    form("Nuevo pedido", fields, "order", customerId, capture = {
        Bundle().apply {
            putLong("customerId", customers[customer.selectedItemPosition].id); putString("date", date.iso())
            putString("address", address.value()); putString("notes", notes.value()); putInt("mode", mode.selectedItemPosition)
            putString("custom", custom.value()); putParcelableArrayList("items", ArrayList(editors.map { it.snapshot() }))
        }
    }, saveLabel = "Crear pedido") {
        require(editors.isNotEmpty()) { "Agrega al menos un artículo al pedido." }
        val id = store.createOrder(customers[customer.selectedItemPosition].id, date.iso(), required(address, "la dirección de entrega o el punto de encuentro"), notes.value(),
            editors.map { it.item() }, modes[mode.selectedItemPosition], if (modes[mode.selectedItemPosition] == InitialPayment.CUSTOM) Money.parse(custom.value()) else 0)
        navigate("Pedidos", id); message("Pedido creado.")
    }
}

internal fun MainActivity.orderDetail(id: Long) {
    val order = store.order(id)
    if (order == null) { selectedId = 0; ordersScreen(); return }
    title("Pedido #${order.id}", order.customerName)
    body.addView(button("Volver a pedidos") { navigate("Pedidos") })
    body.addView(button("Ver cuenta de ${order.customerName}") { navigate("Clientes", order.customerId) })
    body.addView(card().apply {
        addView(text(statusLabel(order.status), 21, MainActivity.GREEN, true))
        addView(text("Entrega: ${dateLabel(order.deliveryDate)}"))
        addView(text("Dirección / encuentro: ${order.deliveryAddress}"))
        if (order.notes.isNotBlank()) addView(text("Notas: ${order.notes}"))
    })
    body.addView(card().apply {
        metric(this, "Total del pedido", order.totalCents)
        metric(this, "Cobrado", order.paidCents)
        metric(this, "Saldo pendiente", order.balanceCents)
        addView(text(if (order.status == OrderStatus.CANCELLED) "Pedido cancelado: no suma a ventas ni deuda." else when {
            order.balanceCents == 0L -> "Pagado"
            order.paidCents == 0L -> "Sin pagar"
            else -> "Pago parcial"
        }, 16, MainActivity.GREEN, true))
    })
    body.addView(text("Artículos", 21, MainActivity.GREEN, true))
    order.items.forEach { item ->
        body.addView(card().apply {
            addView(text(item.description, 19, MainActivity.GREEN, true))
            addView(text("${item.quantity} × ${Money.format(item.unitPriceCents)} = ${Money.format(item.totalCents)}"))
        })
    }
    if (order.status != OrderStatus.CANCELLED) {
        if (order.balanceCents > 0) body.addView(button("Registrar abono", true) { paymentForm(id) })
        body.addView(button("Cambiar estado") {
            orderStatusConfirmation(id, order.status)
        })
        body.addView(button("Cancelar pedido") {
            confirm("¿Cancelar el pedido #$id?", "La cancelación es definitiva y libera el stock reservado. No se puede cancelar un pedido con pagos registrados: aún no se gestionan reembolsos.", "Cancelar pedido") {
                store.setOrderStatus(id, OrderStatus.CANCELLED); render(); message("Pedido cancelado.")
            }
        })
    }
    body.addView(text("Historial de pagos", 21, MainActivity.GREEN, true))
    if (order.payments.isEmpty()) empty("Sin pagos registrados", "Los anticipos y los abonos aparecerán aquí.")
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
    order.payments.sortedByDescending { it.timestamp }.forEach { payment ->
        body.addView(card().apply {
            addView(text(Money.format(payment.amountCents), 21, MainActivity.GREEN, true))
            addView(text(dateFormat.format(Instant.ofEpochMilli(payment.timestamp)), 14))
            if (payment.note.isNotBlank()) addView(text(payment.note))
        })
    }
}

internal fun MainActivity.paymentForm(orderId: Long, draft: Bundle? = null) {
    val order = store.order(orderId) ?: throw IllegalArgumentException("El pedido ya no existe.")
    require(order.status != OrderStatus.CANCELLED && order.balanceCents > 0) { "Este pedido no admite más pagos." }
    val fields = column()
    fields.addView(text("Saldo actual: ${Money.format(order.balanceCents)}", 20, MainActivity.GREEN, true))
    fields.addView(text("El abono se registra con la fecha y hora actuales. No se puede deshacer desde la aplicación.", 14))
    val amount = moneyField(fields, "Importe del abono *", draft?.string("amount").orEmpty())
    val note = field(fields, "Nota / referencia del pago", draft?.string("note").orEmpty(), true)
    form("Registrar abono · Pedido #$orderId", fields, "payment", orderId,
        capture = { Bundle().apply { putString("amount", amount.value()); putString("note", note.value()) } }, saveLabel = "Registrar pago") {
        store.addPayment(orderId, Money.parse(required(amount, "el importe del abono")), note.value())
        navigate("Pedidos", orderId); message("Abono registrado.")
    }
}
