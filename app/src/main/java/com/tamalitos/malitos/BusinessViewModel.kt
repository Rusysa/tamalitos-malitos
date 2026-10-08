package com.tamalitos.malitos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One retained application-context store; SQLite never runs during composition. */
internal data class BusinessUiState(
    val customers: List<Customer> = emptyList(), val orders: List<Order> = emptyList(),
    val products: List<Product> = emptyList(), val expenses: List<Expense> = emptyList(),
    val report: Report = Report(0, 0, 0, 0, 0, 0), val periodReport: Report = Report(0, 0, 0, 0, 0, 0),
    val generation: Long = 0, val busy: Boolean = true, val revision: Long = 0
)
internal class BusinessRepository(application: Application) : AutoCloseable {
    val store = BusinessStore(application)
    fun snapshot(from: String, to: String): BusinessUiState = store.withCurrentData {
        val range = UiPeriod.validated(from, to)
        BusinessUiState(store.customers(), store.orders(), store.products(true), store.expenses(),
            store.report(), store.report(range.from, range.to), store.generation, false)
    }
    override fun close() = store.close()
}
internal class BusinessViewModel @JvmOverloads constructor(application: Application,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    val session = UiSession()
    private val repository = BusinessRepository(application)
    private val mutable = MutableStateFlow(BusinessUiState())
    val state = mutable.asStateFlow()
    private var from = ""
    private var to = ""
    private var readRequest = 0L
    private var latestReadFailure: (String) -> Unit = {}
    // Main-thread admission guard, independent of any read's loading indicator.
    private var writing = false
    fun refresh(start: String = from, end: String = to, onFailure: (String) -> Unit = {}) {
        from = start; to = end
        latestReadFailure = onFailure
        val request = ++readRequest
        mutable.value = mutable.value.copy(busy = true)
        // The write's post-commit read will collect the latest requested range.
        if (writing) return
        viewModelScope.launch {
            try {
                val snapshot = withContext(ioDispatcher) { repository.snapshot(start, end) }
                if (request == readRequest) mutable.value = snapshot.copy(revision = mutable.value.revision + 1)
            } catch (e: Exception) {
                if (request == readRequest) { mutable.value = mutable.value.copy(busy = false); onFailure(e.message.orEmpty()) }
            }
        }
    }
    fun mutate(expected: Long, action: (BusinessStore) -> Unit, complete: () -> Unit, failure: (String) -> Unit) {
        if (writing || mutable.value.busy) return
        writing = true
        val writeRequest = ++readRequest // No pre-write read may publish over the committed data.
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.store.withGeneration(expected) { action(repository.store) } }
            } catch (e: Exception) {
                writing = false
                mutable.value = mutable.value.copy(busy = false)
                // A rejected write has no post-commit read to service a deferred period.
                if (readRequest != writeRequest) refresh(onFailure = latestReadFailure)
                failure(e.message ?: "No se pudo guardar.")
                return@launch
            }
            try {
                do {
                    val request = readRequest
                    val start = from; val end = to
                    val result = runCatching { withContext(ioDispatcher) { repository.snapshot(start, end) } }
                    // Only repeat reads when the requested period changed, never the write.
                    if (request != readRequest) continue
                    mutable.value = result.getOrThrow().copy(revision = mutable.value.revision + 1)
                    break
                } while (true)
            } catch (e: Exception) {
                failure(e.message ?: "No se pudieron actualizar los datos.")
            } finally {
                writing = false
                mutable.value = mutable.value.copy(busy = false)
            }
            // The write committed even if refreshing failed: close the editor to avoid a retry.
            complete()
        }
    }
    override fun onCleared() { repository.close() }
}
