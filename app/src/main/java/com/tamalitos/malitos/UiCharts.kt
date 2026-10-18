package com.tamalitos.malitos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

internal data class ExpenseSlice(val label: String, val cents: Long)
internal fun expenseSlices(expenses: List<Expense>): List<ExpenseSlice> {
    val ranked = expenses.groupBy { it.category }.map { (category, rows) ->
        ExpenseSlice(category, rows.fold(0L) { total, expense -> Math.addExact(total, expense.amountCents) })
    }.sortedWith(compareByDescending<ExpenseSlice> { it.cents }.thenBy { it.label })
    return if (ranked.size <= 5) ranked else ranked.take(5) +
        ExpenseSlice("Otras categorías", ranked.drop(5).fold(0L) { sum, slice -> Math.addExact(sum, slice.cents) })
}

@Composable internal fun BusinessCharts(report: Report, orders: List<Order>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { FinanceChart(report) }
            Box(Modifier.weight(1f)) { OrderStatusChart(orders) }
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FinanceChart(report)
            OrderStatusChart(orders)
        }
    }
}

@Composable private fun FinanceChart(report: Report) {
    val colors = MaterialTheme.colorScheme
    val maximum = maxOf(report.salesCents, report.collectedCents, report.expensesCents)
    InfoCard {
        Text("Movimiento del dinero", style = MaterialTheme.typography.titleLarge)
        Text("Compara ventas, cobros y gastos", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (maximum == 0L) Text("Aún no hay movimientos en este periodo.")
        MoneyBar("Ventas", report.salesCents, maximum, colors.tertiary)
        MoneyBar("Cobros", report.collectedCents, maximum, colors.primary)
        MoneyBar("Gastos", report.expensesCents, maximum, colors.secondary)
        HorizontalDivider(color = colors.outlineVariant)
        Text("Las ventas siguen la fecha de entrega; los cobros, la fecha del pago. No representan lo mismo.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable private fun MoneyBar(label: String, cents: Long, maximum: Long, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Values remain exact cents; floating point is used only for drawing proportions.
        Text("$label · ${Money.format(cents)}", style = MaterialTheme.typography.titleSmall)
        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clearAndSetSemantics {}) {
            if (cents > 0L && maximum > 0L) Box(Modifier.fillMaxWidth((cents.toDouble() / maximum.toDouble()).toFloat().coerceIn(0f, 1f))
                .fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

@Composable private fun OrderStatusChart(orders: List<Order>) {
    val counts = remember(orders) { OrderStatus.entries.map { status -> orders.count { it.status == status } } }
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.tertiary, scheme.secondary, scheme.primary, scheme.outline)
    val labels = listOf("Pendientes", "En preparación", "Entregados", "Cancelados")
    InfoCard {
        Text("Estado de los pedidos", style = MaterialTheme.typography.titleLarge)
        Text("${orders.size} pedidos en la selección", color = scheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(152.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    val stroke = 20.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(scheme.surfaceContainerHighest, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    if (orders.isNotEmpty()) {
                        var angle = -90f
                        counts.forEachIndexed { index, count ->
                            val sweep = count.toFloat() / orders.size * 360f
                            if (count > 0) drawArc(colors[index], angle, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            angle += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
                    Text(orders.size.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text("pedidos", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        counts.forEachIndexed { index, count ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors[index]))
                Text("${labels[index]}: $count", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (orders.isEmpty()) Text("Aquí verás el avance de tus pedidos cuando registres el primero.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun ExpenseCategoryChart(expenses: List<Expense>) {
    val slices = remember(expenses) { expenseSlices(expenses) }
    InfoCard {
        Text("Distribución de gastos", style = MaterialTheme.typography.titleLarge)
        if (slices.isEmpty()) Text("Registra gastos para ver en qué se usa el dinero.")
        val maximum = slices.maxOfOrNull { it.cents } ?: 0L
        slices.forEach { slice -> MoneyBar(slice.label, slice.cents, maximum, MaterialTheme.colorScheme.secondary) }
        if (slices.isNotEmpty()) Text("Importes del periodo por categoría; las barras comparten la misma escala.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
