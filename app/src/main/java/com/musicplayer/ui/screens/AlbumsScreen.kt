package com.musicplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.size.Size as CoilSize
import com.musicplayer.data.Song
import com.musicplayer.ui.components.boomingDeleteItemModifier
import com.musicplayer.data.UserAlbum
import com.musicplayer.ui.components.OptimizedAlbumArt
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel

private sealed interface AlbumRef {
    data class Device(val name: String) : AlbumRef
    data class User(val id: String) : AlbumRef
}

private data class AlbumViewData(
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val coverSong: Song?,
    val userAlbum: UserAlbum? = null
)

@Composable
private inline fun <reified T> rememberVisibleLazyKeys(
    state: LazyListState
): Set<T> {
    val visibleKeys by remember(state) {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? T }
                .toSet()
        }
    }
    return visibleKeys
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: MusicViewModel,
    onSongClick: (Song) -> Unit
) {
    val font = LocalAppFontFamily.current
    val songs by viewModel.songs.collectAsState()
    val userAlbums by viewModel.userAlbums.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val customArtMap by viewModel.customArtMap.collectAsState()
    val overviewListState = rememberLazyListState()
    val deviceAlbumsRowState = rememberLazyListState()
    val visibleOverviewKeys = rememberVisibleLazyKeys<String>(overviewListState)
    val visibleDeviceAlbumKeys = rememberVisibleLazyKeys<String>(deviceAlbumsRowState)

    val deviceAlbums = remember(songs) {
        songs
            .filter { it.album.isNotBlank() && it.album != "<unknown>" }
            .groupBy { it.album.trim() }
            .map { (albumName, albumSongs) ->
                val artist = albumSongs
                    .map { it.artist.trim() }
                    .firstOrNull { it.isNotBlank() && it != "<unknown>" }
                    .orEmpty()
                AlbumViewData(
                    key = "device:$albumName",
                    title = albumName,
                    subtitle = artist,
                    songs = albumSongs.sortedBy { it.title.lowercase() },
                    coverSong = albumSongs.firstOrNull { it.albumArtUri != null } ?: albumSongs.firstOrNull()
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    val songMap = remember(songs) { songs.associateBy { it.id } }
    val myAlbums = remember(userAlbums, songMap) {
        userAlbums.map { album ->
            val albumSongs = album.songIds.mapNotNull(songMap::get)
            AlbumViewData(
                key = "user:${album.id}",
                title = album.name,
                subtitle = when {
                    album.description.isNotBlank() -> album.description
                    albumSongs.isEmpty() -> "Пустой плейлист"
                    else -> "${albumSongs.size} ${pluralTracks(albumSongs.size)}"
                },
                songs = albumSongs,
                coverSong = album.coverSongId?.let(songMap::get) ?: albumSongs.firstOrNull(),
                userAlbum = album
            )
        }
    }

    var openAlbumRef by remember { mutableStateOf<AlbumRef?>(null) }
    var editingAlbumId by remember { mutableStateOf<String?>(null) }
    var isCreateDialogVisible by remember { mutableStateOf(false) }
    var deleteAlbumId by remember { mutableStateOf<String?>(null) }

    val openAlbum = remember(openAlbumRef, deviceAlbums, myAlbums) {
        when (val target = openAlbumRef) {
            is AlbumRef.Device -> deviceAlbums.firstOrNull { it.title == target.name }
            is AlbumRef.User -> myAlbums.firstOrNull { it.userAlbum?.id == target.id }
            null -> null
        }
    }

    LaunchedEffect(openAlbumRef, openAlbum) {
        if (openAlbumRef != null && openAlbum == null) {
            openAlbumRef = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    if (openAlbum != null) {
                        FilledTonalIconButton(
                            onClick = { openAlbumRef = null },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = openAlbum?.title ?: "Альбомы и плейлисты",
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = openAlbum?.let { "${it.songs.size} ${pluralTracks(it.songs.size)}" }
                                ?: "Альбомы устройства и ваши плейлисты",
                            fontFamily = font,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    when {
                        openAlbum?.userAlbum != null -> {
                            IconButton(onClick = { editingAlbumId = openAlbum.userAlbum.id }) {
                                Icon(Icons.Default.Edit, contentDescription = "Редактировать плейлист")
                            }
                            IconButton(onClick = { deleteAlbumId = openAlbum.userAlbum.id }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить плейлист")
                            }
                        }

                        openAlbum == null -> {
                            FilledTonalIconButton(
                                onClick = { isCreateDialogVisible = true },
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Создать плейлист")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = openAlbum,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = tween(320)
                ) + fadeIn(tween(240)) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { -it / 5 },
                        animationSpec = tween(280)
                    ) + fadeOut(tween(180))
            },
            label = "albumsContent"
        ) { album ->
            if (album == null) {
                AlbumsOverviewContent(
                    padding = padding,
                    deviceAlbums = deviceAlbums,
                    myAlbums = myAlbums,
                    customArtMap = customArtMap,
                    onOpenDeviceAlbum = { openAlbumRef = AlbumRef.Device(it.title) },
                    onOpenUserAlbum = { openAlbumRef = AlbumRef.User(it.userAlbum!!.id) },
                    onCreateAlbum = { isCreateDialogVisible = true },
                    onEditUserAlbum = { editingAlbumId = it.userAlbum?.id }
                )
            } else {
                AlbumDetailContent(
                    padding = padding,
                    album = album,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    customArtMap = customArtMap,
                    onPlayAlbum = {
                        viewModel.playAlbum(album.songs)
                        album.songs.firstOrNull()?.let(onSongClick)
                    },
                    onShuffleAlbum = {
                        val shuffled = album.songs.shuffled()
                        if (shuffled.isNotEmpty()) {
                            viewModel.playQueue(shuffled)
                            onSongClick(shuffled.first())
                        }
                    },
                    onSongClick = { song ->
                        viewModel.playAlbum(album.songs, song)
                        onSongClick(song)
                    },
                    onEditAlbum = {
                        album.userAlbum?.id?.let { editingAlbumId = it }
                    }
                )
            }
        }
    }

    if (isCreateDialogVisible) {
        PlaylistEditorDialog(
            title = "Новый плейлист",
            confirmLabel = "Создать",
            songs = songs,
            viewModel = viewModel,
            initialName = "",
            initialDescription = "",
            initialSelection = emptyList(),
            initialCoverSongId = null,
            initialCustomCoverUri = null,
            initialMotionCoverUri = null,
            initialPinned = false,
            onDismiss = { isCreateDialogVisible = false },
            onConfirm = { name, description, songIds, coverSongId, customCoverUri, motionCoverUri, isPinned ->
                val createdAlbum = viewModel.createUserAlbum(
                    name = name,
                    songIds = songIds,
                    coverSongId = coverSongId,
                    customCoverUri = customCoverUri,
                    motionCoverUri = null,
                    description = description,
                    isPinned = isPinned
                )
                isCreateDialogVisible = false
                createdAlbum?.let { openAlbumRef = AlbumRef.User(it.id) }
            }
        )
    }

    val editingAlbum = remember(editingAlbumId, userAlbums) {
        userAlbums.firstOrNull { it.id == editingAlbumId }
    }
    if (editingAlbum != null) {
        PlaylistEditorDialog(
            title = "Редактировать плейлист",
            confirmLabel = "Сохранить",
            songs = songs,
            viewModel = viewModel,
            initialName = editingAlbum.name,
            initialDescription = editingAlbum.description,
            initialSelection = editingAlbum.songIds,
            initialCoverSongId = editingAlbum.coverSongId,
            initialCustomCoverUri = editingAlbum.customCoverUri,
            initialMotionCoverUri = null,
            initialPinned = editingAlbum.isPinned,
            onDismiss = { editingAlbumId = null },
            onConfirm = { name, description, songIds, coverSongId, customCoverUri, motionCoverUri, isPinned ->
                viewModel.updateUserAlbumDetails(
                    albumId = editingAlbum.id,
                    name = name,
                    description = description,
                    songIds = songIds,
                    coverSongId = coverSongId,
                    customCoverUri = customCoverUri,
                    motionCoverUri = null,
                    isPinned = isPinned
                )
                editingAlbumId = null
                openAlbumRef = AlbumRef.User(editingAlbum.id)
            }
        )
    }

    val deleteAlbum = remember(deleteAlbumId, userAlbums) {
        userAlbums.firstOrNull { it.id == deleteAlbumId }
    }
    if (deleteAlbum != null) {
        AlertDialog(
            onDismissRequest = { deleteAlbumId = null },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUserAlbum(deleteAlbum.id)
                        if (openAlbumRef == AlbumRef.User(deleteAlbum.id)) {
                            openAlbumRef = null
                        }
                        deleteAlbumId = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAlbumId = null }) {
                    Text("Отмена")
                }
            },
            title = { Text("Удалить плейлист?") },
            text = { Text("Плейлист \"${deleteAlbum.name}\" будет удалён из раздела \"Мои плейлисты\".") }
        )
    }
}

@Composable
private fun AlbumsOverviewContent(
    padding: PaddingValues,
    deviceAlbums: List<AlbumViewData>,
    myAlbums: List<AlbumViewData>,
    customArtMap: Map<Long, android.net.Uri>,
    onOpenDeviceAlbum: (AlbumViewData) -> Unit,
    onOpenUserAlbum: (AlbumViewData) -> Unit,
    onCreateAlbum: () -> Unit,
    onEditUserAlbum: (AlbumViewData) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val overviewListState = rememberLazyListState()
    val deviceAlbumsRowState = rememberLazyListState()

    // Отслеживание видимых элементов для ленивой загрузки обложек
    val visibleDeviceAlbumKeys = remember { mutableStateOf(setOf<String>()) }
    val visibleOverviewKeys = remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        state = overviewListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AlbumsSectionHeader(
                title = "Альбомы устройства",
                subtitle = if (deviceAlbums.isEmpty()) {
                    "Добавьте музыку в библиотеку, чтобы увидеть локальные альбомы"
                } else {
                    "${deviceAlbums.size} ${pluralAlbums(deviceAlbums.size)} из вашей библиотеки"
                }
            )
        }

        if (deviceAlbums.isEmpty()) {
            item {
                AlbumsEmptyCard(
                    icon = Icons.Default.Album,
                    title = "Локальные альбомы пока не найдены",
                    subtitle = "Когда треки в библиотеке содержат название альбома, они появятся здесь."
                )
            }
        } else {
            item {
                LazyRow(
                    state = deviceAlbumsRowState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(deviceAlbums, key = { it.key }) { album ->
                        LibraryAlbumCard(
                            album = album,
                            artUri = album.coverSong?.let { customArtMap[it.id] ?: it.albumArtUri },
                            shouldLoadArt = album.key in visibleDeviceAlbumKeys.value,
                            onClick = { onOpenDeviceAlbum(album) }
                        )
                    }
                }
            }
        }

        item {
            AlbumsSectionHeader(
                title = "Мои плейлисты",
                subtitle = "Соберите свои подборки из треков библиотеки",
                actionLabel = "Создать",
                onAction = onCreateAlbum
            )
        }

        if (myAlbums.isEmpty()) {
            item {
                AlbumsEmptyCard(
                    icon = Icons.Default.QueueMusic,
                    title = "Ваших плейлистов пока нет",
                    subtitle = "Создайте свой плейлист и добавьте в него песни из библиотеки.",
                    actionLabel = "Создать плейлист",
                    onAction = onCreateAlbum
                )
            }
        } else {
            items(myAlbums, key = { it.key }) { album ->
                UserAlbumRow(
                    album = album,
                    customArtMap = customArtMap,
                    shouldLoadCover = album.key in visibleOverviewKeys.value,
                    onOpen = { onOpenUserAlbum(album) },
                    onEdit = { onEditUserAlbum(album) }
                )
            }
        }
    }
}

