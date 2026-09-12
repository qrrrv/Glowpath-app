package com.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.android.material.color.DynamicColors
import com.musicplayer.utils.MediaMetadataRetrieverPool
import kotlinx.coroutines.Dispatchers

/**
 * Application class that sets up a singleton Coil ImageLoader
 * with optimised memory (20 % of heap) and disk (100 MB) caches.
 * This is the single biggest scroll-lag fix: every SongRow shares one
 * loader instead of re-creating loaders per composable, and album art
 * that was already decoded never hits disk again.
 */
class MusicApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        // Локальный HTTP-сервер для Hikka / внешних клиентов (.now)
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
            // In-memory LRU cache – 25% of app memory (was 20%).
            // Extra headroom prevents eviction during fast scroll through large libraries.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Disk LRU cache – 150 MB (was 100 MB).
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("album_art_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Release pooled MediaMetadataRetrievers when the system is low on memory
        if (level >= TRIM_MEMORY_MODERATE) {
            MediaMetadataRetrieverPool.clear()
        }
    }
}
