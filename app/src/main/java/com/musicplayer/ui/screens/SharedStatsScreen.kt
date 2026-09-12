package com.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.data.stats.PlaybackStatsSummary
import com.musicplayer.data.stats.StatsShareUtils
import com.musicplayer.data.stats.StatsTimeRange
import com.musicplayer.ui.theme.LocalAppColors
import com.musicplayer.ui.theme.LocalAppFontFamily
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// SharedStatsScreen — shown when user opens a stats share deeplink
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedStatsScreen(
    encodedLink: String,   // the full deep link string received from the Intent
    onBack: () -> Unit
) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current

    val summary = remember(encodedLink) { StatsShareUtils.parseDeepLink(encodedLink) }

    Scaffold(
        containerColor = c.bgDeep,
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(c.bgSurface, c.bgDeep)))
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        FilledIconButton(
                            onClick = onBack,
                            colors  = IconButtonDefaults.filledIconButtonColors(
                                containerColor = c.bgCard, contentColor = c.textPrimary
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        ) { Icon(Icons.Rounded.ArrowBack, "Назад") }
                    },
                    title = {
                        Column {
                            Text(
                                "Статистика",
                                fontFamily = font, fontWeight = FontWeight.Bold,
                                color = c.textPrimary, fontSize = 20.sp
                            )
                            Text(
                                "Поделился с тобой",
                                fontFamily = font, color = c.accent, fontSize = 12.sp
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (summary == null) {
            // Invalid / corrupted link
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.LinkOff, null, tint = c.textDisabled, modifier = Modifier.size(64.dp))
                    Text("Не удалось открыть статистику", color = c.textPrimary, fontFamily = font,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Text("Ссылка повреждена или устарела", color = c.textSecondary, fontFamily = font,
                        fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.bgDeep),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Назад", fontFamily = font, fontWeight = FontWeight.SemiBold) }
                }
            }
        } else {
            SharedStatsContent(summary = summary, padding = padding)
        }
    }
}

