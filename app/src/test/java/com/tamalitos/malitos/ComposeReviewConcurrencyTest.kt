package com.tamalitos.malitos

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ComposeReviewConcurrencyTest {
    private class ControlledIo : CoroutineDispatcher() {
        val tasks = mutableListOf<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun execute(index: Int = 0) {
            val task = tasks.removeAt(index)
            val worker = Thread { task.run() }
            worker.start(); worker.join(10_000); check(!worker.isAlive)
        }
        fun finish(index: Int = 0) { execute(index); shadowOf(Looper.getMainLooper()).idle() }
        fun drain() { while(tasks.isNotEmpty()) finish() }
    }
    private lateinit var io: ControlledIo
    private lateinit var model: BusinessViewModel
    private val owner = ViewModelStore()
    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.databaseList().forEach { app.deleteDatabase(it) }
        io = ControlledIo(); model = BusinessViewModel(app, io); owner.put("review", model)
    }
    @After fun tearDown() { owner.clear(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun refreshDuringWriteCannotReleaseGuardOrDuplicateCustomerOrderOrPayment() {
        model.refresh(); io.drain()
        val expected = model.state.value.generation
        var writes = 0
        val write: (BusinessStore) -> Unit = { store ->
            writes++
            val customer = store.saveCustomer(Customer(name = "Concurrency fixture"))
            val order = store.createOrder(customer, "2026-10-08", "", "", listOf(OrderItem(null, "Fixture", 1, 200)), InitialPayment.UNPAID)
            store.addPayment(order, 100, "Fixture")
        }
        model.mutate(expected, write, {}, { fail(it) })
        model.refresh()
        // A read can finish before the queued write. It must never reopen Save.
        if(io.tasks.size > 1) io.finish(1)
        assertTrue("Loading completion must not clear the writing guard", model.state.value.busy)
        model.mutate(expected, write, {}, { fail(it) })
        io.drain()
        assertEquals(1, writes)
        assertEquals(1, model.state.value.customers.size)
        assertEquals(1, model.state.value.orders.size)
        BusinessStore(RuntimeEnvironment.getApplication()).use { assertEquals(1, it.order(it.orders().single().id)!!.payments.size) }
        assertFalse(model.state.value.busy)
    }

    @Test fun rejectedWriteStillPublishesPeriodRequestedWhileWriteWasPending() {
        BusinessStore(RuntimeEnvironment.getApplication()).use { store ->
            store.saveExpense(Expense(description = "Earlier fixture", category = "Fixture", amountCents = 100, date = "2026-10-01"))
            store.saveExpense(Expense(description = "Latest fixture", category = "Fixture", amountCents = 200, date = "2026-10-02"))
        }
        model.refresh("2026-10-01", "2026-10-01"); io.drain()
        assertEquals(100L, model.state.value.periodReport.expensesCents)
        val before = model.state.value.expenses
        var writes = 0; var failures = 0; var completions = 0
        model.mutate(model.state.value.generation, { writes++; throw IllegalArgumentException("Validation fixture") },
            { completions++ }, { assertEquals("Validation fixture", it); failures++ })
        model.refresh("2026-10-02", "2026-10-02") { fail(it) }
        io.drain()
        assertEquals(200L, model.state.value.periodReport.expensesCents)
        assertFalse(model.state.value.busy)
        assertEquals(before, model.state.value.expenses)
        BusinessStore(RuntimeEnvironment.getApplication()).use { assertEquals(before, it.expenses()) }
        assertEquals(1, writes); assertEquals(1, failures); assertEquals(0, completions)
    }

    @Test fun reversedReportReadsKeepTheLatestRequestedPeriod() {
        BusinessStore(RuntimeEnvironment.getApplication()).use { store ->
            store.saveExpense(Expense(description = "Earlier fixture", category = "Fixture", amountCents = 100, date = "2026-10-01"))
            store.saveExpense(Expense(description = "Latest fixture", category = "Fixture", amountCents = 200, date = "2026-10-02"))
        }
        model.refresh("2026-10-01", "2026-10-01")
        model.refresh("2026-10-02", "2026-10-02")
        io.finish(1); io.finish()
        assertEquals(200L, model.state.value.periodReport.expensesCents)
        assertFalse(model.state.value.busy)
    }

    @Test fun refreshWhilePostWriteReadIsPendingPublishesLatestRangeWithoutRepeatingWrite() {
        model.refresh("2026-10-01", "2026-10-01"); io.drain()
        var writes = 0
        model.mutate(model.state.value.generation, { store ->
            writes++
            store.saveExpense(Expense(description = "Write fixture", category = "Fixture", amountCents = 300, date = "2026-10-02"))
        }, {}, { fail(it) })
        io.finish() // write committed, its post-write read is queued
        io.execute() // old period snapshot captured, but completion not delivered to Main
        model.refresh("2026-10-02", "2026-10-02")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Old post-write completion must not release the latest refresh guard", model.state.value.busy)
        io.drain()
        assertEquals(1, writes)
        assertEquals(300L, model.state.value.periodReport.expensesCents)
        assertEquals(1, model.state.value.expenses.size)
        assertFalse(model.state.value.busy)
    }

    @Test fun staleReadCompletionCannotReleaseAnAdmittedWrite() {
        model.refresh(); model.refresh()
        io.finish(1) // latest read ready; older read remains queued
        var writes = 0
        model.mutate(model.state.value.generation, { store ->
            assertNotSame(Looper.getMainLooper().thread, Thread.currentThread())
            writes++; store.saveCustomer(Customer(name = "Admitted fixture"))
        }, {}, { fail(it) })
        io.finish() // older read completes while the write is still queued
        assertTrue(model.state.value.busy)
        model.mutate(model.state.value.generation, { writes++ }, {}, { fail(it) })
        io.drain()
        assertEquals(1, writes)
        assertEquals("Admitted fixture", model.state.value.customers.single().name)
    }

    @Test fun staleReadFailureCannotReleaseAnAdmittedWriteOrShowItsOldError() {
        var failures = 0
        model.refresh("invalid", "invalid") { failures++ }
        model.refresh("", "")
        io.finish(1)
        var writes = 0
        model.mutate(model.state.value.generation, { store -> writes++; store.saveCustomer(Customer(name = "Failure fixture")) }, {}, { fail(it) })
        io.finish()
        assertTrue(model.state.value.busy)
        assertEquals(0, failures)
        io.drain()
        assertEquals(1, writes)
        assertEquals(1, model.state.value.customers.size)
    }

    @Test fun restoredGenerationStillRejectsTheQueuedWriteWithoutRunningItsAction() {
        model.refresh(); io.drain()
        var writes = 0; var completions = 0; var failures = 0
        model.mutate(model.state.value.generation, { writes++ }, { completions++ }, { failures++ })
        BusinessStore(RuntimeEnvironment.getApplication()).use { it.importBackup(it.exportBackup()) }
        io.drain()
        assertEquals(0, writes); assertEquals(0, completions); assertEquals(1, failures)
        assertFalse(model.state.value.busy)
    }

    @Test fun committedWriteStillCompletesWhenItsRefreshFailsSoEditorDoesNotOfferDuplicateRetry() {
        model.refresh(); io.drain()
        var writes = 0; var completions = 0; var failures = 0
        model.mutate(model.state.value.generation, { store -> writes++; store.saveCustomer(Customer(name = "Committed fixture")) }, { completions++ }, { failures++ })
        model.refresh("invalid", "invalid")
        io.drain()
        assertEquals(1, writes); assertEquals(1, completions); assertEquals(1, failures)
        assertFalse(model.state.value.busy)
        BusinessStore(RuntimeEnvironment.getApplication()).use { assertEquals(1, it.customers().size) }
    }

    @Test fun supersededPostWriteReadFailureStillCollectsLatestRange() {
        model.refresh(); io.drain()
        var writes = 0; var completions = 0; var failures = 0
        model.mutate(model.state.value.generation, { store -> writes++; store.saveCustomer(Customer(name = "Latest range fixture")) }, { completions++ }, { failures++ })
        model.refresh("invalid", "invalid")
        io.finish() // write committed, invalid-period post-write read queued
        io.execute() // fail the old period read, withholding Main delivery
        model.refresh("", "")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("A superseded period failure must not abort the latest post-write read", 0, failures)
        assertTrue(model.state.value.busy)
        io.drain()
        assertEquals(1, writes); assertEquals(1, completions)
        assertEquals("Latest range fixture", model.state.value.customers.single().name)
        assertFalse(model.state.value.busy)
    }

    @Test fun earlierRefreshCannotEnableSavingWhileLatestRefreshIsPending() {
        model.refresh(); model.refresh()
        assertEquals(2, io.tasks.size)
        io.finish()
        assertTrue("An earlier read must not clear the latest read's loading guard", model.state.value.busy)
        io.finish()
        assertFalse(model.state.value.busy)
    }
}
