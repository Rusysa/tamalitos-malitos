package com.tamalitos.malitos

import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import java.time.LocalDate

internal fun MainActivity.periodControls(from: String, to: String, applied: (String, String) -> Unit) {
    body.addView(text(if (from.isBlank() && to.isBlank()) "Periodo: todo el historial" else "Periodo: ${dateLabel(from)} — ${dateLabel(to)}", 16, MainActivity.GREEN, true))
    val filters = card()
    val start = dateButton(filters, "Desde (inclusive)", from.ifBlank { LocalDate.now().withDayOfMonth(1).toString() })
    val end = dateButton(filters, "Hasta (inclusive)", to.ifBlank { LocalDate.now().toString() })
    filters.addView(button("Aplicar periodo", true) { UiPeriod.validated(start.iso(), end.iso()); applied(start.iso(), end.iso()) })
    filters.addView(button("Mes actual") { applied(LocalDate.now().withDayOfMonth(1).toString(), LocalDate.now().toString()) })
    filters.addView(button("Todo el historial") { applied("", "") })
    body.addView(filters)
}

internal fun MainActivity.expensesScreen() {
    title("Gastos", "Registra en qué se usa el dinero del negocio.")
    body.addView(button("Registrar gasto", true) { expenseForm() })
    periodControls(expenseFrom, expenseTo) { from, to -> expenseFrom = from; expenseTo = to; render() }
    val range = UiPeriod.validated(expenseFrom, expenseTo)
    val expenses = store.expenses(range.from, range.to).sortedByDescending { it.date }
    body.addView(card().apply { metric(this, "Gastos del periodo", expenses.fold(0L) { sum, expense -> Math.addExact(sum, expense.amountCents) }) })
    if (expenses.isEmpty()) empty("No hay gastos en este periodo", "Registra un gasto o cambia las fechas.")
    expenses.forEach { expense ->
        body.addView(card().apply {
            addView(text(expense.description, 21, MainActivity.GREEN, true))
            addView(text("${expense.category} · ${dateLabel(expense.date)}", 14))
            addView(text(Money.format(expense.amountCents), 22, MainActivity.GREEN, true))
            if (expense.notes.isNotBlank()) addView(text(expense.notes))
            addView(button("Editar gasto: ${expense.description}") { expenseForm(expense.id) })
            addView(button("Eliminar gasto: ${expense.description}") {
                confirm("¿Eliminar este gasto?", "${expense.description}: ${Money.format(expense.amountCents)}. El registro dejará de aparecer en los informes. Esta acción no se puede deshacer.", "Eliminar gasto") {
                    store.deleteExpense(expense.id); render(); message("Gasto eliminado.")
                }
            })
        })
    }
}

internal fun MainActivity.expenseForm(id: Long = 0, draft: Bundle? = null) {
    val existing = if (id == 0L) null else store.expenses().firstOrNull { it.id == id }
    val fields = column()
    val description = field(fields, "Concepto del gasto *", draft?.string("description", existing?.description.orEmpty()) ?: existing?.description.orEmpty())
    val amount = moneyField(fields, "Importe del gasto *", draft?.string("amount", existing?.let { centsInput(it.amountCents) }.orEmpty()) ?: existing?.let { centsInput(it.amountCents) }.orEmpty())
    fields.addView(text("Categoría *", 14, MainActivity.GREEN, true))
    val category = AutoCompleteTextView(this).apply {
        this.id = android.view.View.generateViewId(); contentDescription = "Categoría del gasto"; hint = "Elige o escribe una categoría"
        setText(draft?.string("category", existing?.category ?: "Ingredientes") ?: existing?.category ?: "Ingredientes")
        threshold = 0; minHeight = dp(48); setTextColor(MainActivity.INK)
        setAdapter(ArrayAdapter(this@expenseForm, android.R.layout.simple_dropdown_item_1line,
            listOf("Ingredientes", "Empaques", "Transporte", "Servicios", "Equipo", "Otros")))
        setOnFocusChangeListener { _, focused -> if (focused) showDropDown() }
    }
    fields.addView(category)
    val date = dateButton(fields, "Fecha del gasto *", draft?.string("date", existing?.date ?: LocalDate.now().toString()) ?: existing?.date ?: LocalDate.now().toString())
    val notes = field(fields, "Notas del gasto", draft?.string("notes", existing?.notes.orEmpty()) ?: existing?.notes.orEmpty(), true)
    form(if (id == 0L) "Registrar gasto" else "Editar gasto", fields, "expense", id, capture = {
        Bundle().apply {
            putString("description", description.value()); putString("amount", amount.value()); putString("category", category.value())
            putString("date", date.iso()); putString("notes", notes.value())
        }
    }) {
        store.saveExpense(Expense(id, required(description, "el concepto del gasto"), required(category, "la categoría"), Money.parse(required(amount, "el importe del gasto")), date.iso(), notes.value()))
        navigate("Gastos"); message("Gasto guardado.")
    }
}

