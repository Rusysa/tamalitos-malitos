package com.tamalitos.malitos

import android.app.Application
import androidx.compose.ui.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
internal class ComposeReviewCustomerSearchTest : ComposeUiHarness() {
    private fun directory() {
        launch()
        activity.store.saveCustomer(Customer(name = "Address fixture", address = "Mercado Central", notes = "Entrega especial"))
        activity.store.saveCustomer(Customer(name = "Other fixture", address = "Otro lugar", notes = "Normal"))
        navigate("Clientes")
    }
    @Test fun addressSearchPreservesStorePredicateIncludingCaseAndWhitespace() {
        directory()
        field("Buscar cliente por nombre o teléfono", "  mErCaDo  ")
        assertEquals(listOf("Address fixture"), activity.store.customers(activity.customerQuery).map { it.name })
        compose.onNodeWithText("Address fixture").assertExists()
        compose.onNodeWithText("Other fixture").assertDoesNotExist()
        compose.onNodeWithText("Sin coincidencias").assertDoesNotExist()
        field("Buscar cliente por nombre o teléfono", "")
        compose.onNodeWithText("Address fixture").assertExists()
        compose.onNodeWithTag("customer-list").performScrollToNode(hasText("Other fixture"))
        compose.onNodeWithText("Other fixture").assertExists()
    }
    @Test fun notesSearchPreservesStorePredicateAndReportsNoMatch() {
        directory()
        field("Buscar cliente por nombre o teléfono", "especial")
        assertEquals(listOf("Address fixture"), activity.store.customers(activity.customerQuery).map { it.name })
        compose.onNodeWithText("Address fixture").assertExists()
        compose.onNodeWithText("Other fixture").assertDoesNotExist()
        field("Buscar cliente por nombre o teléfono", "missing fixture")
        compose.onNodeWithText("Sin coincidencias").assertExists()
        compose.onNodeWithText("Address fixture").assertDoesNotExist()
    }
}
