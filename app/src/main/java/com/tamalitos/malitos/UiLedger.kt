package com.tamalitos.malitos

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable internal fun Period(a: MainActivity, from: String, to: String, generation: Long, applied: (String, String) -> Unit) {
    var start by rememberSaveable(from) { mutableStateOf(from.ifBlank { LocalDate.now().withDayOfMonth(1).toString() }) }
    var end by rememberSaveable(to) { mutableStateOf(to.ifBlank { LocalDate.now().toString() }) }
    InfoCard {
        Text(if(from.isBlank() && to.isBlank()) "Periodo: todo el historial" else "Periodo: ${a.dateLabel(from)} — ${a.dateLabel(to)}")
        DateInput("Desde (inclusive, AAAA-MM-DD)", start, "period-from", generation, a) { start = it }; DateInput("Hasta (inclusive, AAAA-MM-DD)", end, "period-to", generation, a) { end = it }
        Action("Aplicar periodo", true) { UiPeriod.validated(start, end); applied(start, end) }
        Action("Mes actual") { applied(LocalDate.now().withDayOfMonth(1).toString(), LocalDate.now().toString()) }
        Action("Todo el historial") { applied("", "") }
    }
}
@Composable internal fun Expenses(a: MainActivity, state: BusinessUiState) {
    Page {
        Heading("Gastos", "En qué se usa el dinero del negocio")
        Action("Registrar gasto", true) { a.expenseForm() }
        Period(a, a.expenseFrom, a.expenseTo, state.generation) { start, end -> a.expenseFrom = start; a.expenseTo = end }
        val rows = state.expenses.filter { (a.expenseFrom.isBlank() || it.date >= a.expenseFrom) && (a.expenseTo.isBlank() || it.date <= a.expenseTo) }
        Metric("Gastos del periodo", rows.fold(0L) { sum, expense -> Math.addExact(sum, expense.amountCents) })
        if(rows.isEmpty()) Empty("No hay gastos en este periodo", "Registra un gasto o cambia las fechas.")
        rows.sortedByDescending { it.date }.forEach { expense -> InfoCard {
            Text(expense.description, style = MaterialTheme.typography.titleLarge); Text("${expense.category} · ${a.dateLabel(expense.date)}")
            Text(Money.format(expense.amountCents)); Text(expense.notes)
            Action("Editar gasto: ${expense.description}") { a.expenseForm(expense.id) }
            Action("Eliminar gasto: ${expense.description}") { a.confirm("¿Eliminar este gasto?", "${expense.description}: ${Money.format(expense.amountCents)}. No se puede deshacer.", "Eliminar gasto") { a.write(state.generation, { it.deleteExpense(expense.id) }) } }
        } }
    }
}
@Composable internal fun Reports(a: MainActivity, state: BusinessUiState) {
    Page {
        Heading("Informes del negocio", "Ventas y dinero recibido son distintos")
        Period(a, a.reportFrom, a.reportTo, state.generation) { start, end -> a.reportFrom = start; a.reportTo = end; a.render() }
        Summary(state.periodReport, true)
        InfoCard {
            Text("Cómo leer este informe", style = MaterialTheme.typography.titleMedium)
            Text("Ventas, deuda y pedidos: fecha de entrega. Cobros: fecha real de cada anticipo o abono, aunque el pedido se entregue fuera del periodo. Gastos: fecha registrada. Ambos extremos son inclusivos.")
            Text("La deuda es el saldo actual, no una reconstrucción histórica. Los cancelados no cuentan como ventas ni deuda. El flujo de efectivo no representa utilidad contable.")
        }
        val expenses = state.expenses.filter { (a.reportFrom.isBlank() || it.date >= a.reportFrom) && (a.reportTo.isBlank() || it.date <= a.reportTo) }
        Heading("Gastos por categoría")
        if(expenses.isEmpty()) Empty("Sin gastos en este periodo", "Los gastos se agrupan por categoría.")
        expenses.groupBy { it.category }.toSortedMap().forEach { (category, rows) -> Metric(category, rows.fold(0L) { sum, expense -> Math.addExact(sum, expense.amountCents) }) }
        Heading("Pedidos con saldo por cobrar")
        val orders = state.orders.filter { it.status != OrderStatus.CANCELLED && it.balanceCents > 0 && (a.reportFrom.isBlank() || it.deliveryDate >= a.reportFrom) && (a.reportTo.isBlank() || it.deliveryDate <= a.reportTo) }
        if(orders.isEmpty()) Empty("Sin deuda en este periodo", "No hay saldo pendiente en los pedidos seleccionados.")
        orders.forEach { OrderRow(a, it) }
    }
}
@Composable internal fun Products(a: MainActivity, state: BusinessUiState) {
    Page {
        Heading("Catálogo y existencias", "Precios reutilizables y stock opcional")
        Action("Agregar producto", true) { a.productForm() }
        Text("El stock se reserva al crear pedidos y se recupera al cancelarlos. Déjalo vacío para no controlar existencias.")
        if(state.products.isEmpty()) Empty("Tu catálogo está vacío", "También puedes crear pedidos con artículos personalizados.")
        state.products.forEach { product -> InfoCard { Text(product.name, style = MaterialTheme.typography.titleLarge)
            Text(Money.format(product.priceCents)); Text(product.stock?.let { "Existencias: $it" } ?: "Sin control de existencias")
            Text(if(product.active) "Activo" else "Inactivo · no aparece en pedidos nuevos")
            Action("Editar producto: ${product.name}") { a.productForm(product.id) }
        } }
    }
}
internal fun MainActivity.expenseForm(id: Long = 0, saved: Bundle? = null) {
    val existing = model.state.value.expenses.firstOrNull { it.id == id }
    openEditor("expense", id, saved ?: Bundle().apply { putString("description", existing?.description.orEmpty()); putString("amount", existing?.let { centsInput(it.amountCents) }.orEmpty()); putString("category", existing?.category ?: "Ingredientes"); putString("date", existing?.date ?: LocalDate.now().toString()); putString("notes", existing?.notes.orEmpty()) })
}
@Composable internal fun ExpenseFields(a: MainActivity) {
    FormField(a, "Concepto del gasto *", "description"); FormField(a, "Importe del gasto *", "amount", true)
    FormField(a, "Categoría del gasto", "category")
    Choice("Categorías frecuentes", listOf("Ingredientes", "Empaques", "Transporte", "Servicios", "Equipo", "Otros"), listOf("Ingredientes", "Empaques", "Transporte", "Servicios", "Equipo", "Otros").indexOf(a.draft.string("category")).coerceAtLeast(0)) { a.updateDraft("category", listOf("Ingredientes", "Empaques", "Transporte", "Servicios", "Equipo", "Otros")[it]) }
    FormField(a, "Fecha del gasto * (AAAA-MM-DD)", "date"); FormField(a, "Notas del gasto", "notes", multiline = true)
}
internal fun MainActivity.productForm(id: Long = 0, saved: Bundle? = null) {
    val existing = model.state.value.products.firstOrNull { it.id == id }
    openEditor("product", id, saved ?: Bundle().apply { putString("name", existing?.name.orEmpty()); putString("price", existing?.let { centsInput(it.priceCents) }.orEmpty()); putString("stock", existing?.stock?.toString().orEmpty()); putBoolean("active", existing?.active ?: true) })
}
@Composable internal fun ProductFields(a: MainActivity) {
    FormField(a, "Nombre del producto *", "name"); FormField(a, "Precio unitario *", "price", true)
    FormField(a, "Existencias disponibles (vacío: sin control)", "stock", true)
    Text("Ajusta existencias disponibles, no unidades ya reservadas. Los precios nuevos no modifican pedidos anteriores.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(a.draft.getBoolean("active", true), { a.guarded { a.store.withGeneration(a.draftGeneration) { a.draft = Bundle(a.draft).apply { putBoolean("active", it) } } } }, Modifier.semantics { contentDescription = "Producto activo para pedidos nuevos" })
        Text("Producto activo para pedidos nuevos")
    }
}
