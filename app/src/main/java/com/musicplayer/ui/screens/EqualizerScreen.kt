package com.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.ui.theme.*
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// EQ Presets — каждый пресет = список gain-значений для 10 полос (в dB, -12..+12)
// Полосы: 32Hz, 64Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
// ─────────────────────────────────────────────────────────────────────────────

data class EqPreset(val name: String, val gains: List<Float>)

val EQ_BANDS = listOf("32Hz", "64Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

val EQ_PRESETS = listOf(
    EqPreset("Плоский",           listOf(0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f)),
    EqPreset("Рок",               listOf(5f,  4f,  3f, -1f, -2f,  0f,  2f,  4f,  5f,  5f)),
    EqPreset("Поп",               listOf(-1f, 0f,  2f,  4f,  4f,  2f,  0f, -1f, -1f, -1f)),
    EqPreset("Джаз",              listOf(4f,  3f,  1f,  2f, -1f, -1f,  0f,  1f,  2f,  3f)),
    EqPreset("Классика",          listOf(5f,  4f,  3f,  2f,  0f,  0f, -1f, -2f, -3f, -4f)),
    EqPreset("Электроника",       listOf(7f,  6f,  4f,  0f, -3f,  0f,  3f,  5f,  6f,  7f)),
    EqPreset("Хип-хоп",          listOf(6f,  5f,  4f,  2f, -1f, -1f,  2f,  3f,  4f,  4f)),
    EqPreset("R&B",               listOf(6f,  5f,  2f, -1f, -3f,  0f,  3f,  4f,  5f,  5f)),
    EqPreset("Металл",            listOf(6f,  4f,  2f, -1f, -3f, -1f,  2f,  4f,  6f,  7f)),
    EqPreset("Акустика",          listOf(4f,  3f,  2f,  2f,  1f,  0f,  0f,  1f,  2f,  3f)),
    EqPreset("Вокал",             listOf(-1f,-2f,  0f,  3f,  4f,  5f,  4f,  2f,  1f, -1f)),
    EqPreset("Бас-буст",          listOf(8f,  7f,  5f,  3f,  0f, -1f, -2f, -2f, -1f,  0f)),
    EqPreset("Высокие частоты",   listOf(-2f,-2f, -1f,  0f,  0f,  1f,  3f,  5f,  7f,  8f)),
    EqPreset("Ночной режим",      listOf(2f,  2f,  1f,  0f, -1f, -2f, -3f, -4f, -5f, -6f)),
    EqPreset("Глубокий бас",      listOf(10f, 8f,  5f,  2f, -1f, -2f, -2f, -1f,  0f,  0f)),
    EqPreset("Танцевальный",      listOf(5f,  6f,  4f, -1f, -3f,  1f,  4f,  5f,  5f,  5f)),
    EqPreset("Студийный",         listOf(1f,  1f,  0f, -1f, -1f,  0f,  1f,  2f,  2f,  1f)),
    EqPreset("Живой концерт",     listOf(-3f,-1f,  1f,  3f,  4f,  5f,  4f,  3f,  2f, -1f)),
    EqPreset("Пользовательский",  listOf(0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f)),
)

// ─────────────────────────────────────────────────────────────────────────────
// Equalizer Screen (full-screen, navigated to from Settings)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    val savedGains by viewModel.eqGains.collectAsState()
    val savedEnabled by viewModel.eqEnabled.collectAsState()
    val savedPresetName by viewModel.eqPresetName.collectAsState()
    val audioOutput by viewModel.audioOutput.collectAsState()
    val spatialAudio by viewModel.spatialAudio.collectAsState()
    val savedPreampGain by viewModel.preampGain.collectAsState()
    val savedAudioBalance by viewModel.audioBalance.collectAsState()

    var enabled by remember(savedEnabled) { mutableStateOf(savedEnabled) }
    var gains by remember(savedGains) { mutableStateOf(savedGains.toMutableList()) }
    var selectedPreset by remember(savedPresetName) {
        mutableStateOf(EQ_PRESETS.find { it.name == savedPresetName } ?: EQ_PRESETS[0])
    }
    var preampGain by remember(savedPreampGain) { mutableFloatStateOf(savedPreampGain) }
    var audioBalance by remember(savedAudioBalance) { mutableFloatStateOf(savedAudioBalance) }

    val animatedGains = gains.map { g ->
        animateFloatAsState(
            targetValue = g,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "gain"
        ).value
    }

    fun applyPreset(preset: EqPreset) {
        selectedPreset = preset
        gains = preset.gains.toMutableList()
        viewModel.updateEqGains(preset.gains)
        viewModel.setEqPresetName(preset.name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgDeep)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Назад", tint = c.textPrimary)
            }
            Text(
                "Эквалайзер",
                color = c.textPrimary,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Text(
                if (enabled) "ВКЛ" else "ВЫКЛ",
                color = if (enabled) c.accent else c.textDisabled,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    viewModel.setEqEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.bgDeep, checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.textDisabled, uncheckedTrackColor = c.bgCard
                )
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            EqStudioOverviewCard(
                enabled = enabled,
                presetName = selectedPreset.name,
                audioOutput = audioOutput,
                spatialAudio = spatialAudio,
                preampGain = preampGain,
                audioBalance = audioBalance,
                gains = gains
            )
            Spacer(Modifier.height(12.dp))

            EqSpectrumVisualizer(gains = animatedGains, enabled = enabled)
            Spacer(Modifier.height(8.dp))

            EqBandSliders(
                gains   = gains,
                enabled = enabled,
                onGainChange = { idx, v ->
                    gains = gains.toMutableList().also { it[idx] = v }
                    viewModel.updateEqGains(gains)
                    if (selectedPreset != EQ_PRESETS.last()) {
                        selectedPreset = EQ_PRESETS.last()
                        viewModel.setEqPresetName(EQ_PRESETS.last().name)
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EqKnobCard("Бас-буст", gains.take(3).average().toFloat(), Modifier.weight(1f), c.accent, enabled)
                EqKnobCard("Средние",  gains.subList(3,7).average().toFloat(), Modifier.weight(1f), c.accentVar, enabled)
                EqKnobCard("Высокие",  gains.takeLast(3).average().toFloat(), Modifier.weight(1f), c.accentMuted, enabled)
            }

            Spacer(Modifier.height(16.dp))

            EqHeadroomAssistantCard(
                gains = gains,
                preampGain = preampGain,
                enabled = enabled,
                onApply = { suggested ->
                    preampGain = suggested
                    viewModel.setPreAmpGain(suggested)
                }
            )

            Spacer(Modifier.height(16.dp))

            // ── Audio output routing ─────────────────────────────────────────
            Text("Вывод звука", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val outputs = listOf(
                    Triple("STEREO",   Icons.Rounded.Headphones,  "Стерео"),
                    Triple("SPEAKER",  Icons.Rounded.VolumeUp,    "Динамик"),
                    Triple("EARPIECE", Icons.Rounded.Hearing, "Трубка"),
                )
                outputs.forEach { (key, icon, label) ->
                    val sel = audioOutput == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) c.accent else c.bgCard)
                            .clickable { viewModel.setAudioOutput(key) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(icon, null, tint = if (sel) c.bgDeep else c.textSecondary, modifier = Modifier.size(22.dp))
                            Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 3D Sound (Virtualizer) ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.bgCard)
                    .clickable { viewModel.setSpatialAudio(!spatialAudio) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SurroundSound, null, tint = c.accentVar, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("3D Звук", color = c.textPrimary, fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Виртуальное объёмное звучание", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                }
                Switch(
                    spatialAudio, { viewModel.setSpatialAudio(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = c.bgDeep, checkedTrackColor = c.accentVar, uncheckedThumbColor = c.textDisabled, uncheckedTrackColor = c.bgElevated)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Pre-Amp (предусиление) ────────────────────────────────────────
            EqPreAmpAndBalanceSection(
                preampGain = preampGain,
                audioBalance = audioBalance,
                enabled = enabled,
                onPreampChange = { v ->
                    preampGain = v
                    viewModel.setPreAmpGain(v)
                },
                onBalanceChange = { v ->
                    audioBalance = v
                    viewModel.setAudioBalance(v)
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        applyPreset(EQ_PRESETS[0])
                        viewModel.setEqEnabled(true)
                        enabled = true
                        preampGain = 0f
                        viewModel.setPreAmpGain(0f)
                        audioBalance = 0f
                        viewModel.setAudioBalance(0f)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                    border = BorderStroke(1.dp, c.divider),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сбросить", fontFamily = font, fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        viewModel.updateEqGains(gains)
                        viewModel.setEqPresetName(selectedPreset.name)
                        viewModel.setEqEnabled(enabled)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.bgDeep),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сохранить", fontFamily = font, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Шаблоны", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EQ_PRESETS) { preset ->
                    val sel = preset.name == selectedPreset.name
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) c.accent else c.bgCard)
                            .clickable { applyPreset(preset) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            preset.name,
                            color = if (sel) c.bgDeep else c.textSecondary,
                            fontFamily = font,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Текущий профиль: ${selectedPreset.name}",
                color = c.textDisabled, fontFamily = font, fontSize = 12.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EQ Studio Overview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EqStudioOverviewCard(
    enabled: Boolean,
    presetName: String,
    audioOutput: String,
    spatialAudio: Boolean,
    preampGain: Float,
    audioBalance: Float,
    gains: List<Float>
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val peakGain = gains.maxOrNull() ?: 0f
    val balanceLabel = when {
        audioBalance < -0.15f -> "L ${(audioBalance * -100).roundToInt()}%"
        audioBalance > 0.15f -> "R ${(audioBalance * 100).roundToInt()}%"
        else -> "CENTER"
    }
    val riskLabel = when {
        !enabled -> "OFF"
        peakGain + preampGain >= 8f -> "HIGH"
        peakGain + preampGain >= 4f -> "MED"
        else -> "SAFE"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(c.bgCard, c.bgSurface.copy(alpha = 0.96f), c.bgDeep.copy(alpha = 0.98f))
                )
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.92f), c.accentVar.copy(alpha = 0.82f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = c.bgDeep, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Studio Rack", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (enabled) "Профиль \"$presetName\" активен и готов к тонкой настройке"
                    else "Эквалайзер выключен, но пресет и мастер-параметры сохранены",
                    color = c.textSecondary,
                    fontFamily = font,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EqOverviewChip("Вывод", audioOutput, Icons.Rounded.Headphones, Modifier.weight(1f))
            EqOverviewChip("3D", if (spatialAudio) "ON" else "OFF", Icons.Rounded.SurroundSound, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EqOverviewChip("Баланс", balanceLabel, Icons.Rounded.SwapHoriz, Modifier.weight(1f))
            EqOverviewChip("Headroom", riskLabel, Icons.Rounded.Speed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EqOverviewChip(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.bgDeep.copy(alpha = 0.52f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(16.dp))
        Column {
            Text(label, color = c.textDisabled, fontFamily = font, fontSize = 10.sp)
            Text(value, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EqHeadroomAssistantCard(
    gains: List<Float>,
    preampGain: Float,
    enabled: Boolean,
    onApply: (Float) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val peak = gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
    val suggested = (-peak).coerceIn(-12f, 0f)
    val safe = peak + preampGain <= 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (safe || !enabled) c.bgCard else c.accent.copy(alpha = 0.10f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (safe || !enabled) c.bgElevated else c.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.TipsAndUpdates, null, tint = if (safe || !enabled) c.textSecondary else c.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Анти-клип ассистент", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                if (!enabled) "Сначала включите эквалайзер, затем можно автоматически подобрать headroom."
                else if (safe) "Headroom в норме. Peak ${peak.roundToInt()} dB, preamp ${preampGain.roundToInt()} dB."
                else "Текущий пик ${peak.roundToInt()} dB. Для безопасного запаса советую preamp ${suggested.roundToInt()} dB.",
                color = c.textSecondary,
                fontFamily = font,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        FilledTonalButton(
            onClick = { onApply(suggested) },
            enabled = enabled && !safe,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = c.accent.copy(alpha = 0.18f),
                contentColor = c.accent
            )
        ) {
            Text("Применить", fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EQ Spectrum Visualizer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EqSpectrumVisualizer(gains: List<Float>, enabled: Boolean) {
    val c = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.35f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgCard)
            .padding(12.dp)
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val gainScale = h / 28f

        if (gains.isEmpty()) return@Canvas

        drawLine(
            color = c.divider.copy(alpha = 0.5f * alpha),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1f
        )
        listOf(-6f, 6f).forEach { db ->
            val y = midY - db * gainScale
            drawLine(
                color = c.divider.copy(alpha = 0.25f * alpha),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 0.5f
            )
        }

        val path = Path()
        val n = gains.size
        gains.forEachIndexed { i, gain ->
            val x = w * i / (n - 1f)
            val y = (midY - gain * gainScale).coerceIn(0f, h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(w, midY); lineTo(0f, midY); close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(c.accent.copy(alpha = 0.35f * alpha), c.accent.copy(alpha = 0.0f)),
                startY = 0f, endY = h
            )
        )
        drawPath(path, color = c.accent.copy(alpha = alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round
        ))
        gains.forEachIndexed { i, gain ->
            val x = w * i / (n - 1f)
            val y = (midY - gain * gainScale).coerceIn(0f, h)
            drawCircle(c.accent.copy(alpha = alpha), 4f, Offset(x, y))
            drawCircle(c.bgCard, 2f, Offset(x, y))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EQ Band Sliders
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EqBandSliders(
    gains: List<Float>,
    enabled: Boolean,
    onGainChange: (Int, Float) -> Unit
) {
    val c = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgCard)
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EQ_BANDS.forEachIndexed { i, label ->
                val gain = gains.getOrElse(i) { 0f }
                SingleBandSlider(
                    label = label,
                    gain = gain,
                    enabled = enabled,
                    onGainChange = { onGainChange(i, it) }
                )
            }
        }
    }
}

@Composable
private fun SingleBandSlider(
    label: String,
    gain: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val alpha = if (enabled) 1f else 0.4f
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val animGain by animateFloatAsState(
        targetValue = gain,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "animGain"
    )

    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var lastHapticGain by remember { mutableFloatStateOf(gain) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(28.dp)
    ) {
        Text(
            text = if (gain >= 0) "+${gain.roundToInt()}" else "${gain.roundToInt()}",
            color = if (gain == 0f) c.textDisabled.copy(alpha = alpha) else c.accent.copy(alpha = alpha),
            fontFamily = font, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))

        val sliderHeightDp = 130.dp
        val density = androidx.compose.ui.platform.LocalDensity.current
        val sliderHeightPx = with(density) { sliderHeightDp.toPx() }

        Box(
            modifier = Modifier
                .width(24.dp)
                .height(sliderHeightDp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.bgElevated)
                .pointerInput(enabled, gain) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        accumulatedDrag = 0f
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { change.consume(); break }
                            val dy = change.position.y - change.previousPosition.y
                            accumulatedDrag += dy
                            change.consume()
                            val dbPerPixel = 24f / sliderHeightPx
                            val rawNew = gain - accumulatedDrag * dbPerPixel
                            val snapped = rawNew.coerceIn(-12f, 12f).let { (it * 2f).roundToInt() / 2f }
                            if (snapped != gain) {
                                if (kotlin.math.abs(snapped - lastHapticGain) >= 1f) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    lastHapticGain = snapped
                                }
                                onGainChange(snapped)
                                accumulatedDrag = 0f
                            }
                        }
                    }
                }
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val centerY = h / 2f
                    val fillFrac = (animGain / 24f)
                    val fillTop  = (centerY - fillFrac * h).coerceIn(0f, h)
                    val fillBot  = centerY
                    val top = minOf(fillTop, fillBot)
                    val bot = maxOf(fillTop, fillBot)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = if (animGain >= 0f)
                                listOf(c.accent.copy(alpha = 0.9f * alpha), c.accent.copy(alpha = 0.5f * alpha))
                            else
                                listOf(c.accentMuted.copy(alpha = 0.5f * alpha), c.accentMuted.copy(alpha = 0.9f * alpha)),
                            startY = top, endY = bot
                        ),
                        topLeft = Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(w, (bot - top).coerceAtLeast(0f))
                    )
                    drawLine(color = c.divider.copy(alpha = alpha), start = Offset(0f, centerY), end = Offset(w, centerY), strokeWidth = 1.5f)
                    val thumbY = (centerY - (animGain / 12f) * (h / 2f)).coerceIn(8f, h - 8f)
                    drawCircle(c.accent.copy(alpha = alpha), 11f, Offset(w / 2f, thumbY))
                    drawCircle(c.bgDeep, 5f, Offset(w / 2f, thumbY))
                }
        ) {}

        Spacer(Modifier.height(6.dp))
        Text(
            label, color = c.textDisabled.copy(alpha = alpha),
            fontFamily = font, fontSize = 8.sp, textAlign = TextAlign.Center, lineHeight = 10.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info Knob Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EqKnobCard(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    color: Color,
    enabled: Boolean
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val alpha = if (enabled) 1f else 0.4f
    val norm = ((value + 12f) / 24f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.bgCard)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(52.dp)) {
            val strokeW = 6f
            val r = size.minDimension / 2f - strokeW
            drawArc(color = c.bgElevated, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW, cap = StrokeCap.Round),
                topLeft = Offset(strokeW, strokeW), size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f))
            drawArc(color = color.copy(alpha = alpha), startAngle = 135f, sweepAngle = 270f * norm, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(strokeW, cap = StrokeCap.Round),
                topLeft = Offset(strokeW, strokeW), size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f))
        }
        Text("${if (value >= 0f) "+" else ""}${value.roundToInt()} dB", color = color.copy(alpha = alpha), fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(label, color = c.textDisabled.copy(alpha = alpha), fontFamily = font, fontSize = 10.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Equalizer Bottom Sheet Content (from TrackSettingsOverlay)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EqualizerSheetContent(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    val savedGains by viewModel.eqGains.collectAsState()
    val savedEnabled by viewModel.eqEnabled.collectAsState()
    val savedPresetName by viewModel.eqPresetName.collectAsState()
    val audioOutput by viewModel.audioOutput.collectAsState()
    val spatialAudio by viewModel.spatialAudio.collectAsState()
    val savedPreampGain by viewModel.preampGain.collectAsState()
    val savedAudioBalance by viewModel.audioBalance.collectAsState()

    var enabled by remember(savedEnabled) { mutableStateOf(savedEnabled) }
    var gains by remember(savedGains) { mutableStateOf(savedGains.toMutableList()) }
    var selectedPreset by remember(savedPresetName) {
        mutableStateOf(EQ_PRESETS.find { it.name == savedPresetName } ?: EQ_PRESETS[0])
    }
    var preampGain by remember(savedPreampGain) { mutableFloatStateOf(savedPreampGain) }
    var audioBalance by remember(savedAudioBalance) { mutableFloatStateOf(savedAudioBalance) }

    val animatedGains = gains.map { g ->
        animateFloatAsState(g, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "g").value
    }

    fun applyPreset(preset: EqPreset) {
        selectedPreset = preset
        gains = preset.gains.toMutableList()
        viewModel.updateEqGains(preset.gains)
        viewModel.setEqPresetName(preset.name)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Эквалайзер", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(selectedPreset.name, color = c.textSecondary, fontFamily = font, fontSize = 13.sp)
            }
            Text(if (enabled) "ВКЛ" else "ВЫКЛ", color = if (enabled) c.accent else c.textDisabled, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Switch(enabled, {
                enabled = it
                viewModel.setEqEnabled(it)
            }, colors = SwitchDefaults.colors(checkedThumbColor = c.bgDeep, checkedTrackColor = c.accent, uncheckedThumbColor = c.textDisabled, uncheckedTrackColor = c.bgCard))
        }

        Spacer(Modifier.height(4.dp))

        Box(Modifier.padding(horizontal = 16.dp)) {
            EqStudioOverviewCard(
                enabled = enabled,
                presetName = selectedPreset.name,
                audioOutput = audioOutput,
                spatialAudio = spatialAudio,
                preampGain = preampGain,
                audioBalance = audioBalance,
                gains = gains
            )
        }
        Spacer(Modifier.height(12.dp))

        EqSpectrumVisualizer(gains = animatedGains, enabled = enabled)
        Spacer(Modifier.height(8.dp))

        EqBandSliders(gains, enabled) { idx, v ->
            gains = gains.toMutableList().also { it[idx] = v }
            viewModel.updateEqGains(gains)
            if (selectedPreset != EQ_PRESETS.last()) {
                selectedPreset = EQ_PRESETS.last()
                viewModel.setEqPresetName(EQ_PRESETS.last().name)
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EqKnobCard("Бас", gains.take(3).average().toFloat(), Modifier.weight(1f), c.accent, enabled)
            EqKnobCard("Средние", gains.subList(3, 7).average().toFloat(), Modifier.weight(1f), c.accentVar, enabled)
            EqKnobCard("Высокие", gains.takeLast(3).average().toFloat(), Modifier.weight(1f), c.accentMuted, enabled)
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.padding(horizontal = 16.dp)) {
            EqHeadroomAssistantCard(
                gains = gains,
                preampGain = preampGain,
                enabled = enabled,
                onApply = { suggested ->
                    preampGain = suggested
                    viewModel.setPreAmpGain(suggested)
                }
            )
        }
        Spacer(Modifier.height(12.dp))

        // Audio output routing
        Text("Вывод звука", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val outputs = listOf(
                Triple("STEREO",   Icons.Rounded.Headphones,  "Стерео"),
                Triple("SPEAKER",  Icons.Rounded.VolumeUp,    "Динамик"),
                Triple("EARPIECE", Icons.Rounded.Hearing, "Трубка"),
            )
            outputs.forEach { (key, icon, label) ->
                val sel = audioOutput == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) c.accent else c.bgCard)
                        .clickable { viewModel.setAudioOutput(key) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(icon, null, tint = if (sel) c.bgDeep else c.textSecondary, modifier = Modifier.size(20.dp))
                        Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // 3D Sound
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.bgCard)
                .clickable { viewModel.setSpatialAudio(!spatialAudio) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.SurroundSound, null, tint = c.accentVar, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("3D Звук", color = c.textPrimary, fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Объёмное звучание", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
            }
            Switch(spatialAudio, { viewModel.setSpatialAudio(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = c.bgDeep, checkedTrackColor = c.accentVar, uncheckedThumbColor = c.textDisabled, uncheckedTrackColor = c.bgElevated))
        }

        Spacer(Modifier.height(12.dp))

        // ── Pre-Amp & Balance (in sheet) ─────────────────────────────────────
        EqPreAmpAndBalanceSection(
            preampGain = preampGain,
            audioBalance = audioBalance,
            enabled = enabled,
            onPreampChange = { v -> preampGain = v; viewModel.setPreAmpGain(v) },
            onBalanceChange = { v -> audioBalance = v; viewModel.setAudioBalance(v) }
        )
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    applyPreset(EQ_PRESETS[0])
                    enabled = true
                    preampGain = 0f
                    audioBalance = 0f
                    viewModel.setEqEnabled(true)
                    viewModel.setPreAmpGain(0f)
                    viewModel.setAudioBalance(0f)
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                border = BorderStroke(1.dp, c.divider), modifier = Modifier.weight(1f)
            ) { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Сбросить", fontFamily = font, fontSize = 13.sp) }
            Button(
                onClick = {
                    viewModel.updateEqGains(gains)
                    viewModel.setEqPresetName(selectedPreset.name)
                    viewModel.setEqEnabled(enabled)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.bgDeep),
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Rounded.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Сохранить", fontFamily = font, fontSize = 13.sp) }
        }

        Spacer(Modifier.height(16.dp))

        Text("Шаблоны", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(EQ_PRESETS) { preset ->
                val sel = preset.name == selectedPreset.name
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (sel) c.accent else c.bgCard)
                        .clickable { applyPreset(preset) }.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(preset.name, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pre-Amp & Balance Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EqPreAmpAndBalanceSection(
    preampGain: Float,
    audioBalance: Float,
    enabled: Boolean,
    onPreampChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val alpha = if (enabled) 1f else 0.4f
    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.bgCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Pre-Amp ──────────────────────────────────────────────────────────
        Text(
            "Предусиление (Pre-Amp)",
            color = c.textPrimary.copy(alpha = alpha),
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "-12",
                color = c.textDisabled.copy(alpha = alpha),
                fontFamily = font,
                fontSize = 10.sp
            )

            val preampWidthDp = 0.dp
            val preampTrackColor = c.bgElevated
            val preampFillColor = when {
                preampGain > 0f -> c.accent
                preampGain < 0f -> c.accentMuted
                else -> c.textDisabled
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                // Track background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.bgElevated)
                ) {
                    // Fill from center
                    val fillFrac = (preampGain / 12f).coerceIn(-1f, 1f)
                    if (fillFrac != 0f) {
                        val startFrac = if (fillFrac > 0f) 0.5f else (0.5f + fillFrac)
                        val endFrac   = if (fillFrac > 0f) (0.5f + fillFrac) else 0.5f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(endFrac - startFrac + 0f)
                                .offset(x = with(density) { 0.dp })
                                .align(Alignment.CenterStart)
                        )
                    }
                }

                // Actual slider
                androidx.compose.material3.Slider(
                    value = preampGain,
                    onValueChange = { if (enabled) onPreampChange(it) },
                    valueRange = -12f..12f,
                    steps = 47,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = preampFillColor.copy(alpha = alpha),
                        activeTrackColor = preampFillColor.copy(alpha = alpha),
                        inactiveTrackColor = c.bgElevated.copy(alpha = alpha),
                        activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    enabled = enabled
                )
            }

            Text(
                "+12",
                color = c.textDisabled.copy(alpha = alpha),
                fontFamily = font,
                fontSize = 10.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val preampLabel = when {
                preampGain > 0f -> "+${preampGain.roundToInt()} dB"
                preampGain < 0f -> "${preampGain.roundToInt()} dB"
                else -> "0 dB"
            }
            Text(
                "Усиление: $preampLabel",
                color = c.accent.copy(alpha = alpha),
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            if (preampGain != 0f) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.bgElevated)
                        .clickable(enabled = enabled) { onPreampChange(0f) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Центр",
                        color = c.textSecondary.copy(alpha = alpha),
                        fontFamily = font,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // ── Divider ──────────────────────────────────────────────────────────
        androidx.compose.material3.Divider(color = c.divider.copy(alpha = 0.5f))

        // ── Balance ──────────────────────────────────────────────────────────
        Text(
            "Баланс Л/П",
            color = c.textPrimary.copy(alpha = alpha),
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Л",
                color = if (audioBalance < -0.05f) c.accent.copy(alpha = alpha) else c.textDisabled.copy(alpha = alpha),
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            val balanceFillColor = when {
                audioBalance < -0.05f -> c.accentVar
                audioBalance > 0.05f  -> c.accentMuted
                else -> c.textDisabled
            }

            androidx.compose.material3.Slider(
                value = audioBalance,
                onValueChange = { if (enabled) onBalanceChange(it) },
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = balanceFillColor.copy(alpha = alpha),
                    activeTrackColor = balanceFillColor.copy(alpha = alpha),
                    inactiveTrackColor = c.bgElevated.copy(alpha = alpha),
                    activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                    inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                enabled = enabled
            )

            Text(
                "П",
                color = if (audioBalance > 0.05f) c.accent.copy(alpha = alpha) else c.textDisabled.copy(alpha = alpha),
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val balanceLabel = when {
                audioBalance < -0.05f -> "Левый ${(audioBalance * -100).roundToInt()}%"
                audioBalance > 0.05f  -> "Правый ${(audioBalance * 100).roundToInt()}%"
                else -> "Центр"
            }
            Text(
                balanceLabel,
                color = balanceFillColor(audioBalance, c).copy(alpha = alpha),
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            if (kotlin.math.abs(audioBalance) > 0.05f) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.bgElevated)
                        .clickable(enabled = enabled) { onBalanceChange(0f) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Центр",
                        color = c.textSecondary.copy(alpha = alpha),
                        fontFamily = font,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun balanceFillColor(balance: Float, c: ColorScheme): androidx.compose.ui.graphics.Color = when {
    balance < -0.05f -> c.accentVar
    balance > 0.05f  -> c.accentMuted
    else -> c.textDisabled
}