@Composable
private fun SharedStatsContent(summary: PlaybackStatsSummary, padding: PaddingValues) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val rangeLabel = when (summary.range) {
        StatsTimeRange.DAY   -> "за сегодня"
        StatsTimeRange.WEEK  -> "за эту неделю"
        StatsTimeRange.MONTH -> "за этот месяц"
        StatsTimeRange.YEAR  -> "за этот год"
        StatsTimeRange.ALL   -> "за всё время"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))

        // ── Period banner ─────────────────────────────────────────────────────
        AnimatedSharedItem(entered, 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.accentVar.copy(0.15f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = c.accentVar, modifier = Modifier.size(18.dp))
                Text(
                    "Статистика $rangeLabel",
                    color = c.accentVar, fontFamily = font,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Hero card ─────────────────────────────────────────────────────────
        AnimatedSharedItem(entered, 1) {
            SharedHeroCard(summary)
        }

        Spacer(Modifier.height(12.dp))

        // ── Key metrics ───────────────────────────────────────────────────────
        AnimatedSharedItem(entered, 2) {
            SharedMetricsRow(summary)
        }

        Spacer(Modifier.height(12.dp))

        // ── Top songs ─────────────────────────────────────────────────────────
        if (summary.topSongs.isNotEmpty()) {
            AnimatedSharedItem(entered, 3) {
                SharedCard(title = "Любимые треки") {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        summary.topSongs.forEachIndexed { i, song ->
                            val frac by animateFloatAsState(
                                targetValue   = if (entered) song.totalDurationMs.toFloat() / summary.topSongs.first().totalDurationMs else 0f,
                                animationSpec = tween(700, delayMillis = 300 + i * 100, easing = FastOutSlowInEasing),
                                label = "sFrac$i"
                            )
                            Box(
                                Modifier.fillMaxWidth().drawBehind {
                                    drawRect(c.accent.copy(0.08f * frac), size = Size(size.width * frac, size.height))
                                }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier.size(26.dp).clip(CircleShape)
                                            .background(if (i == 0) c.accent else c.bgElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${i+1}", color = if (i == 0) c.bgDeep else c.textDisabled,
                                            fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, color = c.textPrimary, fontFamily = font,
                                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, color = c.textSecondary, fontFamily = font,
                                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    val mins = TimeUnit.MILLISECONDS.toMinutes(song.totalDurationMs)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("$mins мин", color = c.accent, fontFamily = font,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${song.playCount} воспр.", color = c.textDisabled, fontFamily = font, fontSize = 10.sp)
                                    }
                                }
                            }
                            if (i < summary.topSongs.lastIndex)
                                HorizontalDivider(color = c.divider.copy(0.25f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Top artists ───────────────────────────────────────────────────────
        if (summary.topArtists.isNotEmpty()) {
            AnimatedSharedItem(entered, 4) {
                SharedCard(title = "Любимые исполнители") {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        summary.topArtists.forEachIndexed { i, artist ->
                            val frac by animateFloatAsState(
                                targetValue   = if (entered) artist.totalDurationMs.toFloat() / summary.topArtists.first().totalDurationMs else 0f,
                                animationSpec = tween(700, delayMillis = 300 + i * 100, easing = FastOutSlowInEasing),
                                label = "aFrac$i"
                            )
                            Box(
                                Modifier.fillMaxWidth().drawBehind {
                                    drawRect(c.accentVar.copy(0.08f * frac), size = Size(size.width * frac, size.height))
                                }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier.size(26.dp).clip(CircleShape)
                                            .background(if (i == 0) c.accentVar else c.bgElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${i+1}", color = if (i == 0) c.bgDeep else c.textDisabled,
                                            fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(artist.artist, color = c.textPrimary, fontFamily = font,
                                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${artist.uniqueSongs} трек(ов)", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                                    }
                                    val mins = TimeUnit.MILLISECONDS.toMinutes(artist.totalDurationMs)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("$mins мин", color = c.accentVar, fontFamily = font,
                                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${artist.playCount} воспр.", color = c.textDisabled, fontFamily = font, fontSize = 10.sp)
                                    }
                                }
                            }
                            if (i < summary.topArtists.lastIndex)
                                HorizontalDivider(color = c.divider.copy(0.25f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SharedHeroCard(summary: PlaybackStatsSummary) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current

    val totalMs = summary.totalDurationMs
    val hours   = TimeUnit.MILLISECONDS.toHours(totalMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMs) % 60

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(c.accent.copy(0.85f), c.accentVar.copy(0.9f))))
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Headphones, null, tint = c.bgDeep.copy(0.7f), modifier = Modifier.size(18.dp))
                Text("Слушал музыку", color = c.bgDeep.copy(0.8f), fontFamily = font, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (hours > 0) "$hours" else "$minutes",
                    color = c.bgDeep, fontFamily = font,
                    fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 52.sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (hours > 0) "ч $minutes мин" else "мин",
                    color = c.bgDeep.copy(0.85f), fontFamily = font,
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                @Composable fun H(v: String, l: String) {
                    Column {
                        Text(v, color = c.bgDeep, fontFamily = font, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(l, color = c.bgDeep.copy(0.7f), fontFamily = font, fontSize = 12.sp)
                    }
                }
                H("${summary.totalPlayCount}", "воспр.")
                H("${summary.uniqueSongs}", "треков")
                H("${summary.activeDays}", "дней")
            }
        }
    }
}

@Composable
private fun SharedMetricsRow(summary: PlaybackStatsSummary) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current

    @Composable
    fun MetricCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
        Box(
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(c.bgCard)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp)) }
                Column {
                    Text(value, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text(label, color = c.textSecondary, fontFamily = font, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(Icons.Rounded.LocalFireDepartment, "${summary.longestStreakDays} дн", "Лучшая серия", Modifier.weight(1f))
            MetricCard(Icons.Rounded.PlayCircle, "${summary.totalSessions}", "Сессий", Modifier.weight(1f))
        }
        if (!summary.peakDayLabel.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(Icons.Rounded.StarRate, summary.peakDayLabel!!, "Любимый день недели", Modifier.weight(1f))
                if (summary.topSongs.isNotEmpty()) {
                    MetricCard(Icons.Rounded.Favorite, summary.topSongs.first().title, "Любимый трек", Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedSharedItem(entered: Boolean, index: Int, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue   = if (entered) 1f else 0f,
        animationSpec = tween(400, delayMillis = 80 + index * 100, easing = FastOutSlowInEasing),
        label = "sharedAlpha$index"
    )
    val offsetY by animateFloatAsState(
        targetValue   = if (entered) 0f else 30f,
        animationSpec = tween(450, delayMillis = 80 + index * 100, easing = FastOutSlowInEasing),
        label = "sharedOffset$index"
    )
    Box(Modifier.graphicsLayer(alpha = alpha, translationY = offsetY)) {
        content()
    }
}

@Composable
private fun SharedCard(title: String, content: @Composable () -> Unit) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        content()
    }
}
