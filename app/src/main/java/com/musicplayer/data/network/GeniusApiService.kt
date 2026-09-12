package com.musicplayer.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Genius API client для получения текстов песен.
 * Используется как fallback когда LRCLIB не находит текст.
 */
object GeniusApiService {

    private const val TAG = "GeniusApiService"
    private const val BASE_URL = "https://api.genius.com"

    // Public access token (ограниченный, только для поиска)
    // В продакшене лучше хранить в BuildConfig
    private const val ACCESS_TOKEN = "your_genius_api_token_here"

    /**
     * Поиск песни на Genius
     * @return URL страницы с текстом или null
     */
    suspend fun searchSong(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = URL("$BASE_URL/search?q=$query")

            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $ACCESS_TOKEN")
                setRequestProperty("User-Agent", "MusicPlayer/1.0")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "Genius API returned $responseCode")
                return@withContext null
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            val json = JSONObject(response)
            val hits = json.getJSONObject("response").getJSONArray("hits")

            if (hits.length() == 0) {
                return@withContext null
            }

            // Берем первый результат
            val firstHit = hits.getJSONObject(0).getJSONObject("result")
            val songUrl = firstHit.getString("url")

            Log.d(TAG, "Found Genius URL: $songUrl")
            songUrl

        } catch (e: Exception) {
            Log.e(TAG, "Error searching Genius: ${e.message}")
            null
        }
    }

    /**
     * Извлечь текст песни со страницы Genius (упрощенный парсинг HTML)
     * Примечание: для надежного парсинга лучше использовать jsoup (уже в зависимостях)
     */
    suspend fun fetchLyrics(songUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            // Используем jsoup для парсинга
            val doc = org.jsoup.Jsoup.connect(songUrl)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get()

            // Genius хранит тексты в div с data-lyrics-container
            val lyricsContainers = doc.select("div[data-lyrics-container='true']")

            if (lyricsContainers.isEmpty()) {
                Log.w(TAG, "No lyrics found on page")
                return@withContext null
            }

            val lyrics = StringBuilder()
            lyricsContainers.forEach { container ->
                // Заменяем <br> на переводы строк
                container.select("br").before("\\n")
                lyrics.append(container.text().replace("\\n", "\n"))
                lyrics.append("\n\n")
            }

            val result = lyrics.toString().trim()
            if (result.isBlank()) {
                return@withContext null
            }

            Log.d(TAG, "Successfully extracted lyrics (${result.length} chars)")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics: ${e.message}")
            null
        }
    }
}
