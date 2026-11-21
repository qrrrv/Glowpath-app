@file:OptIn(ExperimentalMaterial3Api::class)

package com.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.musicplayer.data.AlbumDetail
import com.musicplayer.data.AlbumPageState
import com.musicplayer.data.AlbumTrack
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.AlbumViewModel
import com.musicplayer.viewmodel.MusicViewModel
import java.util.Locale

internal data class AlbumUiState(
    val albumId: Long = 0L,
    val content: AlbumPageState = AlbumPageState.Idle,
    val tasks: Map<Long, AlbumViewModel.TrackTask> = emptyMap(),
    val savedTracks: Set<Long> = emptySet()
)

internal data class AlbumActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPlay: (AlbumTrack) -> Unit = {},
    val onDownload: (AlbumTrack) -> Unit = {}
)

@Composable
fun AlbumScreen(
    albumId: Long,
    musicViewModel: MusicViewModel,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val savedTracks by viewModel.savedTracks.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(albumId) { viewModel.open(albumId) }
    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    AlbumContent(
        state = AlbumUiState(
            albumId = albumId,
            content = state,
            tasks = tasks,
            savedTracks = savedTracks
        ),
        actions = AlbumActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onPlay = { track -> viewModel.play(track) { song -> musicViewModel.playSong(song) } },
            onDownload = { track -> viewModel.download(track) { song -> musicViewModel.addDownloadedSong(song) } }
        ),
        snackbarHostState = snackbarHostState
    )
}

@Composable
internal fun AlbumContent(
    state: AlbumUiState,
    actions: AlbumActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("album_screen"),
        containerColor = colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Альбом", fontFamily = font, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    scrolledContainerColor = colors.surface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val content = state.content) {
                AlbumPageState.Idle, is AlbumPageState.Loading -> AlbumLoading()
                is AlbumPageState.Empty -> AlbumEmpty(onRetry = actions.onRetry)
                is AlbumPageState.Success -> AlbumLoaded(
                    album = content.album,
                    tasks = state.tasks,
                    savedTracks = state.savedTracks,
                    actions = actions
                )
            }
        }
    }
}

@Composable
private fun AlbumLoading() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("album_loading"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Загружаем альбом", fontFamily = LocalAppFontFamily.current)
    }
}

@Composable
private fun AlbumEmpty(onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("Не удалось загрузить альбом", style = MaterialTheme.typography.titleMedium,
            fontFamily = font, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Проверьте подключение к интернету и попробуйте ещё раз.",
            style = MaterialTheme.typography.bodyMedium, fontFamily = font,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag("album_retry")) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Повторить", fontFamily = font)
        }
    }
}

@Composable
private fun AlbumLoaded(
    album: AlbumDetail,
    tasks: Map<Long, AlbumViewModel.TrackTask>,
    savedTracks: Set<Long>,
    actions: AlbumActions
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(132.dp).clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Album, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    if (album.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = album.coverUrl,
                            contentDescription = album.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("album_title")
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.primary,
                        fontFamily = font,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    val meta = buildList {
                        if (album.kind.isNotBlank()) add(album.kind)
                        if (album.year.isNotBlank()) add(album.year)
                        add(trackCountLabel(album.tracks.size))
                    }.joinToString(" • ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        fontFamily = font
                    )
                }
            }
        }

        item(key = "tracks_title") {
            Text(
                text = "Треки",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
            )
        }

        itemsIndexed(album.tracks, key = { _, track -> track.id }) { index, track ->
            AlbumTrackRow(
                index = index + 1,
                track = track,
                task = tasks[track.id],
                saved = savedTracks.contains(track.id),
                onPlay = { actions.onPlay(track) },
                onDownload = { actions.onDownload(track) }
            )
        }
    }
}

@Composable
private fun AlbumTrackRow(
    index: Int,
    track: AlbumTrack,
    task: AlbumViewModel.TrackTask?,
    saved: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val downloading = task as? AlbumViewModel.TrackTask.Downloading

    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClickLabel = "Воспроизвести " + track.title, onClick = onPlay)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
            textAlign = TextAlign.Center,
            fontFamily = font
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = font,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.artist.isNotBlank()) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    fontFamily = font,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = formatDuration(track.durationSeconds),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            fontFamily = font
        )

        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            when {
                downloading != null -> CircularProgressIndicator(
                    progress = { downloading.progress / 100f },
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = colors.primary
                )
                saved -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Скачано",
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
                else -> IconButton(
                    onClick = onDownload,
                    enabled = task == null,
                    modifier = Modifier.size(40.dp).testTag("album_download_" + track.id)
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Скачать " + track.title,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Surface(color = colors.secondaryContainer, shape = CircleShape, modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (task != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.onSecondaryContainer
                    )
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun trackCountLabel(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "треков"
        count % 10 == 1 -> "трек"
        count % 10 in 2..4 -> "трека"
        else -> "треков"
    }
    return count.toString() + " " + word
}

internal fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "--:--"
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
