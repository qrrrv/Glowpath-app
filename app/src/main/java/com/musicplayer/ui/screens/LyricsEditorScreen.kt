package com.musicplayer.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.data.Song
import com.musicplayer.data.lyrics.LyricsState
import com.musicplayer.data.lyrics.LyricsUtils
import com.musicplayer.data.lyrics.SyncedLine
import com.musicplayer.data.lyrics.SyncedWord
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.ui.theme.accent
import com.musicplayer.ui.theme.bgCard
import com.musicplayer.ui.theme.bgElevated
import com.musicplayer.ui.theme.textDisabled
import com.musicplayer.ui.theme.textPrimary
import com.musicplayer.ui.theme.textSecondary
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// ─────────────────────────────────────────────────────────────────────────────
//  Редактор текста песни: тайминги строк + тайминги слов (ручная правка).
//  Результат сохраняется как «пользовательский текст» (LyricsRepository.saveCustomLyrics)
//  и имеет приоритет над любыми скачанными текстами.
// ─────────────────────────────────────────────────────────────────────────────

// ── Модель редактора ─────────────────────────────────────────────────────────

private data class EditWord(val text: String, val timeMs: Int)

private data class EditLine(
    val id: Int,
    val timeMs: Int,
    val text: String,
    val words: List<EditWord>?
) {
    val isBlank: Boolean get() = text.isBlank()
}

private fun formatTime(ms: Int): String {
    val t = ms.coerceAtLeast(0)
    return String.format(java.util.Locale.US, "%02d:%02d.%02d", t / 60000, (t % 60000) / 1000, (t % 1000) / 10)
}

/** Принимает "мм:сс.сс", "м:сс" или просто секунды ("75.5"). */
private fun parseTime(raw: String): Int? {
    val s = raw.trim().replace(',', '.')
    if (s.isEmpty()) return null
    return try {
        val parts = s.split(':')
        val ms: Long = when (parts.size) {
            1 -> (parts[0].toDouble() * 1000.0).roundToLong()
            2 -> parts[0].toLong() * 60_000L + (parts[1].toDouble() * 1000.0).roundToLong()
            else -> return null
        }
        if (ms < 0L || ms > Int.MAX_VALUE.toLong()) null else ms.toInt()
    } catch (_: Exception) {
        null
    }
}

/** Делит текст на слова; у всех слов кроме последнего в конце пробел (как ждёт плеер). */
private fun tokenize(text: String): List<String> {
    val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return parts.mapIndexed { i, w -> if (i < parts.lastIndex) "$w " else w }
}

/** Черновая расстановка слов пропорционально их длине — потом правится вручную. */
private fun distributeWords(text: String, startMs: Int, endMs: Int): List<EditWord> {
    val tokens = tokenize(text)
    if (tokens.isEmpty()) return emptyList()
    val weights = tokens.map { it.trim().length.coerceAtLeast(1) }
    val total = weights.sum()
    val natural = total * 90
    val span = min(natural, max(endMs - startMs, 0)).coerceAtLeast(300)
    var acc = 0
    return tokens.mapIndexed { i, token ->
        val t = startMs + (span.toLong() * acc / total).toInt()
        acc += weights[i]
        EditWord(token, t)
    }
}

private fun EditLine.withLineTime(newTime: Int): EditLine {
    val nt = newTime.coerceAtLeast(0)
    val delta = nt - timeMs
    return copy(
        timeMs = nt,
        words = words?.map { it.copy(timeMs = (it.timeMs + delta).coerceAtLeast(0)) }
    )
}

/** Меняет время одного слова и сохраняет порядок: предыдущие не позже, следующие не раньше. */
private fun EditLine.withWordTime(index: Int, newTime: Int): EditLine {
    val ws = words ?: return this
    val nt = newTime.coerceAtLeast(0)
    val out = ws.mapIndexed { i, w ->
        when {
            i == index -> w.copy(timeMs = nt)
            i < index -> w.copy(timeMs = min(w.timeMs, nt))
            else -> w.copy(timeMs = max(w.timeMs, nt))
        }
    }
    return copy(words = out)
}

