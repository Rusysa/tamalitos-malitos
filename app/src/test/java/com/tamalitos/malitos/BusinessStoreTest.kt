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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class BusinessStoreTest {
    private lateinit var context: Context
    private lateinit var store: BusinessStore
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("tamalitos.db")
        store = BusinessStore(context)
    }
    @After fun cleanup() { store.close(); context.deleteDatabase("tamalitos.db") }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected validation rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun malformedUnicodeIsRejectedBeforeItCanBeLostInUtf8Backup() {
        rejected { store.saveCustomer(Customer(name = "\uD800")) }
        rejected { store.saveCustomer(Customer(name = "Cliente", notes = "\uDC00")) }
        assertTrue(store.customers().isEmpty())
        val valid = "Cliente \uD83C\uDF3D"
        store.saveCustomer(Customer(name = valid))
        val snapshot = store.exportBackup().toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)
        store.importBackup(snapshot)
        assertEquals(valid, store.customers().single().name)
    }

    @Test fun customerDirectoryPersistsEditsAndSearchesLiteralText() {
        assertTrue(store.customers().isEmpty())
        val id = store.saveCustomer(Customer(name = "  Cliente de prueba  ", phone = "555", address = "Calle", notes = "Nota"))
        assertEquals("Cliente de prueba", store.customers().single().name)
        assertEquals(id, store.saveCustomer(Customer(id, "Renombrado", "123", "Casa", "Nota")))
        assertEquals(id, store.customers("123").single().id)
        assertTrue(store.customers("%").isEmpty())
        store.close(); store = BusinessStore(context)
        assertEquals("Casa", store.customers().single().address)
        store.deleteCustomer(id)
        assertTrue(store.customers().isEmpty())
        rejected { store.saveCustomer(Customer(name = " ")) }
        rejected { store.saveCustomer(Customer(999, "No existe")) }
        rejected { store.deleteCustomer(999) }
    }

    @Test fun orderCreationReservesStockAndSnapshotsCustomerAndPrices() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        val p = store.saveProduct(Product(name = "Tamal", priceCents = 101, stock = 5))
        val id = store.createOrder(c, "2026-10-08", "Casa", "Sin picante", listOf(OrderItem(p, "Tamal original", 3, 101)), InitialPayment.HALF)
        val o = store.order(id)!!
        assertEquals(303L, o.totalCents)
        assertEquals(152L, o.paidCents)
        assertEquals(151L, o.balanceCents)
        assertEquals(2, store.products().single().stock)
        assertEquals(OrderStatus.PENDING, o.status)
        assertEquals(1, o.payments.size)
        store.saveCustomer(Customer(c, "Nuevo nombre"))
        store.saveProduct(Product(p, "Nuevo producto", 500, 2))
        assertEquals("Cliente", store.order(id)!!.customerName)
        assertEquals("Tamal original", store.order(id)!!.items.single().description)
        assertEquals(101L, store.order(id)!!.items.single().unitPriceCents)
        assertEquals(id, store.orders("Cliente").single().id)
        assertTrue(store.orders(status = OrderStatus.DELIVERED).isEmpty())
        rejected { store.deleteCustomer(c) }
        rejected { store.saveProduct(Product(p, "Tamal", 101, null)) }
        assertNull(store.order(999))
    }

    @Test fun invalidOrdersNeverDecrementStockOrLeaveRows() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        val p = store.saveProduct(Product(name = "Tamal", priceCents = 100, stock = 3))
        val item = OrderItem(p, "Tamal", 2, 100)
        rejected { store.createOrder(c, "2026-10-08", "", "", listOf(item, item), InitialPayment.UNPAID) }
        rejected { store.createOrder(c, "2026-02-30", "", "", listOf(item), InitialPayment.UNPAID) }
        rejected { store.createOrder(999, "2026-10-08", "", "", listOf(item), InitialPayment.UNPAID) }
        rejected { store.createOrder(c, "2026-10-08", "", "", emptyList(), InitialPayment.FULL) }
        rejected { store.createOrder(c, "2026-10-08", "", "", listOf(item.copy(quantity = 0)), InitialPayment.FULL) }
        rejected { store.createOrder(c, "2026-10-08", "", "", listOf(item.copy(unitPriceCents = 0)), InitialPayment.FULL) }
        rejected { store.createOrder(c, "2026-10-08", "", "", listOf(item.copy(unitPriceCents = Long.MAX_VALUE)), InitialPayment.FULL) }
        rejected { store.createOrder(c, "2026-10-08", "", "", listOf(item), InitialPayment.CUSTOM, 201) }
        assertEquals(3, store.products().single().stock)
        assertTrue(store.orders().isEmpty())
        store.saveProduct(Product(p, "Tamal", 100, 3, false))
        rejected { store.createOrder(c, "2026-10-08", "", "", listOf(item), InitialPayment.UNPAID) }
    }

    @Test fun laterPaymentsCannotOverpayAndDeliveryMayHaveDebt() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        val id = store.createOrder(c, "2026-10-08", "", "", listOf(OrderItem(null, "Libre", 1, 101)), InitialPayment.CUSTOM, 30)
        val payment = store.addPayment(id, 20, "Segundo")
        assertTrue(payment > 0)
        assertEquals(51L, store.order(id)!!.balanceCents)
        assertEquals("Segundo", store.order(id)!!.payments.last().note)
        rejected { store.addPayment(id, 0) }
        rejected { store.addPayment(id, -1) }
        rejected { store.addPayment(id, 52) }
        rejected { store.addPayment(999, 1) }
        rejected { store.setOrderStatus(id, OrderStatus.CANCELLED) }
        store.setOrderStatus(id, OrderStatus.PREPARING)
        store.setOrderStatus(id, OrderStatus.DELIVERED)
        assertEquals(51L, store.order(id)!!.balanceCents)
        assertEquals(id, store.orders(status = OrderStatus.DELIVERED).single().id)
        store.addPayment(id, 51)
        assertEquals("Pagado", store.order(id)!!.paymentLabel)
        rejected { store.addPayment(id, 1) }
    }

    @Test fun cancellationRestoresStockOnceAndIsFinal() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        val p = store.saveProduct(Product(name = "Tamal", priceCents = 100, stock = 5))
        val id = store.createOrder(c, "2026-10-08", "", "", listOf(OrderItem(p, "Tamal", 2, 100), OrderItem(p, "Tamal", 1, 100)), InitialPayment.UNPAID)
        assertEquals(2, store.products().single().stock)
        store.setOrderStatus(id, OrderStatus.CANCELLED)
        assertEquals(5, store.products().single().stock)
        store.setOrderStatus(id, OrderStatus.CANCELLED)
        assertEquals(5, store.products().single().stock)
        rejected { store.setOrderStatus(id, OrderStatus.PENDING) }
        rejected { store.addPayment(id, 1) }
        rejected { store.setOrderStatus(999, OrderStatus.CANCELLED) }
    }

    @Test fun cancellationStockOverflowRollsBackEverything() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        val p = store.saveProduct(Product(name = "Tamal", priceCents = 100, stock = 1))
        val id = store.createOrder(c, "2026-10-08", "", "", listOf(OrderItem(p, "Tamal", 1, 100)), InitialPayment.UNPAID)
        store.saveProduct(Product(p, "Tamal", 100, Int.MAX_VALUE))
        rejected { store.setOrderStatus(id, OrderStatus.CANCELLED) }
        assertEquals(OrderStatus.PENDING, store.order(id)!!.status)
        assertEquals(Int.MAX_VALUE, store.products().single().stock)
    }

    @Test fun expensesPersistEditDeleteAndValidateDateRanges() {
        val id = store.saveExpense(Expense(description = "Gas", category = "Insumos", amountCents = 2500, date = "2026-10-01"))
        assertEquals(2500L, store.expenses().single().amountCents)
        assertEquals(id, store.saveExpense(Expense(id, "Gas recarga", "Insumos", 2600, "2026-10-02", "Nota")))
        assertEquals("Nota", store.expenses("2026-10-02", "2026-10-02").single().notes)
        assertTrue(store.expenses("2026-10-03", null).isEmpty())
        rejected { store.expenses("2026-10-03", "2026-10-01") }
        rejected { store.saveExpense(Expense(description = " ", category = "", amountCents = 1, date = "2026-10-01")) }
        rejected { store.saveExpense(Expense(description = "X", category = "", amountCents = 0, date = "2026-10-01")) }
        rejected { store.saveExpense(Expense(description = "X", category = "", amountCents = 1, date = "2026-02-30")) }
        rejected { store.saveExpense(Expense(999, "X", "", 1, "2026-10-01")) }
        store.deleteExpense(id)
        assertTrue(store.expenses().isEmpty())
        rejected { store.deleteExpense(id) }
    }

    @Test fun reportsUseDeliveryPaymentAndExpenseDatesIndependently() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        fun create(date: String, cents: Long, mode: InitialPayment) = store.createOrder(c, date, "", "", listOf(OrderItem(null, "Libre", 1, cents)), mode)
        create("2026-10-08", 101, InitialPayment.HALF)
        val paid = create("2026-10-08", 200, InitialPayment.FULL)
        store.setOrderStatus(paid, OrderStatus.DELIVERED)
        val free = create("2026-10-08", 300, InitialPayment.UNPAID)
        store.setOrderStatus(free, OrderStatus.PREPARING)
        val cancelled = create("2026-10-08", 400, InitialPayment.UNPAID)
        store.setOrderStatus(cancelled, OrderStatus.CANCELLED)
        create("2026-11-01", 500, InitialPayment.FULL)
        store.saveExpense(Expense(description = "Gas", category = "Insumos", amountCents = 100, date = "2026-10-08"))
        store.saveExpense(Expense(description = "Gas", category = "Insumos", amountCents = 200, date = "2026-11-01"))
        val october = store.report("2026-10-08", "2026-10-08")
        assertEquals(601L, october.salesCents)
        assertEquals(350L, october.receivablesCents)
        assertEquals(100L, october.expensesCents)
        assertEquals(2, october.pendingOrders)
        assertEquals(1, october.deliveredOrders)
        val total = store.report()
        assertEquals(1101L, total.salesCents)
        assertEquals(751L, total.collectedCents)
        assertEquals(451L, total.cashFlowCents)
        assertEquals(3, total.pendingOrders)
        val today = java.time.LocalDate.now().toString()
        assertEquals(751L, store.report(today, today).collectedCents)
        assertEquals(0L, store.report("2000-01-01", "2000-01-01").collectedCents)
        rejected { store.report("2026-10-02", "2026-10-01") }
    }

    @Test fun paymentInsertFailureRollsBackOrderAndStockReservation() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        val p = store.saveProduct(Product(name = "Tamal", priceCents = 100, stock = 4))
        context.openOrCreateDatabase("tamalitos.db", 0, null).use { db ->
            db.execSQL("CREATE TRIGGER reject_payment BEFORE INSERT ON payments BEGIN SELECT RAISE(ABORT,'injected payment failure'); END")
        }
        try {
            store.createOrder(c, "2026-10-08", "", "", listOf(OrderItem(p, "Tamal", 3, 100)), InitialPayment.FULL)
            fail("Expected actual SQLite write failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(store.orders().isEmpty())
        assertEquals(4, store.products().single().stock)
        assertEquals(c, store.customers().single().id)
    }

    @Test fun separateStoreInstancesNeverUseAStalePaymentBalance() {
        val c = store.saveCustomer(Customer(name = "Cliente"))
        val id = store.createOrder(c, "2026-10-08", "", "", listOf(OrderItem(null, "Libre", 1, 100)), InitialPayment.UNPAID)
        BusinessStore(context).use { other ->
            store.addPayment(id, 70)
            rejected { other.addPayment(id, 31) }
            other.addPayment(id, 30)
            assertEquals(100L, store.order(id)!!.paidCents)
            rejected { store.addPayment(id, 1) }
        }
    }

    @Test fun productCatalogSupportsOptionalStockAndInactiveItems() {
        val id = store.saveProduct(Product(name = "Tamal", priceCents = 1500, stock = 4))
        store.saveProduct(Product(name = "Sin control", priceCents = 100))
        assertEquals(4, store.products().first { it.id == id }.stock)
        store.saveProduct(Product(id, "Tamal", 1600, 3, false))
        assertEquals(1, store.products().size)
        assertEquals(2, store.products(true).size)
        rejected { store.saveProduct(Product(name = "", priceCents = 100)) }
        rejected { store.saveProduct(Product(name = "X", priceCents = 0)) }
        rejected { store.saveProduct(Product(name = "X", priceCents = 100, stock = -1)) }
        rejected { store.saveProduct(Product(999, "X", 100)) }
    }
}
