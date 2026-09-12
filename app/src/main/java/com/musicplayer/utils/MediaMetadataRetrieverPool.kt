package com.musicplayer.utils

import android.media.MediaMetadataRetriever
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe pool of [MediaMetadataRetriever] instances.
 *
 * Creating a new retriever is expensive (native allocation). During
 * mass-scanning of a large library we would otherwise churn through
 * hundreds of allocations. This pool keeps up to [MAX_POOL_SIZE]
 * instances alive and reuses them across coroutines.
 *
 * Usage:
 * ```kotlin
 * val art = MediaMetadataRetrieverPool.withRetriever { retriever ->
 *     retriever.setDataSource(filePath)
 *     retriever.embeddedPicture
 * }
 * ```
 */
object MediaMetadataRetrieverPool {

    private const val MAX_POOL_SIZE = 4
    private val pool         = ConcurrentLinkedQueue<MediaMetadataRetriever>()
    private val createdCount = AtomicInteger(0)

    @PublishedApi
    internal fun acquire(): MediaMetadataRetriever =
        pool.poll() ?: run {
            createdCount.incrementAndGet()
            MediaMetadataRetriever()
        }

    @PublishedApi
    internal fun release(retriever: MediaMetadataRetriever) {
        if (pool.size < MAX_POOL_SIZE) {
            pool.offer(retriever)
        } else {
            runCatching { retriever.release() }
            createdCount.decrementAndGet()
        }
    }

    /**
     * Executes [block] with a pooled retriever and automatically returns
     * it to the pool afterwards. Returns **null** on any exception.
     */
    inline fun <T> withRetriever(block: (MediaMetadataRetriever) -> T): T? {
        val retriever = acquire()
        return try {
            block(retriever)
        } catch (_: Exception) {
            null
        } finally {
            release(retriever)
        }
    }

    /** Call from Application.onTrimMemory / onLowMemory. */
    fun clear() {
        while (true) {
            val r = pool.poll() ?: break
            runCatching { r.release() }
            createdCount.decrementAndGet()
        }
    }

    fun poolSize(): Int  = pool.size
    fun totalCreated(): Int = createdCount.get()
}
