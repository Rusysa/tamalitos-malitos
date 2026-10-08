package com.tamalitos.malitos

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ReviewReplacementTest {
    private lateinit var context: Context
    private lateinit var store: BusinessStore
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("tamalitos.db"); store = BusinessStore(context) }
    @After fun cleanup() { store.close(); context.deleteDatabase("tamalitos.db") }
    private fun restore(target: BusinessStore, json: String, save: (String) -> Unit) = target.restoreBackupWithSafety(json, save)
    private fun waitQueued(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (thread.isAlive && thread.state !in listOf(Thread.State.WAITING, Thread.State.BLOCKED) && System.nanoTime() < deadline) Thread.yield()
    }
    @Test fun queuedWriterCannotDisappearBetweenSafetySnapshotAndReplacement() {
        val id = store.saveCustomer(Customer(name = "Original"))
        val json = store.exportBackup().replace("Original", "Restored")
        val saved = CountDownLatch(1); val release = CountDownLatch(1)
        val safety = AtomicReference<String>(); val writerFailure = AtomicReference<Throwable>(); val restoreFailure = AtomicReference<Throwable>()
        BusinessStore(context).use { writer ->
            val restoring = Thread { try { restore(store, json) { safety.set(it); saved.countDown(); check(release.await(10, TimeUnit.SECONDS)) } } catch (e: Throwable) { restoreFailure.set(e) } }
            restoring.start(); assertTrue(saved.await(10, TimeUnit.SECONDS))
            val writing = Thread { try { writer.saveCustomer(Customer(id, "Queued write")) } catch (e: Throwable) { writerFailure.set(e) } }
            writing.start(); waitQueued(writing)
            release.countDown(); restoring.join(10000); writing.join(10000)
            assertFalse(restoring.isAlive); assertFalse(writing.isAlive); assertNull(restoreFailure.get())
            assertTrue("queued pre-restore writer must be explicitly rejected, never silently lost", writerFailure.get() is IllegalArgumentException)
            assertTrue(safety.get().contains("Original")); assertEquals("Restored", store.customers().single().name)
        }
    }
    @Test fun simultaneousLocalAndDriveReplacementCannotUseTheSamePreviousGeneration() {
        store.saveCustomer(Customer(name = "Original"))
        val json = store.exportBackup()
        val saved = CountDownLatch(1); val release = CountDownLatch(1)
        val failure = AtomicReference<Throwable>(); val firstFailure = AtomicReference<Throwable>(); val secondSafety = AtomicReference<String>()
        BusinessStore(context).use { other ->
            val first = Thread { try { restore(store, json.replace("Original", "Local")) { saved.countDown(); check(release.await(10, TimeUnit.SECONDS)) } } catch(e: Throwable) { firstFailure.set(e) } }
            first.start(); assertTrue(saved.await(10, TimeUnit.SECONDS))
            val second = Thread { try { restore(other, json.replace("Original", "Drive")) { secondSafety.set(it) } } catch(e: Throwable) { failure.set(e) } }
            second.start(); waitQueued(second); release.countDown(); first.join(10000); second.join(10000)
            assertFalse(first.isAlive); assertFalse(second.isAlive); assertNull(firstFailure.get())
            assertTrue("concurrent replacement queued against an old generation must be rejected", failure.get() is IllegalArgumentException)
            assertNull(secondSafety.get()); assertEquals("Local", store.customers().single().name)
        }
    }
    @Test fun failedSafetyPublicationDoesNotReplaceAnything() {
        store.saveCustomer(Customer(name = "Original"))
        try { restore(store, store.exportBackup().replace("Original", "Restored")) { throw java.io.IOException("disk failure") }; fail() } catch (_: java.io.IOException) {}
        assertEquals("Original", store.customers().single().name)
    }
}
