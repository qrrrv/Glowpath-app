package com.musicplayer.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.size.Size as CoilSize
import com.musicplayer.ui.components.OptimizedAlbumArt
import com.musicplayer.R
import com.musicplayer.data.InterfaceStyle
import com.musicplayer.data.OnlineAlbumSummary
import com.musicplayer.data.Song
import com.musicplayer.repository.HitmosRepository
import com.musicplayer.ui.components.AutoScrollingText
import com.musicplayer.ui.components.ExpressiveTopBarContent
import com.musicplayer.ui.components.PlayingEqIcon
import com.musicplayer.ui.theme.*
import com.musicplayer.ui.components.instrumentIconRes
import com.musicplayer.viewmodel.MusicViewModel
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// ── Thanos-style pixel disintegration particle helper ────────────────────────
private const val THANOS_COLS = 14
private const val THANOS_ROWS = 6

private data class ThanosParticle(
    val normX: Float,
    val normY: Float,
    val velX: Float,
    val velY: Float,
    val delay: Float,
    val color: Color
)

private fun deleteAnimationDurationMs(style: Int, speed: Float): Int {
    val base = when (style) {
        0 -> 420f
        2 -> 520f
        else -> 620f
    }
    return (base / speed.coerceIn(0.45f, 2.2f)).roundToInt().coerceIn(220, 900)
}

private fun makeThanosParticles(
    cols: Int,
    rows: Int,
    accent: Color,
    style: Int,
    scatter: Float
): List<ThanosParticle> {
    val lighterAccent = Color(
        red   = (accent.red   + (1f - accent.red)   * 0.32f).coerceIn(0f, 1f),
        green = (accent.green + (1f - accent.green) * 0.24f).coerceIn(0f, 1f),
        blue  = (accent.blue  + (1f - accent.blue)  * 0.34f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val desatAccent = Color(
        red   = (accent.red   * 0.56f + 0.24f).coerceIn(0f, 1f),
        green = (accent.green * 0.56f + 0.24f).coerceIn(0f, 1f),
        blue  = (accent.blue  * 0.56f + 0.24f).coerceIn(0f, 1f),
        alpha = 0.82f
    )
    val palette = listOf(accent, lighterAccent, Color.White.copy(alpha = 0.88f), desatAccent)
    val scatterStrength = scatter.coerceIn(0.65f, 1.8f)
    return buildList(cols * rows) {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val normX = (col + 0.5f) / cols.toFloat()
                val normY = (row + 0.5f) / rows.toFloat()
                val delay = when (style) {
                    0 -> (normX * 0.18f + kotlin.random.Random.nextFloat() * 0.08f).coerceIn(0f, 0.28f)
                    2 -> (normX * 0.46f + kotlin.random.Random.nextFloat() * 0.12f).coerceIn(0f, 0.62f)
                    else -> (normX * 0.34f + kotlin.random.Random.nextFloat() * 0.10f).coerceIn(0f, 0.48f)
                }
                val spreadDeg = when (style) {
                    0 -> -8f + kotlin.random.Random.nextFloat() * 20f
                    2 -> -28f + kotlin.random.Random.nextFloat() * 56f
                    else -> -18f + kotlin.random.Random.nextFloat() * 42f
                }
                val spreadRad = (spreadDeg * PI / 180.0).toFloat()
                val speed = when (style) {
                    0 -> 0.08f + kotlin.random.Random.nextFloat() * 0.16f
                    2 -> 0.16f + kotlin.random.Random.nextFloat() * 0.24f
                    else -> 0.18f + kotlin.random.Random.nextFloat() * 0.34f
                } * scatterStrength
                val baseVelX = when (style) {
                    0 -> 0.06f
                    2 -> 0.24f
                    else -> 0.18f
                }
                val baseVelY = when (style) {
                    0 -> -0.02f
                    2 -> -0.10f
                    else -> -0.08f
                }
                add(ThanosParticle(
                    normX = normX, normY = normY,
                    velX = (baseVelX + cos(spreadRad) * speed).coerceAtLeast(0.04f),
                    velY = sin(spreadRad) * speed + baseVelY,
                    delay = delay, color = palette[(col * 3 + row * 7) % palette.size]
                ))
            }
        }
    }
}

// ── Scrollbar ─────────────────────────────────────────────────────────────────
/** Lightweight scrollbar — no animate*AsState (those recompose every scroll frame). */
fun Modifier.simpleVerticalScrollbar(state: LazyListState, width: Dp = 4.dp, color: Color = Color.White.copy(alpha = 0.5f)): Modifier {
    return drawWithContent {
        drawContent()
        val layoutInfo = state.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleCount = visible.size.coerceAtLeast(1)
        if (totalItems == 0 || totalItems <= visibleCount) return@drawWithContent
        val firstVisible = visible.firstOrNull() ?: return@drawWithContent
        val estimatedItemSize = firstVisible.size.takeIf { it > 0 }?.toFloat() ?: 1f
        val maxScrollableItems = (totalItems - visibleCount).coerceAtLeast(1)
        val preciseIndex = firstVisible.index + (state.firstVisibleItemScrollOffset / estimatedItemSize).coerceIn(0f, 1f)
        val progress = (preciseIndex / maxScrollableItems.toFloat()).coerceIn(0f, 1f)
        val visibleFraction = (visibleCount / totalItems.toFloat()).coerceIn(0.08f, 1f)
        val alpha = if (state.isScrollInProgress) 0.85f else 0.22f
        val sbW = with(density) { width.toPx() }
        val trackLeft = size.width - sbW
        val handleHeight = (size.height * visibleFraction).coerceIn(with(density) { 48.dp.toPx() }, size.height)
        val top = ((size.height - handleHeight) * progress).coerceIn(0f, size.height - handleHeight)
        drawRoundRect(
            color = color.copy(alpha = 0.10f * alpha),
            topLeft = Offset(trackLeft, 0f),
            size = Size(sbW, size.height),
            cornerRadius = CornerRadius(sbW / 2f)
        )
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(trackLeft, top),
            size = Size(sbW, handleHeight),
            cornerRadius = CornerRadius(sbW / 2f)
        )
    }
}

