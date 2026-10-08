package com.tamalitos.malitos

import java.math.BigInteger

object BusinessRules {
    fun initialPayment(totalCents: Long, mode: InitialPayment, customCents: Long = 0): Long {
        require(totalCents >= 0) { "El total no puede ser negativo." }
        return when (mode) {
            InitialPayment.FULL -> totalCents
            InitialPayment.HALF -> totalCents / 2 + totalCents % 2
            InitialPayment.UNPAID -> 0
            InitialPayment.CUSTOM -> {
                require(customCents in 0..totalCents) { "El abono inicial debe estar entre cero y el total." }
                customCents
            }
        }
    }
}

internal fun checkedAdd(a: Long, b: Long): Long = try { Math.addExact(a, b) } catch (_: ArithmeticException) {
    throw IllegalArgumentException("El importe total es demasiado grande.")
}
internal fun checkedMultiply(a: Long, b: Long): Long = try { Math.multiplyExact(a, b) } catch (_: ArithmeticException) {
    throw IllegalArgumentException("El importe total es demasiado grande.")
}
internal fun checkedSubtract(a: Long, b: Long): Long = try { Math.subtractExact(a, b) } catch (_: ArithmeticException) {
    throw IllegalArgumentException("El importe total es demasiado grande.")
}

/** MXN centavos. Locale independent and deliberately not a floating point parser. */
object Money {
    fun parse(text: String): Long {
        val value = text.trim()
        require(value.matches(Regex("[0-9]+([.,][0-9]{1,2})?"))) { "Importe inválido: use hasta dos decimales, sin signos ni separadores de miles." }
        require(value.length <= 24) { "Importe demasiado grande." }
        val parts = value.replace(',', '.').split('.')
        val cents = BigInteger(parts[0]).multiply(BigInteger.valueOf(100)) +
            BigInteger(if (parts.size == 1) "0" else parts[1].padEnd(2, '0'))
        require(cents <= BigInteger.valueOf(Long.MAX_VALUE)) { "Importe demasiado grande." }
        return cents.toLong()
    }

    fun format(cents: Long): String {
        val magnitude = BigInteger.valueOf(cents).abs()
        val parts = magnitude.divideAndRemainder(BigInteger.valueOf(100))
        return (if (cents < 0) "-" else "") + "$" + parts[0] + "." + parts[1].toString().padStart(2, '0')
    }
}
