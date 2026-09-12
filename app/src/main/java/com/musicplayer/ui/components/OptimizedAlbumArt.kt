package com.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.musicplayer.R
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter

/**
 * Optimised album art loader — two variants:
 *  - [OptimizedAlbumArt] uses a plain AsyncImage for LazyColumn list rows.
 *    AsyncImage has zero subcomposition overhead vs SubcomposeAsyncImage, which
 *    makes a significant difference when 200+ rows are composed during fast scroll.
 *  - [OptimizedAlbumArtLarge] uses SubcomposeAsyncImage for the player screen where
 *    a custom loading placeholder is needed and subcompose cost is a one-time hit.
 */
@Composable
fun OptimizedAlbumArt(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = Size.ORIGINAL
) {
    val context = LocalContext.current
    val request = remember(uri, targetSize) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(false)                        // no crossfade in list — saves animation frames
            .error(R.drawable.ic_music_placeholder)
            .size(targetSize)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .build()
    }

    AsyncImage(
        model            = request,
        contentDescription = null,
        modifier           = modifier,
        contentScale       = ContentScale.Crop,
        placeholder        = painterResource(R.drawable.ic_music_placeholder),
        error              = painterResource(R.drawable.ic_music_placeholder),
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
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .build()
    }

    SubcomposeAsyncImage(
        model              = request,
        contentDescription = "Album art of $title",
        modifier           = modifier,
        contentScale       = ContentScale.Crop,
        loading            = { AlbumArtPlaceholder(title) },
        error              = { AlbumArtPlaceholder(title) },
        success            = { SubcomposeAsyncImageContent() }
    )
}

@Composable
fun AlbumArtPlaceholder(title: String) {
    Box(
        modifier = Modifier
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
