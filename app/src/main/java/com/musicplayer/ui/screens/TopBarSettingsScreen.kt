package com.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.data.TopBarSettings
import com.musicplayer.ui.theme.LocalAppColors
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.sin
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// TopBar Settings Screen
// ─────────────────────────────────────────────────────────────────────────────
private fun previewTopBarTint(tb: TopBarSettings, c: com.musicplayer.ui.theme.AppColors): Color = when (tb.glassTintMode) {
    1 -> Color(0xFF9FDBFF)
    2 -> Color.White
    3 -> Color(0xFFFFC98A)
    else -> c.accent
}

private fun previewTopBarTintSecondary(tb: TopBarSettings, c: com.musicplayer.ui.theme.AppColors): Color = when (tb.glassTintMode) {
    1 -> Color(0xFF78B9FF)
    2 -> c.textPrimary
    3 -> Color(0xFFFFA76C)
    else -> c.accentVar
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarSettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current
    val tb   by viewModel.topBarSettings.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val songs by viewModel.songs.collectAsState()

    // Категории
    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Пресет", "Иконка", "Заголовок", "Фон", "Орбы", "Эффекты", "Кнопки", "Прочее")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, null)
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Шапка приложения",
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Структура, акценты и анимация в Material 3 оболочке",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = font,
                            fontSize = 11.sp
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(bottom = 80.dp)) {

            // ── Live Preview ──────────────────────────────────────────────────
            TopBarPreview(tb = tb, c = c, songsCount = songs.size)

            Spacer(Modifier.height(16.dp))

            // ── Tabs ──────────────────────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { tab ->
                    val i = tabs.indexOf(tab)
                    val sel = activeTab == i
                    FilterChip(
                        selected = sel,
                        onClick = { activeTab = i },
                        label = {
                            Text(
                                tab,
                                fontFamily = font,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Tab Content ───────────────────────────────────────────────────
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    if (targetState > initialState)
                        com.musicplayer.ui.navigation.AnimationsApplier.tabEnterFromRight(settings.tabSwitchAnim, settings.animParams)
                            .togetherWith(com.musicplayer.ui.navigation.AnimationsApplier.tabExitToLeft(settings.tabSwitchAnim, settings.animParams))
                    else
                        com.musicplayer.ui.navigation.AnimationsApplier.tabEnterFromLeft(settings.tabSwitchAnim, settings.animParams)
                            .togetherWith(com.musicplayer.ui.navigation.AnimationsApplier.tabExitToRight(settings.tabSwitchAnim, settings.animParams))
                },
                label = "tbSettingsTab"
            ) { tab ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (tab) {
                        // ── 0: Пресеты ────────────────────────────────────────
                        0 -> {
                            Text("Готовые стили", color = c.textPrimary, fontFamily = font,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val presets = listOf(
                                "Стекло" to TopBarSettings(stylePreset = 0),
                                "Минимал" to TopBarSettings(stylePreset = 1, bgStyle = 2, orbsVisible = false, iconRingVisible = false, dividerStyle = 1),
                                "Жирный" to TopBarSettings(stylePreset = 2, titleSize = 26f, titleWeight = 1, iconSize = 34f, bgStyle = 1, bgOpacity = 0.95f),
                                "Неон"    to TopBarSettings(stylePreset = 3, orbsVisible = true, orbColorMode = 2, orbOpacity = 0.32f, sparklesVisible = true, dividerStyle = 3, accentButtons = true),
                                "Ретро"  to TopBarSettings(stylePreset = 4, bgStyle = 1, bgOpacity = 0.98f, orbsVisible = false, iconStyle = 3, titleWeight = 2, dividerStyle = 1),
                                "Аура"   to TopBarSettings(stylePreset = 5, bgStyle = 4, orbColorMode = 5, orbOpacity = 0.28f, iconStyle = 4, iconAnimation = 5, buttonStyle = 4, dividerStyle = 5),
                                "Studio" to TopBarSettings(stylePreset = 6, bgStyle = 5, iconStyle = 1, iconAnimation = 4, buttonStyle = 5, accentButtons = true, buttonBorderVisible = true, dividerStyle = 2),
                                "Лёд"    to TopBarSettings(stylePreset = 7, bgStyle = 4, orbColorMode = 5, iconStyle = 5, iconTintMode = 1, bgOpacity = 0.9f, dividerStyle = 4),
                                "Liquid" to TopBarSettings(stylePreset = 8, bgStyle = 3, glassTintMode = 0, glassDepth = 0.92f, edgeShine = 0.85f, titleCapsuleVisible = true, titleCapsuleOpacity = 0.28f, subtitleStyle = 1, buttonStyle = 4, buttonBorderVisible = true),
                                "Telegram" to TopBarSettings(stylePreset = 9, bgStyle = 3, glassTintMode = 1, glassDepth = 0.88f, edgeShine = 0.92f, titleCapsuleVisible = true, titleCapsuleOpacity = 0.18f, subtitleStyle = 1, buttonStyle = 0, buttonBorderVisible = true, orbsVisible = false, sparklesVisible = false),
                                "Acrylic" to TopBarSettings(stylePreset = 10, bgStyle = 5, glassTintMode = 2, glassDepth = 0.78f, edgeShine = 0.66f, titleCapsuleVisible = false, subtitleStyle = 2, buttonStyle = 5, buttonBorderVisible = true)
                            )
                            presets.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { (name, preset) ->
                                        val sel = tb.stylePreset == preset.stylePreset
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    viewModel.updateTopBarSettings(preset.copy(
                                                        titleText = tb.titleText,
                                                        showSongCount = tb.showSongCount
                                                    ))
                                                },
                                            shape = RoundedCornerShape(18.dp),
                                            color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(
                                                if (sel) 1.5.dp else 1.dp,
                                                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(name, color = if (sel) c.accent else c.textPrimary, fontFamily = font,
                                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                if (sel) Text("Активен", color = c.accent, fontFamily = font, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }

                        // ── 1: Иконка ─────────────────────────────────────────
                        1 -> {
                            TbToggle("Показывать иконку", tb.showIcon, Icons.Rounded.MusicNote, c.accent) {
                                viewModel.updateTopBarSettings(tb.copy(showIcon = it))
                            }
                            if (tb.showIcon) {
                                TbSelector("Стиль иконки", listOf("Нота", "Эквалайзер", "Волна", "Диск", "Наушники", "Радио", "Искра"),
                                    tb.iconStyle, c) { viewModel.updateTopBarSettings(tb.copy(iconStyle = it)) }
                                TbSelector("Анимация", listOf("Пульс", "Вращение", "Прыжок", "Стоп", "Вихрь", "Дыхание"),
                                    tb.iconAnimation, c) { viewModel.updateTopBarSettings(tb.copy(iconAnimation = it)) }
                                TbSlider("Размер иконки", "${tb.iconSize.toInt()} dp", tb.iconSize, 18f..48f) {
                                    viewModel.updateTopBarSettings(tb.copy(iconSize = it))
                                }
                                TbSlider("Свечение вокруг", "${(tb.iconGlowRadius * 100).toInt()}%", tb.iconGlowRadius, 0f..1f) {
                                    viewModel.updateTopBarSettings(tb.copy(iconGlowRadius = it))
                                }
                                TbToggle("Кольцо-пульс", tb.iconRingVisible, Icons.Rounded.RadioButtonChecked, c.accentVar) {
                                    viewModel.updateTopBarSettings(tb.copy(iconRingVisible = it))
                                }
                                TbToggle("Фон иконки", tb.iconBgVisible, Icons.Rounded.CropSquare, c.accent) {
                                    viewModel.updateTopBarSettings(tb.copy(iconBgVisible = it))
                                }
                                TbSelector("Цвет иконки", listOf("Акцент", "Белый", "Primary"),
                                    tb.iconTintMode, c) { viewModel.updateTopBarSettings(tb.copy(iconTintMode = it)) }
                            }
                        }

                        // ── 2: Заголовок ──────────────────────────────────────
                        2 -> {
                            // Редактируемый текст заголовка
                            var editingTitle by remember { mutableStateOf(false) }
                            var draftTitle   by remember { mutableStateOf(tb.titleText) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (editingTitle) {
                                    OutlinedTextField(
                                        value = draftTitle,
                                        onValueChange = { draftTitle = it },
                                        label = { Text("Текст заголовка", fontFamily = font) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = c.accent,
                                            unfocusedBorderColor = c.divider,
                                            focusedTextColor = c.textPrimary,
                                            unfocusedTextColor = c.textPrimary,
                                            cursorColor = c.accent
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FilledIconButton(onClick = {
                                        viewModel.updateTopBarSettings(tb.copy(titleText = draftTitle.ifBlank { "Музыка" }))
                                        editingTitle = false
                                    }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.accent, contentColor = c.bgDeep)) {
                                        Icon(Icons.Rounded.Check, null)
                                    }
                                } else {
                                    Column(Modifier.weight(1f)) {
                                        Text("Текст заголовка", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                                        Text(tb.titleText, color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                    FilledIconButton(onClick = { draftTitle = tb.titleText; editingTitle = true },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.bgElevated, contentColor = c.accent)) {
                                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            TbSlider("Размер заголовка", "${tb.titleSize.toInt()} sp", tb.titleSize, 14f..34f) {
                                viewModel.updateTopBarSettings(tb.copy(titleSize = it))
                            }
                            TbSelector("Насыщенность", listOf("Bold", "ExtraBold", "Medium"),
                                tb.titleWeight, c) { viewModel.updateTopBarSettings(tb.copy(titleWeight = it)) }
                            TbSlider("Межбуквенный", "${(tb.titleLetterSpacing * 10).toInt() / 10f}", tb.titleLetterSpacing, -1f..2f) {
                                viewModel.updateTopBarSettings(tb.copy(titleLetterSpacing = it))
                            }
                            TbToggle("Курсив", tb.titleItalic, Icons.Rounded.FormatItalic, c.accentVar) {
                                viewModel.updateTopBarSettings(tb.copy(titleItalic = it))
                            }
                            TbSelector("Цвет заголовка", listOf("Primary", "Акцент", "Белый", "Secondary"),
                                tb.titleColorMode, c) { viewModel.updateTopBarSettings(tb.copy(titleColorMode = it)) }
                            TbSlider("Свечение заголовка", "${(tb.titleGlowStrength * 100).toInt()}%", tb.titleGlowStrength, 0f..1f) {
                                viewModel.updateTopBarSettings(tb.copy(titleGlowStrength = it))
                            }
                            TbToggle("Капсула под заголовком", tb.titleCapsuleVisible, Icons.Rounded.Title, c.accent) {
                                viewModel.updateTopBarSettings(tb.copy(titleCapsuleVisible = it))
                            }
                            if (tb.titleCapsuleVisible) {
                                TbSlider("Плотность капсулы", "${(tb.titleCapsuleOpacity * 100).toInt()}%", tb.titleCapsuleOpacity, 0.08f..0.45f) {
                                    viewModel.updateTopBarSettings(tb.copy(titleCapsuleOpacity = it))
                                }
                            }
                            HorizontalDivider(color = c.divider.copy(0.4f))
                            TbToggle("Показывать кол-во треков", tb.showSongCount, Icons.Rounded.Numbers, c.accentVar) {
                                viewModel.updateTopBarSettings(tb.copy(showSongCount = it))
                            }
                            if (tb.showSongCount) {
                                TbSlider("Размер подписи", "${tb.subtitleSize.toInt()} sp", tb.subtitleSize, 8f..16f) {
                                    viewModel.updateTopBarSettings(tb.copy(subtitleSize = it))
                                }
                                TbSelector("Стиль подписи", listOf("Простой", "Glass pill", "Accent pill"),
                                    tb.subtitleStyle, c) { viewModel.updateTopBarSettings(tb.copy(subtitleStyle = it)) }
                                var editPrefix by remember { mutableStateOf(false) }
                                var draftPrefix by remember { mutableStateOf(tb.songCountPrefix) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (editPrefix) {
                                        OutlinedTextField(
                                            value = draftPrefix,
                                            onValueChange = { draftPrefix = it },
                                            label = { Text("Префикс (напр. «♫ »)", fontFamily = font) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.accent)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        FilledIconButton(onClick = { viewModel.updateTopBarSettings(tb.copy(songCountPrefix = draftPrefix)); editPrefix = false },
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.accent, contentColor = c.bgDeep)) {
                                            Icon(Icons.Rounded.Check, null)
                                        }
                                    } else {
                                        Column(Modifier.weight(1f)) {
                                            Text("Префикс подписи", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                                            Text(if (tb.songCountPrefix.isBlank()) "(нет)" else "«${tb.songCountPrefix}»", color = c.textPrimary, fontFamily = font, fontSize = 13.sp)
                                        }
                                        FilledIconButton(onClick = { draftPrefix = tb.songCountPrefix; editPrefix = true },
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.bgElevated, contentColor = c.accent)) {
                                            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // ── 3: Фон ────────────────────────────────────────────
                        3 -> {
                            TbSelector("Стиль фона", listOf("Градиент", "Сплошной", "Прозрачный", "Матовое стекло", "Аура", "Spotlight"),
                                tb.bgStyle, c) { viewModel.updateTopBarSettings(tb.copy(bgStyle = it)) }
                            if (tb.bgStyle != 2) {
                                TbSlider("Прозрачность фона", "${(tb.bgOpacity * 100).toInt()}%", tb.bgOpacity, 0.1f..1f) {
                                    viewModel.updateTopBarSettings(tb.copy(bgOpacity = it))
                                }
                            }
                            TbSelector("Tint стекла", listOf("Accent", "Лёд", "Нейтральный", "Тёплый"),
                                tb.glassTintMode, c) { viewModel.updateTopBarSettings(tb.copy(glassTintMode = it)) }
                            TbSlider("Плотность стекла", "${(tb.glassDepth * 100).toInt()}%", tb.glassDepth, 0.2f..1.2f) {
                                viewModel.updateTopBarSettings(tb.copy(glassDepth = it))
                            }
                            TbSlider("Световая кромка", "${(tb.edgeShine * 100).toInt()}%", tb.edgeShine, 0f..1f) {
                                viewModel.updateTopBarSettings(tb.copy(edgeShine = it))
                            }
                        }

                        // ── 4: Орбы ───────────────────────────────────────────
                        4 -> {
                            TbToggle("Показывать орбы", tb.orbsVisible, Icons.Rounded.AutoAwesome, c.accent) {
                                viewModel.updateTopBarSettings(tb.copy(orbsVisible = it))
                            }
                            if (tb.orbsVisible) {
                                TbSlider("Количество орбов", "${tb.orbCount}", tb.orbCount.toFloat(), 1f..5f,
                                    steps = 3) { viewModel.updateTopBarSettings(tb.copy(orbCount = it.toInt())) }
                                TbSlider("Прозрачность орбов", "${(tb.orbOpacity * 100).toInt()}%", tb.orbOpacity, 0.02f..0.7f) {
                                    viewModel.updateTopBarSettings(tb.copy(orbOpacity = it))
                                }
                                TbSlider("Скорость орбов", "${(tb.orbSpeed * 10).toInt() / 10f}×", tb.orbSpeed, 0.1f..4f) {
                                    viewModel.updateTopBarSettings(tb.copy(orbSpeed = it))
                                }
                                TbSlider("Размер орбов", "${(tb.orbSize * 100).toInt()}%", tb.orbSize, 0.2f..2f) {
                                    viewModel.updateTopBarSettings(tb.copy(orbSize = it))
                                }
                                TbSelector("Цвет орбов", listOf("Accent", "Двойной", "Радуга", "Белый", "Тёплый", "Холодный"),
                                    tb.orbColorMode, c) { viewModel.updateTopBarSettings(tb.copy(orbColorMode = it)) }
                            }
                        }

                        // ── 5: Эффекты ────────────────────────────────────────
                        5 -> {
                            TbToggle("Частицы-искры", tb.sparklesVisible, Icons.Rounded.Stars, c.accentVar) {
                                viewModel.updateTopBarSettings(tb.copy(sparklesVisible = it))
                            }
                            if (tb.sparklesVisible) {
                                TbSlider("Количество частиц", "${tb.sparkleCount}", tb.sparkleCount.toFloat(), 2f..30f,
                                    steps = 13) { viewModel.updateTopBarSettings(tb.copy(sparkleCount = it.toInt())) }
                                TbSlider("Скорость частиц", "${(tb.sparkleSpeed * 10).toInt() / 10f}×", tb.sparkleSpeed, 0.2f..4f) {
                                    viewModel.updateTopBarSettings(tb.copy(sparkleSpeed = it))
                                }
                            }
                        }

                        // ── 6: Кнопки ─────────────────────────────────────────
                        6 -> {
                            TbSelector("Стиль кнопок", listOf("Стекло", "Заливка", "Контур", "Без фона", "Пилюля", "Неон"),
                                tb.buttonStyle, c) { viewModel.updateTopBarSettings(tb.copy(buttonStyle = it)) }
                            TbSlider("Размер кнопок", "${tb.buttonSize.toInt()} dp", tb.buttonSize, 32f..60f) {
                                viewModel.updateTopBarSettings(tb.copy(buttonSize = it))
                            }
                            TbSlider("Скругление кнопок", "${tb.buttonCorner.toInt()} dp", tb.buttonCorner, 0f..30f) {
                                viewModel.updateTopBarSettings(tb.copy(buttonCorner = it))
                            }
                            TbSlider("Расстояние между кнопками", "${tb.buttonGap.toInt()} dp", tb.buttonGap, 0f..16f) {
                                viewModel.updateTopBarSettings(tb.copy(buttonGap = it))
                            }
                            TbToggle("Accent-цвет кнопок", tb.accentButtons, Icons.Rounded.Palette, c.accent) {
                                viewModel.updateTopBarSettings(tb.copy(accentButtons = it))
                            }
                            TbToggle("Рамка вокруг кнопок", tb.buttonBorderVisible, Icons.Rounded.BorderAll, c.accentVar) {
                                viewModel.updateTopBarSettings(tb.copy(buttonBorderVisible = it))
                            }
                        }

                        // ── 7: Прочее ─────────────────────────────────────────
                        7 -> {
                            Text("Разделитель", color = c.textPrimary, fontFamily = font,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            TbSelector("Стиль разделителя", listOf("Нет", "Линия", "Акцент", "Свечение", "Пунктир", "Двойной"),
                                tb.dividerStyle, c) { viewModel.updateTopBarSettings(tb.copy(dividerStyle = it)) }
                            if (tb.dividerStyle > 0) {
                                TbSlider("Яркость", "${(tb.dividerOpacity * 100).toInt()}%", tb.dividerOpacity, 0.05f..1f) {
                                    viewModel.updateTopBarSettings(tb.copy(dividerOpacity = it))
                                }
                                TbSlider("Толщина", "${tb.dividerThickness.toInt()} dp", tb.dividerThickness, 1f..8f,
                                    steps = 6) { viewModel.updateTopBarSettings(tb.copy(dividerThickness = it)) }
                            }
                            HorizontalDivider(color = c.divider.copy(0.4f))
                            Text("Размер шапки", color = c.textPrimary, fontFamily = font,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            TbSlider("Высота шапки (+dp)", "+${tb.headerHeightExtra.toInt()} dp", tb.headerHeightExtra, 0f..48f) {
                                viewModel.updateTopBarSettings(tb.copy(headerHeightExtra = it))
                            }
                        }
                    }
                }
            }
            }

            Spacer(Modifier.height(16.dp))

            // ── Reset Button ──────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedButton(
                    onClick = { viewModel.updateTopBarSettings(TopBarSettings()) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Сбросить к настройкам по умолчанию", fontFamily = font)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Live Preview
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarPreview(
    tb: TopBarSettings,
    c: com.musicplayer.ui.theme.AppColors,
    songsCount: Int
) {
    val font = LocalAppFontFamily.current
    val glassTint = previewTopBarTint(tb, c)
    val glassTintSecondary = previewTopBarTintSecondary(tb, c)
    val glassDepth = tb.glassDepth.coerceIn(0.2f, 1.2f)
    val edgeShine = tb.edgeShine.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(20.dp))
    ) {
        // ── Background ────────────────────────────────────────────────────────
        val bgMod = when (tb.bgStyle) {
            1 -> Modifier.background(c.bgSurface.copy(alpha = tb.bgOpacity))
            2 -> Modifier.background(c.bgDeep.copy(alpha = 0.7f))
            3 -> Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        c.bgCard.copy(alpha = (0.62f + glassDepth * 0.18f).coerceIn(0.55f, 0.88f)),
                        glassTint.copy(alpha = 0.08f + glassDepth * 0.10f),
                        c.bgDeep.copy(alpha = (0.70f + glassDepth * 0.12f).coerceIn(0.64f, 0.9f))
                    )
                )
            )
            4 -> Modifier.background(
                Brush.linearGradient(
                    listOf(
                        glassTint.copy(alpha = tb.bgOpacity * (0.14f + glassDepth * 0.12f)),
                        c.bgSurface.copy(alpha = tb.bgOpacity * (0.78f + glassDepth * 0.12f)),
                        glassTintSecondary.copy(alpha = tb.bgOpacity * (0.10f + glassDepth * 0.12f))
                    )
                )
            )
            5 -> Modifier.background(
                Brush.radialGradient(
                    listOf(
                        glassTint.copy(alpha = tb.bgOpacity * (0.12f + glassDepth * 0.16f)),
                        c.bgSurface.copy(alpha = tb.bgOpacity * (0.78f + glassDepth * 0.14f)),
                        c.bgDeep.copy(alpha = tb.bgOpacity * (0.68f + glassDepth * 0.14f))
                    ),
                    radius = 900f
                )
            )
            else -> Modifier.background(Brush.verticalGradient(listOf(
                c.bgSurface.copy(alpha = tb.bgOpacity), c.bgDeep.copy(alpha = 0f)
            )))
        }
        Box(modifier = Modifier.fillMaxWidth().height((56 + tb.headerHeightExtra).dp.coerceAtLeast(56.dp)).then(bgMod)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.02f + edgeShine * 0.08f),
                                Color.Transparent,
                                glassTint.copy(alpha = 0.02f + glassDepth * 0.04f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .fillMaxWidth(0.86f)
                    .height(24.dp)
                    .blur((10.dp + 12.dp * edgeShine).coerceAtLeast(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.03f + edgeShine * 0.10f),
                                glassTint.copy(alpha = 0.02f + edgeShine * 0.05f),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(999.dp)
                    )
            )

            // ── Orbs ──────────────────────────────────────────────────────────
            if (tb.orbsVisible && tb.orbCount > 0) {
                val it = rememberInfiniteTransition(label = "pvOrbs")
                val phases = (0 until minOf(tb.orbCount, 5)).map { i ->
                    it.animateFloat(0f, 1f,
                        infiniteRepeatable(tween((2800 + i * 700) / tb.orbSpeed.coerceAtLeast(0.1f).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing), RepeatMode.Reverse), "pvOrb$i"
                    ).value
                }
                val orbPos = listOf(Offset(0.12f, 0.4f), Offset(0.78f, 0.6f), Offset(0.5f, 0.2f), Offset(0.3f, 0.8f), Offset(0.9f, 0.3f))
                val baseR  = listOf(70f, 55f, 45f, 40f, 42f)
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    phases.forEachIndexed { i, phase ->
                        val pos   = orbPos[i]; val r = (baseR[i] * tb.orbSize) + phase * 20f * tb.orbSize
                        val alpha = tb.orbOpacity + phase * tb.orbOpacity * 0.4f
                        val color = when (tb.orbColorMode) {
                            1 -> if (i % 2 == 0) c.accent else c.accentVar
                            2 -> listOf(c.accent, c.accentVar, Color(0xFF8B6FD4), Color(0xFF6FD48B), Color(0xFFD46F6F))[i % 5]
                            3 -> Color.White
                            4 -> listOf(Color(0xFFFFB86C), Color(0xFFFF8A65), Color(0xFFFFD180), c.accent)[i % 4]
                            5 -> listOf(Color(0xFF80DEEA), Color(0xFF64B5F6), Color(0xFF90CAF9), c.accentVar)[i % 4]
                            else -> c.accent
                        }
                        drawCircle(brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent),
                            center = Offset(pos.x * w, pos.y * h), radius = r),
                            radius = r, center = Offset(pos.x * w, pos.y * h))
                    }
                }
            }

            // ── Sparkles ──────────────────────────────────────────────────────
            if (tb.sparklesVisible) {
                val spIt = rememberInfiniteTransition(label = "pvSp")
                val spT by spIt.animateFloat(0f, 1f, infiniteRepeatable(tween((3000 / tb.sparkleSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(400), easing = LinearEasing)), "pvSpT")
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    repeat(tb.sparkleCount) { i ->
                        val t = (spT + i.toFloat() / tb.sparkleCount) % 1f
                        val x = ((i * 137.5f + t * 60f) % w)
                        val y = h * 0.5f + sin((t + i * 0.3f) * 6.28f).toFloat() * h * 0.4f
                        val a = sin(t * 3.14f).toFloat().coerceIn(0f, 1f) * 0.8f
                        if (a > 0.05f) drawCircle(c.accent.copy(alpha = a), radius = 2f, center = Offset(x, y))
                    }
                }
            }

            // ── Simulated bar content ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon preview
                if (tb.showIcon) {
                    val iconAnim = rememberInfiniteTransition(label = "pvIcon")
                    val phase by iconAnim.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pvIP")
                    val scl = when (tb.iconAnimation) {
                        0 -> 0.88f + phase * 0.24f
                        2 -> 1f + abs(sin(phase * 3.14f * 2).toFloat()) * 0.18f
                        4 -> 0.92f + phase * 0.12f
                        5 -> 0.95f + phase * 0.08f
                        else -> 1f
                    }
                    val rot = when (tb.iconAnimation) {
                        1 -> phase * 360f
                        4 -> phase * 48f
                        else -> 0f
                    }
                    val shiftY = when (tb.iconAnimation) {
                        2 -> -6f * abs(sin(phase * 3.14f * 2).toFloat())
                        5 -> sin(phase * 3.14f * 2).toFloat() * 4f
                        else -> 0f
                    }
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(tb.iconSize.dp + 8.dp)) {
                        if (tb.iconRingVisible) {
                            Box(modifier = Modifier.size(tb.iconSize.dp + 8.dp)
                                .graphicsLayer { scaleX = scl * 1.15f; scaleY = scl * 1.15f; alpha = 0.3f * tb.iconGlowRadius }
                                .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.55f), Color.Transparent)),
                                    androidx.compose.foundation.shape.CircleShape))
                        }
                        Box(modifier = Modifier.size(tb.iconSize.dp)
                            .graphicsLayer { scaleX = scl; scaleY = scl; rotationZ = rot; translationY = shiftY }
                            .clip(RoundedCornerShape(tb.iconSize.dp * 0.36f))
                            .let { m ->
                                if (tb.iconBgVisible)
                                    m.background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.25f), c.accentVar.copy(alpha = 0.35f))))
                                else m
                            },
                            contentAlignment = Alignment.Center) {
                            val iconRes = when (tb.iconStyle) {
                                1 -> Icons.Rounded.GraphicEq
                                2 -> Icons.Rounded.Waves
                                3 -> Icons.Rounded.Album
                                4 -> Icons.Rounded.Headphones
                                5 -> Icons.Rounded.Mic
                                6 -> Icons.Rounded.Bolt
                                else -> Icons.Rounded.MusicNote
                            }
                            val iconTint = when (tb.iconTintMode) {
                                1 -> Color.White
                                2 -> c.textPrimary
                                else -> c.accent
                            }
                            Icon(iconRes, null, tint = iconTint, modifier = Modifier.size(tb.iconSize.dp * 0.58f))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Title preview
                val titlePreviewModifier = if (tb.titleCapsuleVisible) {
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.04f + tb.titleCapsuleOpacity * 0.10f),
                                    glassTint.copy(alpha = tb.titleCapsuleOpacity * 0.55f),
                                    c.bgCard.copy(alpha = 0.42f + glassDepth * 0.18f)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.05f + tb.titleCapsuleOpacity * 0.18f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                } else Modifier
                Column(Modifier.weight(1f).then(titlePreviewModifier)) {
                    val fw = when (tb.titleWeight) {
                        1 -> FontWeight.ExtraBold; 2 -> FontWeight.Medium; else -> FontWeight.Bold
                    }
                    val titleColor = when (tb.titleColorMode) {
                        1 -> c.accent; 2 -> Color.White; 3 -> c.textSecondary; else -> c.textPrimary
                    }
                    val titleGlow = tb.titleGlowStrength.coerceIn(0f, 1f)
                    Text(tb.titleText, color = titleColor, fontFamily = font, fontWeight = fw,
                        fontSize = tb.titleSize.coerceAtMost(20f).sp, letterSpacing = tb.titleLetterSpacing.sp,
                        fontStyle = if (tb.titleItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = titleColor.copy(alpha = titleGlow * 0.55f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 1.6f),
                                blurRadius = 10f * titleGlow
                            )
                        ))
                    if (tb.showSongCount && songsCount > 0) {
                        val subtitleText = "${tb.songCountPrefix}$songsCount треков"
                        when (tb.subtitleStyle) {
                            1, 2 -> {
                                val subtitleBg = if (tb.subtitleStyle == 2) {
                                    Brush.linearGradient(listOf(glassTint.copy(alpha = 0.24f), glassTintSecondary.copy(alpha = 0.18f)))
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.03f + glassDepth * 0.05f),
                                            c.bgCard.copy(alpha = 0.34f + glassDepth * 0.12f),
                                            glassTint.copy(alpha = 0.10f + glassDepth * 0.08f)
                                        )
                                    )
                                }
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(subtitleBg)
                                        .border(
                                            1.dp,
                                            if (tb.subtitleStyle == 2) glassTint.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(999.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        subtitleText,
                                        color = if (tb.subtitleStyle == 2) glassTint else c.textPrimary.copy(alpha = 0.82f),
                                        fontFamily = font,
                                        fontSize = tb.subtitleSize.coerceAtMost(13f).sp
                                    )
                                }
                            }
                            else -> Text(
                                subtitleText,
                                color = glassTint.copy(0.75f),
                                fontFamily = font,
                                fontSize = tb.subtitleSize.coerceAtMost(13f).sp
                            )
                        }
                    }
                }

                // Button previews
                val btnSizePv = (tb.buttonSize * 0.82f).dp.coerceAtMost(44.dp)
                val btnCornerShape = RoundedCornerShape(tb.buttonCorner.dp.coerceAtMost(22.dp))
                val btnBgSearch: Brush = when (tb.buttonStyle) {
                    1 -> Brush.linearGradient(listOf(glassTint.copy(0.88f), glassTint.copy(0.88f)))
                    4 -> Brush.linearGradient(listOf(glassTint.copy(0.18f + glassDepth * 0.10f), glassTintSecondary.copy(0.14f + glassDepth * 0.10f)))
                    5 -> Brush.linearGradient(listOf(glassTint.copy(0.88f), glassTintSecondary.copy(0.76f)))
                    else -> Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.02f + glassDepth * 0.04f),
                            glassTint.copy(alpha = 0.12f + glassDepth * 0.08f),
                            c.bgCard.copy(alpha = 0.34f + glassDepth * 0.10f)
                        )
                    )
                }
                val btnBgMenu: Brush = when (tb.buttonStyle) {
                    1 -> Brush.linearGradient(listOf(glassTint.copy(0.88f), glassTint.copy(0.88f)))
                    4 -> Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.02f + glassDepth * 0.04f),
                            c.bgCard.copy(alpha = 0.48f + glassDepth * 0.14f),
                            glassTint.copy(alpha = 0.08f + glassDepth * 0.08f)
                        )
                    )
                    5 -> Brush.linearGradient(listOf(c.bgCard.copy(0.42f), glassTint.copy(0.34f + glassDepth * 0.08f)))
                    else -> Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.02f + glassDepth * 0.05f),
                            c.bgCard.copy(alpha = 0.50f + glassDepth * 0.14f),
                            c.bgElevated.copy(alpha = 0.62f + glassDepth * 0.12f)
                        )
                    )
                }
                val previewBtnShape = if (tb.buttonStyle == 4) RoundedCornerShape(999.dp) else btnCornerShape
                val searchTint = if (tb.accentButtons) glassTint else if (tb.buttonStyle == 1 || tb.buttonStyle == 5) c.bgDeep else c.textPrimary
                val pvBorder = if (tb.buttonBorderVisible || tb.buttonStyle == 5) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f + glassDepth * 0.12f)) else null

                Row(horizontalArrangement = Arrangement.spacedBy(tb.buttonGap.dp.coerceIn(0.dp, 16.dp))) {
                    Box(Modifier.size(btnSizePv).clip(previewBtnShape).background(btnBgSearch)
                        .let { m -> if (pvBorder != null) m.border(pvBorder, previewBtnShape) else m },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Search, null, tint = searchTint, modifier = Modifier.size(btnSizePv * 0.5f))
                    }
                    Box(Modifier.size(btnSizePv).clip(previewBtnShape).background(btnBgMenu)
                        .let { m -> if (pvBorder != null) m.border(pvBorder, previewBtnShape) else m },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MoreVert, null,
                            tint = if (tb.accentButtons) glassTint else c.textPrimary,
                            modifier = Modifier.size(btnSizePv * 0.5f))
                    }
                }
            }

            // ── Divider preview ───────────────────────────────────────────────
            val pvDivH = tb.dividerThickness.dp.coerceAtLeast(1.dp)
            when (tb.dividerStyle) {
                1 -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(pvDivH).background(c.divider.copy(tb.dividerOpacity)))
                2 -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(pvDivH.coerceAtLeast(2.dp)).background(Brush.horizontalGradient(listOf(Color.Transparent, c.accent.copy(tb.dividerOpacity), Color.Transparent))))
                3 -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(pvDivH.coerceAtLeast(2.dp)).background(Brush.horizontalGradient(listOf(Color.Transparent, c.accent.copy(tb.dividerOpacity * 0.8f), c.accentVar.copy(tb.dividerOpacity), c.accent.copy(tb.dividerOpacity * 0.8f), Color.Transparent))))
                4 -> Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(8) {
                        Box(Modifier.weight(1f).height(pvDivH.coerceAtLeast(2.dp)).clip(RoundedCornerShape(999.dp)).background(c.accent.copy(tb.dividerOpacity * 0.8f)))
                    }
                }
                5 -> Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(pvDivH.coerceAtLeast(2.dp)).background(Brush.horizontalGradient(listOf(Color.Transparent, c.accent.copy(tb.dividerOpacity), Color.Transparent))))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, c.accentVar.copy(tb.dividerOpacity * 0.75f), Color.Transparent))))
                }
            }
        }

        // Preview label
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                "ПРЕВЬЮ",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = font,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TbToggle(
    label: String, checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onToggle: (Boolean) -> Unit
) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!checked) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = c.textPrimary, fontFamily = font, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked,
                onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = color,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun TbSlider(
    label: String, valueLabel: String,
    value: Float, range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    val c    = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = c.textPrimary.copy(0.9f), fontFamily = font,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value,
                onValueChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth().height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun TbSelector(
    label: String, options: List<String>,
    selected: Int, c: com.musicplayer.ui.theme.AppColors,
    onSelect: (Int) -> Unit
) {
    val font = LocalAppFontFamily.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = c.textPrimary.copy(0.9f), fontFamily = font,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        val columns = if (options.size <= 4) options.size else 3
        options.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { offset, name ->
                    val i = rowIndex * columns + offset
                    val sel = selected == i
                    FilterChip(
                        selected = sel,
                        onClick = { onSelect(i) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                name,
                                fontFamily = font,
                                fontSize = 10.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                if (row.size < columns) repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
