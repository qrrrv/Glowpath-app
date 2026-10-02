package com.musicplayer.data.whisper

import android.content.Context

/** Откуда брать распознавание. Все три — это один и тот же OpenAI-совместимый API Whisper. */
enum class WhisperProvider(
    val title: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    GROQ("Groq", "https://api.groq.com/openai/v1", "whisper-large-v3"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "whisper-1"),
    CUSTOM("Свой сервер", "http://192.168.1.10:8000/v1", "Systran/faster-whisper-large-v3")
}

data class WhisperConfig(
    val provider: WhisperProvider,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    /** Код языка (ru, en, ...). Пусто = автоопределение. */
    val language: String
)

/** Хранит настройки Whisper отдельно от остальных настроек приложения. */
object WhisperSettings {

    private const val PREFS = "whisper_prefs"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_LANGUAGE = "language"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedProvider(context: Context): WhisperProvider {
        val raw = prefs(context).getString(KEY_PROVIDER, null)
        return WhisperProvider.values().firstOrNull { it.name == raw } ?: WhisperProvider.GROQ
    }

    fun load(context: Context, provider: WhisperProvider = selectedProvider(context)): WhisperConfig {
        val p = prefs(context)
        return WhisperConfig(
            provider = provider,
            apiKey = p.getString("key_${provider.name}", "").orEmpty(),
            baseUrl = p.getString("url_${provider.name}", null)
                ?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            model = p.getString("model_${provider.name}", null)
                ?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            language = p.getString(KEY_LANGUAGE, "").orEmpty()
        )
    }

    fun save(context: Context, cfg: WhisperConfig) {
        prefs(context).edit()
            .putString(KEY_PROVIDER, cfg.provider.name)
            .putString(KEY_LANGUAGE, cfg.language)
            .putString("key_${cfg.provider.name}", cfg.apiKey)
            .putString("url_${cfg.provider.name}", cfg.baseUrl)
            .putString("model_${cfg.provider.name}", cfg.model)
            .apply()
    }
}
