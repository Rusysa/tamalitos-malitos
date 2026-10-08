package com.tamalitos.malitos

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class BoundaryAllocationTest {
    private lateinit var context: Context
    private lateinit var store: BusinessStore
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        context.deleteDatabase("tamalitos.db")
        store=BusinessStore(context)
    }
    @After fun cleanup() { store.close(); context.deleteDatabase("tamalitos.db") }

    @Test fun allocationCeilingCannotBeImported() {
        store.saveCustomer(Customer(name="Conservar"))
        val generation=store.generation
        val invalid=JSONObject(store.exportBackup()).apply {
            getJSONArray("customers").getJSONObject(0).put("id",BusinessStore.MAX_SUPPORTED_ID)
        }.toString()
        assertThrows(IllegalArgumentException::class.java) { store.importBackup(invalid) }
        assertEquals("Conservar",store.customers().single().name)
        assertEquals(generation,store.generation)
        assertEquals(2L,store.saveCustomer(Customer(name="Sigue escribiendo")))
    }

    @Test fun acceptedBoundaryCanAllocateExportRestoreAndAllocateAfterDeletingHighestId() {
        val importedCeiling=999_999_000_000L
        store.saveCustomer(Customer(name="Importado"))
        val snapshot=JSONObject(store.exportBackup()).apply {
            getJSONArray("customers").getJSONObject(0).put("id",importedCeiling)
        }.toString()
        store.importBackup(snapshot)
        val previousGeneration=store.generation
        val next=store.saveCustomer(Customer(name="Nuevo"))
        assertTrue(next in 1..importedCeiling)
        assertNotEquals(importedCeiling,next)
        assertNotEquals(previousGeneration,store.generation) // lower IDs invalidate stale editor closures
        store.importBackup(store.exportBackup())
        assertEquals(setOf("Importado","Nuevo"),store.customers().map { it.name }.toSet())
        store.deleteCustomer(importedCeiling)
        val afterDelete=store.saveCustomer(Customer(name="Otro"))
        assertTrue(afterDelete in 1..importedCeiling)
        store.importBackup(store.exportBackup())
        assertEquals(setOf("Nuevo","Otro"),store.customers().map { it.name }.toSet())
    }

    @Test fun importedIdsArePreservedEvenWhenCeilingIsReached() {
        val ceiling = 999_999_000_000L
        val json = JSONObject().apply {
            put("format", "com.tamalitos.malitos.backup"); put("version", 1); put("currency", "MXN"); put("exportedAt", 123L)
            put("customers", JSONObject().apply { 
                // Case: ceiling customer followed by a lower ID customer
                // If fallback allocation is applied to everything, the second one becomes ID 1
                val arr = org.json.JSONArray()
                arr.put(JSONObject().apply { put("id", ceiling); put("name", "Ceiling"); put("phone", "1"); put("address", "A"); put("notes", "N") })
                arr.put(JSONObject().apply { put("id", 42L); put("name", "FortyTwo"); put("phone", "2"); put("address", "B"); put("notes", "M") })
                // and some products/orders/expenses for validity
                put("customers", arr); put("products", org.json.JSONArray()); put("orders", org.json.JSONArray()); put("expenses", org.json.JSONArray())
            })
        }.toString()
        // Need to mock a proper snapshot for parseSnapshot to not fail on missing keys
        // The above is a bit simplified, let's use a real export then modify it.
        store.saveCustomer(Customer(name="T1"))
        val realExport = JSONObject(store.exportBackup())
        val customers = realExport.getJSONArray("customers")
        customers.put(0, JSONObject().apply { put("id", ceiling); put("name", "Ceiling"); put("phone", "1"); put("address", "A"); put("notes", "N") })
        customers.put(JSONObject().apply { put("id", 42L); put("name", "FortyTwo"); put("phone", "2"); put("address", "B"); put("notes", "M") })
        
        store.importBackup(realExport.toString())
        val result = store.customers().sortedBy { it.id }
        assertEquals(42L, result[0].id); assertEquals("FortyTwo", result[0].name)
        assertEquals(ceiling, result[1].id); assertEquals("Ceiling", result[1].name)
    }

    @Test fun exportRestoreRoundTripWithReverseIdOrderDoesNotRenumber() {
        val c1 = store.saveCustomer(Customer(name="C1")) // ID 1
        val c2 = store.saveCustomer(Customer(name="C2")) // ID 2
        val p = store.saveProduct(Product(name="P1", priceCents = 100))
        val order1 = store.createOrder(c1, "2026-10-08", "A", "", listOf(OrderItem(p.id, "P1", 1, 100)), InitialPayment.FULL)
        val order2 = store.createOrder(c2, "2026-10-09", "B", "", listOf(OrderItem(p.id, "P1", 1, 100)), InitialPayment.FULL)
        
        // Export is by delivery_date, id. If order2 is 2026-10-09 and order1 is 2026-10-08, 
        // the snapshot puts order1 then order2. 
        // If we manually change the order in the JSON to put the ceiling order first:
        val realExport = JSONObject(store.exportBackup())
        val orders = realExport.getJSONArray("orders")
        val o1 = orders.getJSONObject(0)
        val o2 = orders.getJSONObject(1)
        o1.put("id", 999_999_000_000L)
        o2.put("id", 2L)
        // Now order[0] is ceiling, order[1] is 2.
        
        store.importBackup(realExport.toString())
        val result = store.orders().sortedBy { it.id }
        assertEquals(2L, result[0].id); assertEquals("C2", result[0].customerName)
        assertEquals(999_999_000_000L, result[1].id); assertEquals("C1", result[1].customerName)
    }
}
