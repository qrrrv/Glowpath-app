package com.musicplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.musicplayer.R

/**
 * List-row album art — minimal overhead for LazyColumn.
 *
 * Key performance choices:
 *  - plain AsyncImage (no subcomposition)
 *  - no crossfade (saves frames during fast scroll)
 *  - fixed small decode size (never decode full-res for 52dp thumbnail)
 *  - Precision.INEXACT so Coil can reuse nearby cached sizes
 *  - hardware bitmaps + memory/disk cache
 *  - when [uri] is null the composable shows only a cheap placeholder (no Coil request)
 */
@Composable
fun OptimizedAlbumArt(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = Size(168, 168)
) {
    if (uri == null) {
        AlbumArtPlaceholder(title = title, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val request = remember(uri, targetSize) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(targetSize)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .allowHardware(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // Local MediaStore albumart URIs — skip network interceptor work
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.ic_music_placeholder),
        error = painterResource(R.drawable.ic_music_placeholder),
    )
}

/** Full-quality version for the player screen — supports custom loading state. */
@Composable
fun OptimizedAlbumArtLarge(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = Size.ORIGINAL
) {
    val context = LocalContext.current
    val request = remember(uri, targetSize) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(300)
            .placeholder(R.drawable.ic_music_placeholder)
            .error(R.drawable.ic_music_placeholder)
            .size(targetSize)
            .precision(Precision.EXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Album art of $title",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { AlbumArtPlaceholder(title) },
        error = { AlbumArtPlaceholder(title) },
        success = { SubcomposeAsyncImageContent() }
    )
}

@Composable
fun AlbumArtPlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_music_placeholder),
            contentDescription = "$title placeholder",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}