// ── HomeScreen ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onSongClick: (Song) -> Unit,
    onSettingsClick: () -> Unit,
    onOnlineAlbumClick: (OnlineAlbumSummary) -> Unit = {}
) {
    val c = MaterialTheme.colorScheme
    val songs           by viewModel.songs.collectAsState()
    val currentSong     by viewModel.currentSong.collectAsState()
    val isPlaying       by viewModel.isPlaying.collectAsState()
    val customArtMap    by viewModel.customArtMap.collectAsState()
    val customTitleMap  by viewModel.customTitleMap.collectAsState()
    val customArtistMap by viewModel.customArtistMap.collectAsState()
    val songColors      by viewModel.songColors.collectAsState()
    val settings        by viewModel.settings.collectAsState()
    val topBar          by viewModel.topBarSettings.collectAsState()
    var showMenu        by remember { mutableStateOf(false) }
    var searchQuery     by remember { mutableStateOf("") }
    var isSearching     by remember { mutableStateOf(false) }
    var songToDelete    by remember { mutableStateOf<Song?>(null) }
    var deletingIds     by remember { mutableStateOf(setOf<Long>()) }
    var selectedIds     by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    val listState       = rememberLazyListState()
    // Telegram-style: do NOT gate image loading on a derived visible-id set.
    // That set changes every scroll frame and forces recomposition of every row.
    // LazyColumn already only composes nearby items; Coil loads only when composed.

    val scope           = rememberCoroutineScope()
    val deleteAnimDurationMs = remember(settings.trackDeleteAnimStyle, settings.animParams.deleteAnimSpeed) {
        deleteAnimationDurationMs(settings.trackDeleteAnimStyle, settings.animParams.deleteAnimSpeed)
    }

    // ── Auto-scroll placeholders — actual logic is after filteredSongs ─────────
    var prevSongId    by remember { mutableLongStateOf(-1L) }
    var justChangedId by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(songs) {
        // Debounce: only warm palette after songs settle (avoids thrashing on bulk scan)
        kotlinx.coroutines.delay(100)
        viewModel.warmPaletteCache(songs)
    }

    // Если добавились новые песни — запускаем фоновую загрузку текстов для них
    LaunchedEffect(songs.size) {
        if (songs.isNotEmpty()) viewModel.prefetchLyricsForLibrary()
    }

    val sortedSongs = remember(songs, settings.sortOrder) {
        when (settings.sortOrder) {
            com.musicplayer.data.SortOrder.TITLE      -> songs.sortedBy { it.title.lowercase() }
            com.musicplayer.data.SortOrder.ARTIST     -> songs.sortedBy { it.artist.lowercase() }
            com.musicplayer.data.SortOrder.ALBUM      -> songs.sortedBy { it.album.lowercase() }
            com.musicplayer.data.SortOrder.DURATION   -> songs.sortedBy { it.duration }
            com.musicplayer.data.SortOrder.DATE_ADDED -> songs.sortedByDescending { it.id }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.scanAndAddAllMusic {}
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.addSongFromUri(it) }
    }

    val filteredSongs by remember(sortedSongs, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) sortedSongs
            else sortedSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val artistCount = remember(sortedSongs) {
        sortedSongs
            .map { it.artist.trim() }
            .filter { it.isNotBlank() && it != "<unknown>" }
            .distinct()
            .size
    }
    val albumCount = remember(sortedSongs) {
        sortedSongs
            .map { it.album.trim() }
            .filter { it.isNotBlank() && it != "<unknown>" }
            .distinct()
            .size
    }
    val totalDurationMs = remember(sortedSongs) { sortedSongs.sumOf { it.duration } }
    val recentlyAddedSongs = remember(songs) { songs.sortedByDescending { it.id }.take(6) }
    val dashboardAccent = remember(currentSong?.id, songColors, c.accent) {
        currentSong?.let { songColors[it.id] } ?: c.accent
    }
    val randomAlbumSeed = remember { System.currentTimeMillis() }
    var randomAlbums by remember(randomAlbumSeed, settings.showRandomOnlineAlbumsShelf) {
        mutableStateOf(emptyList<OnlineAlbumSummary>())
    }
    var randomAlbumsLoading by remember(randomAlbumSeed, settings.showRandomOnlineAlbumsShelf) {
        mutableStateOf(false)
    }

    // ── Auto-scroll + flash when current track changes ────────────────────────
    LaunchedEffect(currentSong?.id) {
        val newId = currentSong?.id ?: return@LaunchedEffect
        if (newId == prevSongId) return@LaunchedEffect
        prevSongId = newId
        justChangedId = newId
        if (settings.listScrollAnim) {
            val idx = filteredSongs.indexOfFirst { it.id == newId }
            if (idx >= 0) {
                val visFirst = listState.firstVisibleItemIndex
                val visLast  = visFirst + listState.layoutInfo.visibleItemsInfo.size
                if (idx !in visFirst..visLast) {
                    listState.animateScrollToItem((idx - 3).coerceAtLeast(0))
                }
            }
        }
        delay(900)
        justChangedId = -1L
    }

    LaunchedEffect(settings.showRandomOnlineAlbumsShelf, randomAlbumSeed) {
        if (!settings.showRandomOnlineAlbumsShelf) {
            randomAlbums = emptyList()
            randomAlbumsLoading = false
            return@LaunchedEffect
        }
        randomAlbumsLoading = true
        val random = Random(randomAlbumSeed)
        val loadedAlbums = HOME_RANDOM_ALBUM_QUERIES
            .shuffled(random)
            .take(3)
            .flatMap { query ->
                runCatching { HitmosRepository.search(query).albums }.getOrDefault(emptyList())
            }
            .distinctBy { it.albumUrl }
            .shuffled(Random(randomAlbumSeed + 91L))
            .take(8)
        randomAlbums = loadedAlbums
        randomAlbumsLoading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                HomeTopBar(
                    topBar           = topBar,
                    c                = c,
                    isSearching      = isSearching,
                    isSelectionMode  = isSelectionMode,
                    selectedCount    = selectedIds.size,
                    sortedSongsCount = sortedSongs.size,
                    searchQuery      = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchToggle   = { isSearching = !isSearching; if (!isSearching) searchQuery = "" },
                    onClearSearch    = { searchQuery = "" },
                    onClearSelection = { selectedIds = emptySet() },
                    onSelectAll      = { selectedIds = filteredSongs.map { it.id }.toSet() },
                    onDeleteSelected = {
                        val toDeleteIds = selectedIds.toSet()
                        val toDelete = songs.filter { it.id in toDeleteIds }
                        deletingIds = deletingIds + toDeleteIds
                        selectedIds = emptySet()
                        scope.launch {
                            delay(deleteAnimDurationMs.toLong() + 60L)
                            toDelete.forEach { viewModel.removeSong(it) }
                            deletingIds = deletingIds - toDeleteIds
                        }
                    },
                    showMenu         = showMenu,
                    onMenuToggle     = { showMenu = !showMenu },
                    onMenuDismiss    = { showMenu = false },
                    onAddFile        = { showMenu = false; fileLauncher.launch(arrayOf("audio/*")) },
                    onScanMusic      = {
                        showMenu = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                            permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_AUDIO)
                        else
                            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    },
                    onSettings       = { showMenu = false; onSettingsClick() }
                )
            }
        }
    ) { padding ->
        if (sortedSongs.isEmpty()) {
            EmptyState(
                onAdd  = { fileLauncher.launch(arrayOf("audio/*")) },
                onScan = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                    else
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            val isEmptySearch = filteredSongs.isEmpty() && searchQuery.isNotBlank()
            if (isEmptySearch) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.SearchOff, null, tint = c.textDisabled, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Ничего не найдено", color = c.textDisabled, fontFamily = LocalAppFontFamily.current, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("Попробуйте другой запрос", color = c.textDisabled.copy(alpha = 0.6f), fontFamily = LocalAppFontFamily.current, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .simpleVerticalScrollbar(listState, color = c.accent.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(
                        top  = padding.calculateTopPadding() + 8.dp,
                        bottom = 120.dp
                    ),
                    // Fast fling for smooth scroll in large libraries
                    flingBehavior = ScrollableDefaults.flingBehavior()
                ) {
                    if (searchQuery.isBlank()) {
                        item("dashboard_header") {
                            LibraryDashboardHeader(
                                currentSong = currentSong,
                                displayTitle = currentSong?.let { customTitleMap[it.id] ?: it.title },
                                displayArtist = currentSong?.let { customArtistMap[it.id] ?: it.artist },
                                displayArtUri = currentSong?.let { customArtMap[it.id] ?: it.albumArtUri },
                                isPlaying = isPlaying,
                                songsCount = sortedSongs.size,
                                artistCount = artistCount,
                                albumCount = albumCount,
                                totalDurationMs = totalDurationMs,
                                accentColor = dashboardAccent,
                                onCurrentSongClick = {
                                    currentSong?.let(onSongClick)
                                }
                            )
                        }
                        if (settings.showRandomOnlineAlbumsShelf) {
                            item("random_online_albums") {
                                if (randomAlbumsLoading || randomAlbums.isNotEmpty()) {
                                    RandomOnlineAlbumsShelf(
                                        albums = randomAlbums,
                                        isLoading = randomAlbumsLoading,
                                        onAlbumClick = onOnlineAlbumClick
                                    )
                                } else if (recentlyAddedSongs.isNotEmpty()) {
                                    RecentlyAddedShelf(
                                        songs = recentlyAddedSongs,
                                        customArtMap = customArtMap,
                                        customTitleMap = customTitleMap,
                                        customArtistMap = customArtistMap,
                                        onSongClick = onSongClick
                                    )
                                }
                            }
                        } else if (recentlyAddedSongs.isNotEmpty()) {
                            item("recently_added") {
                                RecentlyAddedShelf(
                                    songs = recentlyAddedSongs,
                                    customArtMap = customArtMap,
                                    customTitleMap = customTitleMap,
                                    customArtistMap = customArtistMap,
                                    onSongClick = onSongClick
                                )
                            }
                        }
                    }

                    item(if (searchQuery.isBlank()) "tracks_header" else "search_results_header") {
                        LibrarySectionHeader(
                            title = if (searchQuery.isBlank()) "Все треки" else "Результаты поиска",
                            subtitle = if (searchQuery.isBlank()) {
                                "Полная библиотека в новом Material 3 стиле"
                            } else {
                                "${filteredSongs.size} ${pluralTracks(filteredSongs.size)}"
                            }
                        )
                    }

                    itemsIndexed(
                        filteredSongs,
                        key         = { _, song -> song.id },
                        contentType = { _, _ -> "song" }
                    ) { index, song ->
                        // Lightweight per-row reads — no derivedStateOf (that still tracks and can churn)
                        val isDeleting = song.id in deletingIds
                        val isSelected = song.id in selectedIds
                        val isCurrent  = song.id == currentSong?.id
                        val customArt   = customArtMap[song.id]
                        val customTitle = customTitleMap[song.id]
                        val customArtist= customArtistMap[song.id]
                        val isM3 = LocalAppStyle.current.interfaceStyle == InterfaceStyle.MATERIAL3

                        // Telegram-style: list cells are dumb. No entry animations, no art visibility set.
                        // LazyColumn only composes nearby rows; Coil loads only when the cell is composed.
                        if (isM3 && !isDeleting) {
                            Material3SongRow(
                                song = song,
                                isCurrentSong = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - song.id else selectedIds + song.id
                                    } else {
                                        onSongClick(song)
                                    }
                                },
                                onLongClick = { selectedIds = selectedIds + song.id },
                                customArtUri = customArt,
                                shouldLoadArt = true,
                                customTitle = customTitle,
                                customArtist = customArtist,
                                showDuration = settings.showDurationInList,
                                showTrackNumber = settings.showTrackNumber,
                                trackIndex = index,
                                artCornerRadius = settings.trackItemCornerRadius,
                                artSize = settings.trackArtSize,
                                showAlbum = settings.showAlbumInList,
                                albumName = song.album,
                                uppercaseTitles = settings.uppercaseTitles,
                                titleFontSize = settings.trackListTitleSize,
                                artistFontSize = settings.trackListArtistSize
                            )
                        } else {
                            SongRow(
                                song         = song,
                                isCurrentSong = isCurrent,
                                isPlaying    = isPlaying,
                                isSelected   = isSelected,
                                isSelectionMode = isSelectionMode,
                                justChanged  = song.id == justChangedId,
                                onClick      = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - song.id else selectedIds + song.id
                                    } else {
                                        onSongClick(song)
                                    }
                                },
                                onLongClick  = {
                                    selectedIds = selectedIds + song.id
                                },
                                accentColor  = c.accent,
                                cachedColor  = songColors[song.id],
                                customArtUri = customArt,
                                shouldLoadArt = true,
                                customTitle  = customTitle,
                                customArtist = customArtist,
                                isDeleting   = isDeleting,
                                rowPressAnim = settings.rowPressAnim,
                                listItemEntryAnim = 0, // never animate entry during list scroll
                                buttonPressStyle  = settings.buttonPressStyle,
                                rowIndex          = index,
                                animParams        = settings.animParams,
                                titleFontSize   = settings.trackListTitleSize,
                                artistFontSize  = settings.trackListArtistSize,
                                boldTitles      = settings.boldTitles,
                                uppercaseTitles = settings.uppercaseTitles,
                                letterSpacingEm = settings.letterSpacingEm,
                                lineHeightScale = settings.lineHeightScale,
                                artistNameStyle = settings.artistNameStyle,
                                showDuration    = settings.showDurationInList,
                                showTrackNumber = settings.showTrackNumber,
                                trackIndex      = index,
                                itemDensity     = settings.trackItemDensity,
                                artCornerRadius = settings.trackItemCornerRadius,
                                artSize         = settings.trackArtSize,
                                glowOnNowPlaying= settings.glowOnNowPlaying,
                                nowPlayingGlowStrength = settings.nowPlayingGlowStrength,
                                showAlbum       = settings.showAlbumInList,
                                albumName       = song.album,
                                metaOpacity     = settings.trackMetaOpacity,
                                artistLetterSpacingEm = settings.artistLetterSpacingEm,
                                trackTextAlign  = settings.trackTextAlign,
                                itemPaddingScale = settings.trackItemPaddingScale,
                                trackMetaCapsule = settings.trackMetaCapsule,
                                trackTitleWeightMode = settings.trackTitleWeightMode,
                                trackMetaWeightMode = settings.trackMetaWeightMode,
                                trackTitleItalic = settings.trackTitleItalic,
                                trackTitleOpacity = settings.trackTitleOpacity,
                                trackMetaUppercase = settings.trackMetaUppercase,
                                trackMetaSpacingScale = settings.trackMetaSpacingScale,
                                trackTitleTwoLines = settings.trackTitleTwoLines,
                                durationBadgeStyle = settings.durationBadgeStyle,
                                trackTitleAccentBlend = settings.trackTitleAccentBlend,
                                trackTitleDecorStyle = settings.trackTitleDecorStyle,
                                trackMetaSeparatorStyle = settings.trackMetaSeparatorStyle,
                                deleteAnimStyle = settings.trackDeleteAnimStyle,
                            )
                            if (!isDeleting && index < filteredSongs.lastIndex) {
                                HorizontalDivider(color = c.divider.copy(alpha = 0.3f), modifier = Modifier.padding(start = 82.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить трек?", color = MaterialTheme.colorScheme.onSurface, fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.Bold) },
            text  = { Text("«${song.title}» будет удален из списка", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = LocalAppFontFamily.current) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingIds += song.id
                        scope.launch {
                            delay(deleteAnimDurationMs.toLong() + 40L)
                            viewModel.removeSong(song)
                            deletingIds -= song.id
                        }
                        songToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape  = MaterialTheme.shapes.medium
                ) { Text("Удалить", fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = LocalAppFontFamily.current)
                }
            }
        )
    }
}

