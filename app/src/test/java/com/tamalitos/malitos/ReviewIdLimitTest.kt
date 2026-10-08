package com.tamalitos.malitos

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ReviewIdLimitTest {
    private lateinit var context: Context
    private lateinit var store: BusinessStore
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("tamalitos.db"); store = BusinessStore(context) }
    @After fun cleanup() { store.close(); context.deleteDatabase("tamalitos.db") }
    private fun ceiling(): Long = BusinessStore.MAX_SUPPORTED_ID
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected supported-ID validation rejection") } catch (_: IllegalArgumentException) {} }
    private fun sequence(table: String, value: Long) {
        context.openOrCreateDatabase("tamalitos.db", 0, null).use { db ->
            db.execSQL("DELETE FROM sqlite_sequence WHERE name=?", arrayOf(table))
            db.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)", arrayOf<Any>(table, value))
        }
    }
    private fun sequenceValue(table: String): Long = context.openOrCreateDatabase("tamalitos.db", 0, null).use { db ->
        db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name=?", arrayOf(table)).use { it.moveToFirst(); it.getLong(0) }
    }
    @Test fun maliciousNearLongMaxBackupIsRejectedBeforeAtomicReplacement() {
        store.saveCustomer(Customer(name = "Keep"))
        val before = store.exportBackup(); val generation = store.generation
        val bad = JSONObject(before).apply { getJSONArray("customers").getJSONObject(0).put("id", Long.MAX_VALUE - 1) }.toString()
        rejected { store.importBackup(bad) }
        assertEquals("Keep", store.customers().single().name); assertEquals(generation, store.generation)
        assertEquals(2L, store.saveCustomer(Customer(name = "Still writable")))
    }
    @Test fun generatedDirectoryAndLedgerIdsAreCheckedInsideTheirTransactions() {
        store.saveCustomer(Customer(name = "Keep"))
        val max = ceiling()
        sequence("customers", max); rejected { store.saveCustomer(Customer(name = "Overflow")) }
        assertEquals(listOf("Keep"), store.customers().map { it.name }); assertEquals(max, sequenceValue("customers"))
        sequence("products", max); rejected { store.saveProduct(Product(name = "Overflow", priceCents = 100)) }
        assertTrue(store.products(true).isEmpty()); assertEquals(max, sequenceValue("products"))
        sequence("expenses", max); rejected { store.saveExpense(Expense(description = "Overflow", category = "", amountCents = 100, date = "2026-10-08")) }
        assertTrue(store.expenses().isEmpty()); assertEquals(max, sequenceValue("expenses"))
        store.exportBackup() // No unsupported row escaped the transaction.
    }
    @Test fun generatedOrderOrPaymentOverflowRollsBackStockAndParentRows() {
        val c = store.saveCustomer(Customer(name = "Keep")); val p = store.saveProduct(Product(name = "Tracked", priceCents = 100, stock = 5))
        val items = listOf(OrderItem(p, "Tracked", 1, 100)); val max = ceiling()
        sequence("orders", max)
        rejected { store.createOrder(c, "2026-10-08", "", "", items, InitialPayment.FULL) }
        assertTrue(store.orders().isEmpty()); assertEquals(5, store.products().single().stock); assertEquals(max, sequenceValue("orders"))
        sequence("orders", 0); sequence("payments", max)
        rejected { store.createOrder(c, "2026-10-08", "", "", items, InitialPayment.FULL) }
        assertTrue(store.orders().isEmpty()); assertEquals(5, store.products().single().stock)
        val order = store.createOrder(c, "2026-10-08", "", "", items, InitialPayment.UNPAID)
        rejected { store.addPayment(order, 100) }
        assertTrue(store.order(order)!!.payments.isEmpty()); assertEquals(max, sequenceValue("payments")); store.exportBackup()
    }
    @Test fun olderSupportedSnapshotPreservesIdsAndLeavesGenerationRoom() {
        store.saveCustomer(Customer(name = "Keep"))
        val id = 1000000L
        val json = JSONObject(store.exportBackup()).apply { getJSONArray("customers").getJSONObject(0).put("id", id) }.toString()
        store.importBackup(json); assertEquals(id, store.customers().single().id)
        assertEquals(id + 1, store.saveCustomer(Customer(name = "Next"))); store.exportBackup()
    }
}