@Composable
private fun AlbumsSectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Text(
                text = subtitle,
                fontFamily = font,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryAlbumCard(
    album: AlbumViewData,
    artUri: Any?,
    shouldLoadArt: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Card(
        modifier = Modifier
            .width(212.dp)
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            LibraryAlbumCover(
                artUri = if (shouldLoadArt) artUri else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = album.title,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = album.subtitle.ifBlank { "${album.songs.size} ${pluralTracks(album.songs.size)}" },
                fontFamily = font,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${album.songs.size} ${pluralTracks(album.songs.size)}",
                fontFamily = font,
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary
            )
        }
    }
}

@Composable
private fun UserAlbumRow(
    album: AlbumViewData,
    customArtMap: Map<Long, android.net.Uri>,
    shouldLoadCover: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val totalDurationMs = remember(album.songs) { album.songs.sumOf { it.duration } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primaryContainer.copy(alpha = 0.95f),
                            colors.secondaryContainer.copy(alpha = 0.68f),
                            colors.surface.copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistCoverArt(
                    songs = album.songs,
                    customArtMap = customArtMap,
                    coverSongId = album.userAlbum?.coverSongId,
                    customCoverUri = album.userAlbum?.customCoverUri,
                    motionCoverUri = album.userAlbum?.motionCoverUri,
                    shouldLoadArt = shouldLoadCover,
                    modifier = Modifier.size(90.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(album.songs.size)
                            append(' ')
                            append(pluralTracks(album.songs.size))
                            if (totalDurationMs > 0L) {
                                append(" • ")
                                append(formatCollectionDuration(totalDurationMs))
                            }
                        },
                        fontFamily = font,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    if (!album.userAlbum?.description.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = album.userAlbum?.description.orEmpty(),
                            fontFamily = font,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (album.songs.isEmpty()) {
                            "Соберите подборку из треков библиотеки"
                        } else {
                            "Открыть плейлист и посмотреть треки"
                        },
                        fontFamily = font,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать плейлист")
                }
            }

            if (album.userAlbum?.isPinned == true) {
                Text(
                    text = "Закреплён",
                    fontFamily = font,
                    fontSize = 11.sp,
                    color = colors.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryAlbumCover(
    artUri: Any?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = colors.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (artUri != null) {
                OptimizedAlbumArt(
                    uri = artUri,
                    title = "Album cover",
                    modifier = Modifier.fillMaxSize(),
                    targetSize = CoilSize(396, 396)
                )
            } else {
                Icon(
                    Icons.Default.Album,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistCoverArt(
    songs: List<Song>,
    customArtMap: Map<Long, android.net.Uri>,
    coverSongId: Long?,
    customCoverUri: String? = null,
    motionCoverUri: String? = null,
    playMotion: Boolean = false,
    shouldLoadArt: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val artModels = remember(songs, customArtMap, coverSongId, shouldLoadArt) {
        if (!shouldLoadArt) return@remember emptyList()
        val preferred = coverSongId?.let { targetId ->
            songs.firstOrNull { it.id == targetId }?.let { customArtMap[it.id] ?: it.albumArtUri }
        }
        buildList<Any> {
            preferred?.let(::add)
            songs.forEach { song ->
                val art = customArtMap[song.id] ?: song.albumArtUri ?: return@forEach
                if (art !in this) add(art)
            }
        }.take(4)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh
    ) {
        when {
            shouldLoadArt && !customCoverUri.isNullOrBlank() -> {
                OptimizedAlbumArt(
                    uri = android.net.Uri.parse(customCoverUri),
                    title = "Playlist cover",
                    modifier = Modifier.fillMaxSize(),
                    targetSize = CoilSize(588, 588)
                )
            }

            artModels.size <= 1 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.primary.copy(alpha = 0.18f),
                                    colors.secondary.copy(alpha = 0.12f),
                                    colors.surfaceContainerHigh
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val singleArt = artModels.firstOrNull()
                    if (singleArt != null) {
                        OptimizedAlbumArt(
                            uri = singleArt,
                            title = "Playlist cover",
                            modifier = Modifier.fillMaxSize(),
                            targetSize = CoilSize(588, 588)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            else -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) {
                        PlaylistCoverTile(artModels.getOrNull(0), Modifier.weight(1f), colors)
                        PlaylistCoverTile(artModels.getOrNull(1), Modifier.weight(1f), colors)
                    }
                    Row(Modifier.weight(1f)) {
                        PlaylistCoverTile(artModels.getOrNull(2), Modifier.weight(1f), colors)
                        PlaylistCoverTile(artModels.getOrNull(3), Modifier.weight(1f), colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverTile(
    artModel: Any?,
    modifier: Modifier,
    colors: androidx.compose.material3.ColorScheme
) {
    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (artModel != null) {
            OptimizedAlbumArt(
                uri = artModel,
                title = "Playlist tile",
                modifier = Modifier.fillMaxSize(),
                targetSize = CoilSize(280, 280)
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AlbumDetailContent(
    padding: PaddingValues,
    album: AlbumViewData,
    currentSong: Song?,
    isPlaying: Boolean,
    customArtMap: Map<Long, android.net.Uri>,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onSongClick: (Song) -> Unit,
    onEditAlbum: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isUserPlaylist = album.userAlbum != null
    val totalDurationMs = remember(album.songs) { album.songs.sumOf { it.duration } }
    val detailListState = rememberLazyListState()
    val visibleSongIds = rememberVisibleLazyKeys<Long>(detailListState)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primaryContainer.copy(alpha = 0.82f),
                            colors.surface.copy(alpha = 0.98f),
                            colors.background
                        )
                    )
                )
        )

        LazyColumn(
            state = detailListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CollectionHeroCard(
                    album = album,
                    customArtMap = customArtMap,
                    totalDurationMs = totalDurationMs,
                    isPlaylist = isUserPlaylist,
                    onPlayAlbum = onPlayAlbum,
                    onShuffleAlbum = onShuffleAlbum,
                    onEditAlbum = onEditAlbum
                )
            }

            if (album.songs.isEmpty()) {
                item {
                    AlbumsEmptyCard(
                        icon = Icons.Default.LibraryMusic,
                        title = if (isUserPlaylist) "В плейлисте пока нет песен" else "В альбоме пока нет песен",
                        subtitle = if (isUserPlaylist) {
                            "Откройте редактирование и добавьте треки из библиотеки."
                        } else {
                            "Треки появятся здесь автоматически, когда альбом есть в библиотеке."
                        },
                        actionLabel = if (isUserPlaylist) "Добавить песни" else null,
                        onAction = if (album.userAlbum != null) onEditAlbum else null
                    )
                }
            } else {
                itemsIndexed(album.songs, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    CollectionSongRow(
                        song = song,
                        index = index,
                        customArtMap = customArtMap,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        isPlaylist = isUserPlaylist,
                        shouldLoadArt = song.id in visibleSongIds,
                        onClick = { onSongClick(song) },
                        modifier = boomingDeleteItemModifier()
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionHeroCard(
    album: AlbumViewData,
    customArtMap: Map<Long, android.net.Uri>,
    totalDurationMs: Long,
    isPlaylist: Boolean,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onEditAlbum: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primaryContainer.copy(alpha = 0.9f),
                            colors.secondaryContainer.copy(alpha = 0.48f),
                            colors.surface.copy(alpha = 0.98f),
                            colors.surface
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlaylistCoverArt(
                    songs = album.songs,
                    customArtMap = customArtMap,
                    coverSongId = album.userAlbum?.coverSongId,
                    customCoverUri = album.userAlbum?.customCoverUri,
                    motionCoverUri = album.userAlbum?.motionCoverUri,
                    playMotion = isPlaylist,
                    modifier = Modifier.size(if (isPlaylist) 196.dp else 154.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = album.title,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    fontSize = if (isPlaylist) 26.sp else 22.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append(album.songs.size)
                        append(' ')
                        append(pluralTracks(album.songs.size))
                        if (totalDurationMs > 0L) {
                            append(" • ")
                            append(formatCollectionDuration(totalDurationMs))
                        }
                        if (!isPlaylist && album.subtitle.isNotBlank()) {
                            append("\n")
                            append(album.subtitle)
                        }
                    },
                    fontFamily = font,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (isPlaylist && !album.userAlbum?.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = album.userAlbum?.description.orEmpty(),
                        fontFamily = font,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (isPlaylist) {
                        FilledTonalIconButton(
                            onClick = onEditAlbum,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить")
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onPlayAlbum,
                        enabled = album.songs.isNotEmpty(),
                        modifier = Modifier.size(74.dp),
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Играть",
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onShuffleAlbum,
                        enabled = album.songs.isNotEmpty(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Перемешать")
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionSongRow(
    song: Song,
    index: Int,
    customArtMap: Map<Long, android.net.Uri>,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isPlaylist: Boolean,
    shouldLoadArt: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val artUri = if (shouldLoadArt) customArtMap[song.id] ?: song.albumArtUri else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = when {
            isCurrent -> colors.secondaryContainer.copy(alpha = 0.8f)
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                fontFamily = font,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.width(30.dp)
            )
            SongArtworkThumb(
                artUri = artUri,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = font,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isCurrent) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = if (isPlaylist) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(song.artist.ifBlank { "Неизвестный исполнитель" })
                        if (song.album.isNotBlank() && song.album != "<unknown>" && isPlaylist) {
                            append(" • ")
                            append(song.album)
                        }
                    },
                    fontFamily = font,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) colors.onPrimaryContainer.copy(alpha = 0.72f) else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = song.formattedDuration(),
                fontFamily = font,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) colors.primary else colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SongArtworkThumb(
    artUri: Any?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) colors.secondaryContainer else colors.surfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (artUri != null) {
                OptimizedAlbumArt(
                    uri = artUri,
                    title = "Song artwork",
                    modifier = Modifier.fillMaxSize(),
                    targetSize = CoilSize(144, 144)
                )
            } else {
                Icon(
                    imageVector = if (isCurrent && isPlaying) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrent) colors.onSecondaryContainer else colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumsEmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                color = colors.secondaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = colors.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontFamily = font,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun pluralTracks(count: Int): String = when {
    count % 100 in 11..19 -> "треков"
    count % 10 == 1 -> "трек"
    count % 10 in 2..4 -> "трека"
    else -> "треков"
}

private fun formatCollectionDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}ч ${minutes}м"
        hours > 0 -> "${hours}ч"
        else -> "${minutes}м"
    }
}

private fun pluralAlbums(count: Int): String = when {
    count % 100 in 11..19 -> "альбомов"
    count % 10 == 1 -> "альбом"
    count % 10 in 2..4 -> "альбома"
    else -> "альбомов"
}
