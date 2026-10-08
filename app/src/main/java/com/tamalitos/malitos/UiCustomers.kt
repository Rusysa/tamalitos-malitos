package com.tamalitos.malitos

import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout

internal fun MainActivity.customersScreen() {
    title("Directorio de clientes", "Busca por nombre o teléfono y consulta sus pedidos y saldo.")
    body.addView(button("Agregar cliente", true) { customerForm() })
    val results = column()
    fun update(query: String) {
        results.removeAllViews()
        val customers = store.customers(query)
        if (customers.isEmpty()) results.addView(card().apply {
            addView(text(if (query.isBlank()) "Todavía no hay clientes" else "Sin coincidencias", 20, MainActivity.GREEN, true))
            addView(text(if (query.isBlank()) "Agrega a tu primer cliente para comenzar." else "Prueba con otro nombre o teléfono."))
        })
        customers.forEach { customer ->
            results.addView(card().apply {
                addView(text(customer.name, 21, MainActivity.GREEN, true))
                if (customer.phone.isNotBlank()) addView(text(customer.phone))
                if (customer.address.isNotBlank()) addView(text(customer.address, 14))
                addView(button("Ver cliente: ${customer.name}") { navigate("Clientes", customer.id) })
            })
        }
    }
    search(body, "Buscar cliente por nombre o teléfono", customerQuery) { customerQuery = it; guarded { update(it) } }
    body.addView(results)
    update(customerQuery)
}

internal fun MainActivity.customerDetail(id: Long) {
    val customer = store.customers().firstOrNull { it.id == id }
    if (customer == null) { selectedId = 0; customersScreen(); return }
    title(customer.name, "Datos de contacto, pedidos y cuenta del cliente")
    body.addView(button("Volver al directorio") { navigate("Clientes") })
    body.addView(card().apply {
        addView(text("Teléfono: ${customer.phone.ifBlank { "Sin registrar" }}"))
        addView(text("Dirección: ${customer.address.ifBlank { "Sin registrar" }}"))
        if (customer.notes.isNotBlank()) addView(text("Notas: ${customer.notes}"))
    })
    val orders = store.orders().filter { it.customerId == id }
    val active = orders.filter { it.status != OrderStatus.CANCELLED }
    val balance = active.fold(0L) { sum, order -> Math.addExact(sum, order.balanceCents) }
    body.addView(card().apply { metric(this, "Saldo por cobrar", balance); addView(text("${orders.size} pedidos registrados")) })
    body.addView(button("Nuevo pedido para este cliente", true) { orderForm(id) })
    body.addView(button("Editar cliente") { customerForm(id) })
    body.addView(button("Eliminar cliente") {
        confirm("¿Eliminar a ${customer.name}?", "Solo se puede eliminar un cliente sin pedidos. Los clientes con historial se conservan para proteger tus registros.", "Eliminar") {
            store.deleteCustomer(id); navigate("Clientes"); message("Cliente eliminado.")
        }
    })
    body.addView(text("Historial de pedidos", 21, MainActivity.GREEN, true))
    if (orders.isEmpty()) empty("Sin pedidos", "Crea un pedido para este cliente.")
    orders.sortedByDescending { it.id }.forEach { orderRow(it) }
}

internal fun MainActivity.customerForm(id: Long = 0, draft: Bundle? = null) {
    val existing = store.customers().firstOrNull { it.id == id }
    val fields = column()
    val name = field(fields, "Nombre del cliente *", draft?.string("name", existing?.name.orEmpty()) ?: existing?.name.orEmpty())
    val phone = field(fields, "Teléfono", draft?.string("phone", existing?.phone.orEmpty()) ?: existing?.phone.orEmpty(), input = InputType.TYPE_CLASS_PHONE)
    val address = field(fields, "Dirección habitual", draft?.string("address", existing?.address.orEmpty()) ?: existing?.address.orEmpty(), true)
    val notes = field(fields, "Notas del cliente", draft?.string("notes", existing?.notes.orEmpty()) ?: existing?.notes.orEmpty(), true)
    form(if (id == 0L) "Agregar cliente" else "Editar cliente", fields, "customer", id,
        capture = { Bundle().apply { putString("name", name.value()); putString("phone", phone.value()); putString("address", address.value()); putString("notes", notes.value()) } }) {
        val savedId = store.saveCustomer(Customer(id, required(name, "el nombre del cliente"), phone.value(), address.value(), notes.value()))
        navigate("Clientes", savedId)
        message("Cliente guardado.")
    }
}
