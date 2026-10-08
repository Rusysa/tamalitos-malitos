package com.tamalitos.malitos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class BackupTest {
    private lateinit var context: Context
    private lateinit var store: BusinessStore
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("tamalitos.db")
        store = BusinessStore(context)
    }
    @After fun cleanup() { store.close(); context.deleteDatabase("tamalitos.db") }

    @Test fun invalidBackupsLeaveExistingDataUnchanged() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        val p = store.saveProduct(Product(name = "Tamal", priceCents = 100, stock = 5))
        store.createOrder(c, "2026-10-08", "", "", listOf(OrderItem(p, "Tamal", 1, 100)), InitialPayment.HALF)
        store.saveExpense(Expense(description = "Gas", category = "", amountCents = 1, date = "2026-10-08"))
        val good = store.exportBackup()
        val originalOrders = store.orders()
        val originalCustomers = store.customers()
        val originalProducts = store.products(true)
        val originalExpenses = store.expenses()
        fun reject(input: String) {
            try { store.importBackup(input); fail("Expected invalid backup rejection") } catch (_: IllegalArgumentException) { }
            assertEquals(originalOrders, store.orders())
            assertEquals(originalCustomers, store.customers())
            assertEquals(originalProducts, store.products(true))
            assertEquals(originalExpenses, store.expenses())
        }
        fun mutate(block: (JSONObject) -> Unit) { val root = JSONObject(good); block(root); reject(root.toString()) }
        reject("not json")
        reject(good + "garbage")
        reject("{\"version\":1,\"version\":2}")
        reject("[".repeat(40) + "0" + "]".repeat(40))
        mutate { it.put("version", 2) }
        mutate { it.put("currency", "USD") }
        mutate { it.remove("customers") }
        mutate { it.getJSONArray("customers").put(it.getJSONArray("customers").getJSONObject(0)) }
        mutate { it.getJSONArray("customers").getJSONObject(0).put("id", 0) }
        mutate { it.getJSONArray("products").getJSONObject(0).put("priceCents", "100") }
        mutate { it.getJSONArray("products").getJSONObject(0).put("stock", -1) }
        mutate { it.getJSONArray("products").getJSONObject(0).put("active", "true") }
        mutate { it.getJSONArray("orders").getJSONObject(0).put("customerId", 999) }
        mutate { it.getJSONArray("orders").getJSONObject(0).put("status", "CANCELLED") }
        mutate { it.getJSONArray("orders").getJSONObject(0).put("deliveryDate", "2026-02-30") }
        mutate { it.getJSONArray("orders").getJSONObject(0).getJSONArray("items").getJSONObject(0).put("quantity", 0) }
        mutate { it.getJSONArray("orders").getJSONObject(0).getJSONArray("items").getJSONObject(0).put("unitPriceCents", Long.MAX_VALUE).put("quantity", 2) }
        mutate { it.getJSONArray("orders").getJSONObject(0).getJSONArray("items").getJSONObject(0).put("productId", 999) }
        mutate { it.getJSONArray("orders").getJSONObject(0).getJSONArray("items").getJSONObject(0).put("stockReserved", false) }
        mutate { it.getJSONArray("orders").getJSONObject(0).getJSONArray("payments").getJSONObject(0).put("amountCents", 101) }
        mutate { it.getJSONArray("orders").getJSONObject(0).getJSONArray("payments").getJSONObject(0).put("orderId", 999) }
        mutate { it.getJSONArray("orders").getJSONObject(0).getJSONArray("payments").getJSONObject(0).put("timestamp", -1) }
        mutate { it.getJSONArray("orders").getJSONObject(0).getJSONArray("payments").put(it.getJSONArray("orders").getJSONObject(0).getJSONArray("payments").getJSONObject(0)) }
        mutate { it.getJSONArray("expenses").getJSONObject(0).put("amountCents", -1) }
        mutate { it.getJSONArray("customers").getJSONObject(0).put("name", "x".repeat(201)) }
        reject(" ".repeat(10 * 1024 * 1024 + 1))
    }

    @Test fun restoreRollsBackOnSqliteFailureAfterDelete() {
        store.saveCustomer(Customer(name = "Cliente"))
        val original = store.customers()
        val snapshot = store.exportBackup()
        context.openOrCreateDatabase("tamalitos.db", 0, null).use { db ->
            db.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON customers BEGIN SELECT RAISE(ABORT,'injected restore failure'); END")
        }
        try { store.importBackup(snapshot); fail("Expected SQLite rejection") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(original, store.customers())
    }

    @Test fun emptyVersionedBackupIsAValidExplicitReplacement() {
        val empty = store.exportBackup()
        store.saveCustomer(Customer(name = "Cliente"))
        store.importBackup(empty)
        assertTrue(store.customers().isEmpty())
        assertTrue(store.orders().isEmpty())
        assertTrue(store.expenses().isEmpty())
    }

    @Test fun versionedSnapshotRoundTripPreservesIdsRelationshipsAndReservedStock() {
        val c = store.saveCustomer(Customer(name = "Cliente de prueba", notes = "Español ñ"))
        val p = store.saveProduct(Product(name = "Tamal", priceCents = 101, stock = 5))
        val unpaid = store.createOrder(c, "2026-10-08", "", "", listOf(OrderItem(p, "Tamal", 2, 101)), InitialPayment.UNPAID)
        val paid = store.createOrder(c, "2026-10-09", "", "", listOf(OrderItem(null, "Libre", 1, 300)), InitialPayment.HALF)
        store.addPayment(paid, 50, "Abono")
        store.setOrderStatus(paid, OrderStatus.DELIVERED)
        store.saveExpense(Expense(description = "Gas", category = "Insumos", amountCents = 100, date = "2026-10-08"))
        val snapshot = store.exportBackup()
        val root = JSONObject(snapshot)
        assertEquals(1, root.getInt("version"))
        assertEquals("MXN", root.getString("currency"))
        val beforeCustomers = store.customers()
        val beforeOrders = store.orders()
        val beforeProducts = store.products(true)
        val beforeExpenses = store.expenses()
        val beforeReport = store.report()
        store.saveCustomer(Customer(name = "Temporal"))
        store.importBackup(snapshot)
        assertEquals(beforeCustomers, store.customers())
        assertEquals(beforeOrders, store.orders())
        assertEquals(beforeProducts, store.products(true))
        assertEquals(beforeExpenses, store.expenses())
        assertEquals(beforeReport, store.report())
        store.setOrderStatus(unpaid, OrderStatus.CANCELLED)
        assertEquals(5, store.products().single().stock)
        assertTrue(store.saveCustomer(Customer(name = "Nuevo")) > c)
    }
}