// ── SongRow ──────────────────────────────────────────────────────────────────
// Optimised: no per-row bitmap I/O, no animateColorAsState, stable keys.
@Composable
private fun SongRow(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    justChanged: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    accentColor: Color,
    cachedColor: Color?,
    customArtUri: android.net.Uri? = null,
    shouldLoadArt: Boolean = true,
    customTitle: String?  = null,
    customArtist: String? = null,
    isDeleting: Boolean   = false,
    rowPressAnim: Boolean = true,
    listItemEntryAnim: Int = 0,
    buttonPressStyle: Int  = 0,
    rowIndex: Int          = 0,
    animParams: com.musicplayer.data.AnimParams = com.musicplayer.data.AnimParams(),
    // ── Типографика ──────────────────────────────────────────────────────────
    titleFontSize: Float    = 15f,
    artistFontSize: Float   = 12f,
    boldTitles: Boolean     = false,
    uppercaseTitles: Boolean= false,
    letterSpacingEm: Float  = 0f,
    lineHeightScale: Float  = 1.0f,
    artistNameStyle: Int    = 0,
    showDuration: Boolean   = true,
    showTrackNumber: Boolean= false,
    trackIndex: Int         = 0,
    itemDensity: Int        = 1,
    artCornerRadius: Float  = 12f,
    artSize: Float          = 52f,
    glowOnNowPlaying: Boolean = true,
    nowPlayingGlowStrength: Float = 0.78f,
    showAlbum: Boolean      = false,
    albumName: String       = "",
    metaOpacity: Float      = 0.78f,
    artistLetterSpacingEm: Float = 0f,
    trackTextAlign: Int     = 0,
    itemPaddingScale: Float = 1.0f,
    trackMetaCapsule: Boolean = false,
    trackTitleWeightMode: Int = 2,
    trackMetaWeightMode: Int = 1,
    trackTitleItalic: Boolean = false,
    trackTitleOpacity: Float = 1.0f,
    trackMetaUppercase: Boolean = false,
    trackMetaSpacingScale: Float = 1.0f,
    trackTitleTwoLines: Boolean = false,
    durationBadgeStyle: Int = 0,
    trackTitleAccentBlend: Float = 0f,
    trackTitleDecorStyle: Int = 0,
    trackMetaSeparatorStyle: Int = 0,
    deleteAnimStyle: Int = 1,
) {
    val c = MaterialTheme.colorScheme
    val appStyle = LocalAppStyle.current
    val isMaterial3 = appStyle.interfaceStyle == InterfaceStyle.MATERIAL3

    // ── FAST PATH: Material 3 list row — zero animation state, minimal recomposition ──
    if (isMaterial3 && !isDeleting) {
        Material3SongRow(
            song = song,
            isCurrentSong = isCurrentSong,
            isPlaying = isPlaying,
            isSelected = isSelected,
            isSelectionMode = isSelectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
            customArtUri = customArtUri,
            shouldLoadArt = shouldLoadArt,
            customTitle = customTitle,
            customArtist = customArtist,
            showDuration = showDuration,
            showTrackNumber = showTrackNumber,
            trackIndex = trackIndex,
            artCornerRadius = artCornerRadius,
            artSize = artSize,
            showAlbum = showAlbum,
            albumName = albumName,
            uppercaseTitles = uppercaseTitles,
            titleFontSize = titleFontSize,
            artistFontSize = artistFontSize
        )
        return
    }


    val rowAccent = cachedColor ?: accentColor
    val rowShape = RoundedCornerShape(appStyle.cardCornerRadius.dp.coerceIn(18.dp, 28.dp))
    val listTextAlign = when (trackTextAlign) {
        1 -> androidx.compose.ui.text.style.TextAlign.Center
        2 -> androidx.compose.ui.text.style.TextAlign.End
        else -> androidx.compose.ui.text.style.TextAlign.Start
    }
    val columnAlignment = when (trackTextAlign) {
        1 -> Alignment.CenterHorizontally
        2 -> Alignment.End
        else -> Alignment.Start
    }
    val densityScale = itemPaddingScale.coerceIn(0.82f, 1.35f)
    val rowVerticalPadding = when (itemDensity) {
        0 -> 6.dp * densityScale
        2 -> 14.dp * densityScale
        else -> 10.dp * densityScale
    }
    val rowHeightDp = ((if (trackTitleTwoLines) 84.dp else 72.dp) * densityScale).coerceAtLeast(if (trackTitleTwoLines) 76.dp else 64.dp)
    val currentGlowStrength = nowPlayingGlowStrength.coerceIn(0.18f, 1.4f)
    val metaAlpha = metaOpacity.coerceIn(0.35f, 1f)
    val titleAlpha = trackTitleOpacity.coerceIn(0.35f, 1f)
    val metaSpacing = (3.dp * trackMetaSpacingScale.coerceIn(0.7f, 1.8f)).coerceIn(2.dp, 8.dp)
    val titleAccentBlend = trackTitleAccentBlend.coerceIn(0f, 0.82f)
    val deleteDurationMs = deleteAnimationDurationMs(deleteAnimStyle, animParams.deleteAnimSpeed)
    val deleteScatter = animParams.deleteScatter.coerceIn(0.65f, 1.8f)
    val deleteParticleDensity = animParams.deleteParticleDensity.coerceIn(0.6f, 1.6f)
    val deleteCols = (THANOS_COLS * deleteParticleDensity).roundToInt().coerceIn(10, 28)
    val deleteRowsBase = if (trackTitleTwoLines) THANOS_ROWS + 2 else THANOS_ROWS
    val deleteRows = (deleteRowsBase * deleteParticleDensity.coerceIn(0.8f, 1.3f)).roundToInt().coerceIn(6, 12)
    val resolvedTitleWeight = when (trackTitleWeightMode) {
        0 -> FontWeight.Normal
        1 -> FontWeight.Medium
        3 -> FontWeight.Bold
        4 -> FontWeight.Black
        else -> FontWeight.SemiBold
    }
    val boostedTitleWeight = when {
        boldTitles && resolvedTitleWeight == FontWeight.Normal -> FontWeight.Medium
        boldTitles && resolvedTitleWeight == FontWeight.Medium -> FontWeight.SemiBold
        boldTitles && resolvedTitleWeight == FontWeight.SemiBold -> FontWeight.Bold
        boldTitles && resolvedTitleWeight == FontWeight.Bold -> FontWeight.ExtraBold
        else -> resolvedTitleWeight
    }
    val currentTitleWeight = when (boostedTitleWeight) {
        FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black -> FontWeight.ExtraBold
        else -> boostedTitleWeight
    }
    val resolvedMetaWeight = when (trackMetaWeightMode) {
        0 -> FontWeight.Normal
        2 -> FontWeight.SemiBold
        3 -> FontWeight.Bold
        else -> FontWeight.Medium
    }
    val titleBaseColor = if (isCurrentSong) {
        lerp(rowAccent, Color.White.copy(alpha = 0.96f), 0.10f).copy(alpha = titleAlpha)
    } else {
        lerp(c.textPrimary, rowAccent, titleAccentBlend).copy(alpha = titleAlpha)
    }
    val titleDecorShape = RoundedCornerShape(16.dp)
    val titleDecorModifier = when (trackTitleDecorStyle) {
        2 -> Modifier
            .clip(titleDecorShape)
            .background(rowAccent.copy(alpha = if (isCurrentSong) 0.18f else 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
        3 -> Modifier
            .clip(titleDecorShape)
            .let { modifier ->
                if (isMaterial3) {
                    modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isCurrentSong) 0.9f else 0.78f))
                } else {
                    modifier.background(
                        Brush.horizontalGradient(
                            listOf(
                                rowAccent.copy(alpha = 0.16f),
                                c.bgElevated.copy(alpha = 0.84f),
                                c.bgDeep.copy(alpha = 0.72f)
                            )
                        )
                    )
                }
            }
            .border(1.dp, if (isMaterial3) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f) else rowAccent.copy(alpha = 0.16f), titleDecorShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
        else -> Modifier
    }
    val metaSeparator = when (trackMetaSeparatorStyle) {
        1 -> " / "
        2 -> " ~ "
        3 -> " ✦ "
        else -> " · "
    }
    // ── Sweep flash animation when track changes ──────────────────────────────
    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(justChanged) {
        if (justChanged) {
            sweepProgress.snapTo(0f)
            sweepProgress.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
            sweepProgress.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        }
    }

    // ── Selection animation ───────────────────────────────────────────────────
    // Animated bounce scale on selection change
    val selBounce = remember { Animatable(1f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            selBounce.animateTo(0.93f, tween(80, easing = FastOutSlowInEasing))
            selBounce.animateTo(1.04f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            selBounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        } else {
            selBounce.animateTo(0.96f, tween(80))
            selBounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // Checkmark draw-in animation
    val checkProgress = remember { Animatable(0f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            checkProgress.snapTo(0f)
            checkProgress.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))
        } else {
            checkProgress.animateTo(0f, animationSpec = tween(120, easing = FastOutLinearInEasing))
        }
    }
    // Animated selection glow pulse — only runs when selected to avoid 200+ infinite animations
    val selGlowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.32f else 0.18f,
        animationSpec = if (isSelected) tween(600, easing = FastOutSlowInEasing) else tween(200),
        label = "selGlowA"
    )
    val selHighlight by animateColorAsState(
        targetValue = if (isSelected) rowAccent.copy(alpha = selGlowAlpha) else rowAccent.copy(alpha = 0.18f),
        animationSpec = tween(250), label = "selHL"
    )
    val highlightColor = when {
        isSelected    -> selHighlight
        isCurrentSong -> rowAccent.copy(alpha = 0.18f)
        else          -> Color.Transparent
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // listItemEntryAnim: появление строки при первом рендере
    var entryVisible by remember { mutableStateOf(listItemEntryAnim == 0) }

    val entryDurMs = (280f / animParams.listItemSpeed.coerceIn(0.2f, 4f)).toInt().coerceAtLeast(50)
    val cascadeDelayMs = ((rowIndex % 8) * 30f * animParams.listItemDelay).toLong().coerceIn(0, 500)
    val listEasing = when (animParams.listItemEasing) {
        1 -> FastOutSlowInEasing
        2 -> FastOutLinearInEasing
        3 -> LinearEasing
        4 -> CubicBezierEasing(0.2f, 1.25f, 0.38f, 1f)
        5 -> CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
        else -> FastOutSlowInEasing
    }
    LaunchedEffect(Unit) {
        if (listItemEntryAnim != 0) {
            delay(cascadeDelayMs)
            entryVisible = true
        }
    }
    val entryOffsetX by animateFloatAsState(
        targetValue = if (entryVisible || listItemEntryAnim == 0) 0f else when (listItemEntryAnim) {
            1, 3, 4 -> -0.3f
            5 -> 0.15f
            8 -> -0.16f
            else -> 0f
        },
        animationSpec = when (listItemEntryAnim) {
            3 -> spring(when(animParams.listItemDamping){1->Spring.DampingRatioLowBouncy;2->Spring.DampingRatioHighBouncy;else->0.45f}, Spring.StiffnessMediumLow)
            else -> tween(entryDurMs, easing = listEasing)
        }, label = "entryX"
    )
    val entryAlpha by animateFloatAsState(
        targetValue = if (entryVisible || listItemEntryAnim == 0) 1f else 0f,
        animationSpec = tween((entryDurMs * 0.85f).toInt(), easing = listEasing),
        label = "entryA"
    )
    val entryScale by animateFloatAsState(
        targetValue = if (entryVisible || listItemEntryAnim == 0) 1f else when (listItemEntryAnim) {
            4 -> 0.7f
            5 -> 1.1f
            7 -> 0.88f
            8 -> 0.95f
            9 -> 1.06f
            else -> 1f
        },
        animationSpec = when (listItemEntryAnim) {
            3, 4 -> spring(when(animParams.listItemDamping){1->Spring.DampingRatioLowBouncy;2->Spring.DampingRatioHighBouncy;else->0.5f}, Spring.StiffnessMediumLow)
            else -> tween(entryDurMs, easing = listEasing)
        }, label = "entrySc"
    )
    val entryOffsetY by animateFloatAsState(
        targetValue = if (entryVisible || listItemEntryAnim == 0) 0f else when (listItemEntryAnim) {
            6 -> 0.28f
            7 -> 0.16f
            else -> 0f
        },
        animationSpec = when (listItemEntryAnim) {
            7 -> spring(when(animParams.listItemDamping){1->Spring.DampingRatioLowBouncy;2->Spring.DampingRatioHighBouncy;else->0.55f}, Spring.StiffnessMediumLow)
            else -> tween(entryDurMs, easing = listEasing)
        },
        label = "entryY"
    )

    val pressStrength = animParams.buttonStrength.coerceIn(0.3f, 2.0f)
    val pressSpeedMs = (120f / animParams.buttonSpeed.coerceIn(0.2f, 3f)).toInt().coerceAtLeast(30)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && rowPressAnim) when (buttonPressStyle) {
            1 -> 1f + 0.04f * pressStrength
            3 -> 1f - 0.07f * pressStrength
            4 -> 1f - 0.05f * pressStrength
            5 -> 1f - 0.04f * pressStrength
            else -> 1f - 0.03f * pressStrength
        } else 1f,
        animationSpec = when (buttonPressStyle) {
            3, 4, 5 -> spring(0.3f, Spring.StiffnessMedium)
            else -> tween(pressSpeedMs)
        }, label = "pScale"
    )
    val pressGlow by animateFloatAsState(
        targetValue = if (isPressed && (buttonPressStyle == 2 || buttonPressStyle == 4)) 1f else 0f,
        animationSpec = tween(150), label = "pGlow"
    )
    val pressRotation by animateFloatAsState(
        targetValue = if (isPressed && rowPressAnim && buttonPressStyle == 5) -8f * pressStrength.coerceAtMost(1.2f) else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "pRot"
    )
    val pressLift by animateFloatAsState(
        targetValue = if (isPressed && rowPressAnim && (buttonPressStyle == 4 || buttonPressStyle == 5)) -6f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "pLift"
    )
    val rowContainerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isCurrentSong && glowOnNowPlaying -> MaterialTheme.colorScheme.primaryContainer
        isPressed -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val rowBorderColor = when {
        isSelected -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.54f)
        isCurrentSong -> MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    }



    // Thanos — cached per song.id; only materialises on deletion
    val particles = remember(song.id) { mutableStateOf<List<ThanosParticle>>(emptyList()) }
    val particleProgress = remember { Animatable(0f) }

    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            particles.value = makeThanosParticles(deleteCols, deleteRows, rowAccent, deleteAnimStyle, deleteScatter)
            particleProgress.snapTo(0f)
            particleProgress.animateTo(1f, animationSpec = tween(deleteDurationMs, easing = LinearOutSlowInEasing))
        } else {
            particleProgress.snapTo(0f)
            particles.value = emptyList()
        }
    }

    val contentAlphaTarget = if (isDeleting) 0f else 1f
    val contentScaleTarget = if (isDeleting) when (deleteAnimStyle) {
        0 -> 0.98f
        2 -> 0.90f
        else -> 0.94f
    } else 1f
    val collapseDelay = when (deleteAnimStyle) {
        0 -> (deleteDurationMs * 0.42f).roundToInt()
        2 -> (deleteDurationMs * 0.50f).roundToInt()
        else -> (deleteDurationMs * 0.54f).roundToInt()
    }
    val contentAlpha by animateFloatAsState(
        contentAlphaTarget,
        tween((deleteDurationMs * 0.34f).roundToInt().coerceAtLeast(160), easing = FastOutLinearInEasing),
        label = "cAlpha"
    )
    val contentScale by animateFloatAsState(
        contentScaleTarget,
        tween((deleteDurationMs * 0.36f).roundToInt().coerceAtLeast(180), easing = FastOutLinearInEasing),
        label = "cScale"
    )
    val rowHeight by animateDpAsState(
        if (isDeleting) 0.dp else rowHeightDp,
        tween(
            delayMillis = collapseDelay,
            durationMillis = (deleteDurationMs * 0.34f).roundToInt().coerceAtLeast(200),
            easing = FastOutSlowInEasing
        ),
        label = "rowH"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .then(if (isDeleting) Modifier.height(rowHeight) else Modifier)
            .clip(rowShape)
    ) {
        // Left accent strip — only non-current rows, static color (no brush)
        if (!isDeleting && !isCurrentSong) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(rowHeightDp)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, rowAccent.copy(alpha = 0.45f), Color.Transparent))
                    )
            )
        }

        if (pressGlow > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeightDp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = if (buttonPressStyle == 4) pressGlow * 0.22f else pressGlow * 0.14f
                        scaleX = if (buttonPressStyle == 4) 1f + pressGlow * 0.16f else 1f
                        scaleY = if (buttonPressStyle == 4) 1f + pressGlow * 0.16f else 1f
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(c.accent.copy(alpha = 0.35f), Color.Transparent),
                            radius = 520f
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .graphicsLayer {
                    scaleX = pressScale * contentScale * selBounce.value * entryScale
                    scaleY = pressScale * contentScale * selBounce.value * entryScale
                    translationX = entryOffsetX * size.width
                    translationY = entryOffsetY * size.height + pressLift
                    rotationZ = pressRotation
                    alpha  = contentAlpha * entryAlpha
                    if (pressGlow > 0f) shadowElevation = 12f * pressGlow
                }
                .let { modifier ->
                    if (isMaterial3) {
                        modifier.background(rowContainerColor, rowShape)
                    } else {
                        modifier.background(
                            when {
                                isSelected -> Brush.linearGradient(
                                    listOf(
                                        highlightColor.copy(alpha = 0.34f),
                                        c.bgElevated.copy(alpha = 0.96f),
                                        c.bgCard.copy(alpha = 0.90f)
                                    )
                                )
                                isCurrentSong && glowOnNowPlaying -> Brush.linearGradient(
                                    listOf(
                                        rowAccent.copy(alpha = (0.16f + currentGlowStrength * 0.16f).coerceIn(0.12f, 0.36f)),
                                        c.bgElevated.copy(alpha = 0.94f),
                                        c.bgSurface.copy(alpha = 0.90f)
                                    )
                                )
                                isPressed -> Brush.linearGradient(
                                    listOf(
                                        rowAccent.copy(alpha = 0.16f),
                                        c.bgElevated.copy(alpha = 0.96f),
                                        c.bgCard.copy(alpha = 0.92f)
                                    )
                                )
                                else -> Brush.linearGradient(
                                    listOf(
                                        c.bgCard.copy(alpha = 0.94f),
                                        c.bgSurface.copy(alpha = 0.84f),
                                        c.bgDeep.copy(alpha = 0.92f)
                                    )
                                )
                            }
                        )
                    }
                }
                .border(
                    1.dp,
                    if (isMaterial3) rowBorderColor else when {
                        isSelected -> rowAccent.copy(alpha = 0.28f)
                        isCurrentSong -> rowAccent.copy(alpha = 0.20f)
                        else -> c.textPrimary.copy(alpha = 0.06f)
                    },
                    rowShape
                )
                .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = rowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number badge
            if (showTrackNumber && !isSelectionMode) {
                Box(
                    modifier = Modifier.size(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${trackIndex + 1}",
                        color = if (isCurrentSong) rowAccent else c.textDisabled,
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            // Artwork thumbnail
            Box(
                modifier = Modifier.size(artSize.dp).clip(RoundedCornerShape(artCornerRadius.dp))
                    .background(
                        if (isMaterial3) {
                            if (isCurrentSong && glowOnNowPlaying) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        } else if (isCurrentSong && glowOnNowPlaying) {
                            rowAccent.copy(alpha = (0.12f + currentGlowStrength * 0.16f).coerceIn(0.12f, 0.34f))
                        } else {
                            c.bgCard
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val displayUri = if (shouldLoadArt) customArtUri ?: song.albumArtUri else null
                if (displayUri != null) {
                    OptimizedAlbumArt(
                        uri        = displayUri,
                        title      = song.title,
                        modifier   = Modifier.fillMaxSize(),
                        targetSize = CoilSize(156, 156)   // 52 dp × 3× density — never decode more than needed
                    )
                }
                if (isSelectionMode) {
                    // Dim overlay + animated checkbox
                    val overlayAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 0.55f else 0.20f,
                        animationSpec = tween(180), label = "dimA"
                    )
                    val circleScale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.72f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "circleS"
                    )
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(c.bgDeep.copy(alpha = overlayAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer { scaleX = circleScale; scaleY = circleScale }
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (isSelected) rowAccent else Color.Transparent)
                                .border(2.dp, if (isSelected) Color.Transparent else c.textDisabled.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checkProgress.value > 0f) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
                                    val w = size.width; val h = size.height
                                    val p = checkProgress.value
                                    val path = androidx.compose.ui.graphics.Path()
                                    // checkmark: left-bottom to mid-bottom, then to right-top
                                    val p1 = Offset(w * 0.15f, h * 0.52f)
                                    val p2 = Offset(w * 0.42f, h * 0.78f)
                                    val p3 = Offset(w * 0.85f, h * 0.22f)
                                    // First segment 0..0.5, second 0.5..1.0
                                    val seg1End = (p / 0.5f).coerceIn(0f, 1f)
                                    val seg2End = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
                                    val mid1 = Offset(p1.x + (p2.x - p1.x) * seg1End, p1.y + (p2.y - p1.y) * seg1End)
                                    path.moveTo(p1.x, p1.y); path.lineTo(mid1.x, mid1.y)
                                    if (p > 0.5f) {
                                        val mid2 = Offset(p2.x + (p3.x - p2.x) * seg2End, p2.y + (p3.y - p2.y) * seg2End)
                                        path.moveTo(p2.x, p2.y); path.lineTo(mid2.x, mid2.y)
                                    }
                                    drawPath(path, color = c.bgDeep, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                                }
                            }
                        }
                    }
                } else if (isCurrentSong) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(if (displayUri != null) c.bgDeep.copy(alpha = 0.5f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) PlayingEqIcon(isPlaying = true, color = if (displayUri != null) c.textPrimary else rowAccent, modifier = Modifier.size(width = 20.dp, height = 20.dp))
                        else Icon(Icons.Rounded.Pause, null, tint = if (displayUri != null) c.textPrimary else rowAccent, modifier = Modifier.size(22.dp))
                    }
                }
                if (!isSelectionMode && !isCurrentSong && displayUri == null) {
                    Icon(painterResource(instrumentIconRes(song.id)), null, tint = rowAccent.copy(alpha = 0.6f), modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = columnAlignment
            ) {
                val displayTitleText = run {
                    val t = customTitle ?: song.title
                    if (uppercaseTitles) t.uppercase() else t
                }
                val titleLineHeight = (titleFontSize * lineHeightScale * 1.4f).sp
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = columnAlignment
                ) {
                    Box(
                        modifier = titleDecorModifier.fillMaxWidth(),
                        contentAlignment = when (trackTextAlign) {
                            1 -> Alignment.Center
                            2 -> Alignment.CenterEnd
                            else -> Alignment.CenterStart
                        }
                    ) {
                        if (isCurrentSong) {
                            AutoScrollingText(
                                text  = displayTitleText,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = titleFontSize.sp,
                                    fontWeight = currentTitleWeight,
                                    fontStyle = if (trackTitleItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                    color = titleBaseColor,
                                    fontFamily = LocalAppFontFamily.current,
                                    letterSpacing = letterSpacingEm.em,
                                    lineHeight = titleLineHeight,
                                    textAlign = listTextAlign,
                                    shadow = if (glowOnNowPlaying) androidx.compose.ui.graphics.Shadow(
                                        color = rowAccent.copy(alpha = (0.14f + currentGlowStrength * 0.16f).coerceIn(0.1f, 0.32f)),
                                        offset = Offset(0f, 1.5f),
                                        blurRadius = 10f * currentGlowStrength.coerceAtMost(1.2f)
                                    ) else null
                                ),
                                textAlign = listTextAlign,
                                gradientEdgeColor = c.bgElevated,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text       = displayTitleText,
                                color      = titleBaseColor,
                                fontFamily = LocalAppFontFamily.current,
                                fontWeight = boostedTitleWeight,
                                fontStyle = if (trackTitleItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                fontSize   = titleFontSize.sp,
                                letterSpacing = letterSpacingEm.em,
                                lineHeight = titleLineHeight,
                                textAlign = listTextAlign,
                                maxLines   = if (trackTitleTwoLines) 2 else 1,
                                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = if (isCurrentSong && glowOnNowPlaying) androidx.compose.ui.graphics.Shadow(
                                        color = rowAccent.copy(alpha = (0.12f + currentGlowStrength * 0.14f).coerceIn(0.08f, 0.28f)),
                                        offset = Offset(0f, 1.5f),
                                        blurRadius = 8f * currentGlowStrength.coerceAtMost(1.2f)
                                    ) else null
                                )
                            )
                        }
                    }

                    if (trackTitleDecorStyle == 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(
                                        when (trackTextAlign) {
                                            1 -> Alignment.Center
                                            2 -> Alignment.CenterEnd
                                            else -> Alignment.CenterStart
                                        }
                                    )
                                    .width(if (trackTitleTwoLines) 56.dp else 42.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                rowAccent.copy(alpha = 0.12f),
                                                rowAccent.copy(alpha = 0.85f),
                                                rowAccent.copy(alpha = 0.18f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(metaSpacing))
                // Artist + optional album — compact single row
                val artistText = run { val a = customArtist ?: song.artist; if (a != "<unknown>" && a.isNotBlank()) a else "Неизвестный" }
                val artistColor = when(artistNameStyle) {
                    2 -> c.textDisabled
                    else -> c.textSecondary.copy(alpha = metaAlpha)
                }
                val artistFontStyle = if (artistNameStyle == 1) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                val artistMetaTextBase = buildString {
                    append(artistText)
                    if (showAlbum && albumName.isNotBlank() && albumName != "<unknown>") {
                        append(metaSeparator)
                        append(albumName)
                    }
                }
                val artistMetaText = if (trackMetaUppercase) artistMetaTextBase.uppercase() else artistMetaTextBase
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = when (trackTextAlign) {
                        1 -> Arrangement.Center
                        2 -> Arrangement.End
                        else -> Arrangement.Start
                    }
                ) {
                    val artistTextModifier = if (trackMetaCapsule) {
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isMaterial3) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
                                else rowAccent.copy(alpha = 0.10f)
                            )
                            .border(
                                1.dp,
                                if (isMaterial3) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                else rowAccent.copy(alpha = 0.14f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    }
                    Text(
                        text = artistMetaText,
                        color = artistColor,
                        fontFamily = LocalAppFontFamily.current,
                        fontWeight = resolvedMetaWeight,
                        fontStyle = artistFontStyle,
                        fontSize = artistFontSize.sp,
                        letterSpacing = artistLetterSpacingEm.em,
                        textAlign = listTextAlign,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = artistTextModifier.widthIn(max = 280.dp)
                    )
                }
            }
            if (showDuration) {
                Spacer(Modifier.width(10.dp))
                when (durationBadgeStyle) {
                    1, 2 -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (durationBadgeStyle == 2) rowAccent.copy(alpha = 0.16f)
                                    else c.bgDeep.copy(alpha = 0.52f)
                                )
                                .border(
                                    1.dp,
                                    if (durationBadgeStyle == 2) rowAccent.copy(alpha = 0.20f)
                                    else c.textPrimary.copy(alpha = 0.06f),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                song.formattedDuration(),
                                color = if (durationBadgeStyle == 2) rowAccent else c.textSecondary,
                                fontFamily = LocalAppFontFamily.current,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    else -> Text(
                        song.formattedDuration(),
                        color = c.textDisabled.copy(alpha = 0.6f),
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.drag_order_icon), null, tint = c.textDisabled.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
        }

        // Sweep flash overlay — animates left→right when track changes
        if (sweepProgress.value > 0.001f) {
            val sweep = sweepProgress.value
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(rowHeightDp)
                .drawWithContent {
                    drawContent()
                    // Travelling shine sweep
                    val sweepW = size.width * 0.45f
                    val centerX = size.width * sweep
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                rowAccent.copy(alpha = 0.28f * (1f - kotlin.math.abs(sweep - 0.5f) * 2f)),
                                rowAccent.copy(alpha = 0.18f * (1f - kotlin.math.abs(sweep - 0.5f) * 2f)),
                                Color.Transparent
                            ),
                            startX = centerX - sweepW / 2f,
                            endX   = centerX + sweepW / 2f
                        ),
                        size = size
                    )
                }
            ) {}
        }

        // Thanos overlay — only rendered for the row being deleted
        if (isDeleting && particleProgress.value > 0f) {
            val progress = particleProgress.value
            Box(modifier = Modifier.fillMaxWidth().height(rowHeightDp).drawWithContent {
                drawContent()
                val w = size.width; val h = size.height
                val cellW = w / deleteCols; val cellH = h / deleteRows
                val pts = particles.value
                val sweepCenter = w * when (deleteAnimStyle) {
                    0 -> 0.12f + progress * 0.82f
                    2 -> 0.22f + progress * 1.04f
                    else -> 0.18f + progress * 0.96f
                }
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            rowAccent.copy(alpha = (if (deleteAnimStyle == 0) 0.10f else 0.18f) * (1f - progress).coerceAtLeast(0f)),
                            Color.Transparent
                        ),
                        startX = sweepCenter - w * if (deleteAnimStyle == 2) 0.20f else 0.16f,
                        endX = sweepCenter + w * if (deleteAnimStyle == 0) 0.04f else 0.08f
                    ),
                    size = size
                )
                pts.forEach { p ->
                    if (progress <= p.delay) return@forEach
                    val localT = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    val travelX = when (deleteAnimStyle) {
                        0 -> 0.30f
                        2 -> 0.74f
                        else -> 0.58f
                    } * deleteScatter
                    val travelY = when (deleteAnimStyle) {
                        0 -> 0.10f
                        2 -> 0.40f
                        else -> 0.30f
                    } * deleteScatter
                    val gravity = when (deleteAnimStyle) {
                        0 -> 0.05f
                        2 -> 0.18f
                        else -> 0.13f
                    }
                    val px = p.normX * w + p.velX * localT * w * travelX + localT * localT * w * if (deleteAnimStyle == 2) 0.10f else 0.06f - cellW * 0.5f
                    val py = p.normY * h + p.velY * localT * h * travelY + gravity * localT * localT * h - cellH * 0.5f
                    val alpha = (1f - localT * when (deleteAnimStyle) {
                        0 -> 1.55f
                        2 -> 1.08f
                        else -> 1.14f
                    }).coerceIn(0f, 1f)
                    val scale = (1f - localT * when (deleteAnimStyle) {
                        0 -> 0.74f
                        2 -> 0.40f
                        else -> 0.46f
                    }).coerceIn(0.08f, 1f)
                    if (alpha > 0.02f) {
                        drawRoundRect(
                            color = p.color.copy(alpha = alpha),
                            topLeft = Offset(px, py),
                            size = Size(cellW * scale, cellH * scale),
                            cornerRadius = CornerRadius(cellW * 0.24f, cellH * 0.24f)
                        )
                    }
                }
            }) {}
        }
    }
}

