package com.tamalitos.malitos

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native widgets, real application startup and real SQLite on minimum Android 8.1. */
@RunWith(AndroidJUnit4::class)
class NativeWorkflowInstrumentedTest {
    @Test fun registerCustomerOrderHalfThenLiquidateAndRegisterExpenseThroughScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val unique = "Cliente QA " + System.nanoTime()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            onView(withText("Agregar cliente")).perform(scrollTo(), click())
            onView(withContentDescription("Nombre del cliente *")).perform(scrollTo(), replaceText(unique), closeSoftKeyboard())
            onView(withContentDescription("Teléfono")).perform(scrollTo(), replaceText("5551234567"), closeSoftKeyboard())
            onView(withContentDescription("Dirección habitual")).perform(scrollTo(), replaceText("Mercado QA"), closeSoftKeyboard())
            onView(allOf(withText("Guardar"), isDisplayed())).perform(click())
            onView(withText("Nuevo pedido para este cliente")).perform(scrollTo(), click())
            onView(withContentDescription("Descripción del artículo *")).perform(scrollTo(), replaceText("Tamal QA"), closeSoftKeyboard())
            onView(withContentDescription("Cantidad *")).perform(scrollTo(), replaceText("3"), closeSoftKeyboard())
            onView(withContentDescription("Precio unitario *")).perform(scrollTo(), replaceText("25"), closeSoftKeyboard())
            onView(withContentDescription("Anticipo al crear el pedido")).perform(scrollTo(), click())
            onData(equalTo("La mitad (50 %)")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click())
            onView(withText("Crear pedido")).perform(click())
            BusinessStore(context).use { store ->
                val order = store.orders(unique).single()
                assertEquals(7500L, order.totalCents)
                assertEquals(3750L, order.paidCents)
                assertEquals(3750L, order.balanceCents)
            }
            onView(withText("Registrar abono")).perform(scrollTo(), click())
            onView(withContentDescription("Importe del abono *")).perform(scrollTo(), replaceText("37.50"), closeSoftKeyboard())
            onView(withText("Registrar pago")).perform(click())
            BusinessStore(context).use { store -> assertEquals(0L, store.orders(unique).single().balanceCents) }
            onView(withText("Cambiar estado")).perform(scrollTo(), click())
            onView(withText("Entregado")).perform(click())
            onView(withText("Guardar estado")).perform(click())
            BusinessStore(context).use { store -> assertEquals(OrderStatus.DELIVERED, store.orders(unique).single().status) }
            onView(allOf(withText("Gastos"), isAssignableFrom(android.widget.Button::class.java))).perform(scrollTo(), click())
            onView(withText("Registrar gasto")).perform(scrollTo(), click())
            onView(withContentDescription("Concepto del gasto *")).perform(scrollTo(), replaceText("Empaque " + unique), closeSoftKeyboard())
            onView(withContentDescription("Importe del gasto *")).perform(scrollTo(), replaceText("15"), closeSoftKeyboard())
            onView(allOf(withText("Guardar"), isDisplayed())).perform(click())
            BusinessStore(context).use { store ->
                assertTrue(store.expenses().any { it.description == "Empaque " + unique && it.amountCents == 1500L })
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
