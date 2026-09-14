package com.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.google.android.material.color.DynamicColors
import com.musicplayer.utils.MediaMetadataRetrieverPool
import kotlinx.coroutines.Dispatchers

/**
 * Singleton Coil ImageLoader optimised for large music libraries.
 *
 * - Shared loader for every SongRow (no per-row ImageLoader allocation)
 * - Generous memory + disk cache so decoded album art survives fast scroll
 * - Hardware bitmaps + Default dispatcher keep decode off the main thread
 */
class MusicApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        try {
            com.musicplayer.server.NowPlayingHttpServer.start(this)
        } catch (e: Exception) {
            android.util.Log.e("MusicApplication", "Failed to start NowPlaying HTTP server", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .dispatcher(Dispatchers.Default)
            .allowHardware(true)
            // Prefer RGB_565 for list thumbnails when alpha is not needed — halves memory.
            // Coil still uses ARGB_8888 when the source needs alpha.
            .allowRgb565(true)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    // 30% of available heap — album thumbs are small; more room = fewer re-decodes
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("album_art_cache"))
                    .maxSizeBytes(200L * 1024 * 1024) // 200 MB
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            MediaMetadataRetrieverPool.clear()
        }
    }
}
