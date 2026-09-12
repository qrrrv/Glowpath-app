package com.musicplayer.ui.screens

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.musicplayer.R
import com.musicplayer.data.stats.AlbumPlaybackSummary
import com.musicplayer.data.stats.ArtistPlaybackSummary
import com.musicplayer.data.stats.PlaybackStatsSummary
import com.musicplayer.data.stats.SongPlaybackSummary
import com.musicplayer.data.stats.StatsShareUtils
import com.musicplayer.data.stats.StatsTimeRange
import com.musicplayer.data.stats.TimelineEntry
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.MusicViewModel
import com.musicplayer.viewmodel.StatsUiState
import com.musicplayer.viewmodel.StatsViewModel
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Animated counter helper
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun animatedCounterValue(target: Float, durationMillis: Int = 900): Float {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.animateTo(
            targetValue   = target,
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        )
    }
    return animatable.value
}

// ─────────────────────────────────────────────────────────────────────────────
// StatsScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun StatsScreen(
    musicViewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val c             = MaterialTheme.colorScheme
    val context       = LocalContext.current
    val statsViewModel: StatsViewModel = viewModel()
    val songs    by musicViewModel.songs.collectAsState()
    val settings by musicViewModel.settings.collectAsState()
    val uiState  by statsViewModel.uiState.collectAsState()
    val selRange by statsViewModel.selectedRange.collectAsState()
    val allRanges = com.musicplayer.data.stats.StatsTimeRange.values()
    var prevRangeIndex by remember { mutableIntStateOf(allRanges.indexOf(selRange)) }
    var slideDirection by remember { mutableIntStateOf(0) } // -1=left (earlier), 1=right (later)

    LaunchedEffect(selRange) {
        val newIdx = allRanges.indexOf(selRange)
        slideDirection = if (newIdx > prevRangeIndex) 1 else -1
        prevRangeIndex = newIdx
        statsViewModel.load(songs, selRange)
    }
    LaunchedEffect(songs.size) {
        statsViewModel.load(songs, selRange)
    }

    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = c.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(c.surface, c.background)))
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        FilledIconButton(
                            onClick = onBack,
                            colors  = IconButtonDefaults.filledIconButtonColors(
                                containerColor = c.surfaceContainerLow,
                                contentColor = c.onSurface
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        ) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад") }
                    },
                    title = {
                        Text(
                            "Статистика",
                            fontFamily = LocalAppFontFamily.current,
                            fontWeight = FontWeight.Bold,
                            color      = c.textPrimary,
                            fontSize   = 20.sp
                        )
                    },
                    actions = {
                        // Share button — only when there's data to share
                        val currentState = uiState
                        if (currentState is StatsUiState.Ready) {
                            IconButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Статистика MusicPlayer")
                                    putExtra(Intent.EXTRA_TEXT, buildStatsShareText(currentState.summary))
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Поделиться статистикой").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Поделиться", tint = c.textDisabled)
                            }
                        }
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = c.textDisabled)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                )
            }
        }
    ) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Range chips ──────────────────────────────────────────────────
            LazyRow(
                contentPadding         = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement  = Arrangement.spacedBy(8.dp)
            ) {
                items(StatsTimeRange.values()) { range ->
                    val selected = range == selRange
                    FilterChip(
                        selected = selected,
                        onClick  = { statsViewModel.load(songs, range) },
                        // Direction set via LaunchedEffect watching selRange
                        label    = {
                            Text(
                                range.displayName,
                                fontFamily = LocalAppFontFamily.current,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize   = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor         = c.bgCard,
                            selectedContainerColor = c.accent,
                            labelColor             = c.textSecondary,
                            selectedLabelColor     = c.bgDeep
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled             = true,
                            selected            = selected,
                            borderColor         = c.divider,
                            selectedBorderColor = c.accent
                        )
                    )
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))

            // ── Animated content ─────────────────────────────────────────────
            AnimatedContent(
                targetState  = uiState,
                transitionSpec = {
                    val goingRight = slideDirection > 0
                    if (goingRight)
                        com.musicplayer.ui.navigation.AnimationsApplier.tabEnterFromRight(settings.tabSwitchAnim, settings.animParams)
                            .togetherWith(com.musicplayer.ui.navigation.AnimationsApplier.tabExitToLeft(settings.tabSwitchAnim, settings.animParams))
                    else
                        com.musicplayer.ui.navigation.AnimationsApplier.tabEnterFromLeft(settings.tabSwitchAnim, settings.animParams)
                            .togetherWith(com.musicplayer.ui.navigation.AnimationsApplier.tabExitToRight(settings.tabSwitchAnim, settings.animParams))
                },
                label = "statsContent"
            ) { state ->
                when (state) {
                    is StatsUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = c.accent)
                                Spacer(Modifier.height(12.dp))
                                Text("Анализируем данные…", color = c.textSecondary,
                                    fontFamily = LocalAppFontFamily.current)
                            }
                        }
                    }
                    is StatsUiState.Empty -> EmptyStatsState()
                    is StatsUiState.Ready -> StatsContent(summary = state.summary)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor   = c.surface,
            shape            = MaterialTheme.shapes.large,
            title  = { Text("Очистить статистику?", color = c.textPrimary, fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.Bold) },
            text   = { Text("Вся история прослушивания будет удалена. Это действие нельзя отменить.", color = c.textSecondary, fontFamily = LocalAppFontFamily.current) },
            confirmButton = {
                Button(
                    onClick = { statsViewModel.clearStats(songs); showClearDialog = false },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = c.errorContainer,
                        contentColor = c.onErrorContainer
                    ),
                    shape   = MaterialTheme.shapes.medium
                ) { Text("Удалить", fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена", color = c.onSurfaceVariant, fontFamily = LocalAppFontFamily.current)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun EmptyStatsState() {
    val c = MaterialTheme.colorScheme

    // Pulse animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue   = 0.92f,
        targetValue    = 1.08f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500)) { fullHeight -> fullHeight / 4 }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(c.bgCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.BarChart, null, tint = c.accent.copy(0.7f),
                        modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("Нет данных", color = c.textPrimary, fontFamily = LocalAppFontFamily.current,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Начни слушать музыку — и здесь появится\nподробная статистика прослушивания",
                    color = c.textSecondary, fontFamily = LocalAppFontFamily.current,
                    fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Stats Content
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatsContent(summary: PlaybackStatsSummary) {
    val c = MaterialTheme.colorScheme

    // Анимация запускается только при смене данных (переключение диапазона),
    // а не при прокрутке. Ключ — уникальный идентификатор набора данных.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(summary.totalDurationMs, summary.totalPlayCount) {
        entered = false
        entered = true
    }

    LazyColumn(
        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedStatsItem(entered = entered, index = 0) {
                HeroTimeCard(summary)
            }
        }

        item {
            AnimatedStatsItem(entered = entered, index = 1) {
                ListeningProfileCard(summary)
            }
        }

        item {
            AnimatedStatsItem(entered = entered, index = 2) {
                StatGridRow(summary)
            }
        }

        if (summary.timeline.any { it.totalDurationMs > 0 }) {
            item {
                AnimatedStatsItem(entered = entered, index = 3) {
                    StatsCard(title = "История прослушивания") {
                        TimelineBarChart(entries = summary.timeline, entered = entered)
                    }
                }
            }
        }

        if (summary.hourlyDistribution.any { it > 0 }) {
            item {
                AnimatedStatsItem(entered = entered, index = 4) {
                    StatsCard(title = "Активность по часам") {
                        HourlyHeatmap(hourly = summary.hourlyDistribution, entered = entered)
                    }
                }
            }
        }

        if (summary.dayListeningDurationMs > 0 || summary.nightListeningDurationMs > 0 || summary.peakWeek != null || summary.peakMonth != null) {
            item {
                AnimatedStatsItem(entered = entered, index = 5) {
                    RhythmBalanceCard(summary)
                }
            }
        }

        if (summary.topGenres.isNotEmpty()) {
            item {
                AnimatedStatsItem(entered = entered, index = 6) {
                    TopGenresCard(summary)
                }
            }
        }

        if (summary.topSongs.isNotEmpty()) {
            item {
                AnimatedStatsItem(entered = entered, index = 7) {
                    StatsCard(title = "Топ треков") {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            summary.topSongs.forEachIndexed { i, song ->
                                TopSongItem(rank = i + 1, song = song,
                                    max = summary.topSongs.first().totalDurationMs,
                                    entryIndex = i, entered = entered)
                                if (i < summary.topSongs.lastIndex)
                                    HorizontalDivider(color = c.divider.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        if (summary.topArtists.isNotEmpty()) {
            item {
                AnimatedStatsItem(entered = entered, index = 8) {
                    StatsCard(title = "Топ артистов") {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            summary.topArtists.forEachIndexed { i, artist ->
                                TopArtistItem(rank = i + 1, artist = artist,
                                    max = summary.topArtists.first().totalDurationMs,
                                    entryIndex = i, entered = entered)
                                if (i < summary.topArtists.lastIndex)
                                    HorizontalDivider(color = c.divider.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        if (summary.topAlbums.isNotEmpty()) {
            item {
                AnimatedStatsItem(entered = entered, index = 9) {
                    StatsCard(title = "Топ альбомов") {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            summary.topAlbums.forEachIndexed { i, album ->
                                TopAlbumItem(
                                    rank = i + 1,
                                    album = album,
                                    max = summary.topAlbums.first().totalDurationMs,
                                    entryIndex = i,
                                    entered = entered
                                )
                                if (i < summary.topAlbums.lastIndex)
                                    HorizontalDivider(color = c.divider.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        item {
            AnimatedStatsItem(entered = entered, index = 10) {
                InsightsRow(summary)
            }
        }
    }
}

// Staggered fade+slide in for each section
@Composable
private fun AnimatedStatsItem(
    entered: Boolean,
    index: Int,
    content: @Composable () -> Unit
) {
    val delayMs = 80 + index * 90
    val alpha by animateFloatAsState(
        targetValue   = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label         = "itemAlpha_$index"
    )
    val offsetY by animateFloatAsState(
        targetValue   = if (entered) 0f else 40f,
        animationSpec = tween(durationMillis = 450, delayMillis = delayMs, easing = FastOutSlowInEasing),
        label         = "itemOffset_$index"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha          = alpha
            this.translationY   = offsetY
        }
    ) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Card — animated counter
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeroTimeCard(summary: PlaybackStatsSummary) {
    val c      = MaterialTheme.colorScheme
    val appStyle = LocalAppStyle.current
    val totalMs = summary.totalDurationMs
    val hours   = TimeUnit.MILLISECONDS.toHours(totalMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMs) % 60

    val displayTarget = if (hours > 0) hours.toFloat() else minutes.toFloat()
    val displayAnim   = animatedCounterValue(displayTarget)
    val countDisplay  = displayAnim.roundToInt().toString()

    val playAnim   = animatedCounterValue(summary.totalPlayCount.toFloat())
    val songsAnim  = animatedCounterValue(summary.uniqueSongs.toFloat())
    val daysAnim   = animatedCounterValue(summary.activeDays.toFloat())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((appStyle.cardCornerRadius + 12f).dp.coerceIn(26.dp, 36.dp)))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        c.accent.copy(alpha = 0.92f),
                        c.accentVar.copy(alpha = 0.9f),
                        c.accentMuted.copy(alpha = 0.72f)
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Общее время прослушивания", color = c.bgDeep.copy(alpha = 0.75f),
                    fontFamily = LocalAppFontFamily.current, fontSize = 13.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.bgDeep.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        summary.range.displayName,
                        color = c.bgDeep.copy(alpha = 0.82f),
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    countDisplay,
                    color = c.bgDeep, fontFamily = LocalAppFontFamily.current,
                    fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (hours > 0) "ч ${minutes} мин" else "мин",
                    color = c.bgDeep.copy(alpha = 0.85f), fontFamily = LocalAppFontFamily.current,
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HeroStat("${playAnim.roundToInt()}", "воспр.", c.bgDeep)
                HeroStat("${songsAnim.roundToInt()}", "треков", c.bgDeep)
                HeroStat("${daysAnim.roundToInt()}", "дней",   c.bgDeep)
            }
        }
    }
}

@Composable
private fun ListeningProfileCard(summary: PlaybackStatsSummary) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val peakHour = summary.hourlyDistribution.indices.maxByOrNull { summary.hourlyDistribution[it] }
    val discoveryRatio = if (summary.totalPlayCount > 0) {
        summary.uniqueSongs.toFloat() / summary.totalPlayCount.toFloat()
    } else 0f
    val replayRatio = if (summary.totalPlayCount > 0 && summary.topSongs.isNotEmpty()) {
        summary.topSongs.first().playCount.toFloat() / summary.totalPlayCount.toFloat()
    } else 0f

    val profileTitle = when (peakHour) {
        null -> "Профиль ещё собирается"
        in 0..4 -> "Ночной поток"
        in 5..10 -> "Ранний старт"
        in 11..16 -> "Дневной ритм"
        else -> "Вечерний режим"
    }
    val profileIcon = when (peakHour) {
        null -> Icons.Rounded.Insights
        in 0..4 -> Icons.Rounded.DarkMode
        in 5..10 -> Icons.Rounded.WbSunny
        in 11..16 -> Icons.Rounded.LightMode
        else -> Icons.Rounded.Headphones
    }
    val habitLabel = when {
        replayRatio >= 0.4f -> "Любите крутить любимое на повторе"
        discoveryRatio >= 0.7f -> "Часто переключаетесь между разными треками"
        else -> "Слушаете ровно и без перекоса в один трек"
    }
    val rhythmLabel = when {
        summary.dayListeningDurationMs == 0L && summary.nightListeningDurationMs == 0L -> "Ритм ещё собирается"
        summary.nightListeningDurationMs > summary.dayListeningDurationMs * 1.2f -> "Больше слушаете ночью"
        summary.dayListeningDurationMs > summary.nightListeningDurationMs * 1.2f -> "Больше слушаете днём"
        else -> "День и ночь примерно сбалансированы"
    }
    val detailLabel = when {
        summary.topGenres.isNotEmpty() -> "По атмосфере сейчас впереди жанр: ${summary.topGenres.first().genre}"
        summary.topArtists.isNotEmpty() -> "Главный артист сейчас: ${summary.topArtists.first().artist}"
        summary.topSongs.isNotEmpty() -> "Чаще всего включали: ${summary.topSongs.first().title}"
        else -> "Пара деталей появится после нескольких прослушиваний"
    }

    StatsCard(title = "Портрет прослушивания") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(c.accent.copy(alpha = 0.2f), c.accentVar.copy(alpha = 0.12f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(profileIcon, null, tint = c.accent, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(profileTitle, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("$habitLabel. $rhythmLabel", color = c.textSecondary, fontFamily = font, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileChip(
                modifier = Modifier.weight(1f),
                label = "Пик",
                value = peakHour?.let { "${it.toString().padStart(2, '0')}:00" } ?: "Скоро"
            )
            ProfileChip(
                modifier = Modifier.weight(1f),
                label = "Новые треки",
                value = "${(discoveryRatio * 100f).roundToInt()}%"
            )
            ProfileChip(
                modifier = Modifier.weight(1f),
                label = "Повторы",
                value = "${(replayRatio * 100f).roundToInt()}%"
            )
        }

        Text(
            detailLabel,
            color = c.textSecondary,
            fontFamily = font,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ProfileChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgElevated.copy(alpha = 0.82f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = c.textDisabled, fontFamily = font, fontSize = 11.sp)
        Text(value, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun RhythmBalanceCard(summary: PlaybackStatsSummary) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val total = (summary.dayListeningDurationMs + summary.nightListeningDurationMs).coerceAtLeast(1L)
    val dayFraction = summary.dayListeningDurationMs.toFloat() / total.toFloat()
    val nightFraction = summary.nightListeningDurationMs.toFloat() / total.toFloat()

    StatsCard(title = "Ритм прослушивания") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RhythmMeterRow(
                icon = Icons.Rounded.LightMode,
                label = "День",
                fraction = dayFraction,
                value = "${(dayFraction * 100f).roundToInt()}%",
                tint = c.accentVar
            )
            RhythmMeterRow(
                icon = Icons.Rounded.DarkMode,
                label = "Ночь",
                fraction = nightFraction,
                value = "${(nightFraction * 100f).roundToInt()}%",
                tint = c.accent
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PeakPeriodChip(
                    modifier = Modifier.weight(1f),
                    title = "Лучшая неделя",
                    value = summary.peakWeek?.label ?: "Скоро",
                    sub = summary.peakWeek?.totalDurationMs?.let(::formatDurationCompact) ?: "нет данных"
                )
                PeakPeriodChip(
                    modifier = Modifier.weight(1f),
                    title = "Лучший месяц",
                    value = summary.peakMonth?.label ?: "Скоро",
                    sub = summary.peakMonth?.totalDurationMs?.let(::formatDurationCompact) ?: "нет данных"
                )
            }
            Text(
                when {
                    summary.nightListeningDurationMs > summary.dayListeningDurationMs * 1.2f -> "Ночной режим явно доминирует. Интерфейс и рекомендации можно сильнее подстраивать под поздние сессии."
                    summary.dayListeningDurationMs > summary.nightListeningDurationMs * 1.2f -> "Вы чаще слушаете музыку днём. Подборки и быстрые действия лучше ориентировать на активный дневной сценарий."
                    else -> "Прослушивание распределено довольно ровно. Это хороший баланс между фоном днём и длинными сессиями вечером."
                },
                color = c.textSecondary,
                fontFamily = font,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun RhythmMeterRow(
    icon: ImageVector,
    label: String,
    fraction: Float,
    value: String,
    tint: Color
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Text(label, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(value, color = tint, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.bgElevated.copy(alpha = 0.88f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f).coerceAtLeast(0.04f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                tint.copy(alpha = 0.8f),
                                tint.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun PeakPeriodChip(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    sub: String
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgElevated.copy(alpha = 0.82f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = c.textDisabled, fontFamily = font, fontSize = 11.sp)
        Text(value, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = c.accent, fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    }
}

@Composable
private fun HeroStat(value: String, label: String, color: Color) {
    Column {
        Text(value, color = color, fontFamily = LocalAppFontFamily.current,
            fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color.copy(alpha = 0.7f), fontFamily = LocalAppFontFamily.current,
            fontSize = 12.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat Grid — mini cards with pop-in
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatGridRow(summary: PlaybackStatsSummary) {
    val avgMin     = TimeUnit.MILLISECONDS.toMinutes(summary.averageDailyDurationMs)
    val sessAvgMin = TimeUnit.MILLISECONDS.toMinutes(summary.averageSessionDurationMs)
    val streakDays = summary.longestStreakDays

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedMiniCard(
            modifier = Modifier.weight(1f),
            icon     = Icons.Rounded.AccessTime,
            value    = "$avgMin мин",
            label    = "В среднем в день",
            delay    = 0
        )
        AnimatedMiniCard(
            modifier = Modifier.weight(1f),
            icon     = Icons.Rounded.LocalFireDepartment,
            value    = "$streakDays дн",
            label    = "Лучшая серия",
            delay    = 80
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedMiniCard(
            modifier = Modifier.weight(1f),
            icon     = Icons.Rounded.PlayCircle,
            value    = "${summary.totalSessions}",
            label    = "Сессий прослушивания",
            delay    = 160
        )
        AnimatedMiniCard(
            modifier = Modifier.weight(1f),
            icon     = Icons.Rounded.Timer,
            value    = "$sessAvgMin мин",
            label    = "Средняя сессия",
            delay    = 240
        )
    }
}

@Composable
private fun AnimatedMiniCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    delay: Int
) {
    val c = MaterialTheme.colorScheme
    val appStyle = LocalAppStyle.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        visible = false
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "miniCardScale"
    )
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = delay),
        label         = "miniCardAlpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(RoundedCornerShape((appStyle.cardCornerRadius + 2f).dp.coerceIn(16.dp, 28.dp)))
            .background(c.bgCard.copy(alpha = appStyle.surfaceAlpha))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment      = Alignment.CenterVertically,
            horizontalArrangement  = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(c.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(value, color = c.textPrimary, fontFamily = LocalAppFontFamily.current,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(label, color = c.textSecondary, fontFamily = LocalAppFontFamily.current,
                    fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Timeline Bar Chart — bars grow from bottom with stagger + rounded tops + glow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TimelineBarChart(entries: List<TimelineEntry>, entered: Boolean) {
    val c     = MaterialTheme.colorScheme
    val maxMs = entries.maxOfOrNull { it.totalDurationMs } ?: 1L

    Column {
        Row(
            modifier              = Modifier.fillMaxWidth().height(140.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            entries.forEachIndexed { i, entry ->
                val targetFrac = if (maxMs > 0 && entry.totalDurationMs > 0)
                    entry.totalDurationMs.toFloat() / maxMs else 0f

                val heightFrac by animateFloatAsState(
                    targetValue   = if (entered) targetFrac else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness    = Spring.StiffnessLow,
                    ),
                    label = "bar_$i"
                )

                val isMax = entry.totalDurationMs == maxMs && entry.totalDurationMs > 0

                val glowAlpha = if (isMax) {
                    val transition = rememberInfiniteTransition(label = "glow$i")
                    transition.animateFloat(
                        initialValue = 0.5f,
                        targetValue  = 1f,
                        animationSpec = infiniteRepeatable(
                            animation  = tween(1100, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "glowAlpha$i"
                    ).value
                } else 0f

                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Value label on top for max bar
                    if (isMax && entry.totalDurationMs > 0 && heightFrac > 0.1f) {
                        val mins = TimeUnit.MILLISECONDS.toMinutes(entry.totalDurationMs)
                        Text(
                            "$mins",
                            color = c.accent,
                            fontFamily = LocalAppFontFamily.current,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (entry.totalDurationMs > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFrac.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    if (isMax)
                                        Brush.verticalGradient(
                                            listOf(
                                                c.accent.copy(alpha = (0.7f + glowAlpha * 0.3f).coerceIn(0f, 1f)),
                                                c.accentVar.copy(alpha = 0.9f),
                                                c.accentMuted.copy(alpha = 0.6f)
                                            )
                                        )
                                    else
                                        Brush.verticalGradient(
                                            listOf(
                                                c.accent.copy(alpha = 0.55f),
                                                c.accentMuted.copy(alpha = 0.35f)
                                            )
                                        )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c.bgElevated.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Baseline separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, c.divider.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )
        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            entries.forEach { entry ->
                Text(entry.label, color = c.textDisabled, fontFamily = LocalAppFontFamily.current,
                    fontSize = 9.sp, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f))
            }
        }

        val peak = entries.maxByOrNull { it.totalDurationMs }
        if (peak != null && peak.totalDurationMs > 0) {
            Spacer(Modifier.height(10.dp))
            val mins = TimeUnit.MILLISECONDS.toMinutes(peak.totalDurationMs)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(c.accent)
                )
                Text(
                    "Пик: ${peak.label} · $mins мин",
                    color = c.accent,
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hourly Heatmap — animated bars + fill pulse
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HourlyHeatmap(hourly: List<Long>, entered: Boolean) {
    val c      = MaterialTheme.colorScheme
    val maxVal = hourly.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val peakHour = hourly.indexOfFirst { it == maxVal }

    Column {
        Row(
            modifier              = Modifier.fillMaxWidth().height(60.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            hourly.forEachIndexed { h, v ->
                val targetFrac = v.toFloat() / maxVal
                val frac by animateFloatAsState(
                    targetValue   = if (entered) targetFrac else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessLow,
                    ),
                    label = "hourBar_$h"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(frac.coerceAtLeast(0.04f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(c.accentVar.copy(alpha = 0.3f + frac * 0.7f))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "23").forEach { h ->
                Text("$h:00", color = c.textDisabled, fontFamily = LocalAppFontFamily.current,
                    fontSize = 9.sp)
            }
        }
        if (peakHour >= 0 && maxVal > 0) {
            Spacer(Modifier.height(8.dp))
            val mins = TimeUnit.MILLISECONDS.toMinutes(maxVal)
            Text(
                "Самое активное время: ${peakHour}:00–${peakHour + 1}:00 · $mins мин",
                color = c.accentVar, fontFamily = LocalAppFontFamily.current,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Song Item — progress bar slides in + stagger
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopSongItem(
    rank: Int,
    song: SongPlaybackSummary,
    max: Long,
    entryIndex: Int,
    entered: Boolean
) {
    val c          = MaterialTheme.colorScheme
    val fracTarget = if (max > 0) song.totalDurationMs.toFloat() / max else 0f
    val frac by animateFloatAsState(
        targetValue   = if (entered) fracTarget else 0f,
        animationSpec = tween(
            durationMillis = 700,
            delayMillis    = 200 + entryIndex * 100,
            easing         = FastOutSlowInEasing
        ),
        label = "songFrac_$entryIndex"
    )
    val mins = TimeUnit.MILLISECONDS.toMinutes(song.totalDurationMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = c.accent.copy(alpha = 0.08f + 0.16f * frac),
                    size  = Size(size.width * frac, size.height)
                )
            }
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(if (rank == 1) c.accent else c.bgElevated),
                contentAlignment = Alignment.Center
            ) {
                Text("$rank", color = if (rank == 1) c.bgDeep else c.textDisabled,
                    fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(c.bgCard),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(model = song.albumArtUri, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(painter = painterResource(R.drawable.genre_default), contentDescription = null,
                        tint = c.accentMuted, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(song.title, color = c.textPrimary, fontFamily = LocalAppFontFamily.current,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = c.textSecondary, fontFamily = LocalAppFontFamily.current,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$mins мин", color = c.accent, fontFamily = LocalAppFontFamily.current,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${song.playCount} воспр.", color = c.textDisabled,
                    fontFamily = LocalAppFontFamily.current, fontSize = 10.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Artist Item — staggered slide in
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopArtistItem(
    rank: Int,
    artist: ArtistPlaybackSummary,
    max: Long,
    entryIndex: Int,
    entered: Boolean
) {
    val c          = MaterialTheme.colorScheme
    val fracTarget = if (max > 0) artist.totalDurationMs.toFloat() / max else 0f
    val frac by animateFloatAsState(
        targetValue   = if (entered) fracTarget else 0f,
        animationSpec = tween(
            durationMillis = 700,
            delayMillis    = 200 + entryIndex * 100,
            easing         = FastOutSlowInEasing
        ),
        label = "artistFrac_$entryIndex"
    )
    val mins = TimeUnit.MILLISECONDS.toMinutes(artist.totalDurationMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = c.accentVar.copy(alpha = 0.08f + 0.16f * frac),
                    size  = Size(size.width * frac, size.height)
                )
            }
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(if (rank == 1) c.accentVar else c.bgElevated),
                contentAlignment = Alignment.Center
            ) {
                Text("$rank", color = if (rank == 1) c.bgDeep else c.textDisabled,
                    fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(c.bgCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Person, null, tint = c.accentMuted, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(artist.artist, color = c.textPrimary, fontFamily = LocalAppFontFamily.current,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${artist.uniqueSongs} трек(ов)", color = c.textSecondary,
                    fontFamily = LocalAppFontFamily.current, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$mins мин", color = c.accentVar, fontFamily = LocalAppFontFamily.current,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${artist.playCount} воспр.", color = c.textDisabled,
                    fontFamily = LocalAppFontFamily.current, fontSize = 10.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Album Item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopAlbumItem(
    rank: Int,
    album: AlbumPlaybackSummary,
    max: Long,
    entryIndex: Int,
    entered: Boolean
) {
    val c          = MaterialTheme.colorScheme
    val fracTarget = if (max > 0) album.totalDurationMs.toFloat() / max else 0f
    val frac by animateFloatAsState(
        targetValue   = if (entered) fracTarget else 0f,
        animationSpec = tween(
            durationMillis = 700,
            delayMillis    = 220 + entryIndex * 90,
            easing         = FastOutSlowInEasing
        ),
        label = "albumFrac_$entryIndex"
    )
    val mins = TimeUnit.MILLISECONDS.toMinutes(album.totalDurationMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = c.accentMuted.copy(alpha = 0.08f + 0.14f * frac),
                    size  = Size(size.width * frac, size.height)
                )
            }
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .background(if (rank == 1) c.accentMuted else c.bgElevated),
                contentAlignment = Alignment.Center
            ) {
                Text("$rank", color = if (rank == 1) c.bgDeep else c.textDisabled,
                    fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(c.accentMuted.copy(alpha = 0.55f), c.bgCard))),
                contentAlignment = Alignment.Center
            ) {
                if (album.albumArtUri != null) {
                    AsyncImage(model = album.albumArtUri, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.LibraryMusic, null, tint = c.bgDeep.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = album.album.ifBlank { "Без названия альбома" },
                    color = c.textPrimary,
                    fontFamily = LocalAppFontFamily.current,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${album.uniqueSongs} трек(ов)", color = c.textSecondary,
                    fontFamily = LocalAppFontFamily.current, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$mins мин", color = c.accentMuted, fontFamily = LocalAppFontFamily.current,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${album.playCount} воспр.", color = c.textDisabled,
                    fontFamily = LocalAppFontFamily.current, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TopGenresCard(summary: PlaybackStatsSummary) {
    StatsCard(title = "Любимые жанры") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val max = summary.topGenres.maxOfOrNull { it.totalDurationMs } ?: 1L
            summary.topGenres.forEachIndexed { index, genre ->
                GenreStatRow(
                    genre = genre,
                    fraction = if (max > 0) genre.totalDurationMs.toFloat() / max.toFloat() else 0f,
                    index = index
                )
            }
        }
    }
}

@Composable
private fun GenreStatRow(
    genre: com.musicplayer.data.stats.GenrePlaybackSummary,
    fraction: Float,
    index: Int
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val tint = when (index % 4) {
        0 -> c.accent
        1 -> c.accentVar
        2 -> c.accentMuted
        else -> c.textPrimary
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(genre.genre, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "${formatDurationCompact(genre.totalDurationMs)} · ${genre.playCount} воспр.",
                color = tint,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.bgElevated.copy(alpha = 0.82f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f).coerceAtLeast(0.05f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.42f))
                        )
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Insights
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun InsightsRow(summary: PlaybackStatsSummary) {
    val c = MaterialTheme.colorScheme
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(summary.peakDayLabel, summary.longestStreakDays) {
        visible = false
        visible = true
    }

    StatsCard(title = "Инсайты") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val insights = buildList<Pair<ImageVector, String>> {
                if (!summary.peakDayLabel.isNullOrBlank() && summary.peakDayDurationMs > 0) {
                    val mins = TimeUnit.MILLISECONDS.toMinutes(summary.peakDayDurationMs)
                    add(Icons.Rounded.StarRate to "Любимый день недели: ${summary.peakDayLabel} ($mins мин)")
                }
                if (summary.longestStreakDays > 1) {
                    add(Icons.Rounded.LocalFireDepartment to "Лучшая серия прослушивания: ${summary.longestStreakDays} дней подряд")
                }
                summary.peakWeek?.let {
                    add(Icons.Rounded.CalendarMonth to "Рекордная неделя: ${it.label} (${formatDurationCompact(it.totalDurationMs)})")
                }
                summary.peakMonth?.let {
                    add(Icons.Rounded.DateRange to "Рекордный месяц: ${it.label} (${formatDurationCompact(it.totalDurationMs)})")
                }
                val longestMin = TimeUnit.MILLISECONDS.toMinutes(summary.longestSessionDurationMs)
                if (longestMin > 0) {
                    add(Icons.Rounded.Headphones to "Самая длинная сессия: $longestMin мин")
                }
                if (summary.topSongs.isNotEmpty()) {
                    add(Icons.Rounded.Favorite to "Любимый трек: ${summary.topSongs.first().title}")
                }
                if (summary.topGenres.isNotEmpty()) {
                    add(Icons.Rounded.Equalizer to "По жанру сейчас лидирует: ${summary.topGenres.first().genre}")
                }
                if (summary.dayListeningDurationMs > 0 || summary.nightListeningDurationMs > 0) {
                    val rhythmInsight = if (summary.nightListeningDurationMs > summary.dayListeningDurationMs) {
                        Icons.Rounded.DarkMode to "Больше музыки уходит в ночные часы"
                    } else {
                        Icons.Rounded.LightMode to "Основной поток прослушивания приходится на день"
                    }
                    add(rhythmInsight)
                }
                if (isEmpty()) {
                    add(
                        Icons.Rounded.Insights to "Инсайты появятся, когда накопится больше данных о прослушивании"
                    )
                }
            }

            insights.forEachIndexed { i, (icon, text) ->
                val alpha by animateFloatAsState(
                    targetValue   = if (visible) 1f else 0f,
                    animationSpec = tween(durationMillis = 350, delayMillis = i * 120, easing = FastOutSlowInEasing),
                    label         = "insightAlpha_$i"
                )
                val tx by animateFloatAsState(
                    targetValue   = if (visible) 0f else -24f,
                    animationSpec = tween(durationMillis = 400, delayMillis = i * 120, easing = FastOutSlowInEasing),
                    label         = "insightTx_$i"
                )
                Row(
                    modifier              = Modifier.graphicsLayer { this.alpha = alpha; translationX = tx },
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Text(text, color = c.textSecondary, fontFamily = LocalAppFontFamily.current,
                        fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card wrapper
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = MaterialTheme.colorScheme
    val appStyle = LocalAppStyle.current
    val shape = RoundedCornerShape((appStyle.cardCornerRadius + 10f).dp.coerceIn(24.dp, 34.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        c.bgCard.copy(alpha = appStyle.surfaceAlpha),
                        c.bgSurface.copy(alpha = (appStyle.surfaceAlpha + 0.08f).coerceAtMost(1f))
                    )
                )
            )
            .border(1.dp, c.divider.copy(alpha = appStyle.surfaceBorderAlpha), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, color = c.textPrimary, fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold, fontSize = 16.sp)
        content()
    }
}

private fun buildStatsShareText(summary: PlaybackStatsSummary): String {
    val hours = TimeUnit.MILLISECONDS.toHours(summary.totalDurationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(summary.totalDurationMs) % 60
    val timeLabel = if (hours > 0) "$hours ч $minutes мин" else "${TimeUnit.MILLISECONDS.toMinutes(summary.totalDurationMs)} мин"
    val topSong = summary.topSongs.firstOrNull()
    val topArtist = summary.topArtists.firstOrNull()
    val topAlbum = summary.topAlbums.firstOrNull()
    val topGenre = summary.topGenres.firstOrNull()

    return buildString {
        appendLine("Моя статистика в MusicPlayer")
        appendLine("Период: ${summary.range.displayName}")
        appendLine("Время: $timeLabel")
        appendLine("Прослушиваний: ${summary.totalPlayCount}")
        appendLine("Уникальных треков: ${summary.uniqueSongs}")
        topSong?.let { appendLine("Топ-трек: ${it.title} — ${it.artist}") }
        topArtist?.let { appendLine("Топ-артист: ${it.artist}") }
        topAlbum?.let { appendLine("Топ-альбом: ${it.album}") }
        topGenre?.let { appendLine("Топ-жанр: ${it.genre}") }
        if (summary.dayListeningDurationMs > 0 || summary.nightListeningDurationMs > 0) {
            appendLine(
                if (summary.nightListeningDurationMs > summary.dayListeningDurationMs)
                    "Ритм: больше слушаю ночью"
                else
                    "Ритм: больше слушаю днём"
            )
        }
        appendLine()
        append(StatsShareUtils.buildDeepLink(summary))
    }
}

private fun formatDurationCompact(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    return if (hours > 0) "$hours ч $minutes мин" else "${TimeUnit.MILLISECONDS.toMinutes(durationMs)} мин"
}
