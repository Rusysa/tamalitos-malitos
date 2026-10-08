package com.tamalitos.malitos

import java.time.LocalDate

internal data class UiDateRange(val from: String?, val to: String?)

internal object UiPeriod {
    fun validated(from: String, to: String): UiDateRange {
        val first = from.trim().takeIf { it.isNotEmpty() }
        val last = to.trim().takeIf { it.isNotEmpty() }
        try {
            val start = first?.let { LocalDate.parse(it) }
            val end = last?.let { LocalDate.parse(it) }
            require(start == null || end == null || !start.isAfter(end)) { "La fecha inicial debe ser anterior o igual a la fecha final." }
        } catch (e: java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("El periodo debe contener fechas válidas.", e)
        }
        return UiDateRange(first, last)
    }
}
