package com.tamalitos.malitos

import org.junit.Assert.*
import org.junit.Test

class UiChartsTest {
    private fun expense(category: String, amount: Long) = Expense(description = "Prueba", category = category, amountCents = amount, date = "2026-10-12")

    @Test fun categoryChartAggregatesExactCentsAndOrdersByAmount() {
        val result = expenseSlices(listOf(expense("Ingredientes", 1001), expense("Transporte", 900), expense("Ingredientes", 2)))
        assertEquals(listOf(ExpenseSlice("Ingredientes", 1003), ExpenseSlice("Transporte", 900)), result)
    }

    @Test fun smallerCategoriesAreGroupedWithoutLosingMoney() {
        val expenses = (1..8).map { expense("Categoría $it", it.toLong()) }
        val slices = expenseSlices(expenses)
        assertEquals(6, slices.size)
        assertEquals(ExpenseSlice("Otras categorías", 6), slices.last())
        assertEquals(expenses.sumOf { it.amountCents }, slices.sumOf { it.cents })
    }

    @Test fun emptyChartContainsNoInventedData() {
        assertTrue(expenseSlices(emptyList()).isEmpty())
    }
}
