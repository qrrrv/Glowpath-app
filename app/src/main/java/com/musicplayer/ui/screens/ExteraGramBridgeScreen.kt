package com.musicplayer.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.bridge.ExteraGramBridgeStateStore
import com.musicplayer.ui.theme.LocalAppColors
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExteraGramBridgeScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSearch: (String) -> Unit,
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val snapshot = remember(currentSong, isPlaying, songs.size) {
        ExteraGramBridgeStateStore.loadSnapshot(context)
    }
    val currentTrackCard = remember(snapshot.title, snapshot.artist, snapshot.album, snapshot.isPlaying) {
        if (!snapshot.hasSong) {
            ""
        } else {
            buildString {
                append("Сейчас слушаю: ")
                append(snapshot.artist.ifBlank { "Неизвестный артист" })
                append(" — ")
                append(snapshot.title.ifBlank { "Без названия" })
                if (snapshot.album.isNotBlank()) {
                    append(" | Альбом: ")
                    append(snapshot.album)
                }
                append(" | ")
                append(if (snapshot.isPlaying) "играет" else "на паузе")
            }
        }
    }

    var query by remember(currentSong?.title, currentSong?.artist) {
        mutableStateOf(
            listOfNotNull(currentSong?.artist?.takeIf { it.isNotBlank() }, currentSong?.title?.takeIf { it.isNotBlank() })
                .joinToString(" ")
        )
    }

    fun shareCurrentTrack() {
        if (currentTrackCard.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentTrackCard)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить в ExteraGram / Telegram"))
    }

    fun copyCurrentTrack() {
        if (currentTrackCard.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("current_track", currentTrackCard))
        android.widget.Toast.makeText(context, "Карточка трека скопирована", android.widget.Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = c.bgDeep,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(c.bgSurface, c.bgDeep)))
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        FilledIconButton(
                            onClick = onBack,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = c.bgCard,
                                contentColor = c.textPrimary,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        Brush.linearGradient(listOf(c.accent, c.accentVar)),
                                        RoundedCornerShape(12.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Cable, contentDescription = null, tint = c.bgDeep)
                            }
                            Column {
                                Text(
                                    text = "ExteraGram Bridge",
                                    color = c.textPrimary,
                                    fontFamily = font,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 21.sp,
                                )
                                Text(
                                    text = "Связка плеера с Telegram-плагином",
                                    color = c.textSecondary,
                                    fontFamily = font,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.bgCard),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (currentSong != null) "Сейчас играет" else "Плеер готов к мосту",
                                color = c.textSecondary,
                                fontFamily = font,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = currentSong?.title ?: "Можно управлять из ExteraGram",
                                color = c.textPrimary,
                                fontFamily = font,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = currentSong?.artist?.ifBlank { "Неизвестный" } ?: "Импорт, поиск, play/pause, next/prev",
                                color = c.textSecondary,
                                fontFamily = font,
                                fontSize = 13.sp,
                            )
                        }

                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (isPlaying) "PLAYING" else "READY",
                                    fontFamily = font,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isPlaying) c.accent.copy(alpha = 0.18f) else c.bgSurface,
                                labelColor = if (isPlaying) c.accent else c.textSecondary,
                            ),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledIconButton(
                            onClick = { viewModel.playPrevious() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = c.bgSurface,
                                contentColor = c.textPrimary,
                            ),
                        ) {
                            Icon(Icons.Rounded.FastRewind, contentDescription = "Назад")
                        }
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = c.accent,
                                contentColor = c.bgDeep,
                            ),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                                contentDescription = "Play / Pause",
                            )
                        }
                        FilledIconButton(
                            onClick = { viewModel.playNext() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = c.bgSurface,
                                contentColor = c.textPrimary,
                            ),
                        ) {
                            Icon(Icons.Rounded.FastForward, contentDescription = "Вперёд")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onOpenPlayer) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = c.accent)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Плеер", color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (currentTrackCard.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = ::shareCurrentTrack) {
                                Icon(Icons.Rounded.Share, contentDescription = null, tint = c.accent)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Отправить в ExteraGram", color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = ::copyCurrentTrack) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = c.textPrimary)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Скопировать карточку", color = c.textPrimary, fontFamily = font)
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = c.bgCard),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Поиск из ExteraGram",
                        color = c.textPrimary,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "Плагин может передавать сюда текст из сообщений, ссылки и запросы на поиск.",
                        color = c.textSecondary,
                        fontFamily = font,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        label = { Text("Запрос", fontFamily = font) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                    )
                    TextButton(onClick = { onOpenSearch(query) }, enabled = query.isNotBlank()) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = c.accent)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Открыть онлайн поиск", color = c.accent, fontFamily = font, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = c.bgCard),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Что умеет bridge",
                        color = c.textPrimary,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    BridgeBullet(Icons.Rounded.Headphones, "Play / pause, next / prev прямо из ExteraGram", c.textPrimary)
                    BridgeBullet(Icons.Rounded.Search, "Поиск трека по тексту сообщения", c.textPrimary)
                    BridgeBullet(Icons.Rounded.Album, "Импорт аудио, Telegram batch и запуск очереди", c.textPrimary)
                    BridgeBullet(Icons.Rounded.LibraryMusic, "Статус и часть библиотеки доступны плагину через provider", c.textPrimary)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = c.bgCard),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Диагностика",
                        color = c.textPrimary,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    DiagnosticRow("Authority", "content://com.musicplayer.bridge")
                    DiagnosticRow("Песен в библиотеке", songs.size.toString())
                    DiagnosticRow("Bridge queue", snapshot.bridgeQueueSize.toString())
                    DiagnosticRow("Последняя bridge-команда", snapshot.lastCommandLabel.ifBlank { "ещё не было" })
                    DiagnosticRow("Последний поиск", snapshot.lastQuery.ifBlank { "нет" })
                    DiagnosticRow("Последний batch", snapshot.lastBatchSummary.ifBlank { "нет" })
                }
            }
        }
    }
}

@Composable
private fun BridgeBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.bgSurface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.accent)
        }
        Text(
            text = text,
            color = color,
            fontFamily = font,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
        Text(
            text = value,
            color = c.textPrimary,
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}
