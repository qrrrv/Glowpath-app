@file:OptIn(ExperimentalMaterial3Api::class)

package com.musicplayer.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.musicplayer.ui.components.ExpressiveLoadingIndicator
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.ArtistViewModel

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
            ArtistPageState.Idle, is ArtistPageState.Loading -> ArtistLoading(actions.onBack, state.name)
            is ArtistPageState.Empty -> ArtistError(state.name, actions)
            is ArtistPageState.Success -> ArtistPageContent(content.page, actions)
        }
    }
}

@Composable
private fun ArtistLoading(onBack: () -> Unit, name: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().testTag("artist_loading").statusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад") }
            Text(name, fontFamily = LocalAppFontFamily.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        ExpressiveLoadingIndicator(size = 52.dp, color = colors.primary)
        Spacer(Modifier.height(18.dp))
        Text("Собираем страницу исполнителя", fontFamily = LocalAppFontFamily.current)
        Spacer(Modifier.weight(1.2f))
    }
}

@Composable
private fun ArtistError(name: String, actions: ArtistActions) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад") }
            Text(name, fontFamily = font, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Rounded.CloudOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(16.dp))
        Text("Не удалось загрузить исполнителя", style = MaterialTheme.typography.titleMedium,
            fontFamily = font, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Проверьте подключение к интернету и попробуйте ещё раз.",
            style = MaterialTheme.typography.bodyMedium, fontFamily = font,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = actions.onRetry, modifier = Modifier.testTag("artist_retry")) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Повторить", fontFamily = font)
        }
        Spacer(Modifier.weight(1.3f))
    }
}

@Composable
private fun ArtistPageContent(page: ArtistPage, actions: ArtistActions) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 230
        }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(220),
        label = "artistBarAlpha"
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("artist_list"),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "hero") { ArtistHero(page.profile) }

            if (page.related.isNotEmpty()) {
                item(key = "related_title") {
                    ArtistSectionTitle("Похожие исполнители", page.related.size,
                        Modifier.testTag("artist_related_title"))
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
                    ArtistSectionTitle("Видео", page.videos.size, Modifier.testTag("artist_videos_title"))
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
                    ArtistSectionTitle("Где встречается", page.albums.size,
                        Modifier.testTag("artist_albums_title"))
                }
                item(key = "albums") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(page.albums, key = { it.id }) { album ->
                            ArtistAlbumCard(album, Modifier.testTag("artist_album_" + album.id)) {
                                actions.onOpenAlbum(album.id)
                            }
                        }
                    }
                }
            }

            item(key = "source") {
                Text(
                    text = "Данные: Deezer • Видео: iTunes",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    fontFamily = LocalAppFontFamily.current,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)
                )
            }
        }

        ArtistTopBar(
            title = page.profile.name,
            barAlpha = barAlpha,
            onBack = actions.onBack
        )
    }
}

@Composable
private fun ArtistTopBar(title: String, barAlpha: Float, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(colors.surface.copy(alpha = barAlpha))
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp)
                .clip(CircleShape)
                .background(
                    if (barAlpha > 0.5f) Color.Transparent
                    else colors.scrim.copy(alpha = 0.32f)
                )
                .clickable(onClickLabel = "Назад", onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "Назад",
                tint = lerp(Color.White, colors.onSurface, barAlpha)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = LocalAppFontFamily.current,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { this.alpha = barAlpha }
        )
    }
}

@Composable
private fun ArtistHero(profile: ArtistProfile) {
    val colors = MaterialTheme.colorScheme
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing))
    }

    Box(Modifier.fillMaxWidth().height(372.dp)) {
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
                    0.40f to colors.surface.copy(alpha = 0.10f),
                    0.68f to colors.surface.copy(alpha = 0.72f),
                    1f to colors.surface
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                .graphicsLayer {
                    val progress = reveal.value
                    alpha = progress
                    translationY = (1f - progress) * 26f
                }
        ) {
            Text(
                text = "ИСПОЛНИТЕЛЬ",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.8.sp,
                color = colors.onSurfaceVariant,
                fontFamily = LocalAppFontFamily.current
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                fontFamily = LocalAppFontFamily.current,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val stats = buildList {
                if (profile.fans > 0L) add(compactFans(profile.fans) + " слушателей")
                if (profile.albumCount > 0) add(profile.albumCount.toString() + " релизов")
            }
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stats.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    fontFamily = LocalAppFontFamily.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistSectionTitle(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = LocalAppFontFamily.current,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = LocalAppFontFamily.current
        )
    }
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
        modifier = Modifier.width(244.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClickLabel = "Смотреть " + video.title, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(137.dp).clip(RoundedCornerShape(22.dp))
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
        modifier = modifier.width(152.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClickLabel = "Открыть альбом " + album.title, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(152.dp).clip(RoundedCornerShape(22.dp))
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

private fun compactFans(value: Long): String = when {
    value >= 1_000_000L -> String.format(java.util.Locale.ROOT, "%.1f", value / 1_000_000.0).replace('.', ',') + " млн"
    value >= 1_000L -> String.format(java.util.Locale.ROOT, "%.1f", value / 1_000.0).replace('.', ',') + " тыс."
    else -> value.toString()
}
