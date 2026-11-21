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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.musicplayer.data.ArtistAlbum
import com.musicplayer.data.ArtistPage
import com.musicplayer.data.ArtistPageState
import com.musicplayer.data.ArtistVideo
import com.musicplayer.data.RelatedArtist
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.ArtistViewModel
import java.util.Locale

internal data class ArtistUiState(
    val name: String = "",
    val content: ArtistPageState = ArtistPageState.Idle
)

internal data class ArtistActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenArtist: (String) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {}
)

@Composable
fun ArtistScreen(
    artistName: String,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    viewModel: ArtistViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(artistName) { viewModel.open(artistName) }

    ArtistContent(
        state = ArtistUiState(name = artistName, content = state),
        actions = ArtistActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onOpenArtist = onOpenArtist,
            onOpenUrl = onOpenUrl
        )
    )
}

@Composable
internal fun ArtistContent(state: ArtistUiState, actions: ArtistActions) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("artist_screen"),
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.name.isNotBlank()) state.name else "Исполнитель",
                        fontFamily = font,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
                ArtistPageState.Idle, is ArtistPageState.Loading -> ArtistLoading()
                is ArtistPageState.Empty -> ArtistEmpty(onRetry = actions.onRetry)
                is ArtistPageState.Success -> ArtistLoaded(content.page, actions)
            }
        }
    }
}

@Composable
private fun ArtistLoading() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("artist_loading"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Собираем страницу исполнителя", fontFamily = LocalAppFontFamily.current)
    }
}

@Composable
private fun ArtistEmpty(onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("Не удалось загрузить исполнителя", style = MaterialTheme.typography.titleMedium,
            fontFamily = font, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Проверьте подключение к интернету и попробуйте ещё раз.",
            style = MaterialTheme.typography.bodyMedium, fontFamily = font,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag("artist_retry")) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Повторить", fontFamily = font)
        }
    }
}

@Composable
private fun ArtistLoaded(page: ArtistPage, actions: ArtistActions) {
    val font = LocalAppFontFamily.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(key = "hero") { ArtistHero(page, font) }

        if (page.related.isNotEmpty()) {
            item(key = "related_title") {
                ArtistSectionTitle("Похожие исполнители", Modifier.testTag("artist_related_title"))
            }
            item(key = "related") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(page.related, key = { it.id }) { artist ->
                        RelatedArtistCard(artist) { actions.onOpenArtist(artist.name) }
                    }
                }
            }
        }

        if (page.videos.isNotEmpty()) {
            item(key = "videos_title") {
                ArtistSectionTitle("Видео", Modifier.testTag("artist_videos_title"))
            }
            item(key = "videos") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(page.videos, key = { it.id }) { video ->
                        ArtistVideoCard(video) { actions.onOpenUrl(video.externalUrl) }
                    }
                }
            }
        }

        if (page.albums.isNotEmpty()) {
            item(key = "albums_title") {
                ArtistSectionTitle("Где встречается", Modifier.testTag("artist_albums_title"))
            }
            item(key = "albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(page.albums, key = { it.id }) { album ->
                        ArtistAlbumCard(album) {
                            if (album.link.isNotBlank()) actions.onOpenUrl(album.link)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHero(page: ArtistPage, font: androidx.compose.ui.text.font.FontFamily) {
    val colors = MaterialTheme.colorScheme
    val profile = page.profile

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(156.dp).clip(CircleShape)
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Person, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(56.dp))
            if (profile.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = profile.imageUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val chips = buildList {
            if (profile.fans > 0L) add(Icons.Rounded.Headphones to formatFans(profile.fans))
            if (profile.albumCount > 0) add(Icons.Rounded.Album to profile.albumCount.toString() + " релизов")
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (icon, label) -> ArtistStatChip(icon, label, font) }
            }
        }
    }
}

@Composable
private fun ArtistStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    font: androidx.compose.ui.text.font.FontFamily
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = colors.onSecondaryContainer, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontFamily = font, color = colors.onSecondaryContainer)
        }
    }
}

@Composable
private fun ArtistSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        fontFamily = LocalAppFontFamily.current,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun RelatedArtistCard(artist: RelatedArtist, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(96.dp).clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Открыть исполнителя " + artist.name, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape)
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Person, null, tint = colors.onSurfaceVariant)
            if (artist.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = LocalAppFontFamily.current,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistVideoCard(video: ArtistVideo, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Смотреть " + video.title, onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Surface(color = colors.scrim.copy(alpha = 0.55f), shape = CircleShape) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = colors.surface,
                    modifier = Modifier.padding(6.dp).size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = LocalAppFontFamily.current,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (video.artist.isNotBlank()) {
            Text(
                text = video.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                fontFamily = LocalAppFontFamily.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistAlbumCard(album: ArtistAlbum, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Открыть " + album.title, onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Album, null, tint = colors.onSurfaceVariant)
            if (album.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = LocalAppFontFamily.current,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val meta = listOf(album.kind, album.year).filter { it.isNotBlank() }.joinToString(" • ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                fontFamily = LocalAppFontFamily.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatFans(value: Long): String {
    return when {
        value >= 1_000_000L -> String.format(Locale.ROOT, "%.1f млн слушателей", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.ROOT, "%.1f тыс. слушателей", value / 1_000.0)
        else -> value.toString() + " слушателей"
    }
}
