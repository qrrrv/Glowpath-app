@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.musicplayer.ui.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import com.musicplayer.viewmodel.SleepTimerAction
import com.musicplayer.viewmodel.SleepTimerState
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

internal fun formatSleepTimerDuration(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60
    return "%d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
fun SleepTimerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val timerState by viewModel.sleepTimerState.collectAsState()

    var selectedHours by rememberSaveable { mutableIntStateOf(0) }
    var selectedMinutes by rememberSaveable { mutableIntStateOf(30) }
    var fadeOutEnabled by rememberSaveable { mutableStateOf(false) }
    var stopAfterTrackEnabled by rememberSaveable { mutableStateOf(false) }
    var action by rememberSaveable { mutableStateOf(SleepTimerAction.PAUSE) }

    val hourValues = remember { (0..5).toList() }
    val minuteValues = remember { (0..59).toList() }
    val selectedTotalSeconds = selectedHours * 3600 + selectedMinutes * 60

    LaunchedEffect(
        timerState.isRunning,
        timerState.remainingSeconds,
        timerState.fadeOut,
        timerState.stopAfterTrack,
        timerState.action
    ) {
        if (timerState.isRunning) {
            val totalMinutes = timerState.remainingSeconds.coerceAtLeast(0) / 60
            selectedHours = (totalMinutes / 60).coerceIn(0, 5)
            selectedMinutes = (totalMinutes % 60).coerceIn(0, 59)
            fadeOutEnabled = timerState.fadeOut
            stopAfterTrackEnabled = timerState.stopAfterTrack
            action = timerState.action
        }
    }

    val buttonLabel = when {
        timerState.isRunning && timerState.stopAfterTrack && timerState.remainingSeconds <= 0 ->
            "После текущего трека — Отменить"
        timerState.isRunning ->
            "Осталось ${formatSleepTimerDuration(timerState.remainingSeconds)} — Отменить"
        else -> "Запустить таймер"
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurfaceVariant
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Таймер сна",
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "Завершение музыки по времени, с затуханием или после текущего трека",
                            color = colors.onSurfaceVariant,
                            fontFamily = font,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = colors.background) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = {
                            if (timerState.isRunning) {
                                viewModel.cancelSleepTimer()
                            } else {
                                viewModel.startSleepTimer(
                                    hours = selectedHours,
                                    minutes = selectedMinutes,
                                    fadeOut = fadeOutEnabled,
                                    stopAfterTrack = stopAfterTrackEnabled,
                                    action = action
                                )
                            }
                        },
                        enabled = timerState.isRunning || selectedTotalSeconds > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (timerState.isRunning) colors.error else colors.primary,
                            contentColor = if (timerState.isRunning) colors.onError else colors.onPrimary,
                            disabledContainerColor = colors.surfaceContainerHigh,
                            disabledContentColor = colors.onSurfaceVariant
                        )
                    ) {
                        AnimatedContent(targetState = buttonLabel, label = "sleepTimerButtonLabel") { label ->
                            Text(
                                text = label,
                                textAlign = TextAlign.Center,
                                fontFamily = font,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SleepTimerHeroCard(
                timerState = timerState,
                modifier = Modifier.fillMaxWidth()
            )

            SleepTimerPickerCard(
                hourValues = hourValues,
                minuteValues = minuteValues,
                selectedHours = selectedHours,
                selectedMinutes = selectedMinutes,
                enabled = !timerState.isRunning,
                onHoursSelected = { selectedHours = it },
                onMinutesSelected = { selectedMinutes = it }
            )

            SleepTimerSwitchCard(
                icon = Icons.Rounded.VolumeDown,
                title = "Плавное затухание",
                subtitle = "За последние 30 секунд громкость плавно снижается каждые 500 мс",
                checked = fadeOutEnabled,
                enabled = !timerState.isRunning,
                onCheckedChange = { fadeOutEnabled = it }
            )

            SleepTimerSwitchCard(
                icon = Icons.Rounded.QueueMusic,
                title = "Остановить после текущего трека",
                subtitle = "После отсчёта дождаться завершения текущей песни и только потом выполнить действие",
                checked = stopAfterTrackEnabled,
                enabled = !timerState.isRunning,
                onCheckedChange = { stopAfterTrackEnabled = it }
            )

            SleepTimerActionCard(
                selectedAction = action,
                enabled = !timerState.isRunning,
                onActionSelected = { action = it }
            )

            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun SleepTimerHeroCard(
    timerState: SleepTimerState,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.34f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.12f),
                            colors.surfaceContainerLow
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            if (timerState.isRunning) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = colors.primaryContainer
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Bedtime,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = if (timerState.stopAfterTrack && timerState.remainingSeconds <= 0) {
                                "Ждём завершения текущего трека"
                            } else {
                                "Таймер активен"
                            },
                            color = colors.onSurfaceVariant,
                            fontFamily = font,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = formatSleepTimerDuration(timerState.remainingSeconds),
                        color = colors.primary,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp,
                        lineHeight = 42.sp
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surfaceContainerHigh
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Bedtime,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Text(
                        text = "Таймер не запущен",
                        color = colors.onSurfaceVariant,
                        fontFamily = font,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerPickerCard(
    hourValues: List<Int>,
    minuteValues: List<Int>,
    selectedHours: Int,
    selectedMinutes: Int,
    enabled: Boolean,
    onHoursSelected: (Int) -> Unit,
    onMinutesSelected: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val itemHeight = 60.dp
    val visibleItemCount = 5
    val pickerHeight = itemHeight * visibleItemCount

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.34f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.secondary.copy(alpha = 0.08f),
                            colors.surfaceContainerLow
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Часы",
                        color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pickerHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surfaceContainerHigh.copy(alpha = 0.9f))
                        )
                        SleepTimerWheelPicker(
                            values = hourValues,
                            selectedValue = selectedHours,
                            enabled = enabled,
                            itemHeight = itemHeight,
                            valueFormatter = { it.toString() },
                            onSelectedValueChange = onHoursSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Text(
                    text = ":",
                    color = colors.onSurface.copy(alpha = 0.6f),
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    modifier = Modifier.padding(top = 18.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Минуты",
                        color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pickerHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .height(itemHeight)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surfaceContainerHigh.copy(alpha = 0.9f))
                        )
                        SleepTimerWheelPicker(
                            values = minuteValues,
                            selectedValue = selectedMinutes,
                            enabled = enabled,
                            itemHeight = itemHeight,
                            valueFormatter = { value -> value.toString().padStart(2, '0') },
                            onSelectedValueChange = onMinutesSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerWheelPicker(
    values: List<Int>,
    selectedValue: Int,
    enabled: Boolean,
    itemHeight: androidx.compose.ui.unit.Dp,
    valueFormatter: (Int) -> String,
    onSelectedValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val itemHeightPx = remember(itemHeight, density) { with(density) { itemHeight.roundToPx() } }
    val initialIndex = values.indexOf(selectedValue).takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val latestOnSelectedValueChange by rememberUpdatedState(onSelectedValueChange)
    val latestSelectedValue by rememberUpdatedState(selectedValue)
    var centeredIndex by remember(values) { mutableIntStateOf(initialIndex) }
    var lastFeedbackIndex by remember(values) { mutableIntStateOf(-1) }

    LaunchedEffect(listState, values, enabled, itemHeightPx) {
        snapshotFlow { listState.firstVisibleItemScrollOffset to listState.firstVisibleItemIndex }
            .collectLatest { (offset, index) ->
                val current = (if (offset > itemHeightPx / 2) index + 1 else index)
                    .coerceIn(0, values.lastIndex)
                centeredIndex = current
                val currentValue = values[current]
                if (currentValue != latestSelectedValue) {
                    latestOnSelectedValueChange(currentValue)
                }
                if (current != lastFeedbackIndex) {
                    if (lastFeedbackIndex != -1 && enabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        runCatching {
                            audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.3f)
                        }
                    }
                    lastFeedbackIndex = current
                }
            }
    }

    LaunchedEffect(selectedValue, values) {
        val targetIndex = values.indexOf(selectedValue).takeIf { it >= 0 } ?: 0
        if (
            !listState.isScrollInProgress &&
            (listState.firstVisibleItemIndex != targetIndex || listState.firstVisibleItemScrollOffset != 0)
        ) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        userScrollEnabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = itemHeight * 2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(values, key = { _, value -> value }) { index, value ->
            val distance = abs(index - centeredIndex)
            val targetAlpha = when (distance) {
                0 -> 1f
                1 -> 0.45f
                else -> 0.18f
            } * if (enabled) 1f else 0.58f
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                label = "sleepTimerPickerAlpha$value"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueFormatter(value),
                    color = colors.onSurface,
                    fontFamily = font,
                    fontWeight = when (distance) {
                        0 -> FontWeight.Black
                        1 -> FontWeight.Medium
                        else -> FontWeight.Medium
                    },
                    fontSize = when (distance) {
                        0 -> 52.sp
                        1 -> 34.sp
                        else -> 24.sp
                    },
                    modifier = Modifier.graphicsLayer { this.alpha = alpha }
                )
            }
        }
    }
}

@Composable
private fun SleepTimerSwitchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.26f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = colors.surfaceContainerHigh
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.56f),
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = subtitle,
                    color = colors.onSurfaceVariant,
                    fontFamily = font,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                    uncheckedThumbColor = colors.onSurfaceVariant,
                    uncheckedTrackColor = colors.surfaceContainerHigh
                )
            )
        }
    }
}

@Composable
private fun SleepTimerActionCard(
    selectedAction: SleepTimerAction,
    enabled: Boolean,
    onActionSelected: (SleepTimerAction) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val options = remember {
        listOf(
            SleepTimerAction.PAUSE to "Пауза",
            SleepTimerAction.STOP to "Стоп"
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.26f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceContainerHigh
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Alarm,
                            contentDescription = null,
                            tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Действие по истечении",
                        color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.56f),
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Выбери, нужно ли поставить музыку на паузу или остановить её полностью",
                        color = colors.onSurfaceVariant,
                        fontFamily = font,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (option, label) ->
                    val icon = if (option == SleepTimerAction.STOP) Icons.Rounded.Stop else Icons.Rounded.Pause
                    SegmentedButton(
                        selected = selectedAction == option,
                        onClick = { if (enabled) onActionSelected(option) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontFamily = font,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
        }
    }
}