internal fun MainActivity.reportsScreen() {
    title("Informes del negocio", "Las ventas no son lo mismo que el dinero que ya recibiste.")
    periodControls(reportFrom, reportTo) { from, to -> reportFrom = from; reportTo = to; render() }
    val range = UiPeriod.validated(reportFrom, reportTo)
    val report = store.report(range.from, range.to)
    body.addView(card().apply {
        metric(this, "Ventas por fecha de entrega", report.salesCents)
        metric(this, "Cobros por fecha real de pago", report.collectedCents)
        metric(this, "Gastos del periodo", report.expensesCents)
        metric(this, "Flujo de efectivo (cobros − gastos)", report.cashFlowCents)
        metric(this, "Saldo por cobrar de esos pedidos", report.receivablesCents)
        addView(text("${report.pendingOrders} pedidos pendientes · ${report.deliveredOrders} entregados", 16, MainActivity.GREEN, true))
    })
    body.addView(card().apply {
        addView(text("Cómo leer este informe", 20, MainActivity.GREEN, true))
        addView(text("Ventas, deuda y conteo de pedidos: fecha de entrega. Cobros: fecha real de cada anticipo o abono, aunque el pedido se entregue fuera del periodo. Gastos: fecha registrada. Ambos extremos del periodo son inclusivos.", 15))
        addView(text("La deuda es el saldo actual de los pedidos de ese periodo, no una reconstrucción histórica. Los cancelados no cuentan como ventas ni deuda. El flujo de efectivo no representa utilidad contable.", 15))
    })
    body.addView(text("Gastos por categoría", 21, MainActivity.GREEN, true))
    val expenses = store.expenses(range.from, range.to)
    if (expenses.isEmpty()) empty("Sin gastos en este periodo", "Los gastos aparecerán agrupados por categoría.")
    expenses.groupBy { it.category }.toSortedMap().forEach { (category, values) ->
        body.addView(card().apply { metric(this, category, values.fold(0L) { sum, expense -> Math.addExact(sum, expense.amountCents) }) })
    }
    body.addView(text("Pedidos con saldo por cobrar", 21, MainActivity.GREEN, true))
    val owing = store.orders().filter {
        it.status != OrderStatus.CANCELLED && it.balanceCents > 0 &&
            (range.from == null || it.deliveryDate >= range.from) && (range.to == null || it.deliveryDate <= range.to)
    }
    if (owing.isEmpty()) empty("Sin deuda en este periodo", "No hay saldo pendiente en los pedidos seleccionados.")
    owing.forEach { orderRow(it) }
}

internal fun MainActivity.productsScreen() {
    title("Catálogo y existencias", "Opcional: crea productos para reutilizar precios y controlar stock.")
    body.addView(button("Agregar producto", true) { productForm() })
    body.addView(text("El stock se reserva al crear pedidos y se recupera al cancelarlos. Déjalo vacío si no deseas controlar existencias.", 15))
    val products = store.products(includeInactive = true)
    if (products.isEmpty()) empty("Tu catálogo está vacío", "También puedes crear pedidos con artículos personalizados sin usar el catálogo.")
    products.forEach { product ->
        body.addView(card().apply {
            addView(text(product.name, 21, MainActivity.GREEN, true))
            addView(text(Money.format(product.priceCents), 22, MainActivity.GREEN, true))
            addView(text(product.stock?.let { "Existencias: $it" } ?: "Sin control de existencias"))
            addView(text(if (product.active) "Activo" else "Inactivo · no aparece en pedidos nuevos", 14))
            addView(button("Editar producto: ${product.name}") { productForm(product.id) })
        })
    }
}

internal fun MainActivity.productForm(id: Long = 0, draft: Bundle? = null) {
    val existing = store.products(true).firstOrNull { it.id == id }
    val fields = column()
    val name = field(fields, "Nombre del producto *", draft?.string("name", existing?.name.orEmpty()) ?: existing?.name.orEmpty())
    val price = moneyField(fields, "Precio unitario *", draft?.string("price", existing?.let { centsInput(it.priceCents) }.orEmpty()) ?: existing?.let { centsInput(it.priceCents) }.orEmpty())
    val stock = field(fields, "Existencias disponibles (vacío: sin control)", draft?.string("stock", existing?.stock?.toString().orEmpty()) ?: existing?.stock?.toString().orEmpty(), input = InputType.TYPE_CLASS_NUMBER)
    fields.addView(text("Ajusta las existencias disponibles, no las unidades ya reservadas en pedidos. Los cambios de precio no modifican pedidos anteriores.", 14))
    val active = CheckBox(this).apply { text = "Producto activo para pedidos nuevos"; contentDescription = text; isChecked = draft?.getBoolean("active", existing?.active ?: true) ?: existing?.active ?: true; setTextColor(MainActivity.GREEN) }
    fields.addView(active)
    form(if (id == 0L) "Agregar producto" else "Editar producto", fields, "product", id, capture = {
        Bundle().apply { putString("name", name.value()); putString("price", price.value()); putString("stock", stock.value()); putBoolean("active", active.isChecked) }
    }) {
        val quantity = if (stock.value().isBlank()) null else stock.value().toIntOrNull().also {
            require(it != null && it >= 0) { "Las existencias deben ser un entero igual o mayor que cero, o quedar vacías." }
        }
        store.saveProduct(Product(id, required(name, "el nombre del producto"), Money.parse(required(price, "el precio del producto")), quantity, active.isChecked))
        navigate("Productos"); message("Producto guardado.")
    }
}
