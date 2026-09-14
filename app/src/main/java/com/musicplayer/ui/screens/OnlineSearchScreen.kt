@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)

package com.musicplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import coil.compose.AsyncImage
import com.musicplayer.R
import com.musicplayer.data.DownloadState
import com.musicplayer.data.OnlineAlbumDetail
import com.musicplayer.data.OnlineAlbumSection
import com.musicplayer.data.OnlineAlbumSheetState
import com.musicplayer.data.OnlineAlbumSummary
import com.musicplayer.data.OnlineSearchState
import com.musicplayer.data.OnlineSong
import com.musicplayer.data.Song
import com.musicplayer.ui.components.DownloadButton
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import com.musicplayer.viewmodel.OnlineSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    musicViewModel: MusicViewModel,
    searchViewModel: OnlineSearchViewModel = viewModel()
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    val bridgeQuery by searchViewModel.bridgeQuery.collectAsState()
    var query by remember { mutableStateOf("") }
    val searchState by searchViewModel.searchState.collectAsState()
    val albumSheetState by searchViewModel.albumSheetState.collectAsState()
    val downloadStates by searchViewModel.downloadStates.collectAsState()
    val readySongs by searchViewModel.readySongs.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val addedSongs by musicViewModel.songs.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val albumSheetUiState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(bridgeQuery) {
        if (bridgeQuery.isNotBlank() && bridgeQuery != query) {
            query = bridgeQuery
            keyboard?.hide()
        }
    }

    var selectedUrls by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedUrls.isNotEmpty()

    val locallyAdded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(readySongs, addedSongs) {
        readySongs.forEach { (url, song) ->
            if (addedSongs.any { it.uri == song.uri }) locallyAdded[url] = true
        }
    }

    fun playOnlineSong(song: OnlineSong, albumName: String = "Онлайн") {
        val tempSong = Song(
            id = song.downloadUrl.hashCode().toLong() and 0xFFFFFFFFL,
            title = song.title.ifBlank { "Неизвестный трек" },
            artist = song.artist,
            album = albumName,
            duration = 0L,
            uri = android.net.Uri.parse(song.downloadUrl),
            albumArtUri = null
        )
        musicViewModel.playSong(tempSong)
    }

    fun markSongAdded(song: OnlineSong) {
        readySongs[song.downloadUrl]?.let { downloadedSong ->
            musicViewModel.addDownloadedSong(downloadedSong)
            locallyAdded[song.downloadUrl] = true
        }
    }

    val runSearch = {
        keyboard?.hide()
        searchViewModel.search(query)
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurfaceVariant,
                    actionIconContentColor = colors.onSurfaceVariant
                ),
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedUrls = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть выбор")
                        }
                    }
                },
                title = {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220))
                                .togetherWith(fadeOut(animationSpec = tween(180)))
                        },
                        label = "onlineSearchTopBar"
                    ) { selectionMode ->
                        if (selectionMode) {
                            Text(
                                text = "Выбрано: ${selectedUrls.size}",
                                fontFamily = font,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Column {
                                Text(
                                    text = "Поиск",
                                    fontFamily = font,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Онлайн-треки и скачивание",
                                    fontFamily = font,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (isSelectionMode && selectedUrls.isNotEmpty()) {
                        FilledIconButton(
                            onClick = {
                                val urls = selectedUrls.toSet()
                                val currentState = searchState
                                if (currentState is OnlineSearchState.Success) {
                                    currentState.songs
                                        .filter { it.downloadUrl in urls }
                                        .forEach { searchViewModel.startDownload(it) }
                                }
                                selectedUrls = emptySet()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer
                            ),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Скачать выбранное")
                        }
                    }
                }
            )
        }

    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchInputSurface(
                query = query,
                onQueryChange = { query = it },
                onClearQuery = { query = "" },
                onSearch = runSearch,
                focusRequester = focusRequester
            )

            AnimatedContent(
                targetState = searchState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(260))
                        .togetherWith(fadeOut(animationSpec = tween(180)))
                },
                label = "onlineSearchState"
            ) { state ->
                when (state) {
                    OnlineSearchState.Idle -> OnlineIdleHint(
                        history = searchHistory,
                        onChipClick = {
                            query = it
                            searchViewModel.search(it)
                        },
                        onRemoveHistory = { searchViewModel.removeHistoryQuery(it) },
                        onClearHistory = { searchViewModel.clearSearchHistory() }
                    )

                    OnlineSearchState.Loading -> OnlineLoadingState()

                    is OnlineSearchState.Empty -> EmptySearchState(query = state.query)

                    is OnlineSearchState.Error -> ErrorSearchState(
                        message = state.message,
                        onRetry = { searchViewModel.search(query) }
                    )

                    is OnlineSearchState.Success -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        item {
                            SearchResultsSummary(
                                songCount = state.songs.size,
                                albumCount = state.albums.size,
                                isSelectionMode = isSelectionMode,
                                selectionCount = selectedUrls.size
                            )
                        }

                        if (state.albums.isNotEmpty()) {
                            item {
                                SearchSectionLabel(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Album,
                                            contentDescription = null,
                                            tint = colors.primary
                                        )
                                    },
                                    title = "Альбомы",
                                    subtitle = "Откройте альбом, чтобы увидеть все треки"
                                )
                            }

                            item {
                                AlbumResultsCarousel(
                                    albums = state.albums,
                                    onAlbumClick = {
                                        keyboard?.hide()
                                        searchViewModel.openAlbum(it)
                                    }
                                )
                            }
                        }

                        if (state.songs.isNotEmpty()) {
                            item {
                                SearchSectionLabel(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.LibraryMusic,
                                            contentDescription = null,
                                            tint = colors.primary
                                        )
                                    },
                                    title = "Треки",
                                    subtitle = "Потоковое воспроизведение, загрузка и добавление в библиотеку"
                                )
                            }
                        }

                        itemsIndexed(state.songs, key = { _, item -> item.downloadUrl }) { index, song ->
                            val dlState = downloadStates[song.downloadUrl] ?: DownloadState.Idle
                            val readySong = readySongs[song.downloadUrl]
                            val isAdded = locallyAdded[song.downloadUrl] == true ||
                                (readySong != null && addedSongs.any { it.uri == readySong.uri })
                            val isSelected = song.downloadUrl in selectedUrls

                            OnlineSongCard(
                                song = song,
                                index = index,
                                dlState = dlState,
                                isAdded = isAdded,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                accentColor = colors.primary,
                                onDownload = { searchViewModel.startDownload(song) },
                                onAdd = { markSongAdded(song) },
                                onPlay = {
                                    if (isSelectionMode) {
                                        selectedUrls = if (isSelected) {
                                            selectedUrls - song.downloadUrl
                                        } else {
                                            selectedUrls + song.downloadUrl
                                        }
                                    } else {
                                        playOnlineSong(song)
                                    }
                                },
                                onLongClick = {
                                    selectedUrls = selectedUrls + song.downloadUrl
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (albumSheetState !is OnlineAlbumSheetState.Hidden) {
        ModalBottomSheet(
            onDismissRequest = { searchViewModel.closeAlbum() },
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) },
            sheetState = albumSheetUiState,
            containerColor = colors.surface
        ) {
            AlbumDetailSheet(
                state = albumSheetState,
                downloadStates = downloadStates,
                readySongs = readySongs,
                addedSongs = addedSongs,
                locallyAdded = locallyAdded,
                onRetry = { searchViewModel.retryAlbum() },
                onDownload = { searchViewModel.startDownload(it) },
                onAdd = { markSongAdded(it) },
                onPlay = { song, albumName -> playOnlineSong(song, albumName) }
            )
        }
    }
}

