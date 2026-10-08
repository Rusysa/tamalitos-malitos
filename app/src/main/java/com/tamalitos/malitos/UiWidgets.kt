package com.tamalitos.malitos

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal fun Bundle.string(key: String, fallback: String = "") = getString(key) ?: fallback
internal fun centsInput(cents: Long) = java.math.BigDecimal.valueOf(cents, 2).toPlainString()
@Composable internal fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable internal fun Heading(title: String, subtitle: String = "") {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
}
@Composable internal fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
@Composable internal fun Action(label: String, primary: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
    val guard = LocalActionGuard.current
    if(primary) Button(onClick = { guard(action) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    else OutlinedButton(onClick = { guard(action) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}
@Composable internal fun Metric(label: String, cents: Long, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(label, style = MaterialTheme.typography.labelLarge)
        Text(Money.format(cents), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) } }
}
@Composable internal fun Empty(title: String, detail: String) { InfoCard { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail) } }
@Composable internal fun Input(label: String, value: String, tag: String = label, numeric: Boolean = false, multiline: Boolean = false, changed: (String) -> Unit) {
    OutlinedTextField(value, changed, Modifier.fillMaxWidth().testTag(tag), label = { Text(label) }, singleLine = !multiline,
        minLines = if(multiline) 2 else 1, keyboardOptions = KeyboardOptions(keyboardType = if(numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable internal fun Choice(label: String, options: List<String>, selected: Int, changed: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val guard = LocalActionGuard.current
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box { OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(label)) { Text(options.getOrElse(selected) { "Seleccionar" }) }
            DropdownMenu(expanded, { expanded = false }) { options.forEachIndexed { i, value -> DropdownMenuItem(text = { Text(value) }, onClick = { expanded = false; guard { changed(i) } }) } } }
    }
}
@Composable internal fun FormField(a: MainActivity, label: String, key: String, numeric: Boolean = false, multiline: Boolean = false) {
    val generation = a.draftGeneration
    if(key == "date") DateInput(label, a.draft.string(key), key, generation, a) { a.updateDraft(key, it, generation) }
    else Input(label, a.draft.string(key), key, numeric, multiline) { a.updateDraft(key, it, generation) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DateInput(label: String, value: String, tag: String, generation: Long, a: MainActivity, changed: (String) -> Unit) {
    var showing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var captured by androidx.compose.runtime.saveable.rememberSaveable { mutableLongStateOf(generation) }
    Input(label, value, tag, changed = changed)
    OutlinedButton({ captured = generation; showing = true }, Modifier.heightIn(min = 48.dp).testTag("calendar-$tag")) { Text("Elegir fecha en calendario") }
    if(showing) {
        val initial = runCatching { java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val date = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { showing = false }, confirmButton = {
            TextButton({ a.guarded { a.store.withGeneration(captured) {
                date.selectedDateMillis?.let { millis -> changed(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }
            } }; showing = false }, enabled = date.selectedDateMillis != null) { Text("Usar fecha") }
        }, dismissButton = { TextButton({ showing = false }) { Text("Volver al formulario") } }) {
            DatePicker(date, Modifier.testTag("material-date-picker"), showModeToggle = true)
        }
    }
}
@Composable internal fun Editor(a: MainActivity, state: BusinessUiState) {
    val kind = a.draftKind ?: return
    val editorGeneration = a.draftGeneration
    Dialog(onDismissRequest = a::closeEditor, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
            val short = maxHeight < 300.dp
            Surface(Modifier.padding(if(short) 4.dp else 16.dp).widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(if(short) 1f else .94f).testTag("editor"), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(if(short) 4.dp else 20.dp)) {
                    val title = when(kind) { "customer" -> if(a.draftId == 0L) "Agregar cliente" else "Editar cliente"; "order" -> "Nuevo pedido"; "expense" -> "Registrar gasto"; "product" -> "Producto"; "status" -> "Estado del pedido"; else -> "Registrar abono" }
                    if(short) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        EditorActions(a, state, kind, editorGeneration)
                    } else Text(title, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when(kind) { "customer" -> CustomerFields(a); "order" -> OrderFields(a, state); "expense" -> ExpenseFields(a)
                            "product" -> ProductFields(a); "payment" -> PaymentFields(a, state); "status" -> Choice("Estado del pedido", listOf("Pendiente", "En preparación", "Entregado"), a.draft.string("status", "0").toIntOrNull() ?: 0) { a.updateDraft("status", it.toString()) } }
                        if(a.draftError.isNotBlank()) Text(a.draftError, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
                    }
                    if(!short) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        EditorActions(a, state, kind, editorGeneration)
                    }
                }
            }
        }
    }
}
@Composable private fun EditorActions(a: MainActivity, state: BusinessUiState, kind: String, generation: Long) {
    TextButton(a::closeEditor, Modifier.heightIn(min = 48.dp)) { Text("Volver") }
    Button({ a.saveEditor(generation) }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("save-editor")) {
        Text(when(kind) { "order" -> "Crear pedido"; "payment" -> "Registrar pago"; "status" -> "Guardar estado"; else -> "Guardar" })
    }
}
internal fun MainActivity.saveEditor(expectedGeneration: Long = draftGeneration) {
    val kind = draftKind ?: return
    if(expectedGeneration != draftGeneration) { draftError = "Los datos fueron restaurados. Vuelve a abrir el formulario; no se guardó ningún cambio."; return }
    val id = draftId; val captured = Bundle(draft); val expected = expectedGeneration
    val identity = editorIdentity
    if(UiBackupJobs.isRunning) { draftError = "Espera a que termine el respaldo."; return }
    var destination = screen; var selection = selectedId
    model.mutate(expected, { database ->
        fun required(key: String, label: String) = captured.string(key).trim().also { require(it.isNotEmpty()) { "Escribe $label." } }
        when(kind) {
            "customer" -> { selection = database.saveCustomer(Customer(id, required("name", "el nombre del cliente"), captured.string("phone"), captured.string("address"), captured.string("notes"))); destination = "Clientes" }
            "order" -> {
                val items = captured.items().map { row -> OrderItem(row.getLong("productId").takeIf { it != 0L }, row.string("description"), row.string("quantity").toIntOrNull() ?: throw IllegalArgumentException("La cantidad debe ser un entero mayor que cero."), Money.parse(row.string("price"))) }
                val mode = InitialPayment.entries[captured.string("mode", "2").toInt()]
                selection = database.createOrder(captured.getLong("customerId"), captured.string("date"), required("address", "la dirección de entrega o punto de encuentro"), captured.string("notes"), items, mode, if(mode == InitialPayment.CUSTOM) Money.parse(captured.string("custom")) else 0)
                destination = "Pedidos"
            }
            "payment" -> { database.addPayment(id, Money.parse(required("amount", "el importe del abono")), captured.string("note")); destination = "Pedidos"; selection = id }
            "status" -> { database.setOrderStatus(id, listOf(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.DELIVERED)[captured.string("status", "0").toInt()]); destination = "Pedidos"; selection = id }
            "expense" -> { database.saveExpense(Expense(id, required("description", "el concepto del gasto"), required("category", "la categoría"), Money.parse(required("amount", "el importe")), captured.string("date"), captured.string("notes"))); destination = "Gastos"; selection = 0 }
            "product" -> {
                val stock = captured.string("stock").let { if(it.isBlank()) null else it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: throw IllegalArgumentException("Existencias: escribe un entero no negativo o deja vacío.") }
                database.saveProduct(Product(id, required("name", "el nombre del producto"), Money.parse(required("price", "el precio")), stock, captured.getBoolean("active", true))); destination = "Productos"; selection = 0
            }
        }
    }, {
        // If this editor was already replaced by a new one, don't close it.
        if (draftKind != null && identity == editorIdentity) {
            closeEditor()
            navigate(destination, selection)
            message("Registro guardado.")
        }
    }, { draftError = it })
}
@Suppress("DEPRECATION") internal fun Bundle.items(): List<Bundle> = getParcelableArrayList<Bundle>("items").orEmpty()
internal fun MainActivity.orderStatusConfirmation(orderId: Long, status: OrderStatus) = openEditor("status", orderId, Bundle().apply { putString("status", listOf(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.DELIVERED).indexOf(status).coerceAtLeast(0).toString()) })
