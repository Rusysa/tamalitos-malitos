package com.tamalitos.malitos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import java.time.LocalDate

internal data class UiConfirmation(val title: String, val detail: String, val label: String, val generation: Long,
    val action: () -> Unit, val cancel: () -> Unit = {})

class MainActivity : ComponentActivity() {
    internal lateinit var store: BusinessStore
    internal lateinit var drive: DriveController
    internal lateinit var model: BusinessViewModel
    internal var screen: String
        get() = model.session.screen
        set(value) { model.session.screen = value }
    internal var selectedId: Long
        get() = model.session.selectedId
        set(value) { model.session.selectedId = value }
    internal var customerQuery: String
        get() = model.session.customerQuery
        set(value) { model.session.customerQuery = value }
    internal var orderQuery: String
        get() = model.session.orderQuery
        set(value) { model.session.orderQuery = value }
    internal var orderFilter: Int
        get() = model.session.orderFilter
        set(value) { model.session.orderFilter = value }
    internal var reportFrom: String
        get() = model.session.reportFrom
        set(value) { model.session.reportFrom = value }
    internal var reportTo: String
        get() = model.session.reportTo
        set(value) { model.session.reportTo = value }
    internal var expenseFrom: String
        get() = model.session.expenseFrom
        set(value) { model.session.expenseFrom = value }
    internal var expenseTo: String
        get() = model.session.expenseTo
        set(value) { model.session.expenseTo = value }
    internal var draftKind: String?
        get() = model.session.draftKind
        set(value) { model.session.draftKind = value }
    internal var draftId: Long
        get() = model.session.draftId
        set(value) { model.session.draftId = value }
    internal var draftGeneration: Long
        get() = model.session.draftGeneration
        set(value) { model.session.draftGeneration = value }
    internal var editorIdentity: Long
        get() = model.session.editorIdentity
        set(value) { model.session.editorIdentity = value }
    internal var draft: Bundle
        get() = model.session.draft
        set(value) { model.session.draft = value }
    internal var draftError: String
        get() = model.session.draftError
        set(value) { model.session.draftError = value }
    internal var pendingImportGeneration: Long
        get() = model.session.pendingImportGeneration
        set(value) { model.session.pendingImportGeneration = value }
    internal var pendingSafetyPath: String?
        get() = model.session.pendingSafetyPath
        set(value) { model.session.pendingSafetyPath = value }
    internal var pendingImportUri: String?
        get() = model.session.pendingImportUri
        set(value) { model.session.pendingImportUri = value }
    internal var pendingDocumentGeneration: Long
        get() = model.session.pendingDocumentGeneration
        set(value) { model.session.pendingDocumentGeneration = value }
    internal var confirmation by mutableStateOf<UiConfirmation?>(null)
    internal var safetyChoices by mutableStateOf<List<java.io.File>>(emptyList())
    internal var notice: String
        get() = model.session.notice
        set(value) { model.session.notice = value }
    internal var problem: String?
        get() = model.session.problem
        set(value) { model.session.problem = value }
    internal var backupRevision by mutableIntStateOf(0)
    internal val captureDraft: (() -> Bundle)? get() = draftKind?.let { { Bundle(draft) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BusinessStore(applicationContext)
        model = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application))[BusinessViewModel::class.java]
        UiBackupJobs.attach(this)
        drive = DriveController(this, { status -> runOnUiThread { if (!isDestroyed) { message(status); render() } } },
            { runOnUiThread { if (!isDestroyed) onBusinessRestored() } },
            { title, detail, accept, cancel -> confirm(title, detail, "Restaurar y reemplazar", cancel, accept) })
        if (!model.session.initialized) savedInstanceState?.let { saved ->
            screen = saved.getString("screen", "Inicio"); selectedId = saved.getLong("selectedId")
            customerQuery = saved.getString("customerQuery", ""); orderQuery = saved.getString("orderQuery", "")
            orderFilter = saved.getInt("orderFilter"); reportFrom = saved.getString("reportFrom", reportFrom)
            reportTo = saved.getString("reportTo", reportTo); expenseFrom = saved.getString("expenseFrom", ""); expenseTo = saved.getString("expenseTo", "")
            draftGeneration = saved.getLong("draftGeneration", Long.MIN_VALUE)
            draftKind = saved.getString("draftKind"); draftId = saved.getLong("draftId"); draft = saved.getBundle("draft") ?: Bundle()
            pendingSafetyPath = saved.getString("pendingSafetyPath"); pendingImportUri = saved.getString("pendingImportUri")
            pendingImportGeneration = saved.getLong("pendingImportGeneration", Long.MIN_VALUE)
            pendingDocumentGeneration = saved.getLong("pendingDocumentGeneration", Long.MIN_VALUE)
            if (saved.getLong("renderGeneration", Long.MIN_VALUE) != store.generation) selectedId = 0
            if (draftGeneration != store.generation) closeEditor()
            if (pendingImportGeneration != store.generation) pendingImportUri = null
        }
        // Retained ViewModels also require invalidation; do not only check deserialized Bundles.
        if (draftKind != null && draftGeneration != store.generation) closeEditor()
        if (pendingImportUri != null && pendingImportGeneration != store.generation) pendingImportUri = null
        if (model.session.initialized && model.state.value.generation != store.generation) selectedId = 0
        model.session.initialized = true
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when { draftKind != null -> closeEditor(); confirmation != null -> cancelConfirmation()
                    selectedId != 0L -> selectedId = 0; screen != "Inicio" -> navigate("Inicio")
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true } }
            }
        })
        setContent { TamalitosTheme { TamalitosApp(this) } }
        render()
        pendingImportUri?.let { confirmLocalImport(android.net.Uri.parse(it), pendingImportGeneration) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen); outState.putLong("selectedId", selectedId)
        outState.putString("customerQuery", customerQuery); outState.putString("orderQuery", orderQuery); outState.putInt("orderFilter", orderFilter)
        outState.putString("reportFrom", reportFrom); outState.putString("reportTo", reportTo)
        outState.putString("expenseFrom", expenseFrom); outState.putString("expenseTo", expenseTo)
        outState.putString("draftKind", draftKind); outState.putLong("draftId", draftId); outState.putBundle("draft", Bundle(draft))
        outState.putLong("draftGeneration", draftGeneration); outState.putLong("renderGeneration", model.state.value.generation)
        outState.putLong("pendingImportGeneration", pendingImportGeneration); outState.putString("pendingImportUri", pendingImportUri)
        outState.putString("pendingSafetyPath", pendingSafetyPath); outState.putLong("pendingDocumentGeneration", pendingDocumentGeneration)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        UiBackupJobs.detach(this); store.close(); super.onDestroy()
        // Downloaded Drive approvals belong to this Activity. Explicitly cancel on
        // recreation rather than retain callback lambdas or silently lose the dialog.
        // Local imports are URI/generation state and are rebuilt by onCreate instead.
        if (pendingImportUri == null) cancelConfirmation() else confirmation = null
    }
    @Deprecated("SAF and Google authorization result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!drive.handleActivityResult(requestCode, resultCode, data)) handleLocalBackupResult(requestCode, resultCode, data)
    }
    internal fun navigate(destination: String, id: Long = 0) { screen = destination; selectedId = id; render() }
    internal fun render() { backupRevision++; model.refresh(reportFrom, reportTo, ::error) }
    internal fun onBusinessRestored() {
        closeEditor(); confirmation?.cancel?.invoke(); confirmation = null; safetyChoices = emptyList()
        pendingImportUri = null; pendingSafetyPath = null; selectedId = 0; screen = "Inicio"; render()
    }
    internal fun message(value: String) { notice = value }
    internal fun error(value: String) { problem = value }
    internal fun guarded(action: () -> Unit) { try { action() } catch(e: Exception) { error(e.message ?: "No se pudo completar la operación.") } }
    internal fun confirm(heading: String, detail: String, label: String, action: () -> Unit) = confirm(heading, detail, label, {}, action)
    internal fun confirm(heading: String, detail: String, label: String, cancel: () -> Unit, action: () -> Unit) {
        confirmation = UiConfirmation(heading, detail, label, store.generation, action, cancel)
    }
    internal fun cancelConfirmation() { confirmation?.cancel?.invoke(); confirmation = null; pendingImportUri = null }
    internal fun acceptConfirmation(captured: UiConfirmation? = confirmation) {
        captured ?: return
        if (confirmation !== captured) { error("Esta confirmación ya no está activa. Vuelve a abrir la acción."); return }
        confirmation = null
        try { store.withGeneration(captured.generation) { captured.action() } }
        catch(e: Exception) { captured.cancel(); error(e.message ?: "No se pudo completar la operación.") }
    }
    internal fun closeEditor() { draftKind = null; draftId = 0; draftError = ""; draft = Bundle(); editorIdentity = 0 }
    internal fun updateDraft(key: String, value: String, expected: Long = draftGeneration) { guarded { store.withGeneration(expected) { draft = Bundle(draft).apply { putString(key, value) } } } }
    internal fun openEditor(kind: String, id: Long = 0, initial: Bundle = Bundle()) {
        if (UiBackupJobs.isRunning) { message("Espera a que termine la operación de respaldo."); return }
        draftGeneration = store.generation; draftId = id; draft = initial; draftError = ""; draftKind = kind
        editorIdentity = System.nanoTime()
    }
    internal fun write(expected: Long = model.state.value.generation, action: (BusinessStore) -> Unit, complete: () -> Unit = { render() }) {
        if (UiBackupJobs.isRunning) { message("Espera a que termine la operación de respaldo."); return }
        model.mutate(expected, action, complete, ::error)
    }
    internal fun statusLabel(status: OrderStatus) = when(status) { OrderStatus.PENDING -> "Pendiente"; OrderStatus.PREPARING -> "En preparación"; OrderStatus.DELIVERED -> "Entregado"; OrderStatus.CANCELLED -> "Cancelado" }
    internal fun dateLabel(iso: String): String = try { LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) } catch(_: Exception) { iso }
}
