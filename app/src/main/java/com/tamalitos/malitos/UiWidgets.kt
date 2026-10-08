package com.tamalitos.malitos

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.*
import java.time.LocalDate

internal fun MainActivity.field(parent: LinearLayout, label: String, value: String = "", multiline: Boolean = false,
                                input: Int = InputType.TYPE_CLASS_TEXT): EditText {
    val caption = text(label, 14, MainActivity.GREEN, true)
    val edit = EditText(this).apply {
        id = android.view.View.generateViewId()
        inputType = input or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
        setSingleLine(!multiline)
        if (multiline) { minLines = 2; maxLines = 5 }
        setText(value); hint = label; contentDescription = label
        setTextColor(MainActivity.INK); setHintTextColor(MainActivity.LEAF)
        minHeight = dp(48)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    caption.labelFor = edit.id
    parent.addView(caption); parent.addView(edit)
    return edit
}

internal fun MainActivity.moneyField(parent: LinearLayout, label: String, value: String = ""): EditText =
    field(parent, label, value, input = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

internal fun MainActivity.dateButton(parent: LinearLayout, label: String, iso: String): Button {
    parent.addView(text(label, 14, MainActivity.GREEN, true))
    val button = button("") {}
    fun update(date: String) { button.tag = date; button.text = dateLabel(date); button.contentDescription = "$label: ${dateLabel(date)}" }
    update(iso)
    val expectedGeneration = store.generation
    button.setOnClickListener { guarded { store.withGeneration(expectedGeneration) {
        val date = LocalDate.parse(button.tag as String)
        val picker = DatePickerDialog(this, { _, year, month, day -> guarded {
            store.withGeneration(expectedGeneration) { update(LocalDate.of(year, month + 1, day).toString()) }
        } }, date.year, date.monthValue - 1, date.dayOfMonth)
        transientDialogs.add(picker)
        picker.setOnDismissListener { transientDialogs.remove(picker) }
        picker.show()
    } } }
    parent.addView(button)
    return button
}

internal fun MainActivity.spinner(parent: LinearLayout, label: String, options: List<String>, position: Int = 0): Spinner {
    val caption = text(label, 14, MainActivity.GREEN, true)
    val select = Spinner(this).apply {
        id = android.view.View.generateViewId(); contentDescription = label; minimumHeight = dp(48)
        adapter = ArrayAdapter(this@spinner, android.R.layout.simple_spinner_dropdown_item, options)
        setSelection(position.coerceIn(0, (options.size - 1).coerceAtLeast(0)))
    }
    caption.labelFor = select.id
    parent.addView(caption); parent.addView(select)
    return select
}

internal fun MainActivity.search(parent: LinearLayout, label: String, initial: String, changed: (String) -> Unit): EditText {
    val edit = field(parent, label, initial)
    edit.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
        override fun afterTextChanged(s: Editable?) = Unit
    })
    return edit
}

internal fun required(edit: EditText, label: String): String = edit.text.toString().trim().also {
    require(it.isNotEmpty()) { "Escribe $label." }
}

/** A scrollable form whose save action never dismisses on validation failure. */
internal fun MainActivity.form(title: String, fields: LinearLayout, kind: String, entityId: Long = 0,
                               capture: () -> Bundle, saveLabel: String = "Guardar", save: () -> Unit) {
    dialog?.dismiss()
    val expectedGeneration = store.generation
    draftKind = kind; draftId = entityId; draftGeneration = expectedGeneration; captureDraft = capture
    val problem = text("", 15, MainActivity.CLAY).apply {
        visibility = android.view.View.GONE
        accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
    }
    fields.addView(problem)
    fields.setPadding(dp(20), dp(12), dp(20), dp(12))
    val scroll = ScrollView(this).apply { addView(fields) }
    val created = AlertDialog.Builder(this).setTitle(title).setView(scroll)
        .setNegativeButton("Volver", null).setPositiveButton(saveLabel, null).create()
    dialog = created
    created.setOnDismissListener {
        if (dialog === created) { draftKind = null; draftId = 0; captureDraft = null; dialog = null }
    }
    created.setOnShowListener {
        created.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { store.withGeneration(expectedGeneration, save); created.dismiss() }
            catch (e: Exception) {
                problem.text = e.message ?: "No se pudo guardar. Conservamos tus datos para corregirlos."
                problem.visibility = android.view.View.VISIBLE
                problem.announceForAccessibility(problem.text)
                scroll.post { scroll.smoothScrollTo(0, problem.bottom) }
            }
        }
    }
    created.show()
}

internal fun MainActivity.reopenDraft(kind: String, id: Long, saved: Bundle?) {
    when (kind) {
        "customer" -> customerForm(id, saved)
        "order" -> orderForm(id, saved)
        "expense" -> expenseForm(id, saved)
        "product" -> productForm(id, saved)
        "payment" -> paymentForm(id, saved)
    }
}

internal fun MainActivity.orderStatusConfirmation(orderId: Long, status: OrderStatus) {
    val expectedGeneration = store.generation
    val statuses = listOf(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.DELIVERED)
    var choice = statuses.indexOf(status).coerceAtLeast(0)
    val created = AlertDialog.Builder(this).setTitle("Estado del pedido #$orderId")
        .setSingleChoiceItems(statuses.map { statusLabel(it) }.toTypedArray(), choice) { _, which -> choice = which }
        .setNegativeButton("Volver", null).setPositiveButton("Guardar estado") { _, _ -> guarded {
            store.withGeneration(expectedGeneration) {
                store.setOrderStatus(orderId, statuses[choice]); render(); message("Estado actualizado.")
            }
        } }.create()
    transientDialogs.add(created)
    created.setOnDismissListener { transientDialogs.remove(created) }
    created.show()
}

internal fun Bundle.string(key: String, fallback: String = "") = getString(key) ?: fallback
internal fun EditText.value() = text.toString().trim()
internal fun android.widget.Button.iso() = tag as String
internal fun centsInput(cents: Long): String = java.math.BigDecimal.valueOf(cents, 2).toPlainString()
