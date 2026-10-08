package com.tamalitos.malitos

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class UiBackupFilesTest {
    @Test fun boundedReaderPreservesUtf8SpanishText() {
        assertEquals("{\"cliente\":\"María\"}", UiBackupFiles.readBounded(ByteArrayInputStream("{\"cliente\":\"María\"}".toByteArray(Charsets.UTF_8)), 100))
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedBackupIsRefusedBeforeRestore() {
        UiBackupFiles.readBounded(ByteArrayInputStream(ByteArray(11)), 10)
    }
    @Test(expected = java.nio.charset.CharacterCodingException::class) fun invalidUtf8IsRefused() {
        UiBackupFiles.readBounded(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)), 100)
    }
    @Test(expected = IllegalArgumentException::class) fun unreadablyLargeSafetySnapshotIsRefusedBeforeWriting() {
        val root = Files.createTempDirectory(java.nio.file.Path.of(System.getenv("TMPDIR") ?: System.getProperty("java.io.tmpdir")), "ui-backup-large-test").toFile()
        try { UiBackupFiles.safetyCopy(root, "a".repeat(UiBackupFiles.MAX_BYTES + 1)) }
        finally { root.deleteRecursively() }
    }

    @Test fun safetySnapshotContainsExactExistingData() {
        val root = Files.createTempDirectory(java.nio.file.Path.of(System.getenv("TMPDIR") ?: System.getProperty("java.io.tmpdir")), "ui-backup-test").toFile()
        try {
            val file = UiBackupFiles.safetyCopy(root, "{\"version\":1}")
            assertTrue(file.isFile)
            assertEquals("{\"version\":1}", file.readText(Charsets.UTF_8))
            assertFalse(root.listFiles()!!.any { it.extension == "tmp" })
        } finally { root.deleteRecursively() }
    }
}
