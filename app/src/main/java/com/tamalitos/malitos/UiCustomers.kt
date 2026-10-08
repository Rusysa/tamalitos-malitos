package com.tamalitos.malitos

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable internal fun Customers(a: MainActivity, state: BusinessUiState) {
    val rows = state.customers.filter { customer -> a.customerQuery.isBlank() ||
        listOf(customer.name, customer.phone, customer.address, customer.notes).any { it.contains(a.customerQuery.trim(), ignoreCase = true) } }
    LazyColumn(Modifier.fillMaxSize().testTag("customer-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("Directorio de clientes", "Contacto, pedidos y cuenta del cliente") }
        item { Action("Agregar cliente", true) { a.customerForm() } }
        item { Input("Buscar cliente por nombre o teléfono", a.customerQuery) { a.customerQuery = it } }
        if(rows.isEmpty()) item { Empty(if(a.customerQuery.isBlank()) "Todavía no hay clientes" else "Sin coincidencias", "Agrega un cliente o cambia la búsqueda.") }
        items(rows, key = { it.id }) { customer -> InfoCard {
            Text(customer.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge); Text(customer.phone); Text(customer.address)
            Action("Ver cliente: ${customer.name}") { a.navigate("Clientes", customer.id) }
        } }
    }
}
@Composable internal fun CustomerDetail(a: MainActivity, state: BusinessUiState) {
    val customer = state.customers.firstOrNull { it.id == a.selectedId }
    Page {
        if(customer == null) { Empty("Cliente no disponible", "Vuelve al directorio."); return@Page }
        Heading(customer.name, "Datos de contacto, pedidos y cuenta")
        Action("Volver al directorio") { a.navigate("Clientes") }
        InfoCard { Text("Teléfono: ${customer.phone.ifBlank { "Sin registrar" }}"); Text("Dirección: ${customer.address.ifBlank { "Sin registrar" }}"); Text(customer.notes) }
        val orders = state.orders.filter { it.customerId == customer.id }
        Metric("Saldo por cobrar", orders.filter { it.status != OrderStatus.CANCELLED }.fold(0L) { sum, order -> Math.addExact(sum, order.balanceCents) })
        Text("${orders.size} pedidos registrados")
        Action("Nuevo pedido para este cliente", true) { a.orderForm(customer.id) }
        Action("Editar cliente") { a.customerForm(customer.id) }
        Action("Eliminar cliente") { a.confirm("¿Eliminar a ${customer.name}?", "Solo se puede eliminar un cliente sin pedidos. El historial se conserva para proteger tus registros.", "Eliminar") {
            a.write(state.generation, { it.deleteCustomer(customer.id) }, { a.navigate("Clientes") })
        } }
        Heading("Historial de pedidos")
        if(orders.isEmpty()) Empty("Sin pedidos", "Crea un pedido para este cliente.")
        orders.sortedByDescending { it.id }.forEach { OrderRow(a, it) }
    }
}
internal fun MainActivity.customerForm(id: Long = 0, saved: Bundle? = null) {
    val existing = model.state.value.customers.firstOrNull { it.id == id }
    openEditor("customer", id, saved ?: Bundle().apply { putString("name", existing?.name.orEmpty()); putString("phone", existing?.phone.orEmpty()); putString("address", existing?.address.orEmpty()); putString("notes", existing?.notes.orEmpty()) })
}
@Composable internal fun CustomerFields(a: MainActivity) {
    FormField(a, "Nombre del cliente *", "name"); FormField(a, "Teléfono", "phone")
    FormField(a, "Dirección habitual", "address", multiline = true); FormField(a, "Notas del cliente", "notes", multiline = true)
}
