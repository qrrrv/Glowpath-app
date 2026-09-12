package com.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.data.Song
import com.musicplayer.ui.theme.LocalAppColors
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel

// ── Data model ────────────────────────────────────────────────────────────────
private data class FolderItem(
    val path: String,
    val name: String,
    val songs: List<Song>
)

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: MusicViewModel,
    onSongClick: (Song) -> Unit
) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current
    val songs by viewModel.songs.collectAsState()

    val folders = remember(songs) {
        songs
            .filter { !it.folderPath.isNullOrBlank() }
            .groupBy { it.folderPath!! }
            .map { (path, list) ->
                FolderItem(
                    path  = path,
                    name  = path.substringAfterLast("/").ifBlank { path },
                    songs = list.sortedBy { it.title }
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    var openFolder by remember { mutableStateOf<FolderItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    if (openFolder != null) {
                        FilledTonalIconButton(
                            onClick = { openFolder = null },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, "Назад")
                        }
                    }
                },
                title = {
                    val easeOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                    AnimatedContent(
                        targetState = openFolder?.name ?: "Папки",
                        transitionSpec = {
                            slideInHorizontally(
                                animationSpec = tween(320, easing = easeOut),
                                initialOffsetX = { it / 4 }
                            ) + fadeIn(tween(240, easing = easeOut)) togetherWith
                            slideOutHorizontally(
                                animationSpec = tween(260, easing = easeOut),
                                targetOffsetX = { -it / 4 }
                            ) + fadeOut(tween(180))
                        },
                        label = "folderTitle"
                    ) { title ->
                        Column {
                            Text(title, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            if (openFolder != null) {
                                Text(
                                    "${openFolder!!.songs.size} ${pluralTracks(openFolder!!.songs.size)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = font,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Telegram-style folder page transition:
        // открытие папки → контент въезжает справа со слабым scale-up (как ViewPagerFixed)
        // закрытие папки → контент возвращается слева
        val folderEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)  // EaseOutQuint
        AnimatedContent(
            targetState = openFolder,
            transitionSpec = {
                if (targetState != null) {
                    // Открываем папку: новый контент въезжает справа
                    (slideInHorizontally(
                        animationSpec = tween(360, easing = folderEasing),
                        initialOffsetX = { (it * 0.30f).toInt() }
                    ) + fadeIn(
                        animationSpec = tween(280, delayMillis = 40, easing = folderEasing)
                    ) + scaleIn(
                        animationSpec = tween(360, easing = folderEasing),
                        initialScale  = 0.96f
                    )) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(280, easing = folderEasing),
                        targetOffsetX = { -(it * 0.12f).toInt() }
                    ) + fadeOut(tween(200)) + scaleOut(
                        animationSpec = tween(280, easing = folderEasing),
                        targetScale   = 0.97f
                    ))
                } else {
                    // Закрываем папку: предыдущий контент возвращается слева
                    (slideInHorizontally(
                        animationSpec = tween(360, easing = folderEasing),
                        initialOffsetX = { -(it * 0.30f).toInt() }
                    ) + fadeIn(
                        animationSpec = tween(280, delayMillis = 40, easing = folderEasing)
                    ) + scaleIn(
                        animationSpec = tween(360, easing = folderEasing),
                        initialScale  = 0.96f
                    )) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(280, easing = folderEasing),
                        targetOffsetX = { (it * 0.12f).toInt() }
                    ) + fadeOut(tween(200)) + scaleOut(
                        animationSpec = tween(280, easing = folderEasing),
                        targetScale   = 0.97f
                    ))
                }
            },
            label = "folderContent"
        ) { folder ->
            if (folder == null) {
                // ── Folder list ───────────────────────────────────────────────
                if (folders.isEmpty()) {
                    FoldersEmptyState(c)
                } else {
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(
                            top    = padding.calculateTopPadding() + 8.dp,
                            bottom = 120.dp
                        )
                    ) {
                        items(folders, key = { it.path }) { item ->
                            FolderRow(
                                folder      = item,
                                accentColor = c.accent,
                                onClick     = { openFolder = item }
                            )
                        }
                    }
                }
            } else {
                // ── Song list inside folder ───────────────────────────────────
                FolderSongList(
                    songs      = folder.songs,
                    viewModel  = viewModel,
                    onSongClick = onSongClick,
                    padding    = padding
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: FolderItem,
    accentColor: Color,
    onClick: () -> Unit
) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current

    // Telegram-style: строка слегка сжимается при нажатии (spring bounce)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "folderRowScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .graphicsLayer { scaleX = rowScale; scaleY = rowScale },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isPressed) 2.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = accentColor)
                ) { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder.name,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    folder.path.removePrefix("/storage/emulated/0/"),
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font,
                    fontSize   = 11.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "${folder.songs.size} ${pluralTracks(folder.songs.size)}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Открыть",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = font,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FolderSongList(
    songs: List<Song>,
    viewModel: MusicViewModel,
    onSongClick: (Song) -> Unit,
    padding: PaddingValues
) {
    val c          = LocalAppColors.current
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying  by viewModel.isPlaying.collectAsState()
    val songColors by viewModel.songColors.collectAsState()
    val font       = LocalAppFontFamily.current

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(
            top    = padding.calculateTopPadding() + 8.dp,
            bottom = 120.dp
        )
    ) {
        items(songs, key = { it.id }) { song ->
            val isCurrent = song.id == currentSong?.id
            val rowAccent = songColors[song.id] ?: c.accent

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(22.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = if (isCurrent) 2.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (isCurrent) {
                                Icon(
                                    if (isPlaying) Icons.Default.Equalizer else Icons.Default.Pause,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.MusicNote,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontFamily = font,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (song.artist != "<unknown>" && song.artist.isNotBlank()) song.artist else "Неизвестный",
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = font,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            song.formattedDuration(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = font,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersEmptyState(c: com.musicplayer.ui.theme.AppColors) {
    val font = LocalAppFontFamily.current
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val inf = rememberInfiniteTransition(label = "folderPulse")
        val alpha by inf.animateFloat(
            initialValue  = 0.3f,
            targetValue   = 0.7f,
            animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
            label         = "fAlpha"
        )
        Icon(
            Icons.Default.Folder,
            null,
            tint     = c.accent.copy(alpha = alpha),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text("Папки не найдены", color = c.textPrimary, fontFamily = font, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Сначала добавьте музыку через «Найти всю музыку» на главном экране",
            color     = c.textSecondary,
            fontFamily = font,
            fontSize   = 13.sp,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun pluralTracks(count: Int): String = when {
    count % 100 in 11..19 -> "треков"
    count % 10 == 1       -> "трек"
    count % 10 in 2..4    -> "трека"
    else                  -> "треков"
}
