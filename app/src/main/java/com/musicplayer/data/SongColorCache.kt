package com.musicplayer.data

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-level singleton palette cache.
 * Colours are extracted once per URI on an IO thread and reused across
 * recompositions / screen re-entries without any extra disk I/O.
 *
 * Uses a Semaphore to limit concurrent bitmap decodes to 4 at a time.
 * Without this, a library of 500+ songs would spawn 500 simultaneous
 * BitmapFactory calls, saturating IO and stalling the main thread.
 */
object SongColorCache {

    // URI string → packed ARGB int (avoids boxing Color on hot path)
    private val cache = ConcurrentHashMap<String, Int>()

    // Max 4 simultaneous bitmap decodes — keeps IO smooth without blocking
    private val decodeSemaphore = Semaphore(4)

    /**
     * Returns the dominant vibrant colour for [uri], using the cache on subsequent calls.
     * Must be called from a coroutine (suspends on IO thread on first access).
     */
    suspend fun getColor(context: Context, uri: Uri): Color? {
        val key = uri.toString()
        cache[key]?.let { return Color(it) }

        return decodeSemaphore.withPermit {
            // Re-check cache after acquiring permit — another coroutine may have decoded it
            cache[key]?.let { return Color(it) }

            withContext(Dispatchers.IO) {
                try {
                    val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 4   // 1/4 size — more than enough for palette
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565  // 50% less memory
                        }
                        android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                    }
                    bmp?.let {
                        val palette = Palette.from(it).maximumColorCount(6).generate()
                        val raw = palette.getVibrantColor(
                            palette.getDominantColor(0xFF888888.toInt())
                        )
                        it.recycle()
                        cache[key] = raw
                        Color(raw)
                    }
                } catch (_: Exception) { null }
            }
        }
    }

    fun invalidate(uri: Uri) = cache.remove(uri.toString())

    fun clear() = cache.clear()
}
