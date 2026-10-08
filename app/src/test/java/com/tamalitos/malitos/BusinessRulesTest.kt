package com.tamalitos.malitos

import org.junit.Assert.*
import org.junit.Test

class BusinessRulesTest {
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected validation rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun initialPaymentModesAndOddHalfAreBounded() {
        assertEquals(101L, BusinessRules.initialPayment(101, InitialPayment.FULL))
        assertEquals(51L, BusinessRules.initialPayment(101, InitialPayment.HALF))
        assertEquals(0L, BusinessRules.initialPayment(101, InitialPayment.UNPAID))
        assertEquals(30L, BusinessRules.initialPayment(101, InitialPayment.CUSTOM, 30))
        assertEquals(Long.MAX_VALUE / 2 + 1, BusinessRules.initialPayment(Long.MAX_VALUE, InitialPayment.HALF))
        rejected { BusinessRules.initialPayment(-1, InitialPayment.UNPAID) }
        rejected { BusinessRules.initialPayment(10, InitialPayment.CUSTOM, 11) }
        rejected { BusinessRules.initialPayment(10, InitialPayment.CUSTOM, -1) }
    }

    @Test fun strictMoneyUsesIntegerCents() {
        assertEquals(12345L, Money.parse(" 123,45 "))
        assertEquals(100L, Money.parse("1"))
        assertEquals(110L, Money.parse("1.1"))
        assertEquals(0L, Money.parse("0.00"))
        assertEquals("$123.45", Money.format(12345))
        assertEquals("-$0.01", Money.format(-1))
        listOf("", "-1", "+1", "1.001", "1,000.00", "1e2", "NaN", ".5", "1.", "999999999999999999999999").forEach { text -> rejected { Money.parse(text) } }
        assertEquals(Long.MAX_VALUE, Money.parse("92233720368547758.07"))
        rejected { Money.parse("92233720368547758.08") }
    }
}
