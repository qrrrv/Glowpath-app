package com.musicplayer.repository.search

import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup

/** Small JSON-over-HTTP helper shared by the catalog providers. */
internal object SearchHttp {

    private const val TAG = "SearchHttp"
    private const val USER_AGENT = "GlowPath/1.0 (Android)"

    fun jsonObject(url: String, timeoutMs: Int = 8_000): JSONObject? {
        return try {
            val body = Jsoup.connect(url)
                .ignoreContentType(true)
                .followRedirects(true)
                .userAgent(USER_AGENT)
                .header("Accept", "application/json")
                .timeout(timeoutMs)
                .execute()
                .body()
            JSONObject(body)
        } catch (e: Exception) {
            Log.d(TAG, "Request failed: " + url, e)
            null
        }
    }
}
