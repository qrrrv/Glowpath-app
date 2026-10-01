package com.musicplayer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.data.Song
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.utils.TrackFileInfo
import com.musicplayer.utils.TrackFileInfoLoader
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * "Track info": only facts read from the file itself. Rows whose value could not be read are hidden.
 * Tap a row to copy its value.
 */
@Composable
fun TrackInfoDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    var info by remember(song.id) { mutableStateOf<TrackFileInfo?>(null) }
    LaunchedEffect(song.id, song.uri) { info = TrackFileInfoLoader.load(context, song) }

    val data = info
    val sections = data?.let { buildSections(it) }.orEmpty()
    val fullPath = data?.let { d ->
        if (d.folder != null && d.fileName != null) d.folder.trimEnd('/') + "/" + d.fileName else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Info, null, tint = c.primary) },
        title = {
            Text(
                "Информация о треке", fontFamily = font, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            when {
                data == null -> Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data.isOnline -> Text(
                    "Это онлайн-трек: он воспроизводится из интернета, а не из файла на телефоне, " +
                            "поэтому сведений о файле нет.",
                    fontFamily = font, fontSize = 14.sp, lineHeight = 20.sp, color = c.onSurfaceVariant
                )
                sections.isEmpty() -> Text(
                    "Не удалось прочитать сведения о файле.",
                    fontFamily = font, fontSize = 14.sp, color = c.onSurfaceVariant
                )
                else -> Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    sections.forEach { (title, rows) -> InfoSection(title, rows, context) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Закрыть", fontFamily = font, fontWeight = FontWeight.SemiBold) } },
        dismissButton = fullPath?.let { p ->
            { TextButton(onClick = { copyToClipboard(context, p) }) { Text("Копировать путь", fontFamily = font) } }
        }
    )
}

@Composable
private fun InfoSection(title: String, rows: List<Pair<String, String>>, context: Context) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title, color = c.primary, fontFamily = font, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 4.dp)
        )
        Surface(shape = RoundedCornerShape(20.dp), color = c.surfaceContainerHighest) {
            Column {
                rows.forEachIndexed { index, (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { copyToClipboard(context, value) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            label, color = c.onSurfaceVariant, fontFamily = font, fontSize = 13.sp,
                            lineHeight = 18.sp, modifier = Modifier.weight(0.4f)
                        )
                        Text(
                            value, color = c.onSurface, fontFamily = font, fontSize = 13.sp,
                            lineHeight = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f)
                        )
                    }
                    if (index != rows.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

private fun buildSections(d: TrackFileInfo): List<Pair<String, List<Pair<String, String>>>> {
    val file = buildList {
        d.fileName?.let { add("Имя файла" to it) }
        d.folder?.let { add("Папка" to it) }
        formatName(d)?.let { add("Формат" to it) }
        d.sizeBytes?.let { add("Размер" to formatSize(it)) }
        d.addedAtMs?.let {
            add("Добавлен" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)))
        }
    }
    val audio = buildList {
        d.durationMs?.let { add("Длительность" to formatDuration(it)) }
        d.bitrateKbps?.let { add("Битрейт (средний)" to "$it кбит/с") }
        d.sampleRateHz?.let { add("Частота дискретизации" to formatKhz(it)) }
        d.bitsPerSample?.let { add("Разрядность" to "$it бит") }
        d.channels?.let { add("Каналы" to when (it) { 1 -> "Моно"; 2 -> "Стерео"; else -> "$it каналов" }) }
        d.lossless?.let { add("Сжатие" to if (it) "Без потерь качества" else "С потерями качества") }
    }
    val tags = buildList {
        d.tagTitle?.let { add("Название" to it) }
        d.tagArtist?.let { add("Исполнитель" to it) }
        d.tagAlbum?.let { add("Альбом" to it) }
        d.tagAlbumArtist?.let { add("Исполнитель альбома" to it) }
        d.tagGenre?.let { add("Жанр" to it) }
        d.tagYear?.let { add("Год" to it) }
        d.tagTrack?.let { add("Номер трека" to it) }
        d.hasEmbeddedCover?.let { add("Обложка в файле" to if (it) "Есть" else "Нет") }
    }
    return listOf("ФАЙЛ" to file, "ЗВУК" to audio, "ТЕГИ В ФАЙЛЕ" to tags).filter { it.second.isNotEmpty() }
}

private fun formatName(d: TrackFileInfo): String? {
    val ext = d.extension
    val codec = d.codec
    return when {
        ext != null && codec != null && !ext.equals(codec, ignoreCase = true) -> "$ext · $codec"
        ext != null -> ext
        else -> codec
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format(Locale.getDefault(), "%.1f МБ", mb)
    else "${bytes / 1024} КБ"
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

private fun formatKhz(hz: Int): String {
    val khz = hz / 1000.0
    val text = String.format(Locale.getDefault(), "%.1f", khz)
    val sep = if (text.contains(',')) ',' else '.'
    val trimmed = if (text.endsWith("${sep}0")) text.dropLast(2) else text
    return "$trimmed кГц"
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Glowpath", text))
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }
}
