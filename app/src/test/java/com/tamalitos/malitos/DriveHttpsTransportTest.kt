package com.tamalitos.malitos

import org.junit.Assert.*
import org.junit.Test

class DriveHttpsTransportTest {
    @Test fun refusesPlaintextAndNonGoogleTargetsBeforeOpeningConnection() {
        for (url in listOf("http://www.googleapis.com/drive/v3/files", "https://evil.example/", "https://www.googleapis.com.evil.example/", "https://user@www.googleapis.com/", "https://www.googleapis.com:8443/")) {
            assertThrows(IllegalArgumentException::class.java) {
                DriveHttpsTransport().execute(DriveRequest("GET", url, emptyMap()))
            }
        }
    }
}
