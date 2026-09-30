package com.musicplayer.ui.components

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.musicplayer.data.RepeatMode
import com.musicplayer.data.Song
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val QueueTitleRowHeight = 48.dp
private val QueueHandleHeight = 18.dp

// Дополнительный отступ сверху у раскрытой очереди СВЕРХ статус-бара.
// В latentjam шторка останавливается ровно под статус-баром, поэтому 0.dp.
// Хочешь больше воздуха сверху — увеличь это значение.
private val QueueExpandedTopGap = 0.dp
private val NextUpHeight = 36.dp
private val CoverBaseSize = 280.dp
private val PortraitFixedContentHeight = 506.dp

val PlayerQueuePeekHeight: Dp = QueueHandleHeight + QueueTitleRowHeight

private object QueueMotion {
    const val QUICK_MS = 120
    const val APPEAR_MS = 220
    const val REPLACE_MS = 180
    const val REDUCED_MS = 80
    val NavigationEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

data class QueueDisplay(val title: String, val artist: String, val artUri: Uri?)

class PlayerQueueSlots internal constructor(
    val nextUp: @Composable () -> Unit,
    val coverFit: Float,
)

private class QueueIdentity(val hasDuplicates: Boolean)

private fun queueHasDuplicateIds(queue: List<Song>): Boolean {
    val seen = HashSet<Long>(queue.size * 2)
    for (item in queue) {
        if (!seen.add(item.id)) return true
    }
    return false
}

private fun createQueueDisplay(
    titles: Map<Long, String>,
    artists: Map<Long, String>,
    arts: Map<Long, Uri>,
): (Song) -> QueueDisplay = { item ->
    val artist = artists[item.id] ?: item.artist
    QueueDisplay(
        title = titles[item.id] ?: item.title,
        artist = if (artist != "<unknown>" && artist.isNotBlank()) artist else "Неизвестный",
        artUri = arts[item.id] ?: item.albumArtUri,
    )
}

private fun queueSaturation(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[1]
}

private fun queueAccent(gradient: List<Color>, fallback: Color): Color {
    val best = gradient.maxByOrNull { queueSaturation(it) } ?: return fallback
    return if (queueSaturation(best) < 0.12f) fallback else best
}

@Composable
private fun queueAccentInk(vivid: Color): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        lerp(vivid, Color.White, 0.7f)
    } else {
        lerp(vivid, Color.Black, 0.7f)
    }

@Composable
private fun rememberQueueReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun readPreference(): Boolean =
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    val reduced = remember(resolver) { mutableStateOf(readPreference()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced.value = readPreference()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced.value
}

private fun queueFadeThrough(reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) {
        return ContentTransform(
            targetContentEnter = fadeIn(tween(QueueMotion.REDUCED_MS)),
            initialContentExit = fadeOut(tween(QueueMotion.REDUCED_MS)),
            sizeTransform = null,
        )
    }
    return ContentTransform(
        targetContentEnter = fadeIn(
            tween(
                durationMillis = QueueMotion.APPEAR_MS - QueueMotion.QUICK_MS / 2,
                delayMillis = QueueMotion.QUICK_MS / 2,
                easing = LinearEasing,
            ),
        ),
        initialContentExit = fadeOut(tween(QueueMotion.REDUCED_MS, easing = LinearEasing)),
        sizeTransform = null,
    )
}

private fun queueIconTransform(reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) {
        return ContentTransform(
            targetContentEnter = fadeIn(tween(QueueMotion.REDUCED_MS)),
            initialContentExit = fadeOut(tween(QueueMotion.REDUCED_MS)),
            sizeTransform = null,
        )
    }
    return ContentTransform(
        targetContentEnter = fadeIn(tween(QueueMotion.QUICK_MS, easing = LinearEasing)) +
            scaleIn(tween(QueueMotion.QUICK_MS, easing = QueueMotion.NavigationEasing), initialScale = 0.92f),
        initialContentExit = fadeOut(tween(QueueMotion.REDUCED_MS, easing = LinearEasing)) +
            scaleOut(tween(QueueMotion.REDUCED_MS), targetScale = 0.92f),
        sizeTransform = null,
    )
}

private fun Modifier.queueInactiveForMotion(inactive: Boolean): Modifier = if (!inactive) {
    this
} else {
    clearAndSetSemantics { }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
}

