package com.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.res.painterResource
import com.musicplayer.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicplayer.viewmodel.MusicViewModel
import com.musicplayer.data.Curve
import com.musicplayer.data.TransitionMode
import com.musicplayer.data.TransitionSettings
import java.util.concurrent.TimeUnit

/**
 * Экран настройки переходов между треками.
 * Позволяет выбрать режим (нет / кроссфейд), длительность и кривые fade in/out.
 *
 * Доступ: добавить кнопку в SettingsScreen или навигацию → "transitions"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransitionScreen(
    onBack: () -> Unit,
    musicViewModel: com.musicplayer.viewmodel.MusicViewModel
) {
    val savedSettings by musicViewModel.transitionSettings.collectAsState()
    // Local draft — initialized once from prefs, updated independently
    var draft by remember { mutableStateOf(savedSettings) }
    // Sync draft if settings change from outside this screen (e.g., reset)
    LaunchedEffect(savedSettings) {
        // Only sync if the user hasn't made local changes (draft == savedSettings means no drift)
        // This prevents overwriting user's unsaved edits
        if (draft == savedSettings) draft = savedSettings
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    val displayedSettings = draft
    val isCrossfadeEnabled = displayedSettings.mode != TransitionMode.NONE

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Переходы между треками",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 10.dp),
                        onClick = onBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(end = 10.dp),
                        onClick = {
                            musicViewModel.updateTransitionSettings(draft)
                            scope.launch { snackbarHostState.showSnackbar("Изменения сохранены") }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_save_24),
                            contentDescription = "Сохранить",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        text = "Настройте, как треки переходят друг в друга во время воспроизведения.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                item {
                    TransitionModeSection(
                        selected = displayedSettings.mode,
                        onModeSelected = { draft = draft.copy(mode = it) }
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = isCrossfadeEnabled,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            TransitionDurationSection(
                                settings = displayedSettings,
                                onDurationChange = { draft = draft.copy(durationMs = it) }
                            )
                            TransitionCurvesSection(
                                settings = displayedSettings,
                                onCurveInSelected = { draft = draft.copy(curveIn = it) },
                                onCurveOutSelected = { draft = draft.copy(curveOut = it) }
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun TransitionModeSection(
    selected: TransitionMode,
    onModeSelected: (TransitionMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Equalizer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text("Стиль перехода", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Как треки сменяют друг друга",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        TransitionModeToggle(
            options = listOf(TransitionMode.NONE, TransitionMode.OVERLAP),
            selectedOption = selected,
            onOptionSelected = onModeSelected
        )
    }
}

@Composable
private fun TransitionModeToggle(
    options: List<TransitionMode>,
    selectedOption: TransitionMode,
    onOptionSelected: (TransitionMode) -> Unit
) {
    val selectedIndex = if (selectedOption == TransitionMode.OVERLAP) 1 else 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp)
    ) {
        val maxWidth = maxWidth
        val indicatorWidth = maxWidth / 2

        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedIndex == 1) indicatorWidth else 0.dp,
            animationSpec = tween(durationMillis = 300),
            label = "toggleOffset"
        )

        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .fillMaxSize()
                .offset(x = indicatorOffset)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { mode ->
                val isSelected = selectedOption == mode
                val title = if (mode == TransitionMode.OVERLAP) "Кроссфейд" else "Без перехода"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable { onOptionSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitionDurationSection(
    settings: TransitionSettings,
    onDurationChange: (Int) -> Unit
) {
    val durationInSeconds = TimeUnit.MILLISECONDS.toSeconds(settings.durationMs.toLong())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(24.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("Длительность перехода", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${durationInSeconds}с перекрытия",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FilledIconButton(
                onClick = { onDurationChange(TransitionSettings().durationMs) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                            painter = painterResource(R.drawable.outline_restart_alt_24),
                            contentDescription = "Сбросить",
                            modifier = Modifier.size(24.dp)
                        )
            }
        }

        CrossfadeVisualizer(durationMs = settings.durationMs)

        Slider(
            value = settings.durationMs.toFloat(),
            onValueChange = { onDurationChange(it.toInt()) },
            valueRange = 0f..12000f,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(height = 36.dp, width = 8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        )
    }
}

@Composable
private fun CrossfadeVisualizer(durationMs: Int) {
    val maxDuration = 12000f
    val normalized = durationMs.coerceIn(0, 12000)
    val overlapFactor by animateFloatAsState(
        targetValue = normalized / maxDuration,
        label = "overlapWidth"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Текущий трек",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                "Следующий трек",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                            RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                            RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                        )
                )
            }

            // Область перекрытия
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.1f + overlapFactor * 0.4f)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp)
                            )
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                RoundedCornerShape(topEnd = 50.dp, bottomEnd = 50.dp)
                            )
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.gemini_ai),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = "Треки будут перекрываться ${TimeUnit.MILLISECONDS.toSeconds(durationMs.toLong())}с",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TransitionCurvesSection(
    settings: TransitionSettings,
    onCurveInSelected: (Curve) -> Unit,
    onCurveOutSelected: (Curve) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text("Кривые громкости", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Настройте плавность нарастания и затухания",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CurveSelectionColumn(
                modifier = Modifier.weight(1f),
                title = "Затухание",
                selected = settings.curveOut,
                onCurveSelected = onCurveOutSelected,
                activeColor = MaterialTheme.colorScheme.tertiaryContainer,
                onActiveColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            CurveSelectionColumn(
                modifier = Modifier.weight(1f),
                title = "Нарастание",
                selected = settings.curveIn,
                onCurveSelected = onCurveInSelected,
                activeColor = MaterialTheme.colorScheme.secondaryContainer,
                onActiveColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun CurveSelectionColumn(
    modifier: Modifier,
    title: String,
    selected: Curve,
    onCurveSelected: (Curve) -> Unit,
    activeColor: Color,
    onActiveColor: Color
) {
    val labels = mapOf(
        Curve.LINEAR to "Линейная",
        Curve.EASE_IN to "Нарастание",
        Curve.EASE_OUT to "Затухание",
        Curve.EASE_IN_OUT to "S-образная"
    )

    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp, top = 4.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Curve.entries.forEach { curve ->
                    val isSelected = selected == curve
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) activeColor else Color.Transparent)
                            .clickable { onCurveSelected(curve) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = labels[curve] ?: curve.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) onActiveColor
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = onActiveColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
