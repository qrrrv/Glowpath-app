package com.musicplayer.data

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.google.material.color.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-level cache for the exact two-color gradient input used by OuterTune.
 */
object SongColorCache {
    private val gradientCache = ConcurrentHashMap<String, List<Int>>()
    private val decodeSemaphore = Semaphore(4)
    private val fallbackGradient = listOf(0xFF595959.toInt(), 0xFF0D0D0D.toInt())

    /**
     * Equivalent to OuterTune's Bitmap.extractGradientColors():
     * Coil request at 100x100, Palette max 16 swatches, Material HCT Score,
     * then luminance ordering of the two selected colors.
     */
    suspend fun getGradientColors(context: Context, uri: Uri): List<Color> {
        val key = uri.toString()
        gradientCache[key]?.let { return it.map(::Color) }

        return decodeSemaphore.withPermit {
            gradientCache[key]?.let { return it.map(::Color) }

            val packedColors = withContext(Dispatchers.IO) {
                try {
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(Size(100, 100))
                            .allowHardware(false)
                            .build()
                    )
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    val colors = bitmap?.let {
                        val extractedColors = Palette.from(it)
                            .maximumColorCount(16)
                            .generate()
                            .swatches
                            .associate { swatch -> swatch.rgb to swatch.population }

                        Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
                            .sortedByDescending { color -> Color(color).luminance() }
                    } ?: emptyList()

                    if (colors.size >= 2) colors else fallbackGradient
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
