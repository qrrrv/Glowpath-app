package com.musicplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.data.Curve
import com.musicplayer.data.TransitionMode
import com.musicplayer.data.TransitionSettings
import com.musicplayer.data.player.fadeIn
import com.musicplayer.data.player.fadeOut
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val DurationPresetsMs = listOf(0, 500, 1000, 2000, 3000, 5000, 8000, 12000)
private const val MaxDurationMs = 12_000
private const val DurationStepMs = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransitionScreen(
    onBack: () -> Unit,
    musicViewModel: MusicViewModel
) {
    val savedSettings by musicViewModel.transitionSettings.collectAsState()
    var draft by remember { mutableStateOf(savedSettings) }
    var savedFlash by remember { mutableStateOf(false) }
    val font = LocalAppFontFamily.current
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(draft) {
        if (draft == savedSettings) return@LaunchedEffect
        delay(220)
        musicViewModel.updateTransitionSettings(draft)
        savedFlash = true
        delay(900)
        savedFlash = false
    }

    val enabled = draft.mode == TransitionMode.OVERLAP

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
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.onSurface
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Переходы",
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        AnimatedContent(
                            targetState = if (savedFlash) "Сохранено" else if (enabled) {
                                "Кроссфейд · ${draft.formattedDuration()}"
                            } else {
                                "Жёсткая смена треков"
                            },
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "subtitle"
                        ) { text ->
                            Text(
                                text = text,
                                color = if (savedFlash) colors.primary else colors.onSurfaceVariant,
                                fontFamily = font,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EnableCrossfadeCard(
                enabled = enabled,
                durationLabel = draft.formattedDuration(),
                onEnabledChange = { on ->
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    draft = draft.copy(
                        mode = if (on) TransitionMode.OVERLAP else TransitionMode.NONE
                    )
                }
            )

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(tween(280)) + fadeIn(tween(280)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(180))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DurationCard(
                        durationMs = draft.durationMs,
                        onDurationChange = { value ->
                            draft = draft.copy(durationMs = value.coerceIn(0, MaxDurationMs))
                        },
                        onReset = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            draft = draft.copy(durationMs = 2000)
                        }
                    )

                    CurvesCard(
                        settings = draft,
                        onCurveIn = { draft = draft.copy(curveIn = it) },
                        onCurveOut = { draft = draft.copy(curveOut = it) }
                    )

                    HowItWorksCard(durationMs = draft.durationMs)
                }
            }

            if (!enabled) {
                DisabledHintCard()
            }
        }
    }
}

@Composable
private fun EnableCrossfadeCard(
    enabled: Boolean,
    durationLabel: String,
    onEnabledChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val container by animateColorAsState(
        targetValue = if (enabled) colors.primaryContainer else colors.surfaceContainerLow,
        label = "enableContainer"
    )
    val content by animateColorAsState(
        targetValue = if (enabled) colors.onPrimaryContainer else colors.onSurface,
        label = "enableContent"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onEnabledChange
            ),
        color = container,
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) colors.primary.copy(alpha = 0.18f)
                        else colors.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Кроссфейд",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    color = content
                )
                Text(
                    text = if (enabled) {
                        "Треки перекрываются $durationLabel"
                    } else {
                        "Следующий трек стартует после паузы"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = font,
                    color = content.copy(alpha = 0.78f)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                    uncheckedThumbColor = colors.outline,
                    uncheckedTrackColor = colors.surfaceContainerHighest
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationCard(
    durationMs: Int,
    onDurationChange: (Int) -> Unit,
    onReset: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val haptic = LocalHapticFeedback.current
    val seconds = durationMs / 1000f
    val sliderValue = seconds.coerceIn(0f, 12f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = colors.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Длительность",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Сколько секунд оба трека звучат вместе",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = font,
                        color = colors.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = onReset,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.surfaceContainerHighest,
                        contentColor = colors.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Сбросить на 2 с")
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = if (durationMs == 0) "0" else formatSeconds(seconds),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Text(
                    text = if (durationMs == 0) "встык, без фейда" else "секунды перекрытия",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = font,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            OverlapVisualizer(durationMs = durationMs)

            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    val snapped = (value * 2f).roundToInt() / 2f
                    val ms = (snapped * 1000f).roundToInt().coerceIn(0, MaxDurationMs)
                    if (ms != durationMs) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onDurationChange(ms)
                },
                valueRange = 0f..12f,
                steps = (12f / (DurationStepMs / 1000f)).toInt() - 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceContainerHighest,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DurationPresetsMs.forEach { preset ->
                    val selected = durationMs == preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDurationChange(preset)
                        },
                        label = {
                            Text(
                                text = presetLabel(preset),
                                fontFamily = font,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.secondaryContainer,
                            selectedLabelColor = colors.onSecondaryContainer,
                            selectedLeadingIconColor = colors.onSecondaryContainer,
                            containerColor = colors.surfaceContainerHighest,
                            labelColor = colors.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlapVisualizer(durationMs: Int) {
    val colors = MaterialTheme.colorScheme
    val overlap by animateFloatAsState(
        targetValue = (durationMs / MaxDurationMs.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(320),
        label = "overlap"
    )
    val outgoing = colors.tertiary
    val incoming = colors.secondary

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Текущий", style = MaterialTheme.typography.labelMedium, color = outgoing)
            Text("Следующий", style = MaterialTheme.typography.labelMedium, color = incoming)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainerHigh)
        ) {
            val pad = 14.dp.toPx()
            val barH = 18.dp.toPx()
            val width = size.width - pad * 2
            val overlapPx = (width * (0.06f + overlap * 0.42f)).coerceAtLeast(8.dp.toPx())
            val topY = pad + 6.dp.toPx()
            val botY = size.height - pad - barH - 6.dp.toPx()
            val radius = CornerRadius(barH / 2f, barH / 2f)

            val currentWidth = width - overlapPx / 2f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(outgoing.copy(alpha = 0.95f), outgoing.copy(alpha = 0.15f))
                ),
                topLeft = Offset(pad, topY),
                size = Size(currentWidth, barH),
                cornerRadius = radius
            )
            val nextStart = pad + width - currentWidth
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(incoming.copy(alpha = 0.15f), incoming.copy(alpha = 0.95f))
                ),
                topLeft = Offset(nextStart, botY),
                size = Size(currentWidth, barH),
                cornerRadius = radius
            )

            val overlapLeft = pad + currentWidth - overlapPx
            drawRoundRect(
                color = colors.primary.copy(alpha = 0.18f),
                topLeft = Offset(overlapLeft, pad),
                size = Size(overlapPx, size.height - pad * 2),
                cornerRadius = CornerRadius(16.dp.toPx())
            )
        }
    }
}