/** Новый текст строки. Если число слов изменилось — тайминги слов сбрасываются. */
private fun EditLine.withText(newText: String): EditLine {
    val clean = newText.replace('\n', ' ').trim()
    val old = words ?: return copy(text = clean)
    val tokens = tokenize(clean)
    return if (tokens.isNotEmpty() && tokens.size == old.size) {
        copy(text = clean, words = tokens.mapIndexed { i, t -> EditWord(t, old[i].timeMs) })
    } else {
        copy(text = clean, words = null)
    }
}

private fun List<SyncedLine>.toEditLines(): List<EditLine> {
    var nextId = 1
    return sortedBy { it.time }.map { src ->
        val merged = ArrayList<EditWord>()
        src.words.orEmpty().forEach { w ->
            if (w.word.isBlank()) {
                // Пробел между словами — приклеиваем к предыдущему слову.
                val last = merged.lastOrNull()
                if (last != null && !last.text.last().isWhitespace()) {
                    merged[merged.lastIndex] = last.copy(text = last.text + " ")
                }
            } else {
                merged.add(EditWord(w.word, w.time))
            }
        }
        val words = merged.takeIf { it.isNotEmpty() }
        val text = if (words != null) {
            words.joinToString("") { it.text }.replace(Regex("\\s+"), " ").trim()
        } else {
            src.line.replace('\n', ' ').trim()
        }
        EditLine(nextId++, src.time, text, words)
    }
}

private fun plainToEditLines(rows: List<String>, durationMs: Long): List<EditLine> {
    val clean = rows.map { it.trim() }.filter { it.isNotEmpty() }
    if (clean.isEmpty()) return emptyList()
    val dur = if (durationMs > 0L) durationMs.toInt() else clean.size * 4000
    return clean.mapIndexed { i, t ->
        EditLine(i + 1, (dur.toLong() * i / clean.size).toInt(), t, null)
    }
}

private fun List<EditLine>.toSyncedLines(): List<SyncedLine> =
    sortedBy { it.timeMs }.map { l ->
        val ws = l.words
        if (ws != null && ws.isNotEmpty() && l.text.isNotBlank()) {
            var prev = 0
            val fixed = ws.map { w ->
                prev = max(prev, w.timeMs)
                SyncedWord(prev, w.text)
            }
            val lineTime = min(l.timeMs, fixed.first().time)
            SyncedLine(lineTime, fixed.joinToString("") { it.word }.trim(), fixed)
        } else {
            SyncedLine(l.timeMs, l.text.trim(), null)
        }
    }

// ── Экран ────────────────────────────────────────────────────────────────────

@Composable
fun LyricsEditorScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val song by viewModel.currentSong.collectAsState()
    val current = song
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    // При смене трека редактор начинается заново для нового трека.
    key(current.id) {
        LyricsEditorContent(song = current, viewModel = viewModel, onBack = onBack)
    }
}

