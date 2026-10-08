package com.tamalitos.malitos

import android.os.Bundle
import androidx.compose.runtime.*
import java.time.LocalDate

/** Retained presentation state, never an Activity or View. Serialized by the launcher for process recreation. */
internal class UiSession {
    var initialized = false
    internal var pendingImportGeneration = 0L
    internal var pendingSafetyPath: String? = null
    internal var pendingImportUri: String? = null
    internal var pendingDocumentGeneration = 0L

    internal var screen by mutableStateOf("Inicio")
    internal var selectedId by mutableLongStateOf(0)
    internal var customerQuery by mutableStateOf("")
    internal var orderQuery by mutableStateOf("")
    internal var orderFilter by mutableIntStateOf(0)
    internal var reportFrom by mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())
    internal var reportTo by mutableStateOf(LocalDate.now().toString())
    internal var expenseFrom by mutableStateOf("")
    internal var expenseTo by mutableStateOf("")
    internal var draftKind by mutableStateOf<String?>(null)
    internal var draftId = 0L
    internal var draftGeneration = 0L
    internal var editorIdentity = 0L
    internal var draft by mutableStateOf(Bundle())
    internal var draftError by mutableStateOf("")
    internal var notice by mutableStateOf("")
    internal var problem by mutableStateOf<String?>(null)
}
