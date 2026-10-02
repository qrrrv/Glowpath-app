package com.musicplayer.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.data.Song
import com.musicplayer.data.lyrics.SyncedLine
import com.musicplayer.data.whisper.WhisperConfig
import com.musicplayer.data.whisper.WhisperProvider
import com.musicplayer.data.whisper.WhisperSettings
import com.musicplayer.data.whisper.WhisperTranscriber
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.ui.theme.textDisabled
import com.musicplayer.ui.theme.textSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class WhisperStage { SETTINGS, RUNNING, ERROR }

/**
 * Диалог «Распознать текст на слух»: настройки Whisper → прогресс → результат в редактор.
 * onResult получает готовые строки с таймингами слов и флаг, были ли тайминги слов настоящими.
 */
@Composable
fun WhisperTranscribeDialog(
    song: Song,
    hasExistingLines: Boolean,
    onResult: (List<SyncedLine>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val scope = rememberCoroutineScope()

    val initial = remember { WhisperSettings.load(context) }
    var provider by remember { mutableStateOf(initial.provider) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var language by remember { mutableStateOf(initial.language) }
    var showKey by remember { mutableStateOf(false) }

    var stage by remember { mutableStateOf(WhisperStage.SETTINGS) }
    var status by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            WhisperTranscriber.abort()
        }
    }

    fun selectProvider(p: WhisperProvider) {
        if (p == provider) return
        // Сохраняем введённое для текущего провайдера, чтобы не потерять.
        WhisperSettings.save(context, WhisperConfig(provider, apiKey.trim(), baseUrl.trim(), model.trim(), language.trim()))
        val cfg = WhisperSettings.load(context, p)
        provider = p
        apiKey = cfg.apiKey
        baseUrl = cfg.baseUrl
        model = cfg.model
    }

    fun start() {
        val cfg = WhisperConfig(
            provider = provider,
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trim(),
            model = model.trim().ifBlank { provider.defaultModel },
            language = language.trim()
        )
        WhisperSettings.save(context, cfg)
        errorText = ""
        status = "Подготовка…"
        stage = WhisperStage.RUNNING
        job = scope.launch {
            try {
                val result = WhisperTranscriber.transcribe(context, song, cfg) { s -> status = s }
                onResult(result.lines, result.wordLevel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isActive) return@launch
                errorText = e.message ?: "Неизвестная ошибка"
                stage = WhisperStage.ERROR
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (stage != WhisperStage.RUNNING) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = stage != WhisperStage.RUNNING,
            dismissOnClickOutside = stage == WhisperStage.SETTINGS
        ),
        title = {
            Text(
                when (stage) {
                    WhisperStage.SETTINGS -> "Распознать текст (Whisper)"
                    WhisperStage.RUNNING -> "Распознаю…"
                    WhisperStage.ERROR -> "Не получилось"
                }
            )
        },
        text = {
            when (stage) {
                WhisperStage.SETTINGS -> Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Трек отправляется в Whisper, ответ собирается в строки с таймингами слов. " +
                            "Результат появится в редакторе — проверь и нажми «Сохранить».",
                        color = c.textSecondary, fontFamily = font, fontSize = 13.sp
                    )
                    if (hasExistingLines) {
                        Text(
                            "Текущие строки в редакторе заменятся (пока не сохранишь, можно выйти без сохранения).",
                            color = c.textDisabled, fontFamily = font, fontSize = 12.sp
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WhisperProvider.values().forEach { p ->
                            FilterChip(
                                selected = provider == p,
                                onClick = { selectProvider(p) },
                                label = { Text(p.title) }
                            )
                        }
                    }

                    Text(
                        when (provider) {
                            WhisperProvider.GROQ -> "Бесплатный ключ: console.groq.com/keys"
                            WhisperProvider.OPENAI -> "Ключ: platform.openai.com/api-keys (платно)"
                            WhisperProvider.CUSTOM -> "Любой OpenAI-совместимый сервер, например faster-whisper-server на твоём ПК. " +
                                "Адрес вместе с /v1"
                        },
                        color = c.textDisabled, fontFamily = font, fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text(if (provider == WhisperProvider.CUSTOM) "API-ключ (необязательно)" else "API-ключ")
                        },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "Скрыть" else "Показать", fontSize = 11.sp)
                            }
                        }
                    )

                    if (provider == WhisperProvider.CUSTOM) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Адрес сервера") }
                        )
                    }

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Модель") }
                    )

                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Язык: ru, en… (пусто = авто)") }
                    )
                }

                WhisperStage.RUNNING -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(status, color = c.textSecondary, fontFamily = font, fontSize = 14.sp)
                }

                WhisperStage.ERROR -> Text(
                    errorText,
                    color = c.textSecondary, fontFamily = font, fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            when (stage) {
                WhisperStage.SETTINGS -> Button(
                    onClick = { start() },
                    enabled = provider == WhisperProvider.CUSTOM || apiKey.isNotBlank()
                ) { Text("Начать") }

                WhisperStage.RUNNING -> TextButton(onClick = {
                    job?.cancel()
                    WhisperTranscriber.abort()
                    stage = WhisperStage.SETTINGS
                }) { Text("Отмена") }

                WhisperStage.ERROR -> Button(onClick = { stage = WhisperStage.SETTINGS }) { Text("Назад") }
            }
        },
        dismissButton = {
            if (stage != WhisperStage.RUNNING) {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}
