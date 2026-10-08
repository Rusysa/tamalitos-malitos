package com.tamalitos.malitos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class Destination(val label: String, val icon: ImageVector)
private val destinations = listOf(Destination("Inicio", Icons.Default.Home), Destination("Clientes", Icons.Default.People),
    Destination("Pedidos", Icons.Default.ShoppingBag), Destination("Gastos", Icons.AutoMirrored.Filled.ReceiptLong),
    Destination("Informes", Icons.Default.BarChart), Destination("Productos", Icons.Default.Inventory2), Destination("Respaldo", Icons.Default.Backup))
internal val LocalActionGuard = staticCompositionLocalOf<((() -> Unit) -> Unit)> { { it() } }
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TamalitosApp(a: MainActivity) {
    val state by a.model.state.collectAsStateWithLifecycle()
    var more by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(a.notice) { if(a.notice.isNotBlank()) { snackbar.showSnackbar(a.notice); a.notice = "" } }
    // A generation belongs to the displayed records, not a newly restored record sharing its ID.
    CompositionLocalProvider(LocalActionGuard provides { action -> a.guarded { a.store.withGeneration(state.generation) { if(!UiBackupJobs.isRunning) action() else a.message("Espera a que termine el respaldo.") } } }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 600.dp
            val expanded = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if(!compact) NavigationRail(Modifier.testTag("navigation-rail").verticalScroll(rememberScrollState())) {
                    Text("TM", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    destinations.forEach { item -> NavigationRailItem(selected = a.screen == item.label, onClick = { a.navigate(item.label) }, icon = { Icon(item.icon, null) }, label = { Text(item.label) }) }
                }
                Scaffold(Modifier.weight(1f), topBar = {
                    TopAppBar(title = { Column { Text("Tamalitos Malitos", style = MaterialTheme.typography.titleLarge); Text(a.screen, style = MaterialTheme.typography.labelMedium) } })
                }, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                    if(compact) NavigationBar(Modifier.testTag("navigation-bottom")) {
                        destinations.take(3).forEach { item -> NavigationBarItem(a.screen == item.label, { a.navigate(item.label) }, { Icon(item.icon, null) }, label = { Text(item.label) }) }
                        NavigationBarItem(a.screen in destinations.drop(3).map { it.label }, { more = true }, { Icon(Icons.Default.Menu, null) }, label = { Text("Más") })
                    }
                }) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
                        if(expanded && a.screen in listOf("Clientes", "Pedidos")) {
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(.42f).fillMaxHeight().testTag("list-pane")) { if(a.screen == "Clientes") Customers(a, state) else Orders(a, state) }
                                VerticalDivider()
                                Box(Modifier.weight(.58f).fillMaxHeight().testTag("detail-pane")) {
                                    if(a.selectedId == 0L) Page { Empty("Selecciona ${if(a.screen == "Clientes") "un cliente" else "un pedido"}", "El detalle aparece aquí sin perder la lista.") }
                                    else if(a.screen == "Clientes") CustomerDetail(a, state) else OrderDetail(a, state)
                                }
                            }
                        } else Box(Modifier.fillMaxSize().widthIn(max = 1200.dp).testTag(if(a.selectedId == 0L) "list-pane" else "detail-pane")) {
                            when(a.screen) { "Clientes" -> if(a.selectedId == 0L) Customers(a, state) else CustomerDetail(a, state)
                                "Pedidos" -> if(a.selectedId == 0L) Orders(a, state) else OrderDetail(a, state)
                                "Gastos" -> Expenses(a, state); "Informes" -> Reports(a, state); "Productos" -> Products(a, state)
                                "Respaldo" -> Backup(a); else -> Dashboard(a, state) }
                        }
                    }
                }
            }
        }
        if(more) ModalBottomSheet(onDismissRequest = { more = false }) {
            Column(Modifier.testTag("navigation-drawer").verticalScroll(rememberScrollState()).padding(16.dp)) {
                destinations.drop(3).forEach { item -> TextButton({ more = false; a.navigate(item.label) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(item.icon, null); Spacer(Modifier.width(16.dp)); Text(item.label) } }
            }
        }
        Editor(a, state)
        a.confirmation?.let { confirmation -> AlertDialog(onDismissRequest = a::cancelConfirmation,
            title = { Text(confirmation.title) }, text = { Text(confirmation.detail) },
            confirmButton = { TextButton({ a.acceptConfirmation(confirmation) }, Modifier.testTag("confirm-action")) { Text(confirmation.label) } },
            dismissButton = { TextButton(a::cancelConfirmation) { Text("Volver") } }) }
        a.problem?.let { problem -> AlertDialog(onDismissRequest = { a.problem = null }, title = { Text("Revisa los datos") }, text = { Text(problem) }, confirmButton = { TextButton({ a.problem = null }) { Text("Entendido") } }) }
        if(a.safetyChoices.isNotEmpty()) AlertDialog(onDismissRequest = { a.safetyChoices = emptyList() }, title = { Text("Elegir copia de seguridad previa") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { a.safetyChoices.forEach { file -> TextButton({ a.guarded { a.pendingSafetyPath = file.canonicalPath; a.selectExportDocument(true); a.safetyChoices = emptyList() } }) { Text("${file.parentFile!!.name}/${file.name}") } } } },
            confirmButton = {}, dismissButton = { TextButton({ a.safetyChoices = emptyList() }) { Text("Volver") } })
    }
}
@Composable private fun Dashboard(a: MainActivity, state: BusinessUiState) {
    Page {
        Heading("Resumen del negocio", "Sin conexión · MXN · Datos en este dispositivo")
        Summary(state.report)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Nuevo pedido", true) { a.orderForm() }; Action("Registrar gasto") { a.expenseForm() }; Action("Agregar cliente") { a.customerForm() }
        }
        Heading("Próximas entregas")
        val upcoming = state.orders.filter { it.status in listOf(OrderStatus.PENDING, OrderStatus.PREPARING) }.sortedBy { it.deliveryDate }.take(5)
        if(upcoming.isEmpty()) Empty("No hay entregas pendientes", "Agrega un cliente y crea tu primer pedido.")
        upcoming.forEach { OrderRow(a, it) }
        Action("Ver informes por periodo") { a.navigate("Informes") }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun Summary(report: Report, period: Boolean = false) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf((if(period) "Ventas por fecha de entrega" else "Ventas registradas") to report.salesCents,
            (if(period) "Cobros por fecha real de pago" else "Dinero cobrado") to report.collectedCents,
            "Gastos del periodo" to report.expensesCents, (if(period) "Saldo por cobrar de esos pedidos" else "Saldo por cobrar") to report.receivablesCents,
            "Flujo de efectivo (cobros − gastos)" to report.cashFlowCents).forEach { (label, cents) -> Metric(label, cents, Modifier.widthIn(min = 230.dp, max = 330.dp)) }
    }
    Text("${report.pendingOrders} pedidos pendientes · ${report.deliveredOrders} entregados")
}
