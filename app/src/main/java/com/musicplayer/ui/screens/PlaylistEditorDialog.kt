package com.musicplayer.ui.screens

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.musicplayer.data.Song
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditorDialog(
    title: String,
    confirmLabel: String,
    songs: List<Song>,
    viewModel: MusicViewModel,
    initialName: String,
    initialDescription: String,
    initialSelection: List<Long>,
    initialCoverSongId: Long?,
    initialCustomCoverUri: String?,
    initialMotionCoverUri: String?,
    initialPinned: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        description: String,
        songIds: List<Long>,
        coverSongId: Long?,
        customCoverUri: String?,
        motionCoverUri: String?,
        isPinned: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val customArtMap by viewModel.customArtMap.collectAsState()
    val songMap = remember(songs) { songs.associateBy { it.id } }

    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var description by remember(title, initialDescription) { mutableStateOf(initialDescription) }
    var query by remember(title) { mutableStateOf("") }
    var selectedSongIds by remember(title, initialSelection) {
        mutableStateOf(initialSelection.distinct().filter { it in songMap })
    }
    var coverSongId by remember(title, initialSelection, initialCoverSongId) {
        mutableStateOf(initialCoverSongId?.takeIf { it in initialSelection } ?: initialSelection.firstOrNull())
    }
    var customCoverUri by remember(title, initialCustomCoverUri) { mutableStateOf(initialCustomCoverUri) }
    var motionCoverUri by remember(title) { mutableStateOf<String?>(null) }
    var isPinned by remember(title, initialPinned) { mutableStateOf(initialPinned) }
    var showSelectedOnly by remember(title) { mutableStateOf(false) }
    var coverMessage by remember(title) { mutableStateOf<String?>(null) }

    val selectedSongs = remember(selectedSongIds, songMap) {
        selectedSongIds.mapNotNull(songMap::get)
    }
    val totalDurationMs = remember(selectedSongs) { selectedSongs.sumOf { it.duration } }
    val visibleSongs = remember(songs, selectedSongs, query, showSelectedOnly) {
        when {
            showSelectedOnly -> selectedSongs
            query.isBlank() -> songs.sortedBy { it.title.lowercase() }
            else -> viewModel.searchLibrary(query, limit = 240)
        }
    }

    LaunchedEffect(selectedSongIds) {
        if (coverSongId != null && coverSongId !in selectedSongIds) {
            coverSongId = selectedSongIds.firstOrNull()
        } else if (coverSongId == null && selectedSongIds.isNotEmpty()) {
            coverSongId = selectedSongIds.firstOrNull()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val importedUri = importPlaylistCoverMedia(context, uri, prefix = "cover", fallbackExtension = "jpg")
        if (importedUri == null) {
            coverMessage = "Не удалось сохранить обложку"
            return@rememberLauncherForActivityResult
        }
        customCoverUri = importedUri
        motionCoverUri = null
        coverMessage = "Обложка из галереи сохранена"
    }
    fun toggleSong(songId: Long) {
        selectedSongIds = if (songId in selectedSongIds) {
            selectedSongIds.filterNot { it == songId }
        } else {
            selectedSongIds + songId
        }
    }

    fun sortSelection(selector: (Song) -> String) {
        selectedSongIds = selectedSongs
            .sortedBy { selector(it).lowercase() }
            .map { it.id }
    }

    fun reorderRecent() {
        selectedSongIds = selectedSongs
            .sortedByDescending { it.id }
            .map { it.id }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.background,
                            titleContentColor = colors.onSurface
                        ),
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = title,
                                    fontFamily = font,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = "Полноэкранный редактор плейлиста",
                                    color = colors.onSurfaceVariant,
                                    fontFamily = font,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    onConfirm(
                                        name,
                                        description,
                                        selectedSongIds,
                                        coverSongId,
                                        customCoverUri,
                                        null,
                                        isPinned
                                    )
                                },
                                enabled = name.isNotBlank()
                            ) {
                                Text(confirmLabel, fontFamily = font, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        modifier = Modifier.statusBarsPadding()
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        EditorSummaryCard(
                            name = name.ifBlank { "Новый плейлист" },
                            description = description,
                            selectedSongs = selectedSongs,
                            customArtMap = customArtMap,
                            coverSongId = coverSongId,
                            customCoverUri = customCoverUri,
                            motionCoverUri = null,
                            isPinned = isPinned
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Название плейлиста") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp)
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Описание или заметка") },
                            minLines = 3,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp)
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Обложка и поведение",
                                            fontFamily = font,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface
                                        )
                                        Text(
                                            "Можно использовать коллаж из песен или фото из галереи",
                                            fontFamily = font,
                                            fontSize = 12.sp,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isPinned,
                                        onCheckedChange = { isPinned = it }
                                    )
                                }

                                Text(
                                    text = if (isPinned) "Плейлист закреплён и будет выше остальных" else "Можно закрепить плейлист наверху раздела",
                                    fontFamily = font,
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant
                                )

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        EditorActionChip(
                                            icon = Icons.Rounded.Image,
                                            label = "Фото из галереи",
                                            onClick = {
                                                imagePicker.launch(
                                                    PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    item {
                                        EditorActionChip(
                                            icon = Icons.Rounded.Collections,
                                            label = "Коллаж из треков",
                                            onClick = {
                                                customCoverUri = null
                                                motionCoverUri = null
                                                coverMessage = "Используется автоколлаж из треков"
                                            }
                                        )
                                    }
                                    item {
                                        EditorActionChip(
                                            icon = Icons.Rounded.Refresh,
                                            label = "Сбросить",
                                            onClick = {
                                                customCoverUri = null
                                                motionCoverUri = null
                                                coverMessage = "Пользовательская обложка очищена"
                                            }
                                        )
                                    }
                                }

                                if (!coverMessage.isNullOrBlank()) {
                                    Text(
                                        text = coverMessage.orEmpty(),
                                        fontFamily = font,
                                        fontSize = 12.sp,
                                        color = colors.primary
                                    )
                                }

                                if (selectedSongs.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Трек для основной обложки",
                                            fontFamily = font,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            items(selectedSongs.take(16), key = { it.id }) { song ->
                                                val isCover = coverSongId == song.id
                                                Surface(
                                                    modifier = Modifier.clickable { coverSongId = song.id },
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = if (isCover) colors.secondaryContainer else colors.surface,
                                                    tonalElevation = if (isCover) 2.dp else 0.dp
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(8.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        PlaylistEditorSongArtwork(
                                                            artUri = customArtMap[song.id] ?: song.albumArtUri,
                                                            modifier = Modifier.size(64.dp)
                                                        )
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(
                                                            text = song.title,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontFamily = font,
                                                            fontSize = 11.sp,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.width(78.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("Поиск по библиотеке") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Search, contentDescription = null)
                                    }
                                )

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        EditorActionChip(Icons.Rounded.CheckCircle, "Выбрать всё") {
                                            selectedSongIds = songs.sortedBy { it.title.lowercase() }.map { it.id }
                                        }
                                    }
                                    item {
                                        EditorActionChip(Icons.Rounded.Close, "Очистить") {
                                            selectedSongIds = emptyList()
                                        }
                                    }
                                    item {
                                        EditorActionChip(Icons.Rounded.SortByAlpha, "A-Z") {
                                            sortSelection { it.title }
                                        }
                                    }
                                    item {
                                        EditorActionChip(Icons.Rounded.MoreTime, "Недавние") {
                                            reorderRecent()
                                        }
                                    }
                                    item {
                                        EditorActionChip(Icons.Rounded.Tune, "По артисту") {
                                            sortSelection { it.artist }
                                        }
                                    }
                                    item {
                                        EditorActionChip(
                                            if (showSelectedOnly) Icons.Rounded.LibraryMusic else Icons.Rounded.StarBorder,
                                            if (showSelectedOnly) "Показать всё" else "Только выбранные"
                                        ) {
                                            showSelectedOnly = !showSelectedOnly
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Песни в плейлисте",
                            fontFamily = font,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                    }

                    items(visibleSongs, key = { it.id }) { song ->
                        val isSelected = song.id in selectedSongIds
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggleSong(song.id) },
                            shape = RoundedCornerShape(22.dp),
                            color = if (isSelected) {
                                colors.secondaryContainer.copy(alpha = 0.92f)
                            } else {
                                colors.surface
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlaylistEditorSongArtwork(
                                    artUri = customArtMap[song.id] ?: song.albumArtUri,
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        fontFamily = font,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = buildString {
                                            append(song.artist.ifBlank { "Неизвестный исполнитель" })
                                            if (song.album.isNotBlank() && song.album != "<unknown>") {
                                                append(" • ")
                                                append(song.album)
                                            }
                                        },
                                        fontFamily = font,
                                        fontSize = 12.sp,
                                        color = if (isSelected) colors.onSecondaryContainer.copy(alpha = 0.74f) else colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = song.formattedDuration(),
                                    fontFamily = font,
                                    fontSize = 12.sp,
                                    color = if (isSelected) colors.primary else colors.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onConfirm(
                                    name,
                                    description,
                                    selectedSongIds,
                                    coverSongId,
                                    customCoverUri,
                                    null,
                                    isPinned
                                )
                            },
                            enabled = name.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(confirmLabel, fontFamily = font, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSummaryCard(
    name: String,
    description: String,
    selectedSongs: List<Song>,
    customArtMap: Map<Long, Uri>,
    coverSongId: Long?,
    customCoverUri: String?,
    motionCoverUri: String?,
    isPinned: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val totalDurationMs = remember(selectedSongs) { selectedSongs.sumOf { it.duration } }
    Card(
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primaryContainer.copy(alpha = 0.92f),
                            colors.surface.copy(alpha = 0.98f),
                            colors.surface
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlaylistEditorCoverPreview(
                    selectedSongs = selectedSongs,
                    customArtMap = customArtMap,
                    coverSongId = coverSongId,
                    customCoverUri = customCoverUri,
                    motionCoverUri = motionCoverUri,
                    modifier = Modifier.size(188.dp)
                )
                Text(
                    text = name,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        fontFamily = font,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorStatChip("${selectedSongs.size} треков")
                    if (totalDurationMs > 0L) {
                        EditorStatChip(formatCollectionDuration(totalDurationMs))
                    }
                    if (isPinned) {
                        EditorStatChip("Закреплён")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorStatChip(label: String) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.surface.copy(alpha = 0.8f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontFamily = font,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
    }
}

@Composable
private fun EditorActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val font = LocalAppFontFamily.current
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontFamily = font, fontSize = 12.sp)
    }
}

@Composable
private fun PlaylistEditorCoverPreview(
    selectedSongs: List<Song>,
    customArtMap: Map<Long, Uri>,
    coverSongId: Long?,
    customCoverUri: String?,
    motionCoverUri: String?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val preferredArt = remember(selectedSongs, customArtMap, coverSongId) {
        coverSongId?.let { id ->
            selectedSongs.firstOrNull { it.id == id }?.let { customArtMap[it.id] ?: it.albumArtUri }
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = colors.surface
    ) {
        when {
            !customCoverUri.isNullOrBlank() -> {
                AsyncImage(
                    model = Uri.parse(customCoverUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            selectedSongs.isNotEmpty() -> {
                val artModels = remember(selectedSongs, customArtMap, preferredArt) {
                    buildList<Any> {
                        preferredArt?.let(::add)
                        selectedSongs.forEach { song ->
                            val art = customArtMap[song.id] ?: song.albumArtUri ?: return@forEach
                            if (art !in this) add(art)
                        }
                    }.take(4)
                }
                if (artModels.size <= 1) {
                    if (artModels.firstOrNull() != null) {
                        AsyncImage(
                            model = artModels.first(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.LibraryMusic,
                                contentDescription = null,
                                tint = colors.onSecondaryContainer,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) {
                            PlaylistEditorCoverTile(artModels.getOrNull(0), Modifier.weight(1f))
                            PlaylistEditorCoverTile(artModels.getOrNull(1), Modifier.weight(1f))
                        }
                        Row(Modifier.weight(1f)) {
                            PlaylistEditorCoverTile(artModels.getOrNull(2), Modifier.weight(1f))
                            PlaylistEditorCoverTile(artModels.getOrNull(3), Modifier.weight(1f))
                        }
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.primaryContainer,
                                    colors.secondaryContainer,
                                    colors.surface
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistEditorCoverTile(model: Any?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistEditorSongArtwork(
    artUri: Any?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerHigh
    ) {
        if (artUri != null) {
            AsyncImage(
                model = artUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun PlaylistMotionCover(
    uriString: String,
    modifier: Modifier = Modifier,
    showLiveBadge: Boolean = true
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val videoView = remember(context, uriString) { VideoView(context) }

    DisposableEffect(videoView, uriString) {
        val uri = Uri.parse(uriString)
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { player ->
            player.isLooping = true
            player.setVolume(0f, 0f)
            videoView.start()
        }
        onDispose {
            videoView.stopPlayback()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { videoView },
            modifier = Modifier.fillMaxSize()
        )
        if (showLiveBadge) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(999.dp),
                color = colors.primary.copy(alpha = 0.88f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LIVE",
                        color = colors.onPrimary,
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaylistMotionCoverFrame(
    uriString: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp)
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val frameBitmap = remember(uriString) { extractVideoFrame(context, uriString) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceContainerHigh)
    ) {
        if (frameBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = frameBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            shape = RoundedCornerShape(999.dp),
            color = colors.primary.copy(alpha = 0.9f)
        ) {
            Text(
                text = "LIVE",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = colors.onPrimary,
                fontFamily = LocalAppFontFamily.current,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun readVideoDurationMs(context: android.content.Context, uri: Uri): Long? {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private fun importPlaylistCoverMedia(
    context: android.content.Context,
    uri: Uri,
    prefix: String,
    fallbackExtension: String
): String? {
    return runCatching {
        val extension = resolvePlaylistCoverExtension(
            mimeType = context.contentResolver.getType(uri),
            fallbackExtension = fallbackExtension
        )
        val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val destFile = File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(destFile).toString()
    }.getOrNull()
}

private fun resolvePlaylistCoverExtension(
    mimeType: String?,
    fallbackExtension: String
): String {
    val normalized = mimeType.orEmpty().lowercase()
    return when {
        "png" in normalized -> "png"
        "webp" in normalized -> "webp"
        "gif" in normalized -> "gif"
        "heic" in normalized -> "heic"
        "heif" in normalized -> "heif"
        "webm" in normalized -> "webm"
        "3gpp" in normalized || "3gp" in normalized -> "3gp"
        "quicktime" in normalized || "mov" in normalized -> "mov"
        "mp4" in normalized || "video/" in normalized -> "mp4"
        "jpeg" in normalized || "jpg" in normalized || "image/" in normalized -> "jpg"
        else -> fallbackExtension
    }
}

private fun extractVideoFrame(context: android.content.Context, uriString: String): Bitmap? {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private fun formatCollectionDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours} ч ${minutes} мин"
    } else {
        "${minutes} мин"
    }
}