@Composable
private fun LyricsEditorContent(
    song: Song,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val context = LocalContext.current

    val lyricsState by viewModel.lyricsState.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()

    var lines by remember { mutableStateOf<List<EditLine>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var hasCustom by remember { mutableStateOf(viewModel.hasCustomLyrics(song.id)) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var nextId by remember { mutableIntStateOf(1_000_000) }
    var reactionOffsetMs by remember { mutableIntStateOf(120) }
    var importText by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    LaunchedEffect(song.id) {
        if (viewModel.lyricsState.value is LyricsState.Idle) viewModel.loadLyricsForSong(song)
    }

    // Один раз подтягиваем текущий текст трека в редактор (потом не перезаписываем правки).
    LaunchedEffect(lyricsState) {
        if (loaded) return@LaunchedEffect
        val st = lyricsState
        if (st is LyricsState.Found) {
            val synced = st.lyrics.synced
            lines = if (!synced.isNullOrEmpty()) {
                synced.toEditLines()
            } else {
                plainToEditLines(st.lyrics.plain.orEmpty(), song.duration)
            }
            loaded = true
        }
    }

    fun updateLine(id: Int, transform: (EditLine) -> EditLine) {
        lines = lines.map { if (it.id == id) transform(it) else it }.sortedBy { it.timeMs }
        dirty = true
    }

    /** Текущее время для «поставить сейчас». Во время игры вычитаем поправку на реакцию. */
    fun stampNow(): Int {
        val raw = viewModel.getExactPositionMs() - (if (isPlaying) reactionOffsetMs else 0)
        return raw.toInt().coerceAtLeast(0)
    }

    fun saveLyrics(onSaved: () -> Unit = {}) {
        viewModel.saveCustomLyrics(song, lines.toSyncedLines()) { ok ->
            if (ok) {
                dirty = false
                hasCustom = true
                Toast.makeText(context, "Текст сохранён", Toast.LENGTH_SHORT).show()
                onSaved()
            } else {
                Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleBack() {
        when {
            showImport && lines.isNotEmpty() -> showImport = false
            selectedId != null -> selectedId = null
            dirty -> showExitDialog = true
            else -> onBack()
        }
    }

    BackHandler(enabled = true) { handleBack() }

    val selectedIndex = lines.indexOfFirst { it.id == selectedId }
    val selectedLine = lines.getOrNull(selectedIndex)
    val activeId = lines.lastOrNull { it.timeMs <= position }?.id

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCard)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        EditorTopBar(
            title = song.displayTitle(),
            canSave = dirty && lines.isNotEmpty(),
            showReset = hasCustom,
            onBack = { handleBack() },
            onReset = { showResetDialog = true },
            onSave = { saveLyrics() },
            c = c, font = font
        )

        TransportBar(
            isPlaying = isPlaying,
            positionMs = position,
            durationMs = song.duration,
            onToggle = { viewModel.togglePlayPause() },
            onSeekBy = { d ->
                val target = viewModel.getExactPositionMs() + d
                viewModel.seekTo(target.coerceIn(0L, max(song.duration, 1L)))
            },
            c = c, font = font
        )

        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                showImport -> ImportView(
                    text = importText,
                    onTextChange = { importText = it },
                    onCreate = {
                        val created = importToEditLines(importText, song.duration)
                        if (created.isNotEmpty()) {
                            lines = created
                            loaded = true
                            dirty = true
                            selectedId = null
                            showImport = false
                        }
                    },
                    onCancel = if (lines.isNotEmpty()) ({ showImport = false }) else null,
                    hasExisting = lines.isNotEmpty(),
                    c = c, font = font
                )

                selectedLine != null -> LineDetail(
                    line = selectedLine,
                    index = selectedIndex,
                    total = lines.size,
                    nextLineTime = lines.getOrNull(selectedIndex + 1)?.timeMs
                        ?: max(song.duration.toInt(), selectedLine.timeMs + 4000),
                    isPlaying = isPlaying,
                    positionMs = position,
                    reactionOffsetMs = reactionOffsetMs,
                    onReactionOffsetChange = { reactionOffsetMs = it.coerceIn(0, 600) },
                    onChange = { transform -> updateLine(selectedLine.id, transform) },
                    onPrev = if (selectedIndex > 0) ({ selectedId = lines[selectedIndex - 1].id }) else null,
                    onNext = if (selectedIndex < lines.lastIndex) ({ selectedId = lines[selectedIndex + 1].id }) else null,
                    onDelete = {
                        val removedId = selectedLine.id
                        lines = lines.filter { it.id != removedId }
                        selectedId = null
                        dirty = true
                    },
                    onSeekPlay = { ms ->
                        viewModel.seekTo(ms.toLong())
                        if (!isPlaying) viewModel.togglePlayPause()
                    },
                    stampNow = { stampNow() },
                    c = c, font = font
                )

                lines.isNotEmpty() -> LineList(
                    lines = lines,
                    activeId = activeId,
                    onOpen = { selectedId = it.id },
                    onAdd = {
                        val newLine = EditLine(nextId++, stampNow(), "Новая строка", null)
                        lines = (lines + newLine).sortedBy { it.timeMs }
                        dirty = true
                        selectedId = newLine.id
                    },
                    onImport = { showImport = true },
                    c = c, font = font
                )

                !loaded && lyricsState !is LyricsState.NotFound && lyricsState !is LyricsState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.accent)
                    }
                }

                else -> ImportView(
                    text = importText,
                    onTextChange = { importText = it },
                    onCreate = {
                        val created = importToEditLines(importText, song.duration)
                        if (created.isNotEmpty()) {
                            lines = created
                            loaded = true
                            dirty = true
                        }
                    },
                    onCancel = null,
                    hasExisting = false,
                    c = c, font = font
                )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Сохранить изменения?") },
            text = { Text("Вы изменили текст или тайминги. Несохранённые правки пропадут.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    saveLyrics(onSaved = onBack)
                }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExitDialog = false; onBack() }) { Text("Не сохранять") }
                    TextButton(onClick = { showExitDialog = false }) { Text("Отмена") }
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить свои правки?") },
            text = { Text("Ваш текст с таймингами будет удалён, а оригинальный текст загрузится заново.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetCustomLyrics(song)
                    hasCustom = false
                    dirty = false
                    loaded = false
                    selectedId = null
                    lines = emptyList()
                }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Отмена") }
            }
        )
    }
}