@Composable
private fun CurvesCard(
    settings: TransitionSettings,
    onCurveIn: (Curve) -> Unit,
    onCurveOut: (Curve) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = colors.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Кривые громкости",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Как затухает текущий трек и нарастает следующий",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = font,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            CombinedCurvePreview(
                curveOut = settings.curveOut,
                curveIn = settings.curveIn
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CurvePicker(
                    modifier = Modifier.weight(1f),
                    title = "Затухание",
                    selected = settings.curveOut,
                    accent = colors.tertiary,
                    container = colors.tertiaryContainer,
                    onContainer = colors.onTertiaryContainer,
                    onSelected = onCurveOut
                )
                CurvePicker(
                    modifier = Modifier.weight(1f),
                    title = "Нарастание",
                    selected = settings.curveIn,
                    accent = colors.secondary,
                    container = colors.secondaryContainer,
                    onContainer = colors.onSecondaryContainer,
                    onSelected = onCurveIn
                )
            }
        }
    }
}

@Composable
private fun CombinedCurvePreview(curveOut: Curve, curveIn: Curve) {
    val colors = MaterialTheme.colorScheme
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        val pad = 16.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val origin = Offset(pad, pad)

        drawRoundRect(
            color = colors.outlineVariant.copy(alpha = 0.35f),
            topLeft = Offset(pad, pad + h / 2f - 1f),
            size = Size(w, 2f),
            cornerRadius = CornerRadius(1f)
        )

        fun curvePath(outgoing: Boolean): Path {
            val path = Path()
            val steps = 64
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val yFrac = if (outgoing) fadeOut(t, curveOut) else fadeIn(t, curveIn)
                val x = origin.x + t * w
                val y = origin.y + (1f - yFrac) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        drawPath(
            path = curvePath(true),
            color = colors.tertiary,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = curvePath(false),
            color = colors.secondary,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun CurvePicker(
    modifier: Modifier,
    title: String,
    selected: Curve,
    accent: Color,
    container: Color,
    onContainer: Color,
    onSelected: (Curve) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier,
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Curve.entries.forEach { curve ->
                val isSelected = curve == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) container else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelected(curve)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniCurveIcon(
                        curve = curve,
                        outgoing = title == "Затухание",
                        color = if (isSelected) onContainer else colors.onSurfaceVariant
                    )
                    Text(
                        text = curveLabel(curve),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = font,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) onContainer else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniCurveIcon(curve: Curve, outgoing: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(22.dp, 14.dp)) {
        val path = Path()
        val steps = 16
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val yFrac = if (outgoing) fadeOut(t, curve) else fadeIn(t, curve)
            val x = t * size.width
            val y = (1f - yFrac) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun HowItWorksCard(durationMs: Int) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val body = if (durationMs == 0) {
        "Следующий трек заранее буферизуется и стартует ровно в конце текущего — без паузы и без наложения."
    } else {
        "За ${formatSeconds(durationMs / 1000f)} с до конца текущий трек затухает, а следующий уже нарастает. Оба звучат вместе ровно столько, сколько выставлено на слайдере."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = font,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DisabledHintCard() {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Как это работает",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Включите кроссфейд, выберите длительность — и треки будут перекрываться ровно столько секунд. Настройка применяется сразу, сохранять ничего не нужно.",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = font,
                color = colors.onSurfaceVariant
            )
        }
    }
}

private fun formatSeconds(seconds: Float): String {
    val rounded = (seconds * 10f).roundToInt() / 10f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString()
    else String.format("%.1f", rounded)
}

private fun presetLabel(ms: Int): String = when (ms) {
    0 -> "Встык"
    else -> formatSeconds(ms / 1000f) + " с"
}

private fun curveLabel(curve: Curve): String = when (curve) {
    Curve.LINEAR -> "Линейная"
    Curve.EASE_IN -> "Нарастание"
    Curve.EASE_OUT -> "Затухание"
    Curve.EASE_IN_OUT -> "S-кривая"
    Curve.EQUAL_POWER -> "Мощность"
}
