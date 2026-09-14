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
import coil.size.Scale
import coil.size.Size
import com.musicplayer.R

/**
 * List thumbnail — modelled after Telegram ImageReceiver for chat avatars:
 *  - load only while the composable is in composition (LazyColumn detach = cancel)
 *  - fixed small decode size (never full-res)
 *  - no crossfade
 *  - hardware bitmaps + memory/disk cache
 *  - null uri → cheap placeholder, zero Coil work
 */
@Composable
fun OptimizedAlbumArt(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = Size(156, 156)
) {
    if (uri == null) {
        AlbumArtPlaceholder(title = title, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val request = remember(uri, targetSize.width, targetSize.height) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(targetSize)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .allowHardware(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
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
            .crossfade(200)
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
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(28.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}