// ── Верхняя панель и плеер ───────────────────────────────────────────────────

@Composable
private fun EditorTopBar(
    title: String,
    canSave: Boolean,
    showReset: Boolean,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    c: ColorScheme,
    font: FontFamily
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = onBack,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = c.bgElevated.copy(0.85f), contentColor = c.textPrimary
            )
        ) { Icon(Icons.Rounded.KeyboardArrowDown, "Назад", Modifier.size(26.dp)) }

        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                "Редактор текста", color = c.textPrimary, fontFamily = font,
                fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1
            )
            Text(
                title, color = c.textSecondary, fontFamily = font,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        if (showReset) {
            IconButton(onClick = onReset) {
                Icon(Icons.Rounded.RestartAlt, "Сбросить правки", tint = c.textSecondary)
            }
        }
        FilledTonalButton(onClick = onSave, enabled = canSave) { Text("Сохранить") }
    }
}

@Composable
private fun TransportBar(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onToggle: () -> Unit,
    onSeekBy: (Long) -> Unit,
    c: ColorScheme,
    font: FontFamily
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgElevated)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledIconButton(
            onClick = onToggle,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = c.accent, contentColor = c.onPrimary
            )
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isPlaying) "Пауза" else "Играть"
            )
        }
        Text(
            formatTime(positionMs.toInt()) + " / " + formatTime(durationMs.toInt()).substringBefore('.'),
            color = c.textPrimary, fontFamily = FontFamily.Monospace,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        NudgeChip("−5с", { onSeekBy(-5000L) }, c, font)
        NudgeChip("+5с", { onSeekBy(5000L) }, c, font)
    }
}

// ── Список строк ─────────────────────────────────────────────────────────────

@Composable
private fun LineList(
    lines: List<EditLine>,
    activeId: Int?,
    onOpen: (EditLine) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    c: ColorScheme,
    font: FontFamily
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "hint") {
            Text(
                "Нажмите на строку, чтобы поменять время и расставить тайминги слов.",
                color = c.textDisabled, fontFamily = font, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        items(lines, key = { it.id }) { line ->
            val isActive = line.id == activeId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isActive) c.accent.copy(alpha = 0.16f) else c.bgElevated.copy(alpha = 0.6f))
                    .clickable { onOpen(line) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    formatTime(line.timeMs),
                    color = if (isActive) c.accent else c.textSecondary,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (line.isBlank) "♪ пауза" else line.text,
                    color = if (line.isBlank) c.textDisabled else c.textPrimary,
                    fontFamily = font, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (line.words != null) {
                    Text(
                        "слова", color = c.accent, fontFamily = font, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.accent.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
        item(key = "add") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.bgElevated.copy(alpha = 0.4f))
                    .clickable { onAdd() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(18.dp))
                    Text("Добавить строку в текущей позиции", color = c.accent, fontFamily = font, fontSize = 13.sp)
                }
            }
        }
        item(key = "import") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.bgElevated.copy(alpha = 0.4f))
                    .clickable { onImport() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Вставить свой текст (LRC с таймингами слов)", color = c.accent, fontFamily = font, fontSize = 13.sp)
            }
        }
    }
}