@Composable
private fun Material3SongRow(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    customArtUri: Uri? = null,
    shouldLoadArt: Boolean = true,
    customTitle: String? = null,
    customArtist: String? = null,
    showDuration: Boolean,
    showTrackNumber: Boolean,
    trackIndex: Int,
    artCornerRadius: Float,
    artSize: Float,
    showAlbum: Boolean,
    albumName: String,
    uppercaseTitles: Boolean,
    titleFontSize: Float,
    artistFontSize: Float
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val displayTitle = (customTitle ?: song.title).let { if (uppercaseTitles) it.uppercase() else it }
    val artistBase = (customArtist ?: song.artist).takeIf { !it.isNullOrBlank() && it != "<unknown>" } ?: "Неизвестный"
    val subtitle = if (showAlbum && albumName.isNotBlank() && albumName != "<unknown>") {
        "$artistBase • $albumName"
    } else {
        artistBase
    }
    val displayUri = if (shouldLoadArt) customArtUri ?: song.albumArtUri else null
    val thumb = artSize.dp.coerceIn(48.dp, 56.dp)
    val corner = artCornerRadius.dp.coerceIn(8.dp, 14.dp)

    val rowBg = when {
        isSelected -> c.secondaryContainer.copy(alpha = 0.50f)
        isCurrentSong -> c.primaryContainer.copy(alpha = 0.32f)
        else -> Color.Transparent
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showTrackNumber && !isSelectionMode) {
                Text(
                    text = "${trackIndex + 1}",
                    color = c.onSurfaceVariant.copy(alpha = 0.65f),
                    fontFamily = font,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(22.dp)
                )
                Spacer(Modifier.width(4.dp))
            }

            Box(
                modifier = Modifier
                    .size(thumb)
                    .clip(RoundedCornerShape(corner))
                    .background(c.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (displayUri != null) {
                    OptimizedAlbumArt(
                        uri = displayUri,
                        title = song.title,
                        modifier = Modifier.fillMaxSize(),
                        targetSize = CoilSize(156, 156)
                    )
                } else {
                    Icon(
                        painter = painterResource(instrumentIconRes(song.id)),
                        contentDescription = null,
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (isCurrentSong) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (displayUri != null) 0.28f else 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            PlayingEqIcon(
                                isPlaying = true,
                                color = c.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = c.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayTitle,
                    color = if (isCurrentSong) c.primary else c.onSurface,
                    fontFamily = font,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = titleFontSize.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = c.onSurfaceVariant,
                    fontFamily = font,
                    fontSize = artistFontSize.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            when {
                isSelectionMode -> Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) c.primary else c.outline,
                    modifier = Modifier.size(22.dp)
                )
                isCurrentSong && isPlaying -> Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(20.dp)
                )
                showDuration -> Text(
                    text = song.formattedDuration(),
                    color = c.onSurfaceVariant.copy(alpha = 0.72f),
                    fontFamily = font,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 16.dp + thumb + 14.dp),
            thickness = 0.5.dp,
            color = c.outlineVariant.copy(alpha = 0.28f)
        )
    }
}

// ── EmptyState ───────────────────────────────────────────────────────────────
@Composable
private fun LibraryDashboardHeader(
    currentSong: Song?,
    displayTitle: String?,
    displayArtist: String?,
    displayArtUri: Uri?,
    isPlaying: Boolean,
    songsCount: Int,
    artistCount: Int,
    albumCount: Int,
    totalDurationMs: Long,
    accentColor: Color,
    onCurrentSongClick: () -> Unit
) {
    val font = LocalAppFontFamily.current
    val stats = listOf(
        Triple(Icons.Rounded.MusicNote, songsCount.toString(), "треков"),
        Triple(Icons.Rounded.Person, artistCount.toString(), "исполнителей"),
        Triple(Icons.Rounded.Album, albumCount.toString(), "альбомов")
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "Material 3 Library",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (currentSong != null) "Музыка выглядит как отдельный продукт" else "Ваша медиатека стала чище",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    lineHeight = 28.sp
                )
                Text(
                    "Большие поверхности, мягкие контейнеры, нормальная иерархия и живой Material 3 shell.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                stats.forEach { (icon, value, label) ->
                    LibraryStatPill(icon = icon, value = value, label = label)
                }
                LibraryStatPill(
                    icon = Icons.Rounded.Schedule,
                    value = formatLibraryDuration(totalDurationMs),
                    label = "всего музыки"
                )
            }

            if (currentSong != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCurrentSongClick)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (displayArtUri != null) {
                                    OptimizedAlbumArt(
                                        uri = displayArtUri,
                                        title = displayTitle ?: currentSong.title,
                                        modifier = Modifier.fillMaxSize(),
                                        targetSize = CoilSize(192, 192)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(instrumentIconRes(currentSong.id)),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                if (isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PlayingEqIcon(
                                            isPlaying = true,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (isPlaying) "Сейчас играет" else "Последний выбранный трек",
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = font,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                            Text(
                                (displayTitle ?: currentSong.title).ifBlank { "Без названия" },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = font,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                (displayArtist ?: currentSong.artist).takeIf { !it.isNullOrBlank() && it != "<unknown>" } ?: "Неизвестный",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = font,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        FilledIconButton(
                            onClick = onCurrentSongClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Equalizer else Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    val font = LocalAppFontFamily.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun RecentlyAddedShelf(
    songs: List<Song>,
    customArtMap: Map<Long, Uri?>,
    customTitleMap: Map<Long, String>,
    customArtistMap: Map<Long, String>,
    onSongClick: (Song) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LibrarySectionHeader(
            title = "Недавно добавлено",
            subtitle = "Быстрый доступ к новым трекам"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            songs.forEach { song ->
                RecentSongCard(
                    song = song,
                    title = customTitleMap[song.id] ?: song.title,
                    artist = customArtistMap[song.id] ?: song.artist,
                    artUri = customArtMap[song.id] ?: song.albumArtUri,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

private val HOME_RANDOM_ALBUM_QUERIES = listOf(
    "new album",
    "top hits",
    "pop album",
    "rock album",
    "dance hits",
    "indie album",
    "rap album",
    "soundtrack",
    "русский рок",
    "поп музыка",
    "хиты 2024",
    "electronic album"
)

@Composable
private fun RandomOnlineAlbumsShelf(
    albums: List<OnlineAlbumSummary>,
    isLoading: Boolean,
    onAlbumClick: (OnlineAlbumSummary) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LibrarySectionHeader(
            title = "Случайные альбомы",
            subtitle = if (isLoading) {
                "Подбираю новую витрину с сайта"
            } else {
                "Подборка меняется при новом запуске приложения"
            }
        )
        if (isLoading && albums.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(28.dp),
                color = colors.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Загружаю случайные альбомы…",
                        color = colors.onSurface,
                        fontFamily = font,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                albums.forEach { album ->
                    RandomOnlineAlbumCard(album = album, onClick = { onAlbumClick(album) })
                }
            }
        }
    }
}

@Composable
private fun RandomOnlineAlbumCard(
    album: OnlineAlbumSummary,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(
        modifier = Modifier
            .width(176.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        color = colors.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHighest
            ) {
                if (album.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = album.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.Album,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = album.title,
                    color = colors.onSurface,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist.ifBlank { album.subtitle.ifBlank { "Онлайн-альбом" } },
                    color = colors.onSurfaceVariant,
                    fontFamily = font,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (album.badge.isNotBlank()) {
                    Text(
                        text = album.badge,
                        color = colors.primary,
                        fontFamily = font,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSongCard(
    song: Song,
    title: String,
    artist: String,
    artUri: Uri?,
    onClick: () -> Unit
) {
    val font = LocalAppFontFamily.current
    Surface(
        modifier = Modifier
            .width(168.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (artUri != null) {
                        OptimizedAlbumArt(
                            uri = artUri,
                            title = title,
                            modifier = Modifier.fillMaxSize(),
                            targetSize = CoilSize(320, 320)
                        )
                    } else {
                        Icon(
                            painter = painterResource(instrumentIconRes(song.id)),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title.ifBlank { "Без названия" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Неизвестный",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LibrarySectionHeader(
    title: String,
    subtitle: String
) {
    val font = LocalAppFontFamily.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = font,
            fontSize = 12.sp
        )
    }
}

private fun formatLibraryDuration(totalDurationMs: Long): String {
    val totalMinutes = (totalDurationMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}ч ${minutes}м"
        hours > 0 -> "${hours}ч"
        else -> "${minutes}м"
    }
}

@Composable
fun EmptyState(onAdd: () -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(120.dp).clip(MaterialTheme.shapes.extraLarge).background(c.bgCard), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = c.accent.copy(alpha = 0.7f), modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Нет музыки", color = c.textPrimary, fontFamily = LocalAppFontFamily.current, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Добавьте песни чтобы начать слушать", color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = 14.sp)
        Spacer(Modifier.height(40.dp))
        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.bgDeep), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(0.65f).height(50.dp)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить файл", fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onScan, border = BorderStroke(1.5.dp, c.accentMuted), shape = MaterialTheme.shapes.medium, colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent), modifier = Modifier.fillMaxWidth(0.65f).height(50.dp)) {
            Icon(Icons.Rounded.LibraryMusic, null); Spacer(Modifier.width(8.dp)); Text("Найти всю музыку", fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun pluralTracks(count: Int): String = when {
    count % 100 in 11..19 -> "треков"
    count % 10 == 1       -> "трек"
    count % 10 in 2..4    -> "трека"
    else                  -> "треков"
}

// ── Animated slide-out menu ──────────────────────────────────────────────────
@Composable
private fun AnimatedSlideMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    var keepOpen by remember { mutableStateOf(false) }
    var animVisible by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) { keepOpen = true; delay(16); animVisible = true }
        else { animVisible = false; delay(250); keepOpen = false }
    }
    if (keepOpen) {
        Popup(alignment = androidx.compose.ui.Alignment.TopEnd, offset = IntOffset(x = 8, y = 0), onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
            AnimatedVisibility(
                visible = animVisible,
                enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)) +
                        expandVertically(expandFrom = androidx.compose.ui.Alignment.Top, animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)) +
                        fadeIn(tween(180)),
                exit  = shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Top, animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(160))
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = containerColor, shadowElevation = 16.dp, tonalElevation = 4.dp,
                    modifier = Modifier.width(220.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
                }
            }
        }
    }
}

@Composable
private fun AnimatedMenuItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "ms")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = ripple()) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(label, color = textColor, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HomeTopBar — driven by TopBarSettings
// ─────────────────────────────────────────────────────────────────────────────
private fun topBarGlassTint(tb: com.musicplayer.data.TopBarSettings, c: ColorScheme): Color = when (tb.glassTintMode) {
    1 -> Color(0xFF9FDBFF)
    2 -> Color.White
    3 -> Color(0xFFFFC98A)
    else -> c.accent
}

private fun topBarGlassTintSecondary(tb: com.musicplayer.data.TopBarSettings, c: ColorScheme): Color = when (tb.glassTintMode) {
    1 -> Color(0xFF78B9FF)
    2 -> c.textPrimary
    3 -> Color(0xFFFFA76C)
    else -> c.accentVar
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    topBar: com.musicplayer.data.TopBarSettings,
    c: ColorScheme,
    isSearching: Boolean,
    isSelectionMode: Boolean,
    selectedCount: Int,
    sortedSongsCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onClearSearch: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    showMenu: Boolean,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onAddFile: () -> Unit,
    onScanMusic: () -> Unit,
    onSettings: () -> Unit
) {
    val font = LocalAppFontFamily.current
    val titleText = topBar.titleText.ifBlank { "Music Player" }
    val subtitleText = if (topBar.showSongCount && sortedSongsCount > 0) {
        val prefix = topBar.songCountPrefix.ifBlank { "" }
        "$prefix$sortedSongsCount ${pluralTracks(sortedSongsCount)}"
    } else null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    when {
                        isSelectionMode -> {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        isSearching -> {
                            IconButton(onClick = {
                                onClearSearch()
                                onSearchToggle()
                            }) {
                                Icon(Icons.Rounded.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        else -> {
                            Box(Modifier.size(48.dp))
                        }
                    }
                },
                title = {
                    when {
                        isSelectionMode -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Выбрано",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = font,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "$selectedCount трек(ов)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = font,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                titleText,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = font,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            if (!isSearching && subtitleText != null) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        subtitleText,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = font,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        FilledTonalIconButton(
                            onClick = onSelectAll,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Rounded.SelectAll, null)
                        }
                        FilledIconButton(
                            onClick = onDeleteSelected,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, null)
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = {
                                if (isSearching) {
                                    onClearSearch()
                                }
                                onSearchToggle()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search, null)
                        }

                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            FilledIconButton(
                                onClick = onMenuToggle,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.MoreVert, null)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = onMenuDismiss,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Добавить файл", fontFamily = font) },
                                    leadingIcon = { Icon(Icons.Rounded.Add, null) },
                                    onClick = {
                                        onMenuDismiss()
                                        onAddFile()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Найти всю музыку", fontFamily = font) },
                                    leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) },
                                    onClick = {
                                        onMenuDismiss()
                                        onScanMusic()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Настройки", fontFamily = font) },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                    onClick = {
                                        onMenuDismiss()
                                        onSettings()
                                    }
                                )
                            }
                        }
                    }
                }
            )

            AnimatedVisibility(
                visible = isSearching,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    placeholder = {
                        Text(
                            "Поиск по библиотеке",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = font
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
        }
    }
}
