package com.musicplayer

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.google.android.material.color.DynamicColors
import com.musicplayer.utils.MediaMetadataRetrieverPool
import com.musicplayer.utils.SongCoverFetcher
import kotlinx.coroutines.Dispatchers
import kotlin.system.exitProcess

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
        // В процессе ":crash" живёт только экран с текстом ошибки — остальное не запускаем
        if (isCrashProcess()) return
        installCrashReporter()
        DynamicColors.applyToActivitiesIfAvailable(this)
        try {
            com.musicplayer.server.NowPlayingHttpServer.start(this)
        } catch (e: Exception) {
            android.util.Log.e("MusicApplication", "Failed to start NowPlaying HTTP server", e)
        }
    }

    private fun isCrashProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            try { java.io.File("/proc/self/cmdline").readText().trim('\u0000') } catch (e: Exception) { "" }
        }
        return name.endsWith(":crash")
    }

    /** При вылете показывает текст ошибки на экране, чтобы его можно было скопировать. */
    private fun installCrashReporter() {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                android.util.Log.e("Crash", "Uncaught exception in ${thread.name}", error)
                val report = buildString {
                    append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                    append("${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("Thread: ${thread.name}\n\n")
                    append(android.util.Log.getStackTraceString(error))
                }.take(60_000)
                startActivity(
                    Intent(this, CrashReportActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(CrashReportActivity.EXTRA_TRACE, report)
                )
            } catch (_: Throwable) {
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .dispatcher(Dispatchers.Default)
            // Finds covers MediaStore doesn't have: embedded picture -> Deezer (cached)
            .components { add(SongCoverFetcher.Factory()) }
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
