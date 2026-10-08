package com.tamalitos.malitos

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Offline SQLite store. Each public write and multi-table read is atomic. */
class BusinessStore(context: Context) : Closeable {
    private val coordinator = BusinessMutationCoordinator.forContext(context)
    val generation: Long get() = coordinator.generation
    internal fun <T> withCurrentData(action: () -> T): T = coordinator.read(action)
    fun <T> withGeneration(expectedGeneration: Long, action: () -> T): T = coordinator.mutate(expectedGeneration, action)
    fun customers(query: String = ""): List<Customer> = coordinator.read { customersImpl(query) }
    fun saveCustomer(customer: Customer): Long = coordinator.mutate(generation) { saveCustomerImpl(customer) }
    fun deleteCustomer(id: Long) = coordinator.mutate(generation) { deleteCustomerImpl(id) }
    fun products(includeInactive: Boolean = false): List<Product> = coordinator.read { productsImpl(includeInactive) }
    fun saveProduct(product: Product): Long = coordinator.mutate(generation) { saveProductImpl(product) }
    fun orders(query: String = "", status: OrderStatus? = null): List<Order> = coordinator.read { ordersImpl(query, status) }
    fun order(id: Long): Order? = coordinator.read { orderImpl(id) }
    fun createOrder(customerId: Long, deliveryDate: String, deliveryAddress: String, notes: String, items: List<OrderItem>, paymentMode: InitialPayment, customPaymentCents: Long = 0): Long =
        coordinator.mutate(generation) { createOrderImpl(customerId, deliveryDate, deliveryAddress, notes, items, paymentMode, customPaymentCents) }
    fun addPayment(orderId: Long, amountCents: Long, note: String = ""): Long = coordinator.mutate(generation) { addPaymentImpl(orderId, amountCents, note) }
    fun setOrderStatus(orderId: Long, status: OrderStatus) = coordinator.mutate(generation) { setOrderStatusImpl(orderId, status) }
    fun expenses(from: String? = null, to: String? = null): List<Expense> = coordinator.read { expensesImpl(from, to) }
    fun saveExpense(expense: Expense): Long = coordinator.mutate(generation) { saveExpenseImpl(expense) }
    fun deleteExpense(id: Long) = coordinator.mutate(generation) { deleteExpenseImpl(id) }
    fun report(from: String? = null, to: String? = null): Report = coordinator.read { reportImpl(from, to) }
    fun exportBackup(): String = coordinator.read { exportBackupImpl() }
    fun importBackup(json: String) = coordinator.replace(generation) { importBackupImpl(json) }
    fun restoreBackupWithSafety(json: String, saveSafety: (String) -> Unit) = restoreBackupWithSafety(json, generation, saveSafety)
    fun restoreBackupWithSafety(json: String, expectedGeneration: Long, saveSafety: (String) -> Unit) = coordinator.replace(expectedGeneration) {
        saveSafety(exportBackupImpl()) // Durable publication must finish before replacing SQLite.
        importBackupImpl(json)
    }
    private val helper = Database(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    private class Database(context: Context) : SQLiteOpenHelper(context, "tamalitos.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, notes TEXT NOT NULL)")
            db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, price_cents INTEGER NOT NULL CHECK(price_cents>0), stock INTEGER CHECK(stock IS NULL OR stock>=0), active INTEGER NOT NULL CHECK(active IN (0,1)))")
            db.execSQL("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL REFERENCES customers(id), customer_name TEXT NOT NULL, delivery_date TEXT NOT NULL, delivery_address TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('PENDING','PREPARING','DELIVERED','CANCELLED')))")
            db.execSQL("CREATE TABLE order_items (order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE, position INTEGER NOT NULL, product_id INTEGER REFERENCES products(id), description TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity>0), unit_price_cents INTEGER NOT NULL CHECK(unit_price_cents>0), stock_reserved INTEGER NOT NULL CHECK(stock_reserved IN (0,1)), PRIMARY KEY(order_id,position))")
            db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE, amount_cents INTEGER NOT NULL CHECK(amount_cents>0), timestamp INTEGER NOT NULL CHECK(timestamp>=0), note TEXT NOT NULL)")
            db.execSQL("CREATE INDEX orders_customer ON orders(customer_id)")
            db.execSQL("CREATE INDEX orders_date ON orders(delivery_date)")
            db.execSQL("CREATE INDEX payments_order ON payments(order_id)")
            db.execSQL("CREATE INDEX items_product ON order_items(product_id)")
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, description TEXT NOT NULL, category TEXT NOT NULL, amount_cents INTEGER NOT NULL CHECK(amount_cents>0), date TEXT NOT NULL, notes TEXT NOT NULL)")
            db.execSQL("CREATE INDEX expenses_date ON expenses(date)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("Versión de base de datos no compatible; no se borraron datos.")
        }
    }

    private var identityMayHaveBeenReused = false
    private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val database = db
        val priorInvalidation = identityMayHaveBeenReused
        identityMayHaveBeenReused = false
        database.beginTransaction()
        var successful = false
        try { val result = block(database); database.setTransactionSuccessful(); successful = true; return result }
        finally {
            val invalidate = identityMayHaveBeenReused
            try {
                database.endTransaction()
                if (successful && invalidate) coordinator.invalidateAllocatedIdentity()
            } finally { identityMayHaveBeenReused = priorInvalidation }
        }
    }
    private fun insertSupported(database: SQLiteDatabase, table: String, data: ContentValues): Long {
        val explicitId = data.get("id") as? Long
        if (explicitId == null) {
            val sequence = rows(database, "SELECT seq FROM sqlite_sequence WHERE name=?", arrayOf(table)) { it.getLong(0) }.singleOrNull() ?: 0L
            if (sequence in MAX_IMPORTED_ID until MAX_SUPPORTED_ID) {
                require(table in listOf("customers", "products", "orders", "payments", "expenses"))
                var candidate = 1L
                database.rawQuery("SELECT id FROM $table ORDER BY id", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val existing = cursor.getLong(0)
                        if (existing > candidate) break
                        if (existing == candidate) candidate++
                    }
                }
                require(candidate <= MAX_IMPORTED_ID) { "No quedan identificadores disponibles; no se guardó ningún cambio." }
                data.put("id", candidate)
                identityMayHaveBeenReused = true
            }
        }
        return database.insertOrThrow(table, null, data).also {
            require(it in 1..MAX_IMPORTED_ID) { "Se alcanzó el límite de identificadores. No se guardó ningún cambio; exporta tus datos." }
        }
    }
    private fun values(vararg pairs: Pair<String, Any?>): ContentValues = ContentValues().apply {
        pairs.forEach { (key, value) -> when (value) {
            null -> putNull(key)
            is String -> put(key, value)
            is Long -> put(key, value)
            is Int -> put(key, value)
            is Boolean -> put(key, if (value) 1 else 0)
            else -> error("Unsupported value")
        } }
    }
    private fun <T> rows(database: SQLiteDatabase, sql: String, args: Array<String> = emptyArray(), read: (Cursor) -> T): List<T> =
        database.rawQuery(sql, args).use { cursor -> buildList { while (cursor.moveToNext()) add(read(cursor)) } }
    private fun text(value: String, label: String, required: Boolean = false, max: Int = 2000): String {
        val clean = value.trim()
        require(!required || clean.isNotEmpty()) { "$label es obligatorio." }
        require(clean.length <= max && !clean.contains('\u0000')) { "$label es demasiado largo o contiene caracteres inválidos." }
        var position = 0
        while (position < clean.length) {
            val char = clean[position++]
            if (Character.isHighSurrogate(char)) {
                require(position < clean.length && Character.isLowSurrogate(clean[position])) { "$label contiene texto Unicode inválido." }
                position++
            } else require(!Character.isLowSurrogate(char)) { "$label contiene texto Unicode inválido." }
        }
        return clean
    }
    private fun validId(id: Long) { require(id >= 0 && id <= MAX_SUPPORTED_ID) { "Identificador inválido." } }
    private fun date(value: String): String {
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "Fecha inválida: use yyyy-MM-dd." }
        try { LocalDate.parse(value) } catch (_: Exception) { throw IllegalArgumentException("Fecha inválida.") }
        return value
    }
    private fun validateCustomer(c: Customer): Customer {
        validId(c.id)
        return c.copy(name = text(c.name, "Nombre", true, 200), phone = text(c.phone, "Teléfono", max = 100), address = text(c.address, "Dirección"), notes = text(c.notes, "Notas"))
    }
    private fun validateProduct(p: Product): Product {
        validId(p.id)
        require(p.priceCents > 0) { "El precio debe ser mayor que cero." }
        require(p.stock == null || p.stock >= 0) { "Las existencias no pueden ser negativas." }
        return p.copy(name = text(p.name, "Producto", true, 200))
    }
    private fun readCustomer(c: Cursor) = Customer(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
    private fun readProduct(c: Cursor) = Product(c.getLong(0), c.getString(1), c.getLong(2), if (c.isNull(3)) null else c.getInt(3), c.getInt(4) != 0)
    private fun getCustomer(database: SQLiteDatabase, id: Long) = rows(database, "SELECT * FROM customers WHERE id=?", arrayOf(id.toString()), ::readCustomer).singleOrNull()
    private fun getProduct(database: SQLiteDatabase, id: Long) = rows(database, "SELECT * FROM products WHERE id=?", arrayOf(id.toString()), ::readProduct).singleOrNull()

    private fun customersImpl(query: String = ""): List<Customer> = rows(db, "SELECT * FROM customers ORDER BY name COLLATE NOCASE,id", read = ::readCustomer)
        .filter { query.isBlank() || listOf(it.name, it.phone, it.address, it.notes).any { field -> field.contains(query.trim(), ignoreCase = true) } }

    private fun saveCustomerImpl(customer: Customer): Long {
        val c = validateCustomer(customer)
        return transaction { database ->
            val data = values("name" to c.name, "phone" to c.phone, "address" to c.address, "notes" to c.notes)
            if (c.id == 0L) insertSupported(database, "customers", data)
            else { require(database.update("customers", data, "id=?", arrayOf(c.id.toString())) == 1) { "Cliente no encontrado." }; c.id }
        }
    }
    private fun deleteCustomerImpl(id: Long) {
        transaction { database ->
            require(getCustomer(database, id) != null) { "Cliente no encontrado." }
            require(rows(database, "SELECT id FROM orders WHERE customer_id=? LIMIT 1", arrayOf(id.toString())) { it.getLong(0) }.isEmpty()) { "No se puede eliminar un cliente con pedidos; conserve su historial." }
            require(database.delete("customers", "id=?", arrayOf(id.toString())) == 1) { "Cliente no encontrado." }
        }
    }
    private fun productsImpl(includeInactive: Boolean = false): List<Product> = rows(db,
        "SELECT * FROM products ${if (includeInactive) "" else "WHERE active=1"} ORDER BY name COLLATE NOCASE,id", read = ::readProduct)

    private fun saveProductImpl(product: Product): Long {
        val p = validateProduct(product)
        return transaction { database ->
            val data = values("name" to p.name, "price_cents" to p.priceCents, "stock" to p.stock, "active" to p.active)
            if (p.id == 0L) insertSupported(database, "products", data)
            else {
                val existing = getProduct(database, p.id) ?: throw IllegalArgumentException("Producto no encontrado.")
                if ((existing.stock == null) != (p.stock == null)) {
                    require(rows(database, "SELECT i.order_id FROM order_items i JOIN orders o ON o.id=i.order_id WHERE i.product_id=? AND o.status!='CANCELLED' LIMIT 1", arrayOf(p.id.toString())) { it.getLong(0) }.isEmpty()) { "No se puede cambiar el control de existencias mientras hay pedidos no cancelados del producto." }
                }
                database.update("products", data, "id=?", arrayOf(p.id.toString())); p.id
            }
        }
    }
    private fun item(item: OrderItem): OrderItem {
        require(item.quantity > 0 && item.unitPriceCents > 0) { "Cantidad y precio deben ser mayores que cero." }
        item.productId?.let { require(it > 0 && it <= MAX_SUPPORTED_ID) { "Producto inválido." } }
        val clean = item.copy(description = text(item.description, "Descripción", true, 200))
        clean.totalCents // checked multiplication
        return clean
    }
    private fun readItems(database: SQLiteDatabase, orderId: Long): List<OrderItem> = rows(database,
        "SELECT product_id,description,quantity,unit_price_cents FROM order_items WHERE order_id=? ORDER BY position", arrayOf(orderId.toString())) {
        OrderItem(if (it.isNull(0)) null else it.getLong(0), it.getString(1), it.getInt(2), it.getLong(3))
    }
    private fun readPayments(database: SQLiteDatabase, orderId: Long): List<Payment> = rows(database,
        "SELECT id,order_id,amount_cents,timestamp,note FROM payments WHERE order_id=? ORDER BY timestamp,id", arrayOf(orderId.toString())) {
        Payment(it.getLong(0), it.getLong(1), it.getLong(2), it.getLong(3), it.getString(4))
    }
    private fun readOrder(database: SQLiteDatabase, c: Cursor) = Order(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5),
        OrderStatus.valueOf(c.getString(6)), readItems(database, c.getLong(0)), readPayments(database, c.getLong(0)))
    private fun getOrder(database: SQLiteDatabase, id: Long): Order? = rows(database, "SELECT * FROM orders WHERE id=?", arrayOf(id.toString())) { readOrder(database, it) }.singleOrNull()
    private fun allOrders(database: SQLiteDatabase): List<Order> = rows(database, "SELECT * FROM orders ORDER BY delivery_date,id") { readOrder(database, it) }

    private fun ordersImpl(query: String = "", status: OrderStatus? = null): List<Order> = transaction { database ->
        allOrders(database).filter { order -> (status == null || order.status == status) && (query.isBlank() ||
            listOf(order.id.toString(), order.customerName, order.deliveryAddress, order.notes).any { it.contains(query.trim(), ignoreCase = true) }) }
    }
    private fun orderImpl(id: Long): Order? = transaction { getOrder(it, id) }

    private fun createOrderImpl(customerId: Long, deliveryDate: String, deliveryAddress: String, notes: String, items: List<OrderItem>, paymentMode: InitialPayment, customPaymentCents: Long = 0): Long {
        val day = date(deliveryDate)
        val address = text(deliveryAddress, "Dirección")
        val note = text(notes, "Notas")
        require(items.isNotEmpty() && items.size <= 1000) { "El pedido debe contener entre 1 y 1000 conceptos." }
        val cleanItems = items.map(::item)
        val total = cleanItems.fold(0L) { sum, row -> checkedAdd(sum, row.totalCents) }
        val initial = BusinessRules.initialPayment(total, paymentMode, customPaymentCents)
        return transaction { database ->
            val customer = getCustomer(database, customerId) ?: throw IllegalArgumentException("Cliente no encontrado.")
            val catalog = cleanItems.mapNotNull { it.productId }.distinct().associateWith { id ->
                val p = getProduct(database, id) ?: throw IllegalArgumentException("Producto no encontrado.")
                require(p.active) { "El producto ${p.name} está inactivo." }; p
            }
            val quantities = mutableMapOf<Long, Long>()
            cleanItems.forEach { row -> row.productId?.let { id -> quantities[id] = checkedAdd(quantities[id] ?: 0, row.quantity.toLong()) } }
            quantities.forEach { (id, quantity) -> catalog.getValue(id).stock?.let { stock ->
                require(quantity <= stock) { "Existencias insuficientes para ${catalog.getValue(id).name}." }
                database.update("products", values("stock" to (stock - quantity).toInt()), "id=?", arrayOf(id.toString()))
            } }
            val id = insertSupported(database, "orders", values("customer_id" to customerId, "customer_name" to customer.name, "delivery_date" to day,
                "delivery_address" to address, "notes" to note, "status" to OrderStatus.PENDING.name))
            cleanItems.forEachIndexed { position, row -> insertItem(database, id, position, row, row.productId?.let { catalog.getValue(it).stock != null } ?: false) }
            if (initial > 0) insertPayment(database, Payment(orderId = id, amountCents = initial, timestamp = System.currentTimeMillis(), note = "Pago inicial"))
            id
        }
    }
    private fun insertItem(database: SQLiteDatabase, orderId: Long, position: Int, row: OrderItem, reserved: Boolean) {
        database.insertOrThrow("order_items", null, values("order_id" to orderId, "position" to position, "product_id" to row.productId,
            "description" to row.description, "quantity" to row.quantity, "unit_price_cents" to row.unitPriceCents, "stock_reserved" to reserved))
    }
    private fun insertPayment(database: SQLiteDatabase, payment: Payment): Long {
        val data = values("order_id" to payment.orderId, "amount_cents" to payment.amountCents, "timestamp" to payment.timestamp, "note" to payment.note)
        if (payment.id != 0L) data.put("id", payment.id)
        return insertSupported(database, "payments", data)
    }
    private fun addPaymentImpl(orderId: Long, amountCents: Long, note: String = ""): Long {
        require(amountCents > 0) { "El abono debe ser mayor que cero." }
        val cleanNote = text(note, "Nota del abono")
        return transaction { database ->
            val order = getOrder(database, orderId) ?: throw IllegalArgumentException("Pedido no encontrado.")
            require(order.status != OrderStatus.CANCELLED) { "No se puede abonar a un pedido cancelado." }
            require(amountCents <= order.balanceCents) { "El abono excede el saldo pendiente." }
            insertPayment(database, Payment(orderId = orderId, amountCents = amountCents, timestamp = System.currentTimeMillis(), note = cleanNote))
        }
    }
    private fun setOrderStatusImpl(orderId: Long, status: OrderStatus) {
        transaction { database ->
            val order = getOrder(database, orderId) ?: throw IllegalArgumentException("Pedido no encontrado.")
            if (order.status == status) return@transaction
            require(order.status != OrderStatus.CANCELLED) { "Un pedido cancelado no se puede reactivar." }
            if (status == OrderStatus.CANCELLED) {
                require(order.paidCents == 0L) { "No se puede cancelar un pedido con abonos; el reembolso no está implementado." }
                val reserved = rows(database, "SELECT product_id,quantity FROM order_items WHERE order_id=? AND stock_reserved=1", arrayOf(orderId.toString())) { it.getLong(0) to it.getLong(1) }
                val quantities = mutableMapOf<Long, Long>()
                reserved.forEach { (id, quantity) -> quantities[id] = checkedAdd(quantities[id] ?: 0L, quantity) }
                quantities.forEach { (id, quantity) ->
                    val stock = getProduct(database, id)?.stock ?: throw IllegalArgumentException("No se puede devolver la reserva de existencias.")
                    val updated = checkedAdd(stock.toLong(), quantity)
                    require(updated <= Int.MAX_VALUE) { "La devolución excede el límite de existencias." }
                    database.update("products", values("stock" to updated.toInt()), "id=?", arrayOf(id.toString()))
                }
            }
            database.update("orders", values("status" to status.name), "id=?", arrayOf(orderId.toString()))
        }
    }
    private fun validateRange(from: String?, to: String?) {
        from?.let(::date); to?.let(::date)
        require(from == null || to == null || from <= to) { "La fecha inicial no puede ser posterior a la final." }
    }
    private fun inRange(day: String, from: String?, to: String?) = (from == null || day >= from) && (to == null || day <= to)
    private fun validateExpense(e: Expense): Expense {
        validId(e.id)
        require(e.amountCents > 0) { "El gasto debe ser mayor que cero." }
        return e.copy(description = text(e.description, "Descripción", true, 200), category = text(e.category, "Categoría", max = 200), date = date(e.date), notes = text(e.notes, "Notas"))
    }
    private fun readExpense(c: Cursor) = Expense(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getString(5))
    private fun allExpenses(database: SQLiteDatabase): List<Expense> = rows(database, "SELECT * FROM expenses ORDER BY date,id", read = ::readExpense)
    private fun expensesImpl(from: String? = null, to: String? = null): List<Expense> {
        validateRange(from, to)
        return allExpenses(db).filter { inRange(it.date, from, to) }
    }
    private fun saveExpenseImpl(expense: Expense): Long {
        val e = validateExpense(expense)
        return transaction { database ->
            val data = values("description" to e.description, "category" to e.category, "amount_cents" to e.amountCents, "date" to e.date, "notes" to e.notes)
            if (e.id == 0L) insertSupported(database, "expenses", data)
            else { require(database.update("expenses", data, "id=?", arrayOf(e.id.toString())) == 1) { "Gasto no encontrado." }; e.id }
        }
    }
    private fun deleteExpenseImpl(id: Long) {
        transaction { database -> require(database.delete("expenses", "id=?", arrayOf(id.toString())) == 1) { "Gasto no encontrado." } }
    }
    private fun reportImpl(from: String? = null, to: String? = null): Report {
        validateRange(from, to)
        return transaction { database ->
            val orders = allOrders(database).filter { it.status != OrderStatus.CANCELLED }
            val selected = orders.filter { inRange(it.deliveryDate, from, to) }
            val sales = selected.fold(0L) { sum, order -> checkedAdd(sum, order.totalCents) }
            val balances = selected.fold(0L) { sum, order -> checkedAdd(sum, order.balanceCents) }
            val collected = orders.flatMap { it.payments }.filter {
                val day = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                inRange(day, from, to)
            }.fold(0L) { sum, payment -> checkedAdd(sum, payment.amountCents) }
            val spent = allExpenses(database).filter { inRange(it.date, from, to) }.fold(0L) { sum, expense -> checkedAdd(sum, expense.amountCents) }
            Report(sales, collected, spent, balances, selected.count { it.status == OrderStatus.PENDING || it.status == OrderStatus.PREPARING }, selected.count { it.status == OrderStatus.DELIVERED })
        }
    }
    private fun jsonObject(vararg fields: Pair<String, Any?>) = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }
    private fun <T> jsonArray(rows: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { rows.forEach { put(encode(it)) } }
    private fun exportBackupImpl(): String = transaction { database ->
        val customers = rows(database, "SELECT * FROM customers ORDER BY id", read = ::readCustomer)
        val products = rows(database, "SELECT * FROM products ORDER BY id", read = ::readProduct)
        val orders = allOrders(database)
        val expenses = allExpenses(database)
        val root = jsonObject("format" to BACKUP_FORMAT, "version" to 1, "currency" to "MXN", "exportedAt" to System.currentTimeMillis(),
            "customers" to jsonArray(customers) { jsonObject("id" to it.id, "name" to it.name, "phone" to it.phone, "address" to it.address, "notes" to it.notes) },
            "products" to jsonArray(products) { jsonObject("id" to it.id, "name" to it.name, "priceCents" to it.priceCents, "stock" to it.stock, "active" to it.active) },
            "orders" to jsonArray(orders) { order ->
                val reservations = rows(database, "SELECT stock_reserved FROM order_items WHERE order_id=? ORDER BY position", arrayOf(order.id.toString())) { it.getInt(0) != 0 }
                val items = JSONArray().apply { order.items.forEachIndexed { index, item -> put(jsonObject("productId" to item.productId, "description" to item.description,
                    "quantity" to item.quantity, "unitPriceCents" to item.unitPriceCents, "stockReserved" to reservations[index])) } }
                jsonObject("id" to order.id, "customerId" to order.customerId, "customerName" to order.customerName, "deliveryDate" to order.deliveryDate,
                    "deliveryAddress" to order.deliveryAddress, "notes" to order.notes, "status" to order.status.name, "items" to items,
                    "payments" to jsonArray(order.payments) { jsonObject("id" to it.id, "orderId" to it.orderId, "amountCents" to it.amountCents, "timestamp" to it.timestamp, "note" to it.note) })
            }, "expenses" to jsonArray(expenses) { jsonObject("id" to it.id, "description" to it.description, "category" to it.category, "amountCents" to it.amountCents, "date" to it.date, "notes" to it.notes) })
        val json = root.toString()
        require(json.length <= MAX_BACKUP_BYTES && json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Respaldo mayor de 10 MiB; reduzca el historial antes de exportar." }
        // Apply the same limits and invariant validation to exports and imports.
        parseSnapshot(json)
        json
    }
    companion object {
        // Separate imported IDs from the allocation guard, leaving explicit headroom.
        const val MAX_SUPPORTED_ID = 1_000_000_000_000L
        const val MAX_IMPORTED_ID = MAX_SUPPORTED_ID - 1_000_000L
        private const val BACKUP_FORMAT = "com.tamalitos.malitos.backup"
        private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
        private const val MAX_ROWS = 10000
        private const val MAX_CHILD_ROWS = 100000
    }
    private data class StoredOrder(val order: Order, val reservations: List<Boolean>)
    private data class Snapshot(val customers: List<Customer>, val products: List<Product>, val orders: List<StoredOrder>, val expenses: List<Expense>)

    private fun jsonKeys(obj: JSONObject, vararg expected: String) {
        require(obj.keys().asSequence().toSet() == expected.toSet()) { "Campos del respaldo inválidos o incompletos." }
    }
    private fun jsonString(obj: JSONObject, key: String): String {
        val value = obj.get(key)
        require(value is String) { "El campo $key debe ser texto." }
        return value
    }
    private fun jsonLong(obj: JSONObject, key: String, min: Long = 0, max: Long = Long.MAX_VALUE): Long {
        val value = obj.get(key)
        require(value is Int || value is Long) { "El campo $key debe ser un entero exacto." }
        val number = (value as Number).toLong()
        require(number in min..max) { "El campo $key está fuera de rango." }
        return number
    }
    private fun jsonId(obj: JSONObject, key: String) = jsonLong(obj, key, 1, MAX_IMPORTED_ID)
    private fun jsonBoolean(obj: JSONObject, key: String): Boolean {
        val value = obj.get(key)
        require(value is Boolean) { "El campo $key debe ser booleano." }
        return value
    }
    private fun jsonNullableLong(obj: JSONObject, key: String, min: Long, max: Long): Long? {
        require(obj.has(key)) { "Falta el campo $key." }
        return if (obj.isNull(key)) null else jsonLong(obj, key, min, max)
    }
    private fun <T> jsonRows(obj: JSONObject, key: String, max: Int = MAX_ROWS, decode: (JSONObject) -> T): List<T> {
        val array = obj.get(key)
        require(array is JSONArray && array.length() <= max) { "La lista $key no es válida o excede el límite." }
        return (0 until array.length()).map { index ->
            val row = array.get(index)
            require(row is JSONObject) { "Registro inválido en $key." }
            decode(row)
        }
    }
    private fun uniqueIds(ids: List<Long>, label: String) { require(ids.toSet().size == ids.size) { "Identificadores duplicados en $label." } }
    private fun parseSnapshot(json: String): Snapshot {
        require(json.length <= MAX_BACKUP_BYTES && json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Respaldo mayor de 10 MiB." }
        try {
            JsonGuard(json).validate()
            val root = JSONObject(json)
            jsonKeys(root, "format", "version", "currency", "exportedAt", "customers", "products", "orders", "expenses")
            require(jsonString(root, "format") == BACKUP_FORMAT && jsonLong(root, "version") == 1L && jsonString(root, "currency") == "MXN") { "Formato, moneda o versión de respaldo no compatible." }
            jsonLong(root, "exportedAt")
            val customers = jsonRows(root, "customers") {
                jsonKeys(it, "id", "name", "phone", "address", "notes")
                validateCustomer(Customer(jsonId(it, "id"), jsonString(it, "name"), jsonString(it, "phone"), jsonString(it, "address"), jsonString(it, "notes")))
            }
            val products = jsonRows(root, "products") {
                jsonKeys(it, "id", "name", "priceCents", "stock", "active")
                validateProduct(Product(jsonId(it, "id"), jsonString(it, "name"), jsonLong(it, "priceCents", 1), jsonNullableLong(it, "stock", 0, Int.MAX_VALUE.toLong())?.toInt(), jsonBoolean(it, "active")))
            }
            uniqueIds(customers.map { it.id }, "clientes"); uniqueIds(products.map { it.id }, "productos")
            val customerIds = customers.map { it.id }.toSet()
            val catalog = products.associateBy { it.id }
            var childCount = 0
            val paymentIds = mutableSetOf<Long>()
            val orders = jsonRows(root, "orders") { obj ->
                jsonKeys(obj, "id", "customerId", "customerName", "deliveryDate", "deliveryAddress", "notes", "status", "items", "payments")
                val id = jsonId(obj, "id")
                val customerId = jsonId(obj, "customerId")
                require(customerId in customerIds) { "Pedido con cliente inexistente." }
                val status = OrderStatus.valueOf(jsonString(obj, "status"))
                val items = jsonRows(obj, "items", 1000) { row ->
                    jsonKeys(row, "productId", "description", "quantity", "unitPriceCents", "stockReserved")
                    val item = item(OrderItem(jsonNullableLong(row, "productId", 1, MAX_SUPPORTED_ID), jsonString(row, "description"), jsonLong(row, "quantity", 1, Int.MAX_VALUE.toLong()).toInt(), jsonLong(row, "unitPriceCents", 1)))
                    val reserved = jsonBoolean(row, "stockReserved")
                    val product = item.productId?.let { catalog[it] ?: throw IllegalArgumentException("Concepto con producto inexistente.") }
                    require(!reserved || product != null) { "Reserva sin producto." }
                    if (status != OrderStatus.CANCELLED) require(reserved == (product?.stock != null)) { "Reserva de existencias inconsistente." }
                    item to reserved
                }
                require(items.isNotEmpty()) { "Pedido sin conceptos." }
                val payments = jsonRows(obj, "payments", MAX_CHILD_ROWS) { row ->
                    jsonKeys(row, "id", "orderId", "amountCents", "timestamp", "note")
                    val payment = Payment(jsonId(row, "id"), jsonId(row, "orderId"), jsonLong(row, "amountCents", 1), jsonLong(row, "timestamp"), text(jsonString(row, "note"), "Nota del abono"))
                    require(payment.orderId == id && paymentIds.add(payment.id)) { "Relación o identificador de abono inválido." }
                    payment
                }
                childCount += items.size + payments.size
                require(childCount <= MAX_CHILD_ROWS) { "Demasiados conceptos y abonos en el respaldo." }
                val order = Order(id, customerId, text(jsonString(obj, "customerName"), "Nombre del cliente", true, 200), date(jsonString(obj, "deliveryDate")),
                    text(jsonString(obj, "deliveryAddress"), "Dirección"), text(jsonString(obj, "notes"), "Notas"), status, items.map { it.first }, payments)
                require(order.paidCents <= order.totalCents) { "El pedido tiene abonos mayores que su total." }
                require(status != OrderStatus.CANCELLED || payments.isEmpty()) { "Pedido cancelado con abonos." }
                StoredOrder(order, items.map { it.second })
            }
            uniqueIds(orders.map { it.order.id }, "pedidos")
            val expenses = jsonRows(root, "expenses") {
                jsonKeys(it, "id", "description", "category", "amountCents", "date", "notes")
                validateExpense(Expense(jsonId(it, "id"), jsonString(it, "description"), jsonString(it, "category"), jsonLong(it, "amountCents", 1), jsonString(it, "date"), jsonString(it, "notes")))
            }
            uniqueIds(expenses.map { it.id }, "gastos")
            return Snapshot(customers, products, orders, expenses)
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: org.json.JSONException) { throw IllegalArgumentException("Respaldo JSON inválido o incompleto.", e) }
    }

    /** Android's JSONTokener is permissive; enforce JSON grammar before using it. */
    private class JsonGuard(private val source: String) {
        private var position = 0
        private var tokens = 0
        fun validate() { value(0); whitespace(); require(position == source.length) { "Contenido adicional después del JSON." } }
        private fun whitespace() { while (position < source.length && source[position] in " \t\r\n") position++ }
        private fun take(c: Char): Boolean {
            whitespace()
            if (position < source.length && source[position] == c) { position++; return true }
            return false
        }
        private fun expect(c: Char) { require(take(c)) { "JSON inválido cerca de la posición $position." } }
        private fun value(depth: Int) {
            whitespace()
            require(depth <= 16 && ++tokens <= 1000000 && position < source.length) { "JSON vacío, demasiado profundo o demasiado complejo." }
            when (source[position]) {
                '{' -> {
                    position++
                    val keys = mutableSetOf<String>()
                    if (take('}')) return
                    do {
                        whitespace()
                        val raw = string()
                        val key = JSONTokener(raw).nextValue() as String
                        require(keys.add(key)) { "Campo JSON duplicado: $key." }
                        expect(':'); value(depth + 1)
                    } while (take(','))
                    expect('}')
                }
                '[' -> {
                    position++
                    if (take(']')) return
                    do { value(depth + 1) } while (take(','))
                    expect(']')
                }
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                else -> {
                    val match = NUMBER.matchAt(source, position)
                    require(match != null && match.value.length <= 32) { "Número o valor JSON inválido." }
                    position += match.value.length
                }
            }
        }
        private fun literal(text: String) {
            require(source.startsWith(text, position)) { "Valor JSON inválido." }
            position += text.length
        }
        private fun string(): String {
            val start = position
            require(position < source.length && source[position++] == '"') { "Se esperaba texto JSON." }
            while (position < source.length) {
                val c = source[position++]
                if (c == '"') return source.substring(start, position)
                require(c >= ' ') { "Carácter de control inválido en JSON." }
                if (c == '\\') {
                    require(position < source.length) { "Escape incompleto en JSON." }
                    val escape = source[position++]
                    if (escape == 'u') {
                        require(position + 4 <= source.length && source.substring(position, position + 4).all { it in "0123456789abcdefABCDEF" }) { "Escape Unicode inválido." }
                        position += 4
                    } else require(escape in "\"\\/bfnrt") { "Escape JSON inválido." }
                }
            }
            throw IllegalArgumentException("Texto JSON sin cierre.")
        }
        companion object { private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?") }
    }

    private fun importBackupImpl(json: String) {
        // No database operation (even opening it) precedes complete pre-validation.
        val snapshot = parseSnapshot(json)
        transaction { database ->
            listOf("payments", "order_items", "orders", "expenses", "products", "customers").forEach { database.delete(it, null, null) }
            database.execSQL("DELETE FROM sqlite_sequence WHERE name IN ('customers','products','orders','payments','expenses')")
            snapshot.customers.forEach { c -> insertSupported(database, "customers", values("id" to c.id, "name" to c.name, "phone" to c.phone, "address" to c.address, "notes" to c.notes)) }
            snapshot.products.forEach { p -> insertSupported(database, "products", values("id" to p.id, "name" to p.name, "price_cents" to p.priceCents, "stock" to p.stock, "active" to p.active)) }
            snapshot.orders.forEach { stored ->
                val order = stored.order
                insertSupported(database, "orders", values("id" to order.id, "customer_id" to order.customerId, "customer_name" to order.customerName,
                    "delivery_date" to order.deliveryDate, "delivery_address" to order.deliveryAddress, "notes" to order.notes, "status" to order.status.name))
                order.items.forEachIndexed { position, item -> insertItem(database, order.id, position, item, stored.reservations[position]) }
                order.payments.forEach { insertPayment(database, it) }
            }
            snapshot.expenses.forEach { e -> insertSupported(database, "expenses", values("id" to e.id, "description" to e.description, "category" to e.category, "amount_cents" to e.amountCents, "date" to e.date, "notes" to e.notes)) }
            require(rows(database, "PRAGMA foreign_key_check") { it.getString(0) }.isEmpty()) { "Relaciones inválidas en el respaldo." }
        }
    }
    override fun close() = coordinator.read { helper.close() }
}