private fun Modifier.fadingListTop(
    height: Dp = 10.dp,
    enabled: () -> Boolean,
): Modifier = drawWithCache {
    val fadeHeight = height.toPx().coerceIn(0f, size.height)
    val stripBounds = Rect(0f, 0f, size.width, fadeHeight)
    val layerPaint = Paint()
    val mask = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black),
        startY = 0f,
        endY = fadeHeight.coerceAtLeast(1f),
    )
    onDrawWithContent {
        if (!enabled() || fadeHeight <= 0f) {
            drawContent()
        } else {
            clipRect(top = fadeHeight) { this@onDrawWithContent.drawContent() }
            clipRect(bottom = fadeHeight) {
                val canvas = drawContext.canvas
                canvas.saveLayer(stripBounds, layerPaint)
                try {
                    this@onDrawWithContent.drawContent()
                    drawRect(
                        brush = mask,
                        size = Size(size.width, fadeHeight),
                        blendMode = BlendMode.DstIn,
                    )
                } finally {
                    canvas.restore()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueHost(
    viewModel: MusicViewModel,
    song: Song,
    isPlaying: Boolean,
    gradientColors: List<Color>,
    content: @Composable (PlayerQueueSlots) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetScaffoldState()
    val haptics = LocalHapticFeedback.current
    val songs by viewModel.songs.collectAsState()
    val bridgeUris by viewModel.bridgeQueueUris.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val revision by viewModel.queueRevision.collectAsState()
    val customArts by viewModel.customArtMap.collectAsState()
    val customTitles by viewModel.customTitleMap.collectAsState()
    val customArtists by viewModel.customArtistMap.collectAsState()
    val shuffleEnabled = settings.shuffleEnabled
    val repeatMode = settings.repeatMode
    val sortOrder = settings.sortOrder
    val showHidden = settings.showHiddenTracks
    var showSaveDialog by remember { mutableStateOf(false) }

    val queue = remember(song.id, songs, bridgeUris, shuffleEnabled, sortOrder, showHidden, revision) {
        viewModel.playerQueue()
    }
    val display = remember(customTitles, customArtists, customArts) {
        createQueueDisplay(customTitles, customArtists, customArts)
    }
    val nextUpSong: Song? = remember(queue, song, repeatMode) {
        when {
            repeatMode == RepeatMode.ONE -> song
            queue.songs.isEmpty() || queue.currentIndex < 0 -> null
            queue.currentIndex < queue.songs.lastIndex -> queue.songs[queue.currentIndex + 1]
            repeatMode == RepeatMode.ALL && queue.songs.size > 1 -> queue.songs.first()
            else -> null
        }
    }
    val primary = colors.primary
    val accentTarget = remember(gradientColors, primary) { queueAccent(gradientColors, primary) }
    val vivid by animateColorAsState(
        targetValue = accentTarget,
        animationSpec = tween(500),
        label = "queue-accent",
    )
    val ink = queueAccentInk(vivid)

    BackHandler(enabled = sheetState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch { sheetState.bottomSheetState.partialExpand() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            .asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val coverFit = (
            (maxHeight - topInset - bottomInset - PlayerQueuePeekHeight - PortraitFixedContentHeight) /
                CoverBaseSize
            ).coerceIn(0.5f, 1f)
        // Область шторки = весь экран минус статус-бар сверху и навигация снизу.
        // Потолок высоты считаем сами, а не полагаемся на inset-модификаторы:
        // раскрытая очередь физически не может залезть под статус-бар.
        val sheetAreaHeight = maxHeight - topInset - bottomInset
        val sheetContentMaxHeight = (sheetAreaHeight - QueueHandleHeight - QueueExpandedTopGap)
            .coerceAtLeast(QueueTitleRowHeight + 1.dp)
        val slots = PlayerQueueSlots(
            nextUp = {
                PlayerNextUpRow(
                    next = nextUpSong,
                    display = display,
                    onOpenQueue = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { sheetState.bottomSheetState.expand() }
                    },
                )
            },
            coverFit = coverFit,
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(colors.surfaceContainerHigh),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(top = topInset, bottom = bottomInset)
                .clipToBounds(),
        ) {
        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = sheetState,
            sheetPeekHeight = PlayerQueuePeekHeight,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContainerColor = colors.surfaceContainerHigh,
            sheetShadowElevation = 0.dp,
            containerColor = Color.Transparent,
            sheetDragHandle = { PlayerQueueDragHandle() },
            sheetContent = {
                Box(modifier = Modifier.heightIn(max = sheetContentMaxHeight)) {
                    PlayerQueueSheetContent(
                        queue = queue.songs,
                        currentIndex = queue.currentIndex,
                        isPlaying = isPlaying,
                        vivid = vivid,
                        ink = ink,
                        display = display,
                        onExpand = { scope.launch { sheetState.bottomSheetState.expand() } },
                        onTogglePlayback = { viewModel.togglePlayPause() },
                        onPlayAt = { index -> viewModel.playQueueItem(index) },
                        onRemoveAt = { index -> viewModel.removeQueueItem(index) },
                        onMove = { from, to -> viewModel.moveQueueItem(from, to) },
                        onPlayNextAt = { index -> viewModel.moveQueueItemNext(index) },
                        onSaveQueue = { showSaveDialog = true },
                    )
                }
            },
        ) { sheetPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = sheetPadding.calculateBottomPadding()),
            ) {
                content(slots)
            }
        }
        }
    }

    if (showSaveDialog) {
        var playlistName by remember { mutableStateOf("Очередь") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Сохранить очередь") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    singleLine = true,
                    label = { Text("Название плейлиста") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = playlistName.isNotBlank(),
                    onClick = {
                        val created = viewModel.createUserAlbum(playlistName, queue.songs.map { it.id })
                        showSaveDialog = false
                        Toast.makeText(
                            context,
                            if (created != null) "Плейлист создан" else "Не удалось создать плейлист",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun PlayerNextUpRow(
    next: Song?,
    display: (Song) -> QueueDisplay,
    onOpenQueue: () -> Unit,
) {
    val reduceMotion = rememberQueueReduceMotion()
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    AnimatedContent(
        targetState = next,
        contentKey = { it?.id },
        transitionSpec = { queueFadeThrough(reduceMotion) },
        modifier = Modifier.fillMaxWidth(),
        label = "next-up",
    ) { shown ->
        if (shown == null) {
            Spacer(modifier = Modifier.height(NextUpHeight))
        } else {
            val info = display(shown)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NextUpHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onOpenQueue)
                    .queueInactiveForMotion(shown.id != next?.id)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QueueArtwork(uri = info.artUri, title = info.title, size = 28.dp, corner = 6.dp)
                Text(
                    text = "Дальше: ${info.title} — ${info.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = font,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun QueueArtwork(uri: Uri?, title: String, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner)),
    ) {
        OptimizedAlbumArt(uri = uri, title = title, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun PlayerQueueDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(QueueHandleHeight)
            .padding(top = 8.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

@Composable
private fun PlayerQueueRow(
    display: QueueDisplay,
    durationMs: Long,
    isCurrent: Boolean,
    isPlayed: Boolean,
    isPlaying: Boolean,
    ink: Color,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val reduceMotion = rememberQueueReduceMotion()
    val artworkAlpha by animateFloatAsState(
        targetValue = if (isPlayed) 0.6f else 1f,
        animationSpec = tween(if (reduceMotion) QueueMotion.REDUCED_MS else QueueMotion.APPEAR_MS),
        label = "queue-artwork-alpha",
    )
    var menuOpen by remember { mutableStateOf(false) }
    val moveUpLabel = "Переместить «${display.title}» выше"
    val moveDownLabel = "Переместить «${display.title}» ниже"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
                    if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
                }
            }
            .padding(start = 20.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.graphicsLayer { alpha = artworkAlpha }) {
            QueueArtwork(uri = display.artUri, title = display.title, size = 44.dp, corner = 12.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = if (isCurrent) ink else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = display.artist,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = font,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) PlayerQueuePlayingBars(isPlaying = isPlaying, tint = ink)
        if (durationMs > 0L) {
            Text(
                text = formatQueueDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = font,
                color = colors.onSurfaceVariant,
            )
        }
        if (onPlayNext != null || onRemove != null) {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Действия с треком",
                        tint = colors.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Слушать следующим") },
                            onClick = {
                                menuOpen = false
                                onPlayNext()
                            },
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Убрать из очереди") },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

private fun formatQueueDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun PlayerQueuePlayingBars(isPlaying: Boolean, tint: Color) {
    val reduceMotion = rememberQueueReduceMotion()
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(18.dp),
    ) {
        if (!isPlaying || reduceMotion) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight(0.45f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(tint),
                )
            }
        } else {
            val transition = rememberInfiniteTransition(label = "playing-bars")
            listOf(620, 430, 780).forEachIndexed { index, period ->
                val fraction by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(period, easing = FastOutSlowInEasing),
                        repeatMode = AnimationRepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 130),
                    ),
                    label = "bar$index",
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleY = fraction
                        }
                        .clip(RoundedCornerShape(2.dp))
                        .background(tint),
                )
            }
        }
    }
}

@Composable
private fun PlayerQueueSheetContent(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    vivid: Color,
    ink: Color,
    display: (Song) -> QueueDisplay,
    onExpand: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onPlayNextAt: (Int) -> Unit,
    onSaveQueue: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0),
    )
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberQueueReduceMotion()
    val identity = remember(queue) { QueueIdentity(queueHasDuplicateIds(queue)) }
    var previouslyHadDuplicates by remember { mutableStateOf(identity.hasDuplicates) }
    val ambiguousIdentity = identity.hasDuplicates || previouslyHadDuplicates
    SideEffect { previouslyHadDuplicates = identity.hasDuplicates }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    val canRemove = queue.size > 1

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(QueueTitleRowHeight)
                .clickable(role = Role.Button, onClickLabel = "Очередь", onClick = onExpand),
        ) {
            IconButton(
                onClick = onTogglePlayback,
                enabled = queue.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    tint = colors.onSurface,
                )
            }
            AnimatedContent(
                targetState = queue.size,
                transitionSpec = { queueFadeThrough(reduceMotion) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
                label = "queue-count",
            ) { count ->
                Text(
                    text = if (count == 0) "Очередь" else "Очередь · $count",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = font,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedContent(
                targetState = queue.isNotEmpty(),
                transitionSpec = { queueIconTransform(reduceMotion) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                label = "save-queue",
            ) { canSave ->
                Box(modifier = Modifier.size(QueueTitleRowHeight), contentAlignment = Alignment.Center) {
                    if (canSave) {
                        IconButton(onClick = onSaveQueue, modifier = Modifier.size(QueueTitleRowHeight)) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryAdd,
                                contentDescription = "Добавить в плейлист",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fadingListTop { listState.canScrollBackward },
            state = listState,
        ) {
            itemsIndexed(
                queue,
                key = { index, item -> if (identity.hasDuplicates) index else item.id },
            ) { index, item ->
                key(identity) {
                    var removalRequested by remember { mutableStateOf(false) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled && !removalRequested) {
                                removalRequested = true
                                onRemoveAt(index)
                            }
                            true
                        },
                    )
                    Box(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = if (ambiguousIdentity) null else tween(
                                    if (reduceMotion) QueueMotion.REDUCED_MS else QueueMotion.APPEAR_MS,
                                ),
                                placementSpec = if (reduceMotion || ambiguousIdentity) {
                                    null
                                } else {
                                    tween(QueueMotion.APPEAR_MS)
                                },
                                fadeOutSpec = if (ambiguousIdentity) null else tween(
                                    if (reduceMotion) QueueMotion.REDUCED_MS else QueueMotion.REPLACE_MS,
                                ),
                            )
                            .then(
                                if (draggingIndex == index) {
                                    Modifier
                                        .zIndex(1f)
                                        .graphicsLayer { translationY = dragOffsetY }
                                } else {
                                    Modifier
                                },
                            )
                            .pointerInput(index, queue.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingIndex = index
                                        dragTargetIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val rowHeight = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == index }
                                            ?.size
                                            ?.takeIf { it > 0 }
                                        if (rowHeight != null) {
                                            val shift = (dragOffsetY / rowHeight).roundToInt()
                                            dragTargetIndex = (index + shift).coerceIn(0, queue.lastIndex)
                                        }
                                    },
                                    onDragEnd = {
                                        val from = draggingIndex
                                        val to = dragTargetIndex
                                        draggingIndex = null
                                        dragTargetIndex = null
                                        dragOffsetY = 0f
                                        if (from != null && to != null && from != to) {
                                            onMove(from, to)
                                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        }
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        dragTargetIndex = null
                                        dragOffsetY = 0f
                                    },
                                )
                            },
                    ) {
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = canRemove,
                            enableDismissFromEndToStart = canRemove,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.errorContainer),
                                    contentAlignment = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        else -> Alignment.CenterEnd
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = colors.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                    )
                                }
                            },
                        ) {
                            PlayerQueueRow(
                                display = display(item),
                                durationMs = item.duration,
                                isCurrent = index == currentIndex,
                                isPlayed = currentIndex >= 0 && index < currentIndex,
                                isPlaying = isPlaying,
                                ink = ink,
                                onClick = { onPlayAt(index) },
                                onPlayNext = if (index != currentIndex) {
                                    { onPlayNextAt(index) }
                                } else {
                                    null
                                },
                                onRemove = if (canRemove) {
                                    { onRemoveAt(index) }
                                } else {
                                    null
                                },
                                canMoveUp = index > 0,
                                canMoveDown = index < queue.lastIndex,
                                onMoveUp = {
                                    onMove(index, index - 1)
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                },
                                onMoveDown = {
                                    onMove(index, index + 1)
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                },
                                modifier = Modifier
                                    .background(colors.surfaceContainerHigh)
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (index == currentIndex) {
                                            vivid.copy(alpha = 0.12f).compositeOver(colors.surfaceContainerHigh)
                                        } else {
                                            colors.surfaceContainerHigh
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
