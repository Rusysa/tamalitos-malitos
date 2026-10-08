package com.tamalitos.malitos

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, manifest = Config.NONE)
class DriveSnapshotTest {
    @Test fun rejectsForeignOrFutureSnapshotWithoutReturningBusinessData() {
        val valid = JSONObject(DriveSnapshot.encode("{\"version\":1}", 1).toString(Charsets.UTF_8))
        for (bad in listOf(JSONObject(valid.toString()).put("version", 2), JSONObject(valid.toString()).put("format", "other"), JSONObject(valid.toString()).put("createdAt", -1), JSONObject(valid.toString()).put("businessJson", "[]"))) {
            assertThrows(IllegalArgumentException::class.java) { DriveSnapshot.decode(bad.toString().toByteArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) { DriveSnapshot.decode(ByteArray(20 * 1024 * 1024 + 1)) }
    }

    @Test fun wrapsVersionedBusinessJsonWithTimestampWithoutChangingPayload() {
        val business = "{\"version\":1,\"customers\":[]}"
        val encoded = DriveSnapshot.encode(business, 1700000000000L)
        val json = JSONObject(encoded.toString(Charsets.UTF_8))
        assertEquals("com.tamalitos.malitos.drive", json.getString("format"))
        assertEquals(1, json.getInt("version"))
        assertEquals(1700000000000L, json.getLong("createdAt"))
        assertEquals(business, json.getString("businessJson"))
        assertEquals(business, DriveSnapshot.decode(encoded))
    }
}
