package com.musicplayer.data

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-level album palette cache. The player uses the same two-color gradient
 * shape as OuterTune; cached packed ARGB values avoid repeated bitmap decoding.
 */
object SongColorCache {
    private val gradientCache = ConcurrentHashMap<String, List<Int>>()
    private val decodeSemaphore = Semaphore(4)
    private val fallbackGradient = listOf(0xFF595959.toInt(), 0xFF0D0D0D.toInt())

    /**
     * Extracts two representative colors from an artwork bitmap. Palette is
     * intentionally sampled at 16 swatches, then ordered by luminance like
     * OuterTune's extractGradientColors() before rendering a vertical gradient.
     */
    suspend fun getGradientColors(context: Context, uri: Uri): List<Color> {
        val key = uri.toString()
        gradientCache[key]?.let { return it.map(::Color) }

        return decodeSemaphore.withPermit {
            gradientCache[key]?.let { return it.map(::Color) }

            val packedColors = withContext(Dispatchers.IO) {
                try {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 4
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        android.graphics.BitmapFactory.decodeStream(stream, null, options)
                    }

                    bitmap?.let {
                        val swatches = Palette.from(it)
                            .maximumColorCount(16)
                            .generate()
                            .swatches

                        val colors = swatches
                            .associate { swatch -> swatch.rgb to swatch.population }
                            .entries
                            .sortedWith(
                                compareByDescending<Map.Entry<Int, Int>> { it.value }
                                    .thenByDescending { Color(it.key).luminance() }
                            )
                            .map { it.key }
                            .distinct()
                            .sortedByDescending { Color(it).luminance() }
                            .take(2)

                        it.recycle()
                        if (colors.size >= 2) colors else fallbackGradient
                    } ?: fallbackGradient
                } catch (_: Exception) {
                    fallbackGradient
                }
            }

            gradientCache[key] = packedColors
            packedColors.map(::Color)
        }
    }

    /** Compatibility helper used by theme warm-up code. */
    suspend fun getColor(context: Context, uri: Uri): Color? =
        getGradientColors(context, uri).firstOrNull()

    fun invalidate(uri: Uri) {
        gradientCache.remove(uri.toString())
    }

    fun clear() {
        gradientCache.clear()
    }
}