@Composable
private fun SearchInputSurface(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(22.dp)
            )

            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Искать трек, артиста или альбом",
                        fontFamily = font,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = font,
                    color = colors.onSurface
                ),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    cursorColor = colors.primary,
                    focusedPlaceholderColor = colors.onSurfaceVariant,
                    unfocusedPlaceholderColor = colors.onSurfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )

            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить",
                        tint = colors.onSurfaceVariant
                    )
                }
            }

            FilledIconButton(
                onClick = onSearch,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Искать",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}


@Composable
private fun SearchResultsSummary(
    songCount: Int,
    albumCount: Int,
    isSelectionMode: Boolean,
    selectionCount: Int
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${albumCount + songCount} результатов",
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = if (isSelectionMode) {
                        "Выбрано для скачивания: $selectionCount"
                    } else {
                        "Альбомов: $albumCount, треков: $songCount"
                    },
                    fontFamily = font,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelectionMode) colors.primaryContainer else colors.secondaryContainer
            ) {
                Text(
                    text = if (isSelectionMode) "$selectionCount" else "$albumCount/$songCount",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontFamily = font,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelectionMode) colors.onPrimaryContainer else colors.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun SearchSectionLabel(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.secondaryContainer
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }

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
    }
}

@Composable
private fun AlbumResultsCarousel(
    albums: List<OnlineAlbumSummary>,
    onAlbumClick: (OnlineAlbumSummary) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 4.dp)
    ) {
        items(albums, key = { it.albumUrl }) { album ->
            AlbumPreviewCard(
                album = album,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumPreviewCard(
    album: OnlineAlbumSummary,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Card(
        modifier = Modifier
            .width(220.dp)
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceContainerHigh
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (album.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = album.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = album.title.ifBlank { "Альбом" },
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (album.artist.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = album.artist,
                    fontFamily = font,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (album.subtitle.isNotBlank() || album.badge.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (album.badge.isNotBlank()) {
                        MetadataPill(text = album.badge)
                    }
                    if (album.subtitle.isNotBlank()) {
                        MetadataPill(text = album.subtitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailSheet(
    state: OnlineAlbumSheetState,
    downloadStates: Map<String, DownloadState>,
    readySongs: Map<String, Song>,
    addedSongs: List<Song>,
    locallyAdded: Map<String, Boolean>,
    onRetry: () -> Unit,
    onDownload: (OnlineSong) -> Unit,
    onAdd: (OnlineSong) -> Unit,
    onPlay: (OnlineSong, String) -> Unit
) {
    val album = when (state) {
        OnlineAlbumSheetState.Hidden -> return
        is OnlineAlbumSheetState.Loading -> state.album
        is OnlineAlbumSheetState.Error -> state.album
        is OnlineAlbumSheetState.Success -> state.album
    }
    val coverUrl = when (state) {
        is OnlineAlbumSheetState.Success -> state.detail.coverUrl.ifBlank { album.coverUrl }
        else -> album.coverUrl
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OnlineAlbumBackdrop(coverUrl = coverUrl)

    when (state) {
        OnlineAlbumSheetState.Hidden -> Unit

        is OnlineAlbumSheetState.Loading -> {
            AlbumSheetStatus(
                album = state.album,
                title = "Загружаем альбом",
                subtitle = "Получаем треки и структуру альбома..."
            )
        }

        is OnlineAlbumSheetState.Error -> {
            AlbumSheetStatus(
                album = state.album,
                title = "Не удалось открыть альбом",
                subtitle = state.message,
                action = {
                    Button(onClick = onRetry) {
                        Text("Повторить")
                    }
                }
            )
        }

        is OnlineAlbumSheetState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AlbumDetailHeader(
                        album = state.album,
                        detail = state.detail
                    )
                }

                state.detail.sections.forEachIndexed { sectionIndex, section ->
                    item {
                        OnlineAlbumSectionHeader(
                            title = section.title.ifBlank { "Треки" },
                            subtitle = section.subtitle.ifBlank { "${section.tracks.size} трек(ов)" }
                        )
                    }

                    itemsIndexed(
                        section.tracks,
                        key = { _, song -> "${sectionIndex}_${song.downloadUrl}" }
                    ) { index, song ->
                        val dlState = downloadStates[song.downloadUrl] ?: DownloadState.Idle
                        val readySong = readySongs[song.downloadUrl]
                        val isAdded = locallyAdded[song.downloadUrl] == true ||
                            (readySong != null && addedSongs.any { it.uri == readySong.uri })

                        OnlineSongCard(
                            song = song,
                            index = index,
                            dlState = dlState,
                            isAdded = isAdded,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onDownload = { onDownload(song) },
                            onAdd = { onAdd(song) },
                            onPlay = { onPlay(song, state.detail.title) }
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AlbumSheetStatus(
    album: OnlineAlbumSummary,
    title: String,
    subtitle: String,
    action: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumCover(
                coverUrl = album.coverUrl,
                modifier = Modifier.size(186.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = album.title.ifBlank { "Альбом" },
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                fontSize = 24.sp
            )
            if (album.artist.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = album.artist,
                    fontFamily = font,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))
            if (action == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = title,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontFamily = font,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

@Composable
private fun AlbumDetailHeader(
    album: OnlineAlbumSummary,
    detail: OnlineAlbumDetail
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val coverUrl = detail.coverUrl.ifBlank { album.coverUrl }
    val trackCount = detail.sections.sumOf { it.tracks.size }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(34.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primaryContainer.copy(alpha = 0.32f),
                            colors.secondaryContainer.copy(alpha = 0.18f),
                            colors.surface.copy(alpha = 0.86f),
                            colors.surface.copy(alpha = 0.97f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AlbumCover(
                    coverUrl = coverUrl,
                    modifier = Modifier.size(204.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = detail.title.ifBlank { album.title.ifBlank { "Альбом" } },
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    fontSize = 27.sp
                )
                if (detail.artist.isNotBlank() || album.artist.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = detail.artist.ifBlank { album.artist },
                        fontFamily = font,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    userScrollEnabled = detail.chips.size > 4
                ) {
                    item { MetadataPill(text = "$trackCount трек(ов)") }
                    if (album.badge.isNotBlank()) {
                        item { MetadataPill(text = album.badge) }
                    }
                    items(detail.chips, key = { it }) { chip ->
                        MetadataPill(text = chip)
                    }
                }
                if (detail.description.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = detail.description,
                        fontFamily = font,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineAlbumBackdrop(
    coverUrl: String
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(42.dp),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.20f),
                            colors.secondary.copy(alpha = 0.10f),
                            colors.surface.copy(alpha = 0.84f),
                            colors.surface
                        )
                    )
                )
        )
    }
}

@Composable
private fun OnlineAlbumSectionHeader(
    title: String,
    subtitle: String
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        MetadataPill(text = subtitle)
    }
}

@Composable
private fun AlbumCover(
    coverUrl: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnlineSongCard(
    song: OnlineSong,
    index: Int,
    dlState: DownloadState,
    isAdded: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    accentColor: Color,
    onDownload: () -> Unit,
    onAdd: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val isDownloading = dlState is DownloadState.Pending || dlState is DownloadState.Progress

    val rowBg = when {
        isSelected -> colors.secondaryContainer.copy(alpha = 0.55f)
        isDownloading -> colors.primaryContainer.copy(alpha = 0.35f)
        isAdded -> colors.tertiaryContainer.copy(alpha = 0.28f)
        else -> Color.Transparent
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg, RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onLongClick() else onPlay()
                    },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (song.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title.ifBlank { "Без названия" },
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(song.artist.ifBlank { "Неизвестный" })
                        if (song.duration.isNotBlank()) {
                            append(" • ")
                            append(song.duration)
                        }
                    },
                    fontFamily = font,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            when {
                isSelectionMode -> {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (isSelected) colors.primary else colors.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary
                    )
                }
                isAdded -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "В библиотеке",
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Скачать",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 76.dp),
            thickness = 0.5.dp,
            color = colors.outlineVariant.copy(alpha = 0.28f)
        )
    }
}


@Composable
private fun OnlineLoadingState() {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.searching))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true,
        speed = 1f
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ищем треки...",
                    fontFamily = font,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Результаты появятся здесь, как только поиск завершится.",
                    fontFamily = font,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = colors.secondaryContainer
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Ничего не найдено",
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (query.isBlank()) {
                        "Попробуйте другой запрос"
                    } else {
                        "По запросу «$query» ничего не нашлось"
                    },
                    fontFamily = font,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


@Composable
private fun ErrorSearchState(
    message: String,
    onRetry: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = colors.errorContainer
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = colors.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Ошибка соединения",
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    fontFamily = font,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Попробовать снова",
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineIdleHint(
    history: List<String>,
    onChipClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero empty — M3 tonal surface
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = colors.secondaryContainer
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Найдите музыку онлайн",
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Введите название трека или артиста, чтобы прослушать результат, скачать его или добавить в библиотеку.",
                    fontFamily = font,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (history.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "Недавние запросы",
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "После первого поиска здесь появятся ваши последние запросы.",
                        fontFamily = font,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Недавние запросы",
                                fontFamily = font,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                            Text(
                                text = "${history.size} сохранено",
                                fontFamily = font,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onClearHistory) {
                            Text(text = "Очистить", fontFamily = font)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { it }) { hint ->
                            HistoryQueryCard(
                                text = hint,
                                onClick = { onChipClick(hint) },
                                onRemove = { onRemoveHistory(hint) }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun HistoryQueryCard(
    text: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                fontFamily = font,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить запрос",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
