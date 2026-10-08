package com.tamalitos.malitos

data class Customer(val id: Long = 0, val name: String, val phone: String = "", val address: String = "", val notes: String = "")
data class Product(val id: Long = 0, val name: String, val priceCents: Long, val stock: Int? = null, val active: Boolean = true)
data class OrderItem(val productId: Long?, val description: String, val quantity: Int, val unitPriceCents: Long) {
    init {
        require(quantity >= 1 && unitPriceCents > 0) { "Cantidad y precio deben ser mayores que cero." }
        require(description.isNotBlank()) { "La descripción del concepto es obligatoria." }
    }
    val totalCents: Long get() = checkedMultiply(quantity.toLong(), unitPriceCents)
}
enum class InitialPayment { FULL, HALF, UNPAID, CUSTOM }
enum class OrderStatus { PENDING, PREPARING, DELIVERED, CANCELLED }
data class Payment(val id: Long = 0, val orderId: Long, val amountCents: Long, val timestamp: Long, val note: String = "")
data class Order(val id: Long, val customerId: Long, val customerName: String, val deliveryDate: String, val deliveryAddress: String, val notes: String, val status: OrderStatus, val items: List<OrderItem>, val payments: List<Payment>) {
    val totalCents: Long get() = items.fold(0L) { sum, item -> checkedAdd(sum, item.totalCents) }
    val paidCents: Long get() = payments.fold(0L) { sum, payment -> checkedAdd(sum, payment.amountCents) }
    val balanceCents: Long get() = checkedSubtract(totalCents, paidCents)
    val paymentLabel: String get() = when {
        balanceCents == 0L -> "Pagado"
        paidCents == 0L -> "Sin pagar"
        else -> "Abonado"
    }
}
data class Expense(val id: Long = 0, val description: String, val category: String, val amountCents: Long, val date: String, val notes: String = "")
data class Report(val salesCents: Long, val collectedCents: Long, val expensesCents: Long, val receivablesCents: Long, val pendingOrders: Int, val deliveredOrders: Int) {
    val cashFlowCents: Long get() = checkedSubtract(collectedCents, expensesCents)
}