// ── Вставка своего текста (обычный или LRC с таймингами слов) ───────────────

/** Если в тексте есть LRC-теги времени — берём их (строки и слова), иначе делим на строки равномерно. */
private fun importToEditLines(raw: String, durationMs: Long): List<EditLine> {
    val parsed = LyricsUtils.parseLyrics(raw)
    val synced = parsed.synced
    return if (!synced.isNullOrEmpty()) {
        synced.toEditLines()
    } else {
        plainToEditLines(raw.lines(), durationMs)
    }
}

@Composable
private fun ImportView(
    text: String,
    onTextChange: (String) -> Unit,
    onCreate: () -> Unit,
    onCancel: (() -> Unit)?,
    hasExisting: Boolean,
    c: ColorScheme,
    font: FontFamily
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (hasExisting) "Вставить свой текст" else "Текст не найден",
            color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 18.sp
        )
        Text(
            "Можно вставить обычный текст (по строке на строку — время распределится равномерно, " +
                "потом подправите) или готовый LRC с таймингами.",
            color = c.textSecondary, fontFamily = font, fontSize = 13.sp
        )
        Text(
            "Тайминги слов (подсветка по словам):\n" +
                "[01:05.20]<01:05.20>Села <01:05.60>в <01:05.90>кресло <01:06.40>депутатши\n\n" +
                "Только строки:\n[01:05.20]Села в кресло депутатши\n\n" +
                "Минуты и секунды — по две цифры, сотые — 2–3 цифры. Пробел ставится после слова, перед следующим тегом.",
            color = c.textDisabled, fontFamily = FontFamily.Monospace, fontSize = 11.sp
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            placeholder = { Text("Вставьте сюда текст песни…") }
        )
        if (hasExisting) {
            Text(
                "Текущие строки заменятся. Пока вы не нажали «Сохранить», это можно отменить.",
                color = c.textDisabled, fontFamily = font, fontSize = 11.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate, enabled = text.isNotBlank()) { Text("Создать строки") }
            if (onCancel != null) TextButton(onClick = onCancel) { Text("Отмена") }
        }
    }
}

// ── Редактирование одной строки ──────────────────────────────────────────────

