package com.tamalitos.malitos

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** One lock order for every store: shared database fence first, no instance monitors.
 * Generation is volatile so an operation captures it BEFORE waiting for the fence.
 * A fresh process uses a fresh nonce: surviving Activity state cannot target reused IDs.
 */
internal class BusinessMutationCoordinator private constructor() {
    private val lock = ReentrantLock(true)
    @Volatile var generation: Long = java.util.UUID.randomUUID().mostSignificantBits
        private set
    fun <T> read(block: () -> T): T = lock.withLock(block)
    fun <T> mutate(expected: Long, block: () -> T): T = lock.withLock {
        require(expected == generation) { "Los datos fueron restaurados. Cierra este formulario y vuelve a abrirlo; no se guardó ningún cambio." }
        block()
    }
    fun replace(expected: Long, block: () -> Unit) = mutate(expected) {
        block() // Only committed replacements invalidate previously captured work.
        generation++
    }
    fun invalidateAllocatedIdentity() {
        check(lock.isHeldByCurrentThread)
        generation++
    }
    companion object {
        private val databases = ConcurrentHashMap<String, BusinessMutationCoordinator>()
        fun forContext(context: Context): BusinessMutationCoordinator = databases.computeIfAbsent(
            context.applicationContext.getDatabasePath("tamalitos.db").canonicalPath
        ) { BusinessMutationCoordinator() }
    }
}
