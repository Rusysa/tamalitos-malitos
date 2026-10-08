package com.tamalitos.malitos

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.LocalDate

/** Native, offline-first shell. All Drive I/O is delegated to DriveController. */
class MainActivity : Activity() {
    internal lateinit var store: BusinessStore
    internal lateinit var drive: DriveController
    internal lateinit var body: LinearLayout
    internal var screen = "Inicio"
    internal var selectedId = 0L
    internal var customerQuery = ""
    internal var orderQuery = ""
    internal var orderFilter = 0
    internal var reportFrom = LocalDate.now().withDayOfMonth(1).toString()
    internal var reportTo = LocalDate.now().toString()
    internal var expenseFrom = ""
    internal var expenseTo = ""
    internal var draftKind: String? = null
    internal var draftId = 0L
    internal var draftGeneration = 0L
    internal var pendingImportGeneration = 0L
    internal val transientDialogs = mutableListOf<android.app.Dialog>()
    private var renderGeneration = 0L
    internal var captureDraft: (() -> Bundle)? = null
    internal var dialog: AlertDialog? = null
    internal var pendingSafetyPath: String? = null
    internal var pendingImportUri: String? = null
    internal var importDialog: AlertDialog? = null
    private var restoringDraft: Bundle? = null
    private var screenScroll: ScrollView? = null
    private var scrollPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BusinessStore(this)
        UiBackupJobs.attach(this)
        drive = DriveController(this, { status ->
            runOnUiThread { if (!isDestroyed) { message(status); if (screen == "Respaldo") render() } }
        }, { runOnUiThread { if (!isDestroyed) onBusinessRestored() } })
        savedInstanceState?.let {
            screen = it.getString("screen", "Inicio")
            selectedId = it.getLong("selectedId")
            customerQuery = it.getString("customerQuery", "")
            orderQuery = it.getString("orderQuery", "")
            orderFilter = it.getInt("orderFilter")
            reportFrom = it.getString("reportFrom", reportFrom)
            reportTo = it.getString("reportTo", reportTo)
            expenseFrom = it.getString("expenseFrom", "")
            expenseTo = it.getString("expenseTo", "")
            scrollPosition = it.getInt("scroll")
            draftKind = it.getString("draftKind")
            draftId = it.getLong("draftId")
            restoringDraft = it.getBundle("draft")
            pendingImportUri = it.getString("pendingImportUri")
            pendingSafetyPath = it.getString("pendingSafetyPath")
            draftGeneration = it.getLong("draftGeneration", Long.MIN_VALUE)
            pendingImportGeneration = it.getLong("pendingImportGeneration", Long.MIN_VALUE)
            if (it.getLong("renderGeneration", Long.MIN_VALUE) != store.generation) selectedId = 0L
            if (draftGeneration != store.generation) { draftKind = null; draftId = 0L; restoringDraft = null }
            if (pendingImportGeneration != store.generation) pendingImportUri = null
        }
        render()
        draftKind?.let { kind -> guarded { store.withGeneration(draftGeneration) { reopenDraft(kind, draftId, restoringDraft) } } }
        pendingImportUri?.let { uri -> guarded { store.withGeneration(pendingImportGeneration) { confirmLocalImport(android.net.Uri.parse(uri)) } } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen)
        outState.putLong("selectedId", selectedId)
        outState.putString("customerQuery", customerQuery)
        outState.putString("orderQuery", orderQuery)
        outState.putInt("orderFilter", orderFilter)
        outState.putString("reportFrom", reportFrom)
        outState.putString("reportTo", reportTo)
        outState.putString("expenseFrom", expenseFrom)
        outState.putString("expenseTo", expenseTo)
        outState.putInt("scroll", screenScroll?.scrollY ?: 0)
        outState.putString("draftKind", draftKind)
        outState.putLong("draftId", draftId)
        outState.putLong("draftGeneration", draftGeneration)
        outState.putLong("pendingImportGeneration", pendingImportGeneration)
        outState.putLong("renderGeneration", renderGeneration)
        outState.putBundle("draft", captureDraft?.invoke())
        outState.putString("pendingImportUri", pendingImportUri)
        outState.putString("pendingSafetyPath", pendingSafetyPath)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        UiBackupJobs.detach(this)
        importDialog?.dismiss()
        dialog?.dismiss()
        transientDialogs.toList().forEach { it.dismiss() }
        store.close()
        super.onDestroy()
    }

    @Deprecated("Native Activity result bridge required by the Drive contract")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (drive.handleActivityResult(requestCode, resultCode, data)) return
        handleLocalBackupResult(requestCode, resultCode, data)
    }

    @Deprecated("Native Activity back navigation")
    override fun onBackPressed() {
        if (UiBackupJobs.isRunning) { message("Espera a que termine la operación de respaldo."); return }
        if (selectedId != 0L) { selectedId = 0; render() }
        else if (screen != "Inicio") navigate("Inicio")
        else super.onBackPressed()
    }

    internal fun navigate(destination: String, id: Long = 0) {
        screen = destination
        selectedId = id
        scrollPosition = 0
        render()
    }

    internal fun onBusinessRestored() {
        dialog?.dismiss(); dialog = null
        importDialog?.dismiss(); importDialog = null
        transientDialogs.toList().forEach { it.dismiss() }; transientDialogs.clear()
        draftKind = null; draftId = 0L; captureDraft = null; restoringDraft = null
        pendingImportUri = null
        selectedId = 0L; screen = "Inicio"; render()
    }

    internal fun render() = store.withCurrentData { renderCurrentData() }

    private fun renderCurrentData() {
        renderGeneration = store.generation
        val root = column().apply { setBackgroundColor(CREAM) }
        val brand = column().apply { setPadding(dp(20), dp(18), dp(20), dp(10)); setBackgroundColor(GREEN) }
        brand.addView(text("Tamalitos Malitos", 24, Color.WHITE, true))
        brand.addView(text("Tu negocio, claro y en orden", 14, Color.WHITE))
        root.addView(brand)
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Inicio", "Clientes", "Pedidos", "Gastos", "Informes", "Productos", "Respaldo").forEach { name ->
            nav.addView(button(name, primary = name == screen) { navigate(name) })
        }
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(nav) })
        body = column().apply { setPadding(dp(18), dp(12), dp(18), dp(28)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        screenScroll = scroll
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        guarded {
            when (screen) {
                "Clientes" -> if (selectedId == 0L) customersScreen() else customerDetail(selectedId)
                "Pedidos" -> if (selectedId == 0L) ordersScreen() else orderDetail(selectedId)
                "Gastos" -> expensesScreen()
                "Informes" -> reportsScreen()
                "Productos" -> productsScreen()
                "Respaldo" -> backupScreen()
                else -> dashboard()
            }
        }
        if (scrollPosition > 0) scroll.post { scroll.scrollTo(0, scrollPosition); scrollPosition = 0 }
    }

    private fun dashboard() {
        title("Resumen del negocio", "Funciona sin conexión. Tus datos se guardan en este dispositivo.")
        val report = store.report()
        val summary = card()
        metric(summary, "Ventas registradas", report.salesCents)
        metric(summary, "Dinero cobrado", report.collectedCents)
        metric(summary, "Gastos", report.expensesCents)
        metric(summary, "Saldo por cobrar", report.receivablesCents)
        metric(summary, "Flujo de efectivo", report.cashFlowCents)
        summary.addView(text("${report.pendingOrders} pedidos pendientes · ${report.deliveredOrders} entregados", 15))
        body.addView(summary)
        body.addView(button("Nuevo pedido", true) { orderForm() })
        body.addView(button("Registrar gasto") { expenseForm() })
        body.addView(button("Agregar cliente") { customerForm() })
        val upcoming = store.orders().filter { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.DELIVERED }
            .sortedBy { it.deliveryDate }.take(5)
        body.addView(text("Próximas entregas", 21, GREEN, true))
        if (upcoming.isEmpty()) empty("No hay entregas pendientes", "Agrega un cliente y crea tu primer pedido.")
        upcoming.forEach { orderRow(it) }
        body.addView(button("Ver informes por periodo") { navigate("Informes") })
    }

    internal fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    internal fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    internal fun text(value: String, size: Int = 16, color: Int = INK, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(7))
    }
    internal fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        val expectedGeneration = store.generation
        text = label; isAllCaps = false; contentDescription = label
        minHeight = dp(48)
        setTextColor(if (primary) Color.WHITE else GREEN)
        backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) LEAF else Color.rgb(233, 237, 226))
        setOnClickListener {
            if (UiBackupJobs.isRunning) message("Espera a que termine la operación de respaldo antes de hacer cambios.")
            else guarded { store.withGeneration(expectedGeneration, action) }
        }
    }
    internal fun card(): LinearLayout = column().apply {
        setPadding(dp(14), dp(10), dp(14), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), Color.rgb(229, 224, 213))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }
    internal fun title(heading: String, subtitle: String = "") {
        body.addView(text(heading, 26, GREEN, true))
        if (subtitle.isNotEmpty()) body.addView(text(subtitle, 15))
    }
    internal fun metric(parent: LinearLayout, label: String, cents: Long) {
        parent.addView(text(label, 14))
        parent.addView(text(Money.format(cents), 24, GREEN, true))
    }
    internal fun empty(heading: String, detail: String) {
        body.addView(card().apply { addView(text(heading, 19, GREEN, true)); addView(text(detail, 15)) })
    }
    internal fun message(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    internal fun guarded(action: () -> Unit) {
        try { action() } catch (e: Exception) { error(e.message ?: "No se pudo completar la operación.") }
    }
    internal fun error(value: String) {
        AlertDialog.Builder(this).setTitle("Revisa los datos").setMessage(value)
            .setPositiveButton("Entendido", null).show()
    }
    internal fun confirm(heading: String, detail: String, label: String, action: () -> Unit) {
        val expectedGeneration = store.generation
        val created = AlertDialog.Builder(this).setTitle(heading).setMessage(detail).setNegativeButton("Volver", null)
            .setPositiveButton(label) { _, _ -> guarded { store.withGeneration(expectedGeneration, action) } }.create()
        transientDialogs.add(created)
        created.setOnDismissListener { transientDialogs.remove(created) }
        created.show()
    }
    internal fun statusLabel(status: OrderStatus): String = when (status) {
        OrderStatus.PENDING -> "Pendiente"
        OrderStatus.PREPARING -> "En preparación"
        OrderStatus.DELIVERED -> "Entregado"
        OrderStatus.CANCELLED -> "Cancelado"
    }
    internal fun dateLabel(iso: String): String = try {
        LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: Exception) { iso }

    companion object {
        internal val CREAM = Color.rgb(255, 249, 238)
        internal val GREEN = Color.rgb(23, 76, 60)
        internal val LEAF = Color.rgb(40, 116, 90)
        internal val INK = Color.rgb(44, 49, 41)
        internal val CLAY = Color.rgb(150, 64, 38)
    }
}