@Composable
private fun LineDetail(
    line: EditLine,
    index: Int,
    total: Int,
    nextLineTime: Int,
    isPlaying: Boolean,
    positionMs: Long,
    reactionOffsetMs: Int,
    onReactionOffsetChange: (Int) -> Unit,
    onChange: ((EditLine) -> EditLine) -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onDelete: () -> Unit,
    onSeekPlay: (Int) -> Unit,
    stampNow: () -> Int,
    c: ColorScheme,
    font: FontFamily
) {
    var showTextDialog by remember { mutableStateOf(false) }
    var showLineTimeDialog by remember { mutableStateOf(false) }
    var wordDialogIndex by remember { mutableIntStateOf(-1) }
    var selectedWord by remember(line.id) { mutableIntStateOf(-1) }
    var recIndex by remember(line.id) { mutableIntStateOf(-1) }

    val words = line.words

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Заголовок: навигация по строкам
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Строка ${index + 1} из $total",
                color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            IconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null) {
                Icon(Icons.Rounded.SkipPrevious, "Предыдущая строка")
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(Icons.Rounded.SkipNext, "Следующая строка")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, "Удалить строку", tint = c.error)
            }
        }

        // Текст
        DetailCard(c) {
            SectionLabel("Текст строки", c, font)
            Text(
                if (line.isBlank) "(пустая строка — пауза в песне)" else line.text,
                color = if (line.isBlank) c.textDisabled else c.textPrimary,
                fontFamily = font, fontSize = 16.sp
            )
            Row { NudgeChip("Изменить текст", { showTextDialog = true }, c, font, accent = true) }
        }

        // Время начала строки
        DetailCard(c) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Начало строки", c, font, Modifier.weight(1f))
                TimeChip(formatTime(line.timeMs), { showLineTimeDialog = true }, c)
            }
            NudgeRow(
                onNudge = { d -> onChange { it.withLineTime(it.timeMs + d) } },
                onNow = {
                    val t = stampNow()
                    onChange { it.withLineTime(t) }
                },
                onSeek = { onSeekPlay(max(line.timeMs - 500, 0)) },
                c = c, font = font
            )
            Text(
                "Шаги в миллисекундах. Если у строки есть тайминги слов — они сдвигаются вместе с ней.",
                color = c.textDisabled, fontFamily = font, fontSize = 11.sp
            )
        }

        // Слова
        DetailCard(c) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Подсветка по словам", c, font)
                    Text(
                        "Слова загораются плавно, как в караоке",
                        color = c.textDisabled, fontFamily = font, fontSize = 11.sp
                    )
                }
                Switch(
                    checked = words != null,
                    enabled = !line.isBlank,
                    onCheckedChange = { on ->
                        recIndex = -1
                        selectedWord = -1
                        if (on) {
                            onChange { l ->
                                val dist = distributeWords(l.text, l.timeMs, nextLineTime)
                                l.copy(words = if (dist.isNotEmpty()) dist else null)
                            }
                        } else {
                            onChange { it.copy(words = null) }
                        }
                    }
                )
            }

            if (words != null) {
                // Запись по тапам
                if (recIndex in words.indices) {
                    val w = words[recIndex]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.accent)
                            .clickable {
                                val t = stampNow()
                                val i = recIndex
                                onChange { it.withWordTime(i, t) }
                                recIndex = if (i + 1 < words.size) i + 1 else -1
                            }
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Нажмите, когда слово начинает звучать",
                            color = c.onPrimary.copy(alpha = 0.8f), fontFamily = font, fontSize = 12.sp
                        )
                        Text(
                            w.text.trim(), color = c.onPrimary, fontFamily = font,
                            fontWeight = FontWeight.Bold, fontSize = 24.sp, textAlign = TextAlign.Center
                        )
                        Text(
                            "${recIndex + 1} из ${words.size}",
                            color = c.onPrimary.copy(alpha = 0.8f), fontFamily = font, fontSize = 12.sp
                        )
                    }
                    Row { NudgeChip("Остановить запись", { recIndex = -1 }, c, font) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NudgeChip(
                            "● Записать по тапам",
                            {
                                onSeekPlay(max(line.timeMs - 1500, 0))
                                recIndex = 0
                            },
                            c, font, accent = true
                        )
                    }
                }

                // Поправка на реакцию
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Поправка на реакцию: $reactionOffsetMs мс",
                        color = c.textSecondary, fontFamily = font, fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    NudgeChip("−20", { onReactionOffsetChange(reactionOffsetMs - 20) }, c, font)
                    Spacer(Modifier.width(6.dp))
                    NudgeChip("+20", { onReactionOffsetChange(reactionOffsetMs + 20) }, c, font)
                }
                Text(
                    "Тап всегда чуть опаздывает за звуком, поэтому время слова сдвигается назад на эту величину " +
                        "(только пока трек играет).",
                    color = c.textDisabled, fontFamily = font, fontSize = 11.sp
                )

                // Список слов
                words.forEachIndexed { i, w ->
                    val isSel = selectedWord == i
                    val endOfWord = words.getOrNull(i + 1)?.timeMs ?: Int.MAX_VALUE
                    val isActiveWord = positionMs >= line.timeMs && positionMs < nextLineTime &&
                        positionMs >= w.timeMs && positionMs < endOfWord
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isActiveWord) c.accent.copy(alpha = 0.16f) else c.bgCard)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedWord = if (isSel) -1 else i }
                                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "${i + 1}", color = c.textDisabled, fontFamily = font, fontSize = 11.sp,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                w.text.trim().ifEmpty { "␣" },
                                color = c.textPrimary, fontFamily = font, fontSize = 15.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TimeChip(formatTime(w.timeMs), { wordDialogIndex = i }, c)
                            IconButton(
                                onClick = {
                                    val t = stampNow()
                                    onChange { it.withWordTime(i, t) }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Timer, "Поставить текущее время",
                                    tint = c.accent, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (isSel) {
                            Box(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
                                NudgeRow(
                                    onNudge = { d -> onChange { it.withWordTime(i, w.timeMs + d) } },
                                    onNow = {
                                        val t = stampNow()
                                        onChange { it.withWordTime(i, t) }
                                    },
                                    onSeek = { onSeekPlay(max(w.timeMs - 300, 0)) },
                                    c = c, font = font
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTextDialog) {
        TextEditDialog(
            initial = line.text,
            hasWords = words != null,
            wordCount = words?.size ?: 0,
            onDismiss = { showTextDialog = false },
            onConfirm = { newText ->
                showTextDialog = false
                onChange { it.withText(newText) }
            }
        )
    }

    if (showLineTimeDialog) {
        TimeEditDialog(
            title = "Начало строки",
            initialMs = line.timeMs,
            onDismiss = { showLineTimeDialog = false },
            onConfirm = { t ->
                showLineTimeDialog = false
                onChange { it.withLineTime(t) }
            }
        )
    }

    val dialogWord = words?.getOrNull(wordDialogIndex)
    if (dialogWord != null) {
        val idx = wordDialogIndex
        TimeEditDialog(
            title = "Слово «${dialogWord.text.trim()}»",
            initialMs = dialogWord.timeMs,
            onDismiss = { wordDialogIndex = -1 },
            onConfirm = { t ->
                wordDialogIndex = -1
                onChange { it.withWordTime(idx, t) }
            }
        )
    }
}

// ── Диалоги ──────────────────────────────────────────────────────────────────

@Composable
private fun TimeEditDialog(
    title: String,
    initialMs: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(formatTime(initialMs)) }
    val parsed = parseTime(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = parsed == null,
                label = { Text("мм:сс.сотые, например 01:23.45") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { if (parsed != null) onConfirm(parsed) }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun TextEditDialog(
    initial: String,
    hasWords: Boolean,
    wordCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val willResetWords = hasWords && tokenize(text).size != wordCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Текст строки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 4
                )
                if (willResetWords) {
                    Text(
                        "Число слов изменилось — тайминги слов этой строки будут сброшены.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ── Мелкие элементы ──────────────────────────────────────────────────────────

@Composable
private fun DetailCard(c: ColorScheme, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgElevated.copy(alpha = 0.75f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String, c: ColorScheme, font: FontFamily, modifier: Modifier = Modifier) {
    Text(
        text, color = c.textSecondary, fontFamily = font,
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = modifier
    )
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit, c: ColorScheme) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text, color = c.accent, fontFamily = FontFamily.Monospace,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NudgeChip(
    label: String,
    onClick: () -> Unit,
    c: ColorScheme,
    font: FontFamily,
    accent: Boolean = false
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) c.accent.copy(alpha = 0.18f) else c.bgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (accent) c.accent else c.textPrimary, fontFamily = font,
            fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
    }
}

/** Ряд кнопок сдвига времени (в мс) + «Сейчас» + «▶ сюда». */
@Composable
private fun NudgeRow(
    onNudge: (Int) -> Unit,
    onNow: () -> Unit,
    onSeek: () -> Unit,
    c: ColorScheme,
    font: FontFamily
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(-500, -100, -20, 20, 100, 500).forEach { d ->
            NudgeChip(if (d > 0) "+$d" else "−${-d}", { onNudge(d) }, c, font)
        }
        NudgeChip("Сейчас", onNow, c, font, accent = true)
        NudgeChip("▶ сюда", onSeek, c, font, accent = true)
    }
}
