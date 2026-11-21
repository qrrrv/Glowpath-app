@file:OptIn(ExperimentalMaterial3Api::class)

package com.musicplayer.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.musicplayer.data.ArtistAlbum
import com.musicplayer.data.ArtistPage
import com.musicplayer.data.ArtistPageState
import com.musicplayer.data.ArtistProfile
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
    val onOpenAlbum: (Long) -> Unit = {},
    val onOpenUrl: (String) -> Unit = {}
)

@Composable
fun ArtistScreen(
    artistName: String,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
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
            onOpenAlbum = onOpenAlbum,
            onOpenUrl = onOpenUrl
        )
    )
}

@Composable
internal fun ArtistContent(state: ArtistUiState, actions: ArtistActions) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier.fillMaxSize()
            .background(colors.surface)
            .testTag("artist_screen")
    ) {
        when (val content = state.content) {
            ArtistPageState.Idle, is ArtistPageState.Loading -> ArtistLoading()
            is ArtistPageState.Empty -> ArtistEmpty(onRetry = actions.onRetry)
            is ArtistPageState.Success -> ArtistPageContent(content.page, actions)
        }

        // Floating back button stays over the hero while the page scrolls.
        Box(
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.scrim.copy(alpha = 0.38f))
                .clickable(onClickLabel = "Назад", onClick = actions.onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад", tint = Color.White)
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
private fun ArtistPageContent(page: ArtistPage, actions: ArtistActions) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("artist_list"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(key = "hero") { ArtistHero(page.profile) }

        item(key = "stats") {
            val fans = page.profile.fans
            val albums = page.profile.albumCount
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (fans > 0L) {
                    ArtistStatTile(
                        icon = Icons.Rounded.Headphones,
                        value = compactFans(fans),
                        label = "слушателей",
                        modifier = Modifier.weight(1f)
                    )
                }
                if (albums > 0) {
                    ArtistStatTile(
                        icon = Icons.Rounded.Album,
                        value = albums.toString(),
                        label = "релизов",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (page.related.isNotEmpty()) {
            item(key = "related_title") {
                ArtistSectionTitle("Похожие исполнители", Modifier.testTag("artist_related_title"))
            }
            item(key = "related") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(page.albums, key = { it.id }) { album ->
                        ArtistAlbumCard(album, Modifier.testTag("artist_album_" + album.id)) {
                            actions.onOpenAlbum(album.id)
                        }
                    }
                }
            }
        }

        item(key = "footer") {
            Spacer(Modifier.height(8.dp))
        }
        item(key = "source") {
            Text(
                text = "Данные: Deezer • Видео: iTunes",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontFamily = LocalAppFontFamily.current,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun ArtistHero(profile: ArtistProfile) {
    val colors = MaterialTheme.colorScheme
    // The page arrives with a short growth animation; the artwork settles from a
    // slight zoom while the title slides up, so the transition keeps moving after the
    // navigation itself is done.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing))
    }

    Box(Modifier.fillMaxWidth().height(380.dp)) {
        Box(
            Modifier.fillMaxSize().background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Person, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(72.dp))
            if (profile.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = profile.imageUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val progress = reveal.value
                        val scale = 1.08f - 0.08f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.35f + 0.65f * progress
                    }
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to colors.surface.copy(alpha = 0.15f),
                    0.72f to colors.surface.copy(alpha = 0.75f),
                    1f to colors.surface
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                .graphicsLayer {
                    val progress = reveal.value
                    alpha = progress
                    translationY = (1f - progress) * 28f
                }
        ) {
            Text(
                text = "ИСПОЛНИТЕЛЬ",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.6.sp,
                color = colors.onSurfaceVariant,
                fontFamily = LocalAppFontFamily.current
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                fontFamily = LocalAppFontFamily.current,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(
        modifier = modifier,
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                fontFamily = font,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)
    )
}

@Composable
private fun RelatedArtistCard(artist: RelatedArtist, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.width(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Открыть исполнителя " + artist.name, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(colors.surfaceContainerHighest),
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
        modifier = Modifier.width(250.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "Смотреть " + video.title, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(24.dp))
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
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f)
                    )
                )
            )
            Surface(color = Color.White.copy(alpha = 0.92f), shape = CircleShape) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.padding(6.dp).size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyLarge,
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
private fun ArtistAlbumCard(album: ArtistAlbum, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.width(156.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "Открыть альбом " + album.title, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(156.dp).clip(RoundedCornerShape(24.dp))
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
            style = MaterialTheme.typography.bodyLarge,
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

private fun compactFans(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.ROOT, "%.1f млн", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.ROOT, "%.1f тыс.", value / 1_000.0)
    else -> value.toString()
}
