package com.tamalitos.malitos

import org.junit.Assert.*
import org.junit.Test

class UiPeriodTest {
    @Test fun emptyRangeMeansEntireHistory() {
        val range = UiPeriod.validated("", "")
        assertNull(range.from)
        assertNull(range.to)
    }
    @Test fun rangeKeepsInclusiveIsoDates() {
        val range = UiPeriod.validated("2026-10-01", "2026-10-31")
        assertEquals("2026-10-01", range.from)
        assertEquals("2026-10-31", range.to)
    }
    @Test(expected = IllegalArgumentException::class) fun reversedRangeIsRejected() {
        UiPeriod.validated("2026-10-31", "2026-10-01")
    }
    @Test(expected = IllegalArgumentException::class) fun invalidCalendarDateIsRejected() {
        UiPeriod.validated("2026-02-30", "2026-10-01")
    }
}
