package com.musicplayer.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.data.AppTheme
import com.musicplayer.data.InterfaceStyle
import com.musicplayer.data.OrbSettings
import com.musicplayer.data.PlayerSettings
import com.musicplayer.data.RepeatMode
import com.musicplayer.data.SortOrder
import kotlin.math.roundToInt
import com.musicplayer.ui.theme.*
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import com.musicplayer.viewmodel.SleepTimerState
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ThemeGridEntry(
    val theme: AppTheme,
    val label: String,
    val previewStart: Color,
    val previewEnd: Color,
    val swatches: List<Color>
)

private fun themeGridEntry(
    theme: AppTheme,
    label: String
): ThemeGridEntry {
    val palette = appColorsForTheme(theme)
    return ThemeGridEntry(
        theme = theme,
        label = label,
        previewStart = palette.accent,
        previewEnd = palette.accentVar,
        swatches = listOf(
            palette.bgDeep,
            palette.bgElevated,
            palette.accent,
            palette.accentVar
        ).distinct().take(4)
    )
}

private data class FontBrowserEntry(
    val file: File,
    val isDirectory: Boolean
)

private data class FontDirectorySuggestion(
    val directory: File,
    val fontCount: Int
)

private data class OrbScenePreset(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val apply: (OrbSettings) -> OrbSettings
)

private data class TypographyStudioPreset(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val apply: (PlayerSettings) -> PlayerSettings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onTransitionsClick: () -> Unit = {},
    onAnimationsClick: () -> Unit = {},
    onOrbsClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onCustomThemeClick: () -> Unit = {},
    onTopBarClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTypographyStudio by remember { mutableStateOf(false) }
    var showFontPickerDialog by remember { mutableStateOf(false) }
    val selectedBuiltInFont = remember(settings.selectedFontId) {
        AppFontList.firstOrNull { it.id == settings.selectedFontId } ?: DefaultAppFont
    }

    // Displayed font label
    val fontLabel = when {
        settings.customFontUri.isNotBlank() ->
            File(settings.customFontUri).name.substringBeforeLast('.')
        settings.selectedFontId == DefaultAppFontId -> "По умолчанию (${DefaultAppFont.displayName})"
        else -> selectedBuiltInFont.displayName
    }
    val themeLabel = settings.theme.settingsLabel()
    val interfaceLabel = if (settings.theme == AppTheme.MATERIAL_YOU) "Material You" else "Material 3"
    val sleepTimerLabel = when {
        sleepTimerState.isRunning && sleepTimerState.stopAfterTrack && sleepTimerState.remainingSeconds <= 0 ->
            "После текущего трека"
        sleepTimerState.isRunning ->
            "Осталось ${formatSleepTimerDuration(sleepTimerState.remainingSeconds)}"
        else -> "Таймер сна, затухание и действие по завершении"
    }
    val appVersion = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("0.9")
    }
    val openFontPicker = {
        showFontPickerDialog = true
    }
    val openAbout = {
        val uri = android.net.Uri.parse("https://t.me/plugin_XD")
        val tgIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
            setPackage("org.telegram.messenger")
        }
        val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(tgIntent)
        } catch (_: Exception) {
            context.startActivity(fallback)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        "Настройки",
                        fontFamily = LocalAppFontFamily.current,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { showThemeDialog = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Palette, contentDescription = "Открыть темы")
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingsOverviewCard(
                themeLabel = themeLabel,
                fontLabel = fontLabel,
                interfaceLabel = interfaceLabel,
                onThemeClick = { showThemeDialog = true },
                onTypographyClick = { showTypographyStudio = true },
                onFontClick = openFontPicker
            )

            SettingsSectionBlock(
                title = "Персонализация",
                subtitle = "Тема, шрифт, типографика и верхняя панель"
            ) {
                SettingsNavigationItem(
                    icon = Icons.Rounded.Palette,
                    iconBg = MaterialTheme.colorScheme.primary,
                    title = "Внешний вид",
                    subtitle = "Текущая тема: $themeLabel",
                    onClick = { showThemeDialog = true }
                )
                SettingsGroupDivider()
                SettingsNavigationItem(
                    icon = Icons.Rounded.FormatSize,
                    iconBg = MaterialTheme.colorScheme.tertiary,
                    title = "Типографика",
                    subtitle = "Размер, контраст и превью текста",
                    onClick = { showTypographyStudio = true }
                )
                SettingsGroupDivider()
                SettingsNavigationItem(
                    icon = Icons.Rounded.TextFields,
                    iconBg = MaterialTheme.colorScheme.secondary,
                    title = "Шрифт",
                    subtitle = fontLabel,
                    onClick = openFontPicker
                )
                SettingsGroupDivider()
                SettingsNavigationItem(
                    icon = Icons.Rounded.Tune,
                    iconBg = MaterialTheme.colorScheme.primary,
                    title = "Верхняя панель",
                    subtitle = "Стиль, фон и поведение шапки",
                    onClick = onTopBarClick
                )
            }

            SettingsSectionBlock(
                title = "Движение и атмосфера",
                subtitle = "Анимации, переходы и фоновые эффекты"
            ) {
                SettingsNavigationItem(
                    icon = Icons.Rounded.AutoAwesome,
                    iconBg = MaterialTheme.colorScheme.primary,
                    title = "Анимации",
                    subtitle = "Плавность, ритм и поведение элементов",
                    onClick = onAnimationsClick
                )
                SettingsGroupDivider()
                SettingsNavigationItem(
                    icon = Icons.Rounded.SwapHoriz,
                    iconBg = MaterialTheme.colorScheme.secondary,
                    title = "Переходы",
                    subtitle = "Кроссфейд и длительность смены треков",
                    onClick = onTransitionsClick
                )
                SettingsGroupDivider()
                SettingsNavigationItem(
                    icon = Icons.Rounded.BlurOn,
                    iconBg = MaterialTheme.colorScheme.tertiary,
                    title = "Орбы",
                    subtitle = "Фоновая сцена и визуальная реакция",
                    onClick = onOrbsClick
                )
            }

            SettingsSectionBlock(
                title = "Воспроизведение",
                subtitle = "Поведение плеера, сортировка и звук"
            ) {
                SettingsSwitchItem(
                    icon = Icons.Rounded.Shuffle,
                    iconBg = MaterialTheme.colorScheme.secondary,
                    title = "Перемешивание",
                    subtitle = "Случайный порядок треков",
                    checked = settings.shuffleEnabled,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(shuffleEnabled = it))
                    }
                )
                SettingsGroupDivider()
                SettingsSwitchItem(
                    icon = Icons.Rounded.ShowChart,
                    iconBg = MaterialTheme.colorScheme.primary,
                    title = "Волнистый ползунок",
                    subtitle = if (settings.useWavySeekBar) {
                        "В плеере используется волнистый анимированный ползунок"
                    } else {
                        "В плеере используется обычный ползунок"
                    },
                    checked = settings.useWavySeekBar,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(useWavySeekBar = it))
                    }
                )
                SettingsGroupDivider()
                SettingsSwitchItem(
                    icon = Icons.Rounded.Album,
                    iconBg = MaterialTheme.colorScheme.tertiary,
                    title = "Случайные онлайн-альбомы",
                    subtitle = if (settings.showRandomOnlineAlbumsShelf) {
                        "На главном экране показывается новая витрина альбомов при каждом запуске"
                    } else {
                        "Вместо «Недавно добавленные» можно показывать случайные альбомы с сайта"
                    },
                    checked = settings.showRandomOnlineAlbumsShelf,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(showRandomOnlineAlbumsShelf = it))
                    }
                )
                SettingsGroupDivider()
                SettingsNavigationItem(
                    icon = Icons.Rounded.Bedtime,
                    iconBg = MaterialTheme.colorScheme.primary,
                    title = "Таймер сна",
                    subtitle = sleepTimerLabel,
                    onClick = onSleepTimerClick
                )
                SettingsGroupDivider()
                SettingsSortItem(
                    settings = settings,
                    onSortChange = { viewModel.updateSettings(settings.copy(sortOrder = it)) }
                )
                SettingsGroupDivider()
                SettingsNavigationItem(
                    icon = Icons.Rounded.GraphicEq,
                    iconBg = MaterialTheme.colorScheme.tertiary,
                    title = "Эквалайзер",
                    subtitle = "Пресеты и настройка звучания",
                    onClick = onEqualizerClick
                )
            }

            SettingsSectionBlock(
                title = "Сервис и информация",
                subtitle = "Статистика и сведения о приложении"
            ) {
                SettingsNavigationItem(
                    icon = Icons.Rounded.BarChart,
                    iconBg = MaterialTheme.colorScheme.secondary,
                    title = "Статистика",
                    subtitle = "История прослушивания и показатели библиотеки",
                    onClick = onStatsClick
                )
                SettingsGroupDivider()
                SettingsNavigationItem(
                    icon = Icons.Rounded.Info,
                    iconBg = MaterialTheme.colorScheme.tertiary,
                    title = "О приложении",
                    subtitle = "Glowpath · v$appVersion",
                    onClick = openAbout
                )
                if (settings.customFontUri.isNotBlank()) {
                    SettingsGroupDivider()
                    SettingsNavigationItem(
                        icon = Icons.Rounded.RestartAlt,
                        iconBg = MaterialTheme.colorScheme.error,
                        title = "Сбросить шрифт",
                        subtitle = "Вернуть ${DefaultAppFont.displayName} по умолчанию",
                        onClick = {
                            viewModel.updateSettings(
                                settings.copy(
                                    customFontUri = "",
                                    selectedFontId = DefaultAppFontId
                                )
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentTheme    = settings.theme,
            onThemeSelected = {
                viewModel.updateSettings(
                    settings.copy(
                        theme = it,
                        useDynamicColors = it == AppTheme.MATERIAL_YOU
                    )
                )
            },
            onCustomThemeClick = {
                showThemeDialog = false
                onCustomThemeClick()
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showTypographyStudio) {
        TypographyStudioDialog(
            viewModel = viewModel,
            onDismiss = { showTypographyStudio = false }
        )
    }

    if (showFontPickerDialog) {
        FontPickerDialog(
            currentFontId = settings.selectedFontId,
            currentCustomFontUri = settings.customFontUri,
            onFontSelected = { fontId ->
                viewModel.updateSettings(
                    settings.copy(
                        selectedFontId = fontId,
                        customFontUri = ""
                    )
                )
                showFontPickerDialog = false
            },
            onCustomFontSelected = { fontPath ->
                viewModel.updateSettings(settings.copy(customFontUri = fontPath))
                showFontPickerDialog = false
            },
            onDismiss = { showFontPickerDialog = false }
        )
    }


}

@Composable
private fun SettingsOverviewCard(
    themeLabel: String,
    fontLabel: String,
    interfaceLabel: String,
    onThemeClick: () -> Unit,
    onTypographyClick: () -> Unit,
    onFontClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = 0.58f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.primaryContainer
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.SettingsSuggest,
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Material 3 настройки",
                        color = colors.onSurface,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp
                    )
                    Text(
                        "Секции собраны на спокойных surface-контейнерах, list items и мягких акцентах",
                        color = colors.onSurfaceVariant,
                        fontFamily = font,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsInfoPill(
                    icon = Icons.Rounded.Palette,
                    label = "Тема",
                    value = themeLabel,
                    modifier = Modifier.weight(1f)
                )
                SettingsInfoPill(
                    icon = Icons.Rounded.TextFields,
                    label = "Шрифт",
                    value = fontLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            SettingsInfoPill(
                icon = Icons.Rounded.DashboardCustomize,
                label = "Стиль интерфейса",
                value = interfaceLabel,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onThemeClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.ColorLens, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Темы", fontFamily = font, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onTypographyClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.TextFormat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Текст", fontFamily = font, fontWeight = FontWeight.SemiBold)
                }
            }

            AssistChip(
                onClick = onFontClick,
                shape = RoundedCornerShape(18.dp),
                label = {
                    Text(
                        "Выбрать другой шрифт",
                        fontFamily = font,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionBlock(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                fontFamily = font,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = colors.onSurfaceVariant,
                fontFamily = font,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        SettingsSection(content = content)
    }
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    )
}

@Composable
fun ThemePickerDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onCustomThemeClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val scrollState = rememberScrollState()
    val themes = listOf(
        themeGridEntry(AppTheme.BLOOMEE, "Малиновый"),
        themeGridEntry(AppTheme.DARK_BROWN, "Шоколадный"),
        themeGridEntry(AppTheme.DARK_BLACK, "Антрацитовый"),
        themeGridEntry(AppTheme.LIGHT, "Молочный"),
        themeGridEntry(AppTheme.PURPLE, "Аметистовый"),
        themeGridEntry(AppTheme.PINK, "Пудровый"),
        themeGridEntry(AppTheme.OCEAN, "Лазурный"),
        themeGridEntry(AppTheme.FOREST, "Хвойно-зелёный"),
        themeGridEntry(AppTheme.SUNSET, "Коралловый"),
        themeGridEntry(AppTheme.MIDNIGHT, "Индиго"),
        themeGridEntry(AppTheme.NEON, "Мятно-неоновый"),
        themeGridEntry(AppTheme.ROSE_GOLD, "Розовое золото"),
        themeGridEntry(AppTheme.ARCTIC, "Ледяной голубой"),
        themeGridEntry(AppTheme.AMBER, "Янтарный"),
        themeGridEntry(AppTheme.EMERALD, "Изумрудный"),
        themeGridEntry(AppTheme.AMOLED, "Угольный"),
        themeGridEntry(AppTheme.LAVENDER, "Лавандовый"),
        themeGridEntry(AppTheme.RUBY, "Рубиновый"),
        themeGridEntry(AppTheme.STEEL, "Стальной"),
        themeGridEntry(AppTheme.MATCHA, "Фисташковый"),
        themeGridEntry(AppTheme.DESERT, "Песочный"),
        themeGridEntry(AppTheme.COBALT, "Кобальтовый"),
        themeGridEntry(AppTheme.CHERRY, "Вишнёвый"),
        themeGridEntry(AppTheme.MOCHA, "Кофейный"),
        themeGridEntry(AppTheme.AURORA, "Мятно-бирюзовый"),
        themeGridEntry(AppTheme.COSMOS, "Орхидейный"),
        themeGridEntry(AppTheme.SUNRISE, "Абрикосовый"),
        themeGridEntry(AppTheme.GRAPHITE, "Графитовый"),
        themeGridEntry(AppTheme.SAKURA, "Нежно-розовый"),
        themeGridEntry(AppTheme.LAGOON, "Аквамариновый"),
        themeGridEntry(AppTheme.VOLCANO, "Терракотовый"),
        themeGridEntry(AppTheme.IVORY, "Слоновая кость"),
        themeGridEntry(AppTheme.CITRUS, "Лимонный"),
        themeGridEntry(AppTheme.FROST, "Морозный циан"),
        themeGridEntry(AppTheme.EMBER, "Медный"),
        themeGridEntry(AppTheme.TURQUOISE, "Бирюзовый"),
        themeGridEntry(AppTheme.PLUM, "Сливовый"),
        themeGridEntry(AppTheme.PEARL, "Жемчужный"),
        themeGridEntry(AppTheme.OLIVE, "Оливковый"),
        themeGridEntry(AppTheme.SAPPHIRE, "Сапфировый"),
        themeGridEntry(AppTheme.MINT, "Мятный"),
        themeGridEntry(AppTheme.BRONZE, "Бронзовый"),
        themeGridEntry(AppTheme.WINE, "Винный"),
    )
    val selectedEntry = themes.firstOrNull { it.theme == currentTheme }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 760.dp),
            shape = RoundedCornerShape(32.dp),
            color = colors.surface,
            tonalElevation = 10.dp,
            shadowElevation = 22.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                lerp(colors.primaryContainer, colors.surface, 0.56f),
                                colors.surface,
                                colors.surfaceContainerLow
                            )
                        )
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 10.dp, top = 18.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = colors.primaryContainer
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Palette,
                                    null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Темы и атмосфера",
                                color = colors.onSurface,
                                fontFamily = font,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp
                            )
                            Text(
                                "Готовые палитры и расширенный редактор своей темы в одном окне",
                                color = colors.onSurfaceVariant,
                                fontFamily = font,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, null, tint = colors.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.55f))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ThemePickerSummaryCard(
                            currentTheme = currentTheme,
                            previewEntry = selectedEntry
                        )

                        CustomThemeHeroCard(
                            isSelected = currentTheme == AppTheme.CUSTOM,
                            onClick = onCustomThemeClick
                        )

                        ThemePickerSectionLabel("Готовые палитры")

                        themes.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { entry ->
                                    ThemeGridCard(
                                        modifier = Modifier.weight(1f),
                                        entry = entry,
                                        selected = currentTheme == entry.theme,
                                        onClick = { onThemeSelected(entry.theme) }
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        FilledTonalButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Готово", fontFamily = font, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePickerSummaryCard(
    currentTheme: AppTheme,
    previewEntry: ThemeGridEntry?
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val previewStart by animateColorAsState(
        targetValue = when (currentTheme) {
            AppTheme.MATERIAL_YOU -> Color(0xFF8CC8FF)
            else -> previewEntry?.previewStart ?: colors.primary
        },
        animationSpec = tween(460),
        label = "themePreviewStart"
    )
    val previewEnd by animateColorAsState(
        targetValue = when (currentTheme) {
            AppTheme.MATERIAL_YOU -> Color(0xFFFFB5D8)
            else -> previewEntry?.previewEnd ?: colors.tertiary
        },
        animationSpec = tween(520),
        label = "themePreviewEnd"
    )
    val previewAccent by animateColorAsState(
        targetValue = lerp(previewStart, previewEnd, 0.38f),
        animationSpec = tween(520),
        label = "themePreviewAccent"
    )
    val previewSurface by animateColorAsState(
        targetValue = lerp(colors.surfaceContainerLow, previewStart, 0.14f),
        animationSpec = tween(420),
        label = "themePreviewSurface"
    )
    val previewSurfaceHigh by animateColorAsState(
        targetValue = lerp(colors.surfaceContainerHigh, previewEnd, 0.12f),
        animationSpec = tween(500),
        label = "themePreviewSurfaceHigh"
    )
    val badgeLabel = when (currentTheme) {
        AppTheme.MATERIAL_YOU -> "Система"
        AppTheme.CUSTOM -> "Своя"
        else -> "Готовая"
    }
    val subtitle = when (currentTheme) {
        AppTheme.MATERIAL_YOU -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "Палитра берётся из обоев и системных тонов Android, поэтому ощущается как родная часть системы"
        } else {
            "На Android 12+ эта тема станет динамической и будет меняться от обоев, а ниже мягко откатится к нейтральной палитре"
        }
        AppTheme.CUSTOM -> "Полный ручной контроль цветов, формы, глубины поверхностей и контейнеров"
        else -> "Готовая палитра с мягкой анимацией смены цветов, поверхностей и акцентов"
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.64f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            previewSurface.copy(alpha = 0.98f),
                            previewSurfaceHigh.copy(alpha = 0.98f),
                            colors.surfaceContainerLow
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = previewAccent.copy(alpha = 0.18f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (currentTheme == AppTheme.MATERIAL_YOU) Icons.Rounded.Wallpaper else Icons.Rounded.AutoAwesome,
                            null,
                            tint = previewAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Активная тема",
                        color = colors.onSurfaceVariant,
                        fontFamily = font,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                    AnimatedContent(
                        targetState = currentTheme.settingsLabel(),
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically { -it / 4 })
                        },
                        label = "themeTitle"
                    ) { label ->
                        Text(
                            label,
                            color = colors.onSurface,
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = previewAccent.copy(alpha = 0.16f)
                ) {
                    Text(
                        badgeLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = previewAccent,
                        fontFamily = font,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                subtitle,
                color = colors.onSurfaceVariant,
                fontFamily = font,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = previewSurfaceHigh.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, previewAccent.copy(alpha = 0.24f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Preview Player",
                                color = colors.onSurface,
                                fontFamily = font,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Показывает, как тема ведёт себя на поверхности, кнопке и прогрессе",
                                color = colors.onSurfaceVariant,
                                fontFamily = font,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(previewStart, previewAccent, previewEnd).forEach { swatch ->
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = previewStart.copy(alpha = 0.18f)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Album, null, tint = previewStart, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Название трека",
                                color = colors.onSurface,
                                fontFamily = font,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(previewEnd.copy(alpha = 0.14f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.46f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(previewStart, previewAccent, previewEnd)
                                            )
                                        )
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = previewAccent
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    null,
                                    tint = if (previewAccent.luminance() > 0.52f) Color.Black else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePickerSectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = LocalAppFontFamily.current,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun MaterialYouHeroCard(
    selected: Boolean,
    supported: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(220),
        label = "materialYouCard"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.secondary else colors.outlineVariant,
        animationSpec = tween(220),
        label = "materialYouBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF7DCBFF).copy(alpha = 0.14f),
                        Color(0xFFFFB5D8).copy(alpha = 0.12f),
                        Color(0xFFA8F0CC).copy(alpha = 0.12f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF8CC8FF).copy(alpha = 0.16f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Wallpaper, null, tint = Color(0xFF5B8DFF), modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Material You",
                            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) colors.secondary else colors.surfaceVariant
                        ) {
                            Text(
                                if (supported) "Adaptive" else "Android 12+",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = if (selected) colors.onSecondary else colors.onSurfaceVariant,
                                fontFamily = font,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        if (supported) {
                            "Цвета подтягиваются из обоев и системной палитры, поэтому приложение выглядит по-настоящему системно"
                        } else {
                            "На Android 12+ эта тема станет динамической; ниже мягко откатится к спокойной нейтральной палитре"
                        },
                        color = if (selected) colors.onSecondaryContainer.copy(alpha = 0.82f) else colors.onSurfaceVariant,
                        fontFamily = font,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomThemeHeroCard(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.tertiaryContainer else colors.surfaceContainerHigh
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) colors.tertiary else colors.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.tertiary
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Palette, null, tint = colors.onTertiary, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Сделать свою тему",
                    color = if (isSelected) colors.onTertiaryContainer else colors.onSurface,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    "Полный редактор цветов, формы и миксов",
                    color = if (isSelected) colors.onTertiaryContainer.copy(alpha = 0.76f) else colors.onSurfaceVariant,
                    fontFamily = font,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = if (isSelected) colors.onTertiaryContainer else colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ThemeGridCard(
    modifier: Modifier = Modifier,
    entry: ThemeGridEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val previewStart by animateColorAsState(
        targetValue = entry.previewStart,
        animationSpec = tween(220),
        label = "themeCardPreviewStart"
    )
    val previewEnd by animateColorAsState(
        targetValue = entry.previewEnd,
        animationSpec = tween(220),
        label = "themeCardPreviewEnd"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = tween(220),
        label = "themeGridScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(220),
        label = "themeGridContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.secondary else colors.outlineVariant,
        animationSpec = tween(220),
        label = "themeGridBorder"
    )
    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            borderColor
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                previewStart.copy(alpha = 0.92f),
                                lerp(previewStart, previewEnd, 0.45f).copy(alpha = 0.82f),
                                previewEnd.copy(alpha = 0.94f)
                            )
                        )
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(22.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f)
                ) {}
                if (selected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(18.dp)
                    )
                }
            }
            Text(
                entry.label,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                entry.swatches.take(4).forEach { swatch ->
                    Surface(
                        modifier = Modifier.size(14.dp),
                        shape = CircleShape,
                        color = swatch,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            colors.onSurface.copy(alpha = 0.08f)
                        )
                    ) {}
                }
            }
            Text(
                if (selected) "Активная тема" else "Основные цвета палитры",
                color = if (selected) colors.onSecondaryContainer.copy(alpha = 0.76f) else colors.onSurfaceVariant,
                fontFamily = font,
                fontSize = 11.sp
            )
        }
    }
}

// ── Полноэкранный проводник шрифтов ─────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPickerDialog(
    currentFontId: String,
    currentCustomFontUri: String,
    onFontSelected: (String) -> Unit,
    onCustomFontSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val rootDirectories = remember { defaultFontDirectories() }
    val initialDir = remember(currentCustomFontUri, rootDirectories) {
        currentCustomFontUri
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.parentFile
            ?.takeIf {
                it.exists() && it.isDirectory && it.absolutePath.startsWith("/storage/emulated/0")
            }
            ?: rootDirectories.firstOrNull()
            ?: File("/storage/emulated/0")
    }
    var currentDirPath by remember(initialDir.absolutePath) { mutableStateOf(initialDir.absolutePath) }
    val pathHistory = remember { mutableStateListOf<String>() }
    var filterQuery by remember { mutableStateOf("") }
    val currentDir = remember(currentDirPath) { File(currentDirPath) }
    val entries = remember(currentDirPath, filterQuery) {
        loadFontBrowserEntries(currentDir, filterQuery)
    }
    val selectedBuiltInFont = remember(currentFontId) {
        AppFontList.firstOrNull { it.id == currentFontId } ?: DefaultAppFont
    }
    val selectedFontName = remember(currentCustomFontUri) {
        currentCustomFontUri.takeIf { it.isNotBlank() }?.let(::File)?.nameWithoutExtension
    }
    var scanRequest by remember { mutableIntStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    val suggestedDirectories by produceState(initialValue = emptyList<FontDirectorySuggestion>(), scanRequest) {
        if (scanRequest == 0) return@produceState
        isScanning = true
        value = withContext(Dispatchers.IO) {
            discoverFontDirectories(rootDirectories)
        }
        isScanning = false
    }
    var autoOpenedFontDirectory by remember { mutableStateOf(false) }

    fun openDirectory(target: File) {
        if (!target.exists() || !target.isDirectory) return
        if (target.absolutePath != currentDirPath) {
            pathHistory.add(currentDirPath)
            currentDirPath = target.absolutePath
        }
    }

    fun goBackInBrowser(): Boolean {
        while (pathHistory.isNotEmpty()) {
            val previousPath = pathHistory.removeAt(pathHistory.lastIndex)
            val previousDir = File(previousPath)
            if (previousDir.exists() && previousDir.isDirectory) {
                currentDirPath = previousDir.absolutePath
                return true
            }
        }
        return currentDir.parentFile
            ?.takeIf { it.exists() && it.isDirectory }
            ?.also { currentDirPath = it.absolutePath } != null
    }

    LaunchedEffect(Unit) {
        scanRequest += 1
    }

    LaunchedEffect(suggestedDirectories) {
        if (!autoOpenedFontDirectory && countFontFiles(currentDir) == 0 && suggestedDirectories.isNotEmpty()) {
            autoOpenedFontDirectory = true
            openDirectory(suggestedDirectories.first().directory)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = c.background
        ) {
            Scaffold(
                containerColor = c.background,
                topBar = {
                    TopAppBar(
                        modifier = Modifier.statusBarsPadding(),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            FilledIconButton(
                                onClick = { goBackInBrowser() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = c.surfaceContainerLow,
                                    contentColor = c.onSurface
                                ),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад по папкам")
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    "Проводник шрифтов",
                                    color = c.textPrimary,
                                    fontFamily = LocalAppFontFamily.current,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                )
                                Text(
                                    "Полноэкранный выбор с превью и автопоиском папок",
                                    color = c.textSecondary,
                                    fontFamily = LocalAppFontFamily.current,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        actions = {
                            FilledIconButton(
                                onClick = { scanRequest += 1 },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = c.surfaceContainerLow,
                                    contentColor = c.onSurface
                                )
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = c.accent
                                    )
                                } else {
                                    Icon(Icons.Rounded.Search, contentDescription = "Найти папки со шрифтами")
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = onDismiss,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = c.primary,
                                    contentColor = c.onPrimary
                                ),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = c.bgCard,
                        shape = RoundedCornerShape(26.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    c.accent.copy(alpha = 0.95f),
                                                    c.accentVar.copy(alpha = 0.85f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.FontDownload, null, tint = c.bgDeep, modifier = Modifier.size(24.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        "Шрифт приложения",
                                        color = c.textPrimary,
                                        fontFamily = LocalAppFontFamily.current,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        if (currentCustomFontUri.isNotBlank()) {
                                            "Сейчас выбран: ${selectedFontName ?: "пользовательский"}"
                                        } else {
                                            "Сейчас выбран встроенный шрифт ${selectedBuiltInFont.displayName}"
                                        },
                                        color = c.textSecondary,
                                        fontFamily = LocalAppFontFamily.current,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val baseSelected = currentCustomFontUri.isBlank() && currentFontId == DefaultAppFontId
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { onFontSelected(DefaultAppFontId) },
                                    color = if (baseSelected) c.accent.copy(alpha = 0.17f) else c.bgSurface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            DefaultAppFont.displayName,
                                            color = if (baseSelected) c.accent else c.textPrimary,
                                            fontFamily = DefaultAppFont.family,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Быстрый возврат к встроенному шрифту",
                                            color = c.textSecondary,
                                            fontFamily = LocalAppFontFamily.current,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { scanRequest += 1 },
                                    color = c.bgSurface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            if (isScanning) Icons.Rounded.Sync else Icons.Rounded.AutoAwesome,
                                            null,
                                            tint = c.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            if (isScanning) "Ищу..." else "Автопоиск",
                                            color = c.textPrimary,
                                            fontFamily = LocalAppFontFamily.current,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Встроенные шрифты",
                                    color = c.textPrimary,
                                    fontFamily = LocalAppFontFamily.current,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                AppFontList.chunked(2).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        row.forEach { appFont ->
                                            val isSelected = currentCustomFontUri.isBlank() && currentFontId == appFont.id
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .clickable { onFontSelected(appFont.id) },
                                                color = if (isSelected) c.accent.copy(alpha = 0.15f) else c.bgSurface
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        appFont.displayName,
                                                        color = if (isSelected) c.accent else c.textPrimary,
                                                        fontFamily = appFont.family,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        if (appFont.id == DefaultAppFontId) "Шрифт по умолчанию" else "Google Font",
                                                        color = c.textSecondary,
                                                        fontFamily = LocalAppFontFamily.current,
                                                        fontSize = 11.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                        if (row.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = c.bgCard,
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                placeholder = {
                                    Text(
                                        "Фильтр по названию шрифта или папки",
                                        color = c.textDisabled,
                                        fontFamily = LocalAppFontFamily.current,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, null, tint = c.textSecondary)
                                },
                                trailingIcon = {
                                    if (filterQuery.isNotBlank()) {
                                        IconButton(onClick = { filterQuery = "" }) {
                                            Icon(Icons.Rounded.Close, null, tint = c.textSecondary)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = c.accent.copy(alpha = 0.55f),
                                    unfocusedBorderColor = c.divider.copy(alpha = 0.45f),
                                    focusedTextColor = c.textPrimary,
                                    unfocusedTextColor = c.textPrimary,
                                    cursorColor = c.accent,
                                    focusedContainerColor = c.bgSurface,
                                    unfocusedContainerColor = c.bgSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (suggestedDirectories.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Найдены папки со шрифтами",
                                            color = c.textPrimary,
                                            fontFamily = LocalAppFontFamily.current,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${suggestedDirectories.size} папок",
                                            color = c.accent,
                                            fontFamily = LocalAppFontFamily.current,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(suggestedDirectories, key = { it.directory.absolutePath }) { suggestion ->
                                            val selected = suggestion.directory.absolutePath == currentDirPath
                                            Surface(
                                                shape = RoundedCornerShape(18.dp),
                                                color = if (selected) c.accent.copy(alpha = 0.17f) else c.bgSurface,
                                                modifier = Modifier.clickable { openDirectory(suggestion.directory) }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        suggestion.directory.name.ifBlank { suggestion.directory.absolutePath },
                                                        color = if (selected) c.accent else c.textPrimary,
                                                        fontFamily = LocalAppFontFamily.current,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        "${suggestion.fontCount} шрифтов",
                                                        color = c.textSecondary,
                                                        fontFamily = LocalAppFontFamily.current,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Быстрые папки",
                                    color = c.textPrimary,
                                    fontFamily = LocalAppFontFamily.current,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(rootDirectories, key = { it.absolutePath }) { directory ->
                                        val selected = currentDirPath == directory.absolutePath
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (selected) c.accent.copy(alpha = 0.16f) else c.bgSurface,
                                            modifier = Modifier.clickable { openDirectory(directory) }
                                        ) {
                                            Text(
                                                directory.name.ifBlank { directory.absolutePath },
                                                color = if (selected) c.accent else c.textSecondary,
                                                fontFamily = LocalAppFontFamily.current,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = c.bgSurface,
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = c.accent.copy(alpha = 0.14f),
                                            modifier = Modifier.clickable { goBackInBrowser() }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Rounded.ArrowBack, null, tint = c.accent, modifier = Modifier.size(16.dp))
                                                Text("Назад", color = c.accent, fontFamily = LocalAppFontFamily.current, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = c.bgElevated,
                                            modifier = Modifier.clickable {
                                                currentDir.parentFile
                                                    ?.takeIf { it.exists() && it.isDirectory }
                                                    ?.let { openDirectory(it) }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Rounded.ArrowUpward, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                                                Text("Вверх", color = c.textSecondary, fontFamily = LocalAppFontFamily.current, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                        Spacer(Modifier.weight(1f))
                                        if (isScanning) {
                                            Text(
                                                "Ищу папки...",
                                                color = c.textSecondary,
                                                fontFamily = LocalAppFontFamily.current,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            "Текущая папка",
                                            color = c.textDisabled,
                                            fontFamily = LocalAppFontFamily.current,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            currentDir.absolutePath,
                                            color = c.textPrimary,
                                            fontFamily = LocalAppFontFamily.current,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        color = c.bgCard,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        if (entries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(62.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(c.bgSurface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.TextFields, null, tint = c.textDisabled, modifier = Modifier.size(28.dp))
                                    }
                                    Text(
                                        "В этой папке шрифтов не найдено",
                                        color = c.textPrimary,
                                        fontFamily = LocalAppFontFamily.current,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (suggestedDirectories.isNotEmpty()) "Попробуйте найденные папки выше или запустите автопоиск ещё раз." else "Откройте другую папку или нажмите автопоиск.",
                                        color = c.textSecondary,
                                        fontFamily = LocalAppFontFamily.current,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(entries, key = { it.file.absolutePath }) { entry ->
                                    if (entry.isDirectory) {
                                        val immediateFonts = remember(entry.file.absolutePath) { countFontFiles(entry.file) }
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .clickable { openDirectory(entry.file) },
                                            color = c.bgSurface
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(c.accent.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Rounded.Folder, null, tint = c.accent, modifier = Modifier.size(22.dp))
                                                }
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        entry.file.name.ifBlank { entry.file.absolutePath },
                                                        color = c.textPrimary,
                                                        fontFamily = LocalAppFontFamily.current,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        if (immediateFonts > 0) "$immediateFonts шрифтов внутри" else "Открыть папку",
                                                        color = c.textSecondary,
                                                        fontFamily = LocalAppFontFamily.current,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Icon(Icons.Rounded.ChevronRight, null, tint = c.textDisabled)
                                            }
                                        }
                                    } else {
                                        val previewFont = rememberFontPreviewFamily(entry.file)
                                        val isSelected = selectedFontName?.let { selected ->
                                            selected.contains(entry.file.nameWithoutExtension, ignoreCase = true) ||
                                                entry.file.nameWithoutExtension.contains(selected, ignoreCase = true)
                                        } == true
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .clickable {
                                                    val copiedPath = importFontFile(context, entry.file)
                                                    if (copiedPath != null) {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Шрифт применён: ${entry.file.nameWithoutExtension}",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                        onCustomFontSelected(copiedPath)
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Не удалось открыть шрифт",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                },
                                            color = if (isSelected) c.accent.copy(alpha = 0.12f) else c.bgSurface
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(if (isSelected) c.accent.copy(alpha = 0.16f) else c.bgElevated),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "Aa",
                                                        color = if (isSelected) c.accent else c.textSecondary,
                                                        fontFamily = previewFont,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        entry.file.nameWithoutExtension,
                                                        color = if (isSelected) c.accent else c.textPrimary,
                                                        fontFamily = previewFont,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        "Абвгд 123  Sample Preview",
                                                        color = c.textSecondary,
                                                        fontFamily = previewFont,
                                                        fontSize = 13.sp,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        "${entry.file.extension.uppercase()}  •  ${formatFileSize(entry.file.length())}",
                                                        color = c.textDisabled,
                                                        fontFamily = LocalAppFontFamily.current,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(Icons.Rounded.CheckCircle, null, tint = c.accent, modifier = Modifier.size(20.dp))
                                                } else {
                                                    Icon(Icons.Rounded.ChevronRight, null, tint = c.textDisabled, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun defaultFontDirectories(): List<File> = listOf(
    File("/storage/emulated/0/Download"),
    File("/storage/emulated/0/Download/Fonts"),
    File("/storage/emulated/0/Documents"),
    File("/storage/emulated/0/fonts"),
    File("/storage/emulated/0/Fonts"),
    File("/storage/emulated/0/Telegram"),
    File("/storage/emulated/0/Android/media"),
    File("/storage/emulated/0")
).filter { it.exists() && it.isDirectory }
    .distinctBy { it.absolutePath }

private fun loadFontBrowserEntries(directory: File, filterQuery: String): List<FontBrowserEntry> {
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    val query = filterQuery.trim().lowercase()
    return directory.listFiles()
        ?.filter { file ->
            (file.isDirectory || file.isFontFile()) &&
                (query.isBlank() || file.name.lowercase().contains(query))
        }
        ?.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
        ?.map { FontBrowserEntry(file = it, isDirectory = it.isDirectory) }
        .orEmpty()
}

private fun File.isFontFile(): Boolean =
    isFile && extension.lowercase() in setOf("ttf", "otf", "ttc")

private fun countFontFiles(directory: File): Int =
    runCatching { directory.listFiles()?.count { it.isFontFile() } ?: 0 }.getOrDefault(0)

private fun shouldSkipFontScanDir(directory: File): Boolean {
    val name = directory.name.lowercase()
    val path = directory.absolutePath.lowercase()
    return name.startsWith(".") ||
        name in setOf("android", "obb", "cache", "caches", "tmp", "temp", "thumbnails", "lost.dir") ||
        path.contains("/android/data") ||
        path.contains("/android/obb")
}

private fun discoverFontDirectories(
    roots: List<File>,
    maxDepth: Int = 5,
    maxResults: Int = 40
): List<FontDirectorySuggestion> {
    val queue = java.util.ArrayDeque<Pair<File, Int>>()
    val visited = hashSetOf<String>()
    val found = linkedMapOf<String, FontDirectorySuggestion>()

    roots.distinctBy { it.absolutePath }
        .filter { it.exists() && it.isDirectory }
        .forEach { queue.addLast(it to 0) }

    while (queue.isNotEmpty() && found.size < maxResults) {
        val (directory, depth) = queue.removeFirst()
        val path = directory.absolutePath
        if (!visited.add(path) || shouldSkipFontScanDir(directory)) continue

        val children = runCatching {
            directory.listFiles()
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        }.getOrDefault(emptyList())

        val fontCount = children.count { it.isFontFile() }
        if (fontCount > 0) {
            found[path] = FontDirectorySuggestion(directory, fontCount)
        }

        if (depth >= maxDepth) continue
        children.filter { it.isDirectory }
            .forEach { child ->
                if (!shouldSkipFontScanDir(child)) {
                    queue.addLast(child to (depth + 1))
                }
            }
    }

    return found.values
        .sortedWith(
            compareByDescending<FontDirectorySuggestion> { it.fontCount }
                .thenBy { it.directory.absolutePath.length }
                .thenBy { it.directory.name.lowercase() }
        )
        .take(maxResults)
}

private fun loadFontFamilyOrNull(filePath: String): androidx.compose.ui.text.font.FontFamily? {
    if (filePath.isBlank()) return null
    return try {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) null
        else androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.createFromFile(file))
    } catch (_: Exception) {
        null
    }
}

private fun cachePreviewFontFile(context: android.content.Context, sourceFile: File): String? {
    if (!sourceFile.exists() || !sourceFile.isFile) return null
    return try {
        val previewsDir = File(context.cacheDir, "font_previews")
        previewsDir.mkdirs()
        val destFile = File(previewsDir, buildSafeFontFileName(sourceFile))
        if (!destFile.exists() || destFile.length() != sourceFile.length()) {
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
        }
        destFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun rememberFontPreviewFamily(sourceFile: File): androidx.compose.ui.text.font.FontFamily {
    val context = LocalContext.current
    val previewFont by produceState(initialValue = DefaultAppFontFamily, key1 = sourceFile.absolutePath) {
        value = withContext(Dispatchers.IO) {
            loadFontFamilyOrNull(sourceFile.absolutePath)
                ?: cachePreviewFontFile(context, sourceFile)?.let(::loadFontFamilyOrNull)
                ?: DefaultAppFontFamily
        }
    }
    return previewFont
}

private fun buildSafeFontFileName(sourceFile: File): String {
    val ext = sourceFile.extension.ifBlank { "ttf" }.lowercase()
    return "${sourceFile.nameWithoutExtension}_${sourceFile.length()}.$ext"
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun formatFileSize(size: Long): String = when {
    size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024f * 1024f))
    size >= 1024 -> "${(size / 1024f).roundToInt()} KB"
    else -> "$size B"
}

private fun importFontUri(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val resolver = context.contentResolver
        val displayName = resolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment ?: "custom_font.ttf"
        val extension = displayName.substringAfterLast('.', "").ifBlank { "ttf" }
        val safeSource = File(displayName.substringBeforeLast('.', displayName))
        val safeName = "${safeSource.nameWithoutExtension}_${System.currentTimeMillis()}.$extension"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destFile = File(context.filesDir, "fonts/$safeName")
        destFile.parentFile?.mkdirs()
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        } ?: return null
        destFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun importFontFile(context: android.content.Context, sourceFile: File): String? {
    if (!sourceFile.exists() || !sourceFile.isFile) return null
    return try {
        val safeName = buildSafeFontFileName(sourceFile)
        val destFile = File(context.filesDir, "fonts/$safeName")
        destFile.parentFile?.mkdirs()
        sourceFile.inputStream().use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun AppTheme.settingsLabel(): String = when (this) {
    AppTheme.BLOOMEE -> "Малиновый"
    AppTheme.MATERIAL_YOU -> "Системные цвета"
    AppTheme.DARK_BROWN -> "Шоколадный"
    AppTheme.DARK_BLACK -> "Антрацитовый"
    AppTheme.LIGHT -> "Молочный"
    AppTheme.PURPLE -> "Аметистовый"
    AppTheme.PINK -> "Пудровый"
    AppTheme.OCEAN -> "Лазурный"
    AppTheme.FOREST -> "Хвойно-зелёный"
    AppTheme.SUNSET -> "Коралловый"
    AppTheme.MIDNIGHT -> "Индиго"
    AppTheme.NEON -> "Мятно-неоновый"
    AppTheme.ROSE_GOLD -> "Розовое золото"
    AppTheme.ARCTIC -> "Ледяной голубой"
    AppTheme.AMBER -> "Янтарный"
    AppTheme.EMERALD -> "Изумрудный"
    AppTheme.AMOLED -> "Угольный"
    AppTheme.LAVENDER -> "Лавандовый"
    AppTheme.RUBY -> "Рубиновый"
    AppTheme.STEEL -> "Стальной"
    AppTheme.MATCHA -> "Фисташковый"
    AppTheme.DESERT -> "Песочный"
    AppTheme.COBALT -> "Кобальтовый"
    AppTheme.CHERRY -> "Вишнёвый"
    AppTheme.MOCHA -> "Кофейный"
    AppTheme.AURORA -> "Мятно-бирюзовый"
    AppTheme.COSMOS -> "Орхидейный"
    AppTheme.SUNRISE -> "Абрикосовый"
    AppTheme.GRAPHITE -> "Графитовый"
    AppTheme.SAKURA -> "Нежно-розовый"
    AppTheme.LAGOON -> "Аквамариновый"
    AppTheme.VOLCANO -> "Терракотовый"
    AppTheme.IVORY -> "Слоновая кость"
    AppTheme.CITRUS -> "Лимонный"
    AppTheme.FROST -> "Морозный циан"
    AppTheme.EMBER -> "Медный"
    AppTheme.TURQUOISE -> "Бирюзовый"
    AppTheme.PLUM -> "Сливовый"
    AppTheme.PEARL -> "Жемчужный"
    AppTheme.OLIVE -> "Оливковый"
    AppTheme.SAPPHIRE -> "Сапфировый"
    AppTheme.MINT -> "Мятный"
    AppTheme.BRONZE -> "Бронзовый"
    AppTheme.WINE -> "Винный"
    AppTheme.CUSTOM -> "Своя тема"
}

@Composable
private fun SettingsHeroBanner(
    themeLabel: String,
    fontLabel: String,
    interfaceLabel: String
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Настройки", color = colors.onSurface, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text(
                        "Тема, типографика, анимации и звук в едином Material 3 интерфейсе",
                        color = colors.onSurfaceVariant,
                        fontFamily = font,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsInfoPill(
                    icon = Icons.Rounded.Palette,
                    label = "Тема",
                    value = themeLabel,
                    modifier = Modifier.weight(1f)
                )
                SettingsInfoPill(
                    icon = Icons.Rounded.DashboardCustomize,
                    label = "Стиль UI",
                    value = interfaceLabel,
                    modifier = Modifier.weight(1f)
                )
            }
            SettingsInfoPill(
                icon = Icons.Rounded.TextFields,
                label = "Шрифт",
                value = fontLabel,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Интерфейс приложения собран вокруг Material 3, а тема и шрифт настраиваются отдельно",
                        color = colors.onSurfaceVariant,
                        fontFamily = font,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsInfoPill(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
                Text(label, color = colors.onSurfaceVariant, fontFamily = font, fontSize = 11.sp)
            }
            Text(value, color = colors.onSurface, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SettingsQuickAccessGrid(
    fontLabel: String,
    onThemeClick: () -> Unit,
    onTypographyClick: () -> Unit,
    onAnimationsClick: () -> Unit,
    onOrbsClick: () -> Unit,
    onTransitionsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onTopBarClick: () -> Unit,
    onFontClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("0.9")
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Разделы",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Palette,
                accent = IconMauve,
                title = "Внешний вид",
                subtitle = "Тема и стиль",
                onClick = onThemeClick
            )
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AutoAwesome,
                accent = Color(0xFF7C4DFF),
                title = "Анимации",
                subtitle = "Новые примеры",
                onClick = onAnimationsClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.SwapHoriz,
                accent = IconPurple,
                title = "Переходы",
                subtitle = "Кроссфейд и длительность",
                onClick = onTransitionsClick
            )
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.GraphicEq,
                accent = IconGold,
                title = "Эквалайзер",
                subtitle = "Звук и пресеты",
                onClick = onEqualizerClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.BlurOn,
                accent = Color(0xFF7B5EA7),
                title = "Орбы",
                subtitle = "Фон и реакция",
                onClick = onOrbsClick
            )
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.BarChart,
                accent = IconTeal,
                title = "Статистика",
                subtitle = "История и цифры",
                onClick = onStatsClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Tune,
                accent = Color(0xFF2E6B9E),
                title = "Шапка",
                subtitle = "Стиль и фон",
                onClick = onTopBarClick
            )
            Spacer(Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FormatSize,
                accent = Color(0xFF4E8F77),
                title = "Типографика",
                subtitle = "Шаблоны и превью",
                onClick = onTypographyClick
            )
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.TextFields,
                accent = IconMauve,
                title = "Шрифт",
                subtitle = fontLabel,
                onClick = onFontClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsQuickLinkCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Info,
                accent = Color(0xFF229ED9),
                title = "О приложении",
                subtitle = "Glowpath · v$appVersion",
                onClick = onAboutClick
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingsQuickLinkCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = colors.onSurface, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = colors.onSurfaceVariant, fontFamily = font, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsLeadingIcon(
    icon: ImageVector,
    tint: Color
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.14f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsLeadingIcon(
    iconPainter: Painter,
    tint: Color
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.14f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconPainter, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = colors.primary,
                    unselectedColor = colors.onSurfaceVariant
                )
            )
            Text(
                text = label,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
                fontFamily = font,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(title, color = colors.onSurface, fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(subtitle, color = colors.onSurfaceVariant, fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp)
        },
        leadingContent = { SettingsLeadingIcon(icon = icon, tint = iconBg) },
        trailingContent = {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    )
}

@Composable
fun SettingsNavigationItem(
    iconPainter: Painter,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(title, color = colors.onSurface, fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(subtitle, color = colors.onSurfaceVariant, fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp)
        },
        leadingContent = { SettingsLeadingIcon(iconPainter = iconPainter, tint = iconBg) },
        trailingContent = {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    )
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val headlineColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.5f)
    val supportingColor = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.55f)
    val iconTint = if (enabled) iconBg else iconBg.copy(alpha = 0.48f)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(title, color = headlineColor, fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(subtitle, color = supportingColor, fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp)
        },
        leadingContent = { SettingsLeadingIcon(icon = icon, tint = iconTint) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                    uncheckedThumbColor = colors.surface,
                    uncheckedTrackColor = colors.outlineVariant,
                    uncheckedBorderColor = colors.outline
                )
            )
        }
    )
}

@Composable
fun SettingsRepeatItem(settings: PlayerSettings, onRepeatChange: (RepeatMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val options = listOf(
        RepeatMode.NONE to "Без повтора",
        RepeatMode.ALL  to "Повторять всё",
        RepeatMode.ONE  to "Повторять один трек"
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Режим повтора", color = colors.onSurface, fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        options.forEach { (mode, label) ->
            SettingsChoiceRow(label = label, selected = settings.repeatMode == mode) {
                onRepeatChange(mode)
            }
        }
    }
}

@Composable
fun SettingsSortItem(settings: PlayerSettings, onSortChange: (SortOrder) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        SortOrder.TITLE      to "По названию",
        SortOrder.ARTIST     to "По исполнителю",
        SortOrder.ALBUM      to "По альбому",
        SortOrder.DURATION   to "По длительности",
        SortOrder.DATE_ADDED to "По дате добавления"
    )
    val currentLabel = options.find { it.first == settings.sortOrder }?.second ?: "По названию"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable { expanded = !expanded },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text("Сортировка", color = colors.onSurface, fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                Text(currentLabel, color = colors.onSurfaceVariant, fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp)
            },
            leadingContent = { SettingsLeadingIcon(icon = Icons.Rounded.Sort, tint = IconGreen) },
            trailingContent = {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(280, easing = EaseOutCubic)) + fadeIn(tween(220)),
            exit  = shrinkVertically(animationSpec = tween(220, easing = EaseInCubic))  + fadeOut(tween(180))
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (order, label) ->
                    SettingsChoiceRow(label = label, selected = settings.sortOrder == order) {
                        onSortChange(order)
                        expanded = false
                    }
                }
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Orb Settings Section — полные настройки орбов
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbSettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val orb by viewModel.orbSettings.collectAsState()
    val audioReactiveLevel by viewModel.audioReactiveLevel.collectAsState()
    val previewReactiveLevel = if (audioReactiveLevel > 0.02f) audioReactiveLevel else -1f
    val previewColor1 = c.accent.copy(alpha = (0.3f + orb.contrast * 0.45f).coerceIn(0f, 1f))
    val previewColor2 = c.accentVar.copy(alpha = (0.25f + orb.contrast * 0.4f).coerceIn(0f, 1f))
    val previewColor3 = c.accentMuted.copy(alpha = (0.2f + orb.contrast * 0.35f).coerceIn(0f, 1f))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, "Назад")
                    }
                },
                title = {
                    Column {
                        Text("Орбы", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        Text("Фиксированный превью сверху и все настройки ниже", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                AnimatedOrbBackground(
                    color1 = previewColor1,
                    color2 = previewColor2,
                    color3 = previewColor3,
                    baseColor = c.bgDeep,
                    orbSettings = orb,
                    modifier = Modifier.fillMaxSize(),
                    audioReactiveLevel = previewReactiveLevel
                )
                Box(Modifier.fillMaxSize().background(c.bgCard.copy(alpha = 0.42f)))
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Живой пример", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Настраивайте снизу и сразу смотрите результат сверху", color = c.textSecondary, fontFamily = font, fontSize = 10.sp)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp)
            ) {
                OrbSettingsSection(
                    viewModel = viewModel,
                    showHeader = false,
                    showPreview = false
                )
            }
        }
    }
}

@Composable
fun OrbSettingsSection(
    viewModel: MusicViewModel,
    showHeader: Boolean = true,
    showPreview: Boolean = true
) {
    val c    = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val orb  by viewModel.orbSettings.collectAsState()
    val audioReactiveLevel by viewModel.audioReactiveLevel.collectAsState()
    val previewReactiveLevel = if (audioReactiveLevel > 0.02f) audioReactiveLevel else -1f
    val presets = listOf(
        OrbScenePreset("Спокойно", "мягкий фон", Icons.Rounded.Spa, c.accent.copy(alpha = 0.9f)) { current ->
            current.copy(
                speed = 0.75f,
                contrast = 0.58f,
                coverage = 0.84f,
                glowIntensity = 0.48f,
                bassReactive = false,
                pulseOnBeat = false,
                beatScale = 0.12f,
                flowMode = 0,
                waveMode = false,
                trailEffect = false,
                particleEmission = false,
                showVisualizerBars = false,
                borderGlow = false,
                frostedGlass = false,
                colorCycleSpeed = 0f
            )
        },
        OrbScenePreset("Bass", "сильная реакция", Icons.Rounded.GraphicEq, c.accentVar.copy(alpha = 0.92f)) { current ->
            current.copy(
                speed = 1.35f,
                contrast = 0.9f,
                coverage = 1.02f,
                glowIntensity = 0.84f,
                bassReactive = true,
                pulseOnBeat = true,
                beatScale = 0.34f,
                flowMode = 1,
                waveMode = false,
                trailEffect = true,
                trailLength = 0.42f,
                particleEmission = true,
                particleCount = 24,
                showVisualizerBars = true
            )
        },
        OrbScenePreset("Glass", "мягкое стекло", Icons.Rounded.BlurOn, c.accentMuted.copy(alpha = 0.92f)) { current ->
            current.copy(
                speed = 0.9f,
                contrast = 0.68f,
                coverage = 0.92f,
                glowIntensity = 0.72f,
                brightness = 0.86f,
                saturation = 0.62f,
                bassReactive = false,
                pulseOnBeat = true,
                beatScale = 0.16f,
                trailEffect = false,
                particleEmission = true,
                particleCount = 16,
                borderGlow = true,
                borderThickness = 0.38f,
                frostedGlass = true,
                showVisualizerBars = false
            )
        },
        OrbScenePreset("Aura", "цвет и воздух", Icons.Rounded.AutoAwesome, c.accent.copy(alpha = 0.82f)) { current ->
            current.copy(
                speed = 1.08f,
                contrast = 0.82f,
                coverage = 1.08f,
                glowIntensity = 0.76f,
                brightness = 0.9f,
                saturation = 1f,
                bassReactive = true,
                pulseOnBeat = true,
                beatScale = 0.22f,
                flowMode = 3,
                waveMode = true,
                trailEffect = true,
                trailLength = 0.35f,
                particleEmission = true,
                particleCount = 18,
                colorCycleSpeed = 1.2f,
                showVisualizerBars = false
            )
        },
        OrbScenePreset("Chaos", "мощнее и шире", Icons.Rounded.Bolt, c.accentVar.copy(alpha = 0.85f)) { current ->
            current.copy(
                speed = 2f,
                contrast = 0.94f,
                coverage = 1.16f,
                orbCount = 5,
                glowIntensity = 0.9f,
                bassReactive = true,
                pulseOnBeat = true,
                beatScale = 0.4f,
                flowMode = 2,
                trailEffect = true,
                trailLength = 0.58f,
                particleEmission = true,
                particleCount = 28,
                showVisualizerBars = true,
                kaleidoscopeMode = false
            )
        }
    )

    val previewColor1 = c.accent.copy(alpha = (0.3f + orb.contrast * 0.45f).coerceIn(0f, 1f))
    val previewColor2 = c.accentVar.copy(alpha = (0.25f + orb.contrast * 0.4f).coerceIn(0f, 1f))
    val previewColor3 = c.accentMuted.copy(alpha = (0.2f + orb.contrast * 0.35f).coerceIn(0f, 1f))

    // Табы для категорий настроек
    var activeTab by remember { mutableStateOf(0) }
    val tabs = listOf("Базовые", "Форма", "Цвет", "Движение", "Эффекты")

    Column(modifier = Modifier.fillMaxWidth()) {

        if (showHeader) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Настройки орбов", color = c.textPrimary, fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Анимация фонового эффекта", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
                }
            }
        }

        if (showPreview) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .height(160.dp).clip(RoundedCornerShape(20.dp))
            ) {
                AnimatedOrbBackground(
                    color1 = previewColor1, color2 = previewColor2, color3 = previewColor3,
                    baseColor = c.bgDeep, orbSettings = orb,
                    modifier = Modifier.fillMaxSize(),
                    audioReactiveLevel = previewReactiveLevel
                )
                Box(Modifier.fillMaxSize().background(c.bgCard.copy(alpha = 0.45f)))
                Column(
                    Modifier.align(Alignment.BottomStart).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Превью орбов", color = c.textPrimary, fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Орбов: ${orb.orbCount}  ·  Скорость: ${"%.1f".format(orb.speed)}×  ·  ${listOf("Дрейф","Орбита","Хаос","Синус")[orb.flowMode.coerceIn(0,3)]}", color = c.textSecondary, fontFamily = font, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(4.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Сцены орбов", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { preset ->
                    Surface(
                        modifier = Modifier
                            .width(138.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { viewModel.updateOrbSettings(preset.apply(orb)) },
                        color = c.bgCard
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(preset.color.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(preset.icon, null, tint = preset.color, modifier = Modifier.size(18.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(preset.label, color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(preset.subtitle, color = c.textSecondary, fontFamily = font, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Быстрые переключатели ─────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbQuickToggle("В плеере", orb.showInPlayer, Icons.Rounded.MusicNote, c.accent, Modifier.weight(1f)) { viewModel.updateOrbSettings(orb.copy(showInPlayer = it)) }
            OrbQuickToggle("В тексте", orb.showInLyrics, Icons.Rounded.Lyrics, c.accentVar, Modifier.weight(1f)) { viewModel.updateOrbSettings(orb.copy(showInLyrics = it)) }
            OrbQuickToggle("Под бас", orb.bassReactive, Icons.Rounded.GraphicEq, c.accentMuted, Modifier.weight(1f)) { viewModel.updateOrbSettings(orb.copy(bassReactive = it)) }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbQuickToggle("Следы", orb.trailEffect, Icons.Rounded.TrendingUp, c.accent, Modifier.weight(1f)) { viewModel.updateOrbSettings(orb.copy(trailEffect = it)) }
            OrbQuickToggle("Частицы", orb.particleEmission, Icons.Rounded.Stars, c.accentVar, Modifier.weight(1f)) { viewModel.updateOrbSettings(orb.copy(particleEmission = it)) }
            OrbQuickToggle("EQ", orb.showVisualizerBars, Icons.Rounded.GraphicEq, c.accentMuted, Modifier.weight(1f)) { viewModel.updateOrbSettings(orb.copy(showVisualizerBars = it)) }
        }

        Spacer(Modifier.height(12.dp))

        // ── Табы категорий ────────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(tabs) { tab ->
                val i = tabs.indexOf(tab)
                val sel = activeTab == i
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (sel) c.accent else c.bgCard)
                        .clickable { activeTab = i }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(tabs[i], color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Контент выбранного таба ───────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp)).background(c.bgElevated)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeTab) {

                // ──── Таб 0: Базовые ──────────────────────────────────────────
                0 -> {
                    OrbSliderRow("Скорость движения", "${"%.1f".format(orb.speed)}×", orb.speed, 0.2f..4.0f, Icons.Rounded.Speed) { viewModel.updateOrbSettings(orb.copy(speed = it)) }
                    OrbSliderRow("Контраст цвета", "${(orb.contrast*100).roundToInt()}%", orb.contrast, 0f..1f, Icons.Rounded.Palette) { viewModel.updateOrbSettings(orb.copy(contrast = it)) }
                    OrbSliderRow("Размер орбов", "${(orb.coverage*100).roundToInt()}%", orb.coverage, 0.4f..1.6f, Icons.Rounded.BlurOn) { viewModel.updateOrbSettings(orb.copy(coverage = it)) }
                    // Количество орбов
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = c.accent.copy(0.9f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Количество орбов", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${orb.orbCount}", color = c.accent, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(orb.orbCount.toFloat(), { viewModel.updateOrbSettings(orb.copy(orbCount = it.roundToInt())) }, valueRange = 1f..8f, steps = 6, modifier = Modifier.fillMaxWidth().height(28.dp), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent.copy(0.8f), inactiveTrackColor = c.textDisabled.copy(0.3f)))
                    }
                    OrbSliderRow("Разброс орбов", "${(orb.orbSpread*100).roundToInt()}%", orb.orbSpread, 0.1f..1f, Icons.Rounded.Fullscreen) { viewModel.updateOrbSettings(orb.copy(orbSpread = it)) }
                    OrbSliderRow("Смещение по вертикали", "${(orb.verticalBias*100).roundToInt()}%", orb.verticalBias, -1f..1f, Icons.Rounded.SwapVert) { viewModel.updateOrbSettings(orb.copy(verticalBias = it)) }
                }

                // ──── Таб 1: Форма ────────────────────────────────────────────
                1 -> {
                    // Форма орба
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Форма орба", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        val shapes = listOf("Круг", "Капля", "Звезда", "Кристалл")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            shapes.forEachIndexed { i, name ->
                                val sel = orb.orbShape == i
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) c.accent else c.bgCard)
                                        .clickable { viewModel.updateOrbSettings(orb.copy(orbShape = i)) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(name, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                            }
                        }
                    }
                    OrbSliderRow("Свечение", "${(orb.glowIntensity*100).roundToInt()}%", orb.glowIntensity, 0f..1f, Icons.Rounded.WbSunny) { viewModel.updateOrbSettings(orb.copy(glowIntensity = it)) }
                    OrbSliderRow("Размытие", "${(orb.blurRadius*100).roundToInt()}%", orb.blurRadius, 0f..1f, Icons.Rounded.BlurOn) { viewModel.updateOrbSettings(orb.copy(blurRadius = it)) }
                    OrbSliderRow("Яркость", "${(orb.brightness*100).roundToInt()}%", orb.brightness, 0.1f..1f, Icons.Rounded.WbSunny) { viewModel.updateOrbSettings(orb.copy(brightness = it)) }
                    OrbSliderRow("Насыщенность", "${(orb.saturation*100).roundToInt()}%", orb.saturation, 0f..1f, Icons.Rounded.Palette) { viewModel.updateOrbSettings(orb.copy(saturation = it)) }
                    OrbToggleRow("Светящийся контур", "Контур светится цветом орба", orb.borderGlow, Icons.Rounded.RadioButtonChecked, c.accentVar) { viewModel.updateOrbSettings(orb.copy(borderGlow = it)) }
                    if (orb.borderGlow) OrbSliderRow("Толщина контура", "${(orb.borderThickness*100).roundToInt()}%", orb.borderThickness, 0.05f..1f, Icons.Rounded.FormatSize) { viewModel.updateOrbSettings(orb.copy(borderThickness = it)) }
                    OrbToggleRow("Матовое стекло", "Эффект frosted glass поверх", orb.frostedGlass, Icons.Rounded.BlurOn, c.accentMuted) { viewModel.updateOrbSettings(orb.copy(frostedGlass = it)) }
                    OrbSliderRow("Зернистость", "${(orb.noiseAmount*100).roundToInt()}%", orb.noiseAmount, 0f..1f, Icons.Rounded.Apps) { viewModel.updateOrbSettings(orb.copy(noiseAmount = it)) }
                }

                // ──── Таб 2: Цвет ─────────────────────────────────────────────
                2 -> {
                    OrbSliderRow("Смещение оттенка", "${(orb.colorShift*360).roundToInt()}°", orb.colorShift, 0f..1f, Icons.Rounded.Palette) { viewModel.updateOrbSettings(orb.copy(colorShift = it)) }
                    OrbSliderRow("Тяга к цвету обложки", "${(orb.colorPullStrength*100).roundToInt()}%", orb.colorPullStrength, 0f..1f, Icons.Rounded.Image) { viewModel.updateOrbSettings(orb.copy(colorPullStrength = it)) }
                    OrbSliderRow("Скорость смены цвета", if (orb.colorCycleSpeed < 0.01f) "Выкл" else "${"%.1f".format(orb.colorCycleSpeed)}×", orb.colorCycleSpeed, 0f..3f, Icons.Rounded.Autorenew) { viewModel.updateOrbSettings(orb.copy(colorCycleSpeed = it)) }
                    OrbToggleRow("RGB Хрома-эффект", "Орбы расслаиваются на RGB каналы", orb.chromaEffect, Icons.Rounded.ColorLens, c.accent) { viewModel.updateOrbSettings(orb.copy(chromaEffect = it)) }
                    OrbSliderRow("Контраст цвета", "${(orb.contrast*100).roundToInt()}%", orb.contrast, 0f..1f, Icons.Rounded.Palette) { viewModel.updateOrbSettings(orb.copy(contrast = it)) }
                }

                // ──── Таб 3: Движение ──────────────────────────────────────────
                3 -> {
                    // Режим потока
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Режим движения", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        val modes = listOf("Дрейф", "Орбита", "Хаос", "Синусоида")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            modes.forEachIndexed { i, name ->
                                val sel = orb.flowMode == i
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) c.accentVar else c.bgCard)
                                        .clickable { viewModel.updateOrbSettings(orb.copy(flowMode = i)) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(name, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                            }
                        }
                    }
                    OrbToggleRow("Вращение", "Орбы вращаются вокруг центра", orb.rotationEnabled, Icons.Rounded.Refresh, c.accent) { viewModel.updateOrbSettings(orb.copy(rotationEnabled = it)) }
                    OrbToggleRow("Волновое движение", "Орбы движутся волнами", orb.waveMode, Icons.Rounded.Loop, c.accentVar) { viewModel.updateOrbSettings(orb.copy(waveMode = it)) }
                    OrbToggleRow("Притяжение к обложке", "Орбы хороводят вокруг обложки", orb.magneticToArt, Icons.Rounded.RadioButtonChecked, c.accentMuted) { viewModel.updateOrbSettings(orb.copy(magneticToArt = it)) }
                    if (orb.magneticToArt) OrbSliderRow("Сила притяжения", "${(orb.magneticStrength*100).roundToInt()}%", orb.magneticStrength, 0.05f..1f, Icons.Rounded.BlurOn) { viewModel.updateOrbSettings(orb.copy(magneticStrength = it)) }
                    OrbToggleRow("Пульс на бит", "Орбы масштабируются в такт", orb.pulseOnBeat, Icons.Rounded.GraphicEq, c.accent) { viewModel.updateOrbSettings(orb.copy(pulseOnBeat = it)) }
                    if (orb.pulseOnBeat || orb.bassReactive) OrbSliderRow("Сила реакции", "${(orb.beatScale*200).roundToInt()}%", orb.beatScale, 0.02f..0.5f, Icons.Rounded.Speed) { viewModel.updateOrbSettings(orb.copy(beatScale = it)) }
                }

                // ──── Таб 4: Эффекты ──────────────────────────────────────────
                4 -> {
                    OrbToggleRow("Следы орбов", "Орбы оставляют цветовые следы", orb.trailEffect, Icons.Rounded.TrendingUp, c.accent) { viewModel.updateOrbSettings(orb.copy(trailEffect = it)) }
                    if (orb.trailEffect) OrbSliderRow("Длина следа", "${(orb.trailLength*100).roundToInt()}%", orb.trailLength, 0.1f..1f, Icons.Rounded.TrendingUp) { viewModel.updateOrbSettings(orb.copy(trailLength = it)) }
                    OrbToggleRow("Эмиссия частиц", "Мерцающие частицы из орбов", orb.particleEmission, Icons.Rounded.Stars, c.accentVar) { viewModel.updateOrbSettings(orb.copy(particleEmission = it)) }
                    if (orb.particleEmission) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = c.accent.copy(0.9f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Кол-во частиц", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${orb.particleCount}", color = c.accent, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(orb.particleCount.toFloat(), { viewModel.updateOrbSettings(orb.copy(particleCount = it.roundToInt())) }, valueRange = 5f..60f, steps = 10, modifier = Modifier.fillMaxWidth().height(28.dp), colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent.copy(0.8f), inactiveTrackColor = c.textDisabled.copy(0.3f)))
                        }
                    }
                    OrbToggleRow("Калейдоскоп", "Зеркальная симметрия орбов", orb.kaleidoscopeMode, Icons.Rounded.FilterCenterFocus, c.accentMuted) { viewModel.updateOrbSettings(orb.copy(kaleidoscopeMode = it)) }
                    OrbToggleRow("Эффект глубины", "Ближний и дальний план орбов", orb.depthEffect, Icons.Rounded.FileCopy, c.accent) { viewModel.updateOrbSettings(orb.copy(depthEffect = it)) }
                    OrbToggleRow("Мини-эквалайзер", "Полосы EQ поверх орбов", orb.showVisualizerBars, Icons.Rounded.GraphicEq, c.accentVar) { viewModel.updateOrbSettings(orb.copy(showVisualizerBars = it)) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Кнопка сброса
        TextButton(
            onClick = { viewModel.updateOrbSettings(com.musicplayer.data.OrbSettings()) },
            modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
        ) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Сбросить всё", fontFamily = font, fontSize = 13.sp)
        }
    }
}

// ── Быстрый переключатель (компактный) ───────────────────────────────────────

@Composable
private fun OrbQuickToggle(
    label: String,
    enabled: Boolean,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle(!enabled) },
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) color.copy(alpha = 0.16f) else colors.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = if (enabled) color else colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(
                label,
                color = if (enabled) color else colors.onSurfaceVariant,
                fontFamily = font,
                fontSize = 10.sp,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// ── Строка с переключателем ───────────────────────────────────────────────────

@Composable
private fun OrbToggleRow(
    title: String, subtitle: String, checked: Boolean,
    icon: ImageVector,
    color: Color,
    onToggle: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerHigh
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!checked) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.onSurface, fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = colors.onSurfaceVariant, fontFamily = font, fontSize = 11.sp)
            }
            Switch(
                checked,
                onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = color,
                    uncheckedThumbColor = colors.surface,
                    uncheckedTrackColor = colors.outlineVariant,
                    uncheckedBorderColor = colors.outline
                )
            )
        }
    }
}

// ── Слайдер-строка ────────────────────────────────────────────────────────────

@Composable
private fun OrbSliderRow(
    label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>,
    icon: ImageVector, onValueChange: (Float) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = colors.onSurface, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(valueLabel, color = colors.primary, fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value, onValueChange = onValueChange, valueRange = range,
                modifier = Modifier.fillMaxWidth().height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceVariant
                )
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypographyStudioDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val settings by viewModel.settings.collectAsState()
    val presets = remember {
        listOf(
            TypographyStudioPreset("Air", "Чисто и просторно", Icons.Rounded.AutoAwesome, Color(0xFF64C3A3)) {
                it.copy(
                    trackListTitleSize = 15f,
                    trackListArtistSize = 12f,
                    letterSpacingEm = 0.01f,
                    trackItemDensity = 2,
                    trackItemPaddingScale = 1.12f,
                    trackTitleWeightMode = 1,
                    trackTitleAccentBlend = 0.08f,
                    trackTitleDecorStyle = 1,
                    trackMetaSeparatorStyle = 0,
                    trackMetaCapsule = false,
                    showAlbumInList = false,
                    durationBadgeStyle = 0,
                    textShadowEnabled = false
                )
            },
            TypographyStudioPreset("Cinema", "Акцент и глубина", Icons.Rounded.Movie, Color(0xFFFF8A65)) {
                it.copy(
                    trackListTitleSize = 17f,
                    trackListArtistSize = 12f,
                    letterSpacingEm = 0.015f,
                    lineHeightScale = 1.14f,
                    trackItemDensity = 1,
                    trackTitleWeightMode = 3,
                    trackTitleAccentBlend = 0.34f,
                    trackTitleDecorStyle = 3,
                    trackMetaSeparatorStyle = 3,
                    trackMetaCapsule = true,
                    showAlbumInList = true,
                    durationBadgeStyle = 2,
                    nowPlayingGlowStrength = 1.08f,
                    textShadowEnabled = true,
                    textShadowIntensity = 0.56f
                )
            },
            TypographyStudioPreset("Broadcast", "Жёсткий эфир", Icons.Rounded.Campaign, Color(0xFF5AB4FF)) {
                it.copy(
                    trackListTitleSize = 15f,
                    trackListArtistSize = 11f,
                    uppercaseTitles = true,
                    trackMetaUppercase = true,
                    letterSpacingEm = 0.08f,
                    artistLetterSpacingEm = 0.05f,
                    trackTitleWeightMode = 4,
                    trackMetaWeightMode = 2,
                    trackTitleAccentBlend = 0.42f,
                    trackTitleDecorStyle = 2,
                    trackMetaSeparatorStyle = 1,
                    durationBadgeStyle = 1,
                    showTrackNumber = true
                )
            },
            TypographyStudioPreset("Capsule", "Стекло и метки", Icons.Rounded.Album, Color(0xFFCE93D8)) {
                it.copy(
                    trackListTitleSize = 16f,
                    trackListArtistSize = 12f,
                    trackItemCornerRadius = 18f,
                    trackItemPaddingScale = 1.05f,
                    trackTitleAccentBlend = 0.20f,
                    trackTitleDecorStyle = 3,
                    trackMetaSeparatorStyle = 0,
                    trackMetaCapsule = true,
                    durationBadgeStyle = 1,
                    showAlbumInList = true
                )
            },
            TypographyStudioPreset("Pulse", "Ярче для текущего трека", Icons.Rounded.Bolt, Color(0xFFFFD54F)) {
                it.copy(
                    trackListTitleSize = 16f,
                    trackListArtistSize = 12f,
                    boldTitles = true,
                    trackTitleAccentBlend = 0.28f,
                    trackTitleDecorStyle = 1,
                    trackMetaSeparatorStyle = 2,
                    nowPlayingGlowStrength = 1.24f,
                    glowOnNowPlaying = true,
                    durationBadgeStyle = 2,
                    showAlbumInList = true
                )
            },
            TypographyStudioPreset("Archive", "Каталоговый вид", Icons.Rounded.LibraryMusic, Color(0xFF90A4AE)) {
                it.copy(
                    trackListTitleSize = 14f,
                    trackListArtistSize = 11f,
                    trackItemDensity = 0,
                    trackItemPaddingScale = 0.92f,
                    trackTitleWeightMode = 2,
                    trackMetaWeightMode = 1,
                    trackTitleAccentBlend = 0.12f,
                    trackTitleDecorStyle = 0,
                    trackMetaSeparatorStyle = 1,
                    showTrackNumber = true,
                    showAlbumInList = true,
                    durationBadgeStyle = 0,
                    trackMetaCapsule = false
                )
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = c.background
        ) {
            Scaffold(
                containerColor = c.background,
                topBar = {
                    TopAppBar(
                        modifier = Modifier.statusBarsPadding(),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            FilledIconButton(
                                onClick = onDismiss,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = c.surfaceContainerLow,
                                    contentColor = c.onSurface
                                ),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                            }
                        },
                        title = {
                            Column {
                                Text("Типографика", color = c.textPrimary, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("Фиксированное превью сверху и живые шаблоны", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                            }
                        },
                        actions = {
                            FilledIconButton(
                                onClick = {
                                    viewModel.updateSettings(
                                        settings.copy(
                                            trackListTitleSize = 14f,
                                            trackListArtistSize = 12f,
                                            letterSpacingEm = 0f,
                                            lineHeightScale = 1f,
                                            boldTitles = false,
                                            uppercaseTitles = false,
                                            textShadowEnabled = false,
                                            textShadowIntensity = 0.5f,
                                            trackItemDensity = 1,
                                            trackItemCornerRadius = 12f,
                                            showTrackNumber = false,
                                            showDurationInList = true,
                                            showAlbumInList = false,
                                            trackArtSize = 52f,
                                            trackMetaOpacity = 0.78f,
                                            artistLetterSpacingEm = 0f,
                                            trackTextAlign = 0,
                                            trackItemPaddingScale = 1f,
                                            nowPlayingGlowStrength = 0.78f,
                                            trackMetaCapsule = false,
                                            trackTitleWeightMode = 2,
                                            trackMetaWeightMode = 1,
                                            trackTitleItalic = false,
                                            trackTitleOpacity = 1f,
                                            trackMetaUppercase = false,
                                            trackMetaSpacingScale = 1f,
                                            trackTitleTwoLines = false,
                                            durationBadgeStyle = 0,
                                            trackTitleAccentBlend = 0f,
                                            trackTitleDecorStyle = 0,
                                            trackMetaSeparatorStyle = 0
                                        )
                                    )
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = c.primaryContainer,
                                    contentColor = c.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = "Сбросить")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TrackTypographyPreviewCard(settings = settings)
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            TypographyControlSection("Шаблоны", "Один тап применяет целый красивый набор") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(presets) { preset ->
                                        TypographyPresetCard(preset = preset, onClick = { viewModel.updateSettings(preset.apply(settings)) })
                                    }
                                }
                            }
                        }
                        item {
                            TypographyControlSection("Размер и ритм", "Сделайте строку плотнее, выше или свободнее") {
                                TypoSlider("Размер названия", "${settings.trackListTitleSize.toInt()} sp", settings.trackListTitleSize, 11f..24f, Icons.Rounded.Title) {
                                    viewModel.updateSettings(settings.copy(trackListTitleSize = it))
                                }
                                TypoSlider("Размер метаданных", "${settings.trackListArtistSize.toInt()} sp", settings.trackListArtistSize, 9f..18f, Icons.Rounded.Person) {
                                    viewModel.updateSettings(settings.copy(trackListArtistSize = it))
                                }
                                TypoSlider("Межбуквенный title", "${(settings.letterSpacingEm * 100).toInt()}%", settings.letterSpacingEm, -0.05f..0.16f, Icons.Rounded.SpaceBar) {
                                    viewModel.updateSettings(settings.copy(letterSpacingEm = it))
                                }
                                TypoSlider("Межбуквенный meta", "${(settings.artistLetterSpacingEm * 100).toInt()}%", settings.artistLetterSpacingEm, -0.05f..0.16f, Icons.Rounded.SpaceBar) {
                                    viewModel.updateSettings(settings.copy(artistLetterSpacingEm = it))
                                }
                                TypoSlider("Межстрочный ритм", "${(settings.lineHeightScale * 100).toInt()}%", settings.lineHeightScale, 0.85f..1.7f, Icons.Rounded.FormatLineSpacing) {
                                    viewModel.updateSettings(settings.copy(lineHeightScale = it))
                                }
                                TypoSlider("Плотность карточки", "${(settings.trackItemPaddingScale * 100).toInt()}%", settings.trackItemPaddingScale, 0.82f..1.35f, Icons.Rounded.UnfoldMore) {
                                    viewModel.updateSettings(settings.copy(trackItemPaddingScale = it))
                                }
                            }
                        }
                        item {
                            TypographyControlSection("Акценты", "Украсьте title, glow и разделители") {
                                TypoSlider("Акцент в title", "${(settings.trackTitleAccentBlend * 100).toInt()}%", settings.trackTitleAccentBlend, 0f..0.82f, Icons.Rounded.Palette) {
                                    viewModel.updateSettings(settings.copy(trackTitleAccentBlend = it))
                                }
                                TypoSlider("Сила glow", "${(settings.nowPlayingGlowStrength * 100).toInt()}%", settings.nowPlayingGlowStrength, 0.18f..1.4f, Icons.Rounded.AutoAwesome) {
                                    viewModel.updateSettings(settings.copy(nowPlayingGlowStrength = it))
                                }
                                TypoSlider("Прозрачность title", "${(settings.trackTitleOpacity * 100).toInt()}%", settings.trackTitleOpacity, 0.35f..1f, Icons.Rounded.Opacity) {
                                    viewModel.updateSettings(settings.copy(trackTitleOpacity = it))
                                }
                                TypoSlider("Прозрачность meta", "${(settings.trackMetaOpacity * 100).toInt()}%", settings.trackMetaOpacity, 0.35f..1f, Icons.Rounded.Opacity) {
                                    viewModel.updateSettings(settings.copy(trackMetaOpacity = it))
                                }
                                TypographyChoicePills("Декор title", Icons.Rounded.TextFields, listOf(0 to "Чисто", 1 to "Подчёрк", 2 to "Акцент", 3 to "Glass"), settings.trackTitleDecorStyle) {
                                    viewModel.updateSettings(settings.copy(trackTitleDecorStyle = it))
                                }
                                TypographyChoicePills("Разделитель meta", Icons.Rounded.LinearScale, listOf(0 to "Точка", 1 to "Слэш", 2 to "Wave", 3 to "Spark"), settings.trackMetaSeparatorStyle) {
                                    viewModel.updateSettings(settings.copy(trackMetaSeparatorStyle = it))
                                }
                            }
                        }
                        item {
                            TypographyControlSection("Композиция", "Карточка, выравнивание и подача метаданных") {
                                TypoSlider("Размер обложки", "${settings.trackArtSize.toInt()} dp", settings.trackArtSize, 36f..72f, Icons.Rounded.Album) {
                                    viewModel.updateSettings(settings.copy(trackArtSize = it))
                                }
                                TypoSlider("Скругление", "${settings.trackItemCornerRadius.toInt()} dp", settings.trackItemCornerRadius, 6f..28f, Icons.Rounded.RoundedCorner) {
                                    viewModel.updateSettings(settings.copy(trackItemCornerRadius = it))
                                }
                                TypoSlider("Интервал title/meta", "${(settings.trackMetaSpacingScale * 100).toInt()}%", settings.trackMetaSpacingScale, 0.7f..1.8f, Icons.Rounded.FormatLineSpacing) {
                                    viewModel.updateSettings(settings.copy(trackMetaSpacingScale = it))
                                }
                                TypographyChoicePills("Выравнивание", Icons.Rounded.FormatAlignLeft, listOf(0 to "Слева", 1 to "Центр", 2 to "Справа"), settings.trackTextAlign) {
                                    viewModel.updateSettings(settings.copy(trackTextAlign = it))
                                }
                                TypographyChoicePills("Вес title", Icons.Rounded.FormatBold, listOf(0 to "Reg", 1 to "Med", 2 to "Semi", 3 to "Bold", 4 to "Black"), settings.trackTitleWeightMode) {
                                    viewModel.updateSettings(settings.copy(trackTitleWeightMode = it))
                                }
                                TypographyChoicePills("Вес meta", Icons.Rounded.Tune, listOf(0 to "Reg", 1 to "Med", 2 to "Semi", 3 to "Bold"), settings.trackMetaWeightMode) {
                                    viewModel.updateSettings(settings.copy(trackMetaWeightMode = it))
                                }
                            }
                        }
                        item {
                            TypographyControlSection("Отображение", "Показывать, прятать и стилизовать детали") {
                                OrbToggleRow("Две строки названия", "Длинные названия могут занимать 2 строки", settings.trackTitleTwoLines, Icons.Rounded.FormatAlignLeft, c.accent) {
                                    viewModel.updateSettings(settings.copy(trackTitleTwoLines = it))
                                }
                                OrbToggleRow("Жирные заголовки", "Лёгкий усилитель title", settings.boldTitles, Icons.Rounded.FormatBold, c.accentVar) {
                                    viewModel.updateSettings(settings.copy(boldTitles = it))
                                }
                                OrbToggleRow("Курсив title", "Наклон для названия трека", settings.trackTitleItalic, Icons.Rounded.FormatItalic, c.accentMuted) {
                                    viewModel.updateSettings(settings.copy(trackTitleItalic = it))
                                }
                                OrbToggleRow("Title CAPS", "Все названия прописными", settings.uppercaseTitles, Icons.Rounded.TextFields, c.accent) {
                                    viewModel.updateSettings(settings.copy(uppercaseTitles = it))
                                }
                                OrbToggleRow("Meta CAPS", "Артист и альбом прописными", settings.trackMetaUppercase, Icons.Rounded.TextFields, c.accentVar) {
                                    viewModel.updateSettings(settings.copy(trackMetaUppercase = it))
                                }
                                OrbToggleRow("Капсула meta", "Обрамление артиста и альбома", settings.trackMetaCapsule, Icons.Rounded.CropSquare, c.accentMuted) {
                                    viewModel.updateSettings(settings.copy(trackMetaCapsule = it))
                                }
                                OrbToggleRow("Показывать альбом", "Добавить альбом рядом с артистом", settings.showAlbumInList, Icons.Rounded.Album, c.accent) {
                                    viewModel.updateSettings(settings.copy(showAlbumInList = it))
                                }
                                OrbToggleRow("Показывать длительность", "Таймкод справа в строке", settings.showDurationInList, Icons.Rounded.Timer, c.accentVar) {
                                    viewModel.updateSettings(settings.copy(showDurationInList = it))
                                }
                                OrbToggleRow("Показывать номер", "Номер трека слева от обложки", settings.showTrackNumber, Icons.Rounded.Tag, c.accentMuted) {
                                    viewModel.updateSettings(settings.copy(showTrackNumber = it))
                                }
                                OrbToggleRow("Тень текста", "Дополнительная глубина под title", settings.textShadowEnabled, Icons.Rounded.Flare, c.accent) {
                                    viewModel.updateSettings(settings.copy(textShadowEnabled = it))
                                }
                                if (settings.textShadowEnabled) {
                                    TypoSlider("Сила тени", "${(settings.textShadowIntensity * 100).toInt()}%", settings.textShadowIntensity, 0.1f..1f, Icons.Rounded.BlurOn) {
                                        viewModel.updateSettings(settings.copy(textShadowIntensity = it))
                                    }
                                }
                                TypographyChoicePills("Стиль длительности", Icons.Rounded.Timer, listOf(0 to "Текст", 1 to "Капсула", 2 to "Акцент"), settings.durationBadgeStyle) {
                                    viewModel.updateSettings(settings.copy(durationBadgeStyle = it))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackTypographyPreviewCard(
    settings: PlayerSettings,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    val titleWeight = when (settings.trackTitleWeightMode) {
        0 -> FontWeight.Normal
        1 -> FontWeight.Medium
        3 -> FontWeight.Bold
        4 -> FontWeight.Black
        else -> FontWeight.SemiBold
    }
    val boostedTitleWeight = when {
        settings.boldTitles && titleWeight == FontWeight.Normal -> FontWeight.Medium
        settings.boldTitles && titleWeight == FontWeight.Medium -> FontWeight.SemiBold
        settings.boldTitles && titleWeight == FontWeight.SemiBold -> FontWeight.Bold
        settings.boldTitles && titleWeight == FontWeight.Bold -> FontWeight.ExtraBold
        else -> titleWeight
    }
    val metaWeight = when (settings.trackMetaWeightMode) {
        0 -> FontWeight.Normal
        2 -> FontWeight.SemiBold
        3 -> FontWeight.Bold
        else -> FontWeight.Medium
    }
    val textAlign = when (settings.trackTextAlign) {
        1 -> TextAlign.Center
        2 -> TextAlign.End
        else -> TextAlign.Start
    }
    val titleAccent = lerp(c.textPrimary, c.accent, settings.trackTitleAccentBlend.coerceIn(0f, 0.82f)).copy(alpha = settings.trackTitleOpacity.coerceIn(0.35f, 1f))
    val metaSeparator = when (settings.trackMetaSeparatorStyle) {
        1 -> " / "
        2 -> " ~ "
        3 -> " ✦ "
        else -> " · "
    }
    val titleDecorShape = RoundedCornerShape(16.dp)
    val titleDecorModifier = when (settings.trackTitleDecorStyle) {
        2 -> Modifier.clip(titleDecorShape).background(c.accent.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp)
        3 -> Modifier
            .clip(titleDecorShape)
            .background(Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.16f), c.bgElevated.copy(alpha = 0.94f), c.bgDeep.copy(alpha = 0.82f))))
            .border(1.dp, c.accent.copy(alpha = 0.14f), titleDecorShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
        else -> Modifier
    }
    val contentAlignment = when (settings.trackTextAlign) {
        1 -> Alignment.Center
        2 -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = c.bgCard) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(c.bgSurface.copy(alpha = 0.94f), c.bgCard.copy(alpha = 0.96f), c.bgDeep.copy(alpha = 0.98f))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Живое превью списка", color = c.textPrimary, fontFamily = font, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Верх не листается, чтобы вы сразу видели результат", color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.accent.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("NOW", color = c.accent, fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                shape = RoundedCornerShape(settings.trackItemCornerRadius.dp.coerceIn(14.dp, 26.dp)),
                color = c.bgElevated.copy(alpha = 0.92f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = (12.dp * settings.trackItemPaddingScale.coerceIn(0.82f, 1.35f))),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(settings.trackArtSize.dp.coerceIn(36.dp, 70.dp))
                            .clip(RoundedCornerShape(settings.trackItemCornerRadius.dp.coerceIn(12.dp, 24.dp)))
                            .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.82f), c.accentVar.copy(alpha = 0.78f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = c.bgDeep, modifier = Modifier.size((settings.trackArtSize * 0.45f).dp.coerceAtLeast(18.dp)))
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = when (settings.trackTextAlign) {
                            1 -> Alignment.CenterHorizontally
                            2 -> Alignment.End
                            else -> Alignment.Start
                        }
                    ) {
                        Box(modifier = titleDecorModifier.fillMaxWidth(), contentAlignment = contentAlignment) {
                            Text(
                                text = if (settings.uppercaseTitles) "MIDNIGHT CITY DRIVE" else "Midnight City Drive",
                                color = titleAccent,
                                fontFamily = font,
                                fontSize = settings.trackListTitleSize.sp,
                                fontWeight = boostedTitleWeight,
                                fontStyle = if (settings.trackTitleItalic) FontStyle.Italic else FontStyle.Normal,
                                letterSpacing = settings.letterSpacingEm.em,
                                lineHeight = (settings.trackListTitleSize * settings.lineHeightScale * 1.35f).sp,
                                textAlign = textAlign,
                                maxLines = if (settings.trackTitleTwoLines) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (settings.trackTitleDecorStyle == 1) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .align(contentAlignment)
                                        .width(if (settings.trackTitleTwoLines) 56.dp else 42.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.14f), c.accent, c.accent.copy(alpha = 0.20f))))
                                )
                            }
                        }
                        Spacer(Modifier.height((3.dp * settings.trackMetaSpacingScale.coerceIn(0.7f, 1.8f)).coerceIn(2.dp, 8.dp)))
                        val meta = buildString {
                            append("The Midnight")
                            if (settings.showAlbumInList) {
                                append(metaSeparator)
                                append("Neon Avenue")
                            }
                        }
                        val metaModifier = if (settings.trackMetaCapsule) {
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(c.accent.copy(alpha = 0.10f))
                                .border(1.dp, c.accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        } else Modifier
                        Text(
                            text = if (settings.trackMetaUppercase) meta.uppercase() else meta,
                            color = c.textSecondary.copy(alpha = settings.trackMetaOpacity.coerceIn(0.35f, 1f)),
                            fontFamily = font,
                            fontWeight = metaWeight,
                            fontSize = settings.trackListArtistSize.sp,
                            letterSpacing = settings.artistLetterSpacingEm.em,
                            textAlign = textAlign,
                            modifier = metaModifier
                        )
                    }

                    if (settings.showDurationInList) {
                        when (settings.durationBadgeStyle) {
                            1, 2 -> Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (settings.durationBadgeStyle == 2) c.accent.copy(alpha = 0.16f) else c.bgDeep.copy(alpha = 0.52f))
                                    .border(
                                        1.dp,
                                        if (settings.durationBadgeStyle == 2) c.accent.copy(alpha = 0.20f) else c.textPrimary.copy(alpha = 0.06f),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("3:45", color = if (settings.durationBadgeStyle == 2) c.accent else c.textSecondary, fontFamily = font, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                            else -> Text("3:45", color = c.textDisabled, fontFamily = font, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypographyControlSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(shape = RoundedCornerShape(22.dp), color = c.bgCard) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = c.textPrimary, fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = c.textSecondary, fontFamily = font, fontSize = 11.sp)
            }
            content()
        }
    }
}

@Composable
private fun TypographyPresetCard(
    preset: TypographyStudioPreset,
    onClick: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(shape = RoundedCornerShape(20.dp), color = preset.color.copy(alpha = 0.14f), modifier = Modifier.width(156.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(preset.color.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(preset.icon, contentDescription = null, tint = preset.color, modifier = Modifier.size(22.dp))
            }
            Text(preset.label, color = c.textPrimary, fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(preset.subtitle, color = c.textSecondary, fontFamily = font, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun TypographyChoicePills(
    title: String,
    icon: ImageVector,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val c = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.accent.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                val isSelected = option.first == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) c.accent else c.bgSurface)
                        .clickable { onSelect(option.first) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(option.second, color = if (isSelected) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Типографика и настройки текста
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TypographySettingsSection(viewModel: MusicViewModel) {
    val c        = MaterialTheme.colorScheme
    val font     = LocalAppFontFamily.current
    val settings by viewModel.settings.collectAsState()

    // Категории настроек
    var activeTab by remember { mutableStateOf(0) }
    val tabs = listOf("Плеер", "Список", "Стиль", "Отображение")

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Заголовок ─────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF3A6B5C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.FormatSize, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Типографика", color = c.textPrimary, fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Текст, размеры и стиль", color = c.textSecondary, fontFamily = font, fontSize = 12.sp)
            }
        }

        // ── Мини-превью ───────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.bgElevated)
                .padding(horizontal = 16.dp, vertical = (14.dp * settings.trackItemPaddingScale.coerceIn(0.82f, 1.35f)))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Album art preview
                Box(
                    modifier = Modifier
                        .size(settings.trackArtSize.dp.coerceIn(36.dp, 72.dp))
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(settings.trackItemCornerRadius.dp))
                        .background(c.accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = c.accent.copy(alpha = 0.7f), modifier = Modifier.size((settings.trackArtSize * 0.5f).dp.coerceIn(18.dp, 36.dp)))
                }
                val previewTextAlign = when (settings.trackTextAlign) {
                    1 -> TextAlign.Center
                    2 -> TextAlign.End
                    else -> TextAlign.Start
                }
                val previewColumnAlignment = when (settings.trackTextAlign) {
                    1 -> Alignment.CenterHorizontally
                    2 -> Alignment.End
                    else -> Alignment.Start
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = previewColumnAlignment
                ) {
                    val titleWeight = when (settings.trackTitleWeightMode) {
                        0 -> FontWeight.Normal
                        1 -> FontWeight.Medium
                        3 -> FontWeight.Bold
                        4 -> FontWeight.Black
                        else -> FontWeight.SemiBold
                    }
                    val previewTitleWeight = when {
                        settings.boldTitles && titleWeight == FontWeight.Normal -> FontWeight.Medium
                        settings.boldTitles && titleWeight == FontWeight.Medium -> FontWeight.SemiBold
                        settings.boldTitles && titleWeight == FontWeight.SemiBold -> FontWeight.Bold
                        settings.boldTitles && titleWeight == FontWeight.Bold -> FontWeight.ExtraBold
                        else -> titleWeight
                    }
                    val titleText   = if (settings.uppercaseTitles) "ON THE FLOOR TONIGHT" else "On The Floor Tonight"
                    Text(
                        titleText,
                        color = c.textPrimary.copy(alpha = settings.trackTitleOpacity.coerceIn(0.35f, 1f)),
                        fontFamily = font,
                        fontSize = settings.trackListTitleSize.sp,
                        fontWeight = previewTitleWeight,
                        fontStyle = if (settings.trackTitleItalic) FontStyle.Italic else FontStyle.Normal,
                        letterSpacing = settings.letterSpacingEm.em,
                        lineHeight = (settings.trackListTitleSize * settings.lineHeightScale * 1.4f).sp,
                        textAlign = previewTextAlign,
                        maxLines = if (settings.trackTitleTwoLines) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height((2.dp * settings.trackMetaSpacingScale.coerceIn(0.7f, 1.8f)).coerceAtLeast(2.dp)))
                    val metaTextBase = buildString {
                        append("Jennifer Lopez")
                        if (settings.showAlbumInList) append("  ·  J Lo")
                    }
                    val metaText = if (settings.trackMetaUppercase) metaTextBase.uppercase() else metaTextBase
                    val metaColor = when(settings.artistNameStyle) {
                        2 -> c.textDisabled
                        else -> c.textSecondary.copy(alpha = settings.trackMetaOpacity.coerceIn(0.35f, 1f))
                    }
                    val metaWeight = when (settings.trackMetaWeightMode) {
                        0 -> FontWeight.Normal
                        2 -> FontWeight.SemiBold
                        3 -> FontWeight.Bold
                        else -> FontWeight.Medium
                    }
                    val metaModifier = if (settings.trackMetaCapsule) {
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(c.accent.copy(alpha = 0.10f))
                            .border(1.dp, c.accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    } else Modifier
                    Text(
                        metaText,
                        color = metaColor,
                        fontFamily = font,
                        fontWeight = metaWeight,
                        fontSize = settings.trackListArtistSize.sp,
                        fontStyle = if (settings.artistNameStyle == 1) FontStyle.Italic else FontStyle.Normal,
                        letterSpacing = settings.artistLetterSpacingEm.em,
                        textAlign = previewTextAlign,
                        modifier = metaModifier
                    )
                    if (settings.showDurationInList) {
                        Spacer(Modifier.height(2.dp))
                        when (settings.durationBadgeStyle) {
                            1, 2 -> Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (settings.durationBadgeStyle == 2) c.accent.copy(alpha = 0.16f)
                                        else c.bgDeep.copy(alpha = 0.52f)
                                    )
                                    .border(
                                        1.dp,
                                        if (settings.durationBadgeStyle == 2) c.accent.copy(alpha = 0.22f)
                                        else c.textPrimary.copy(alpha = 0.08f),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("3:45", color = if (settings.durationBadgeStyle == 2) c.accent else c.textSecondary, fontFamily = font, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                            else -> Text("3:45", color = c.accent.copy(alpha = 0.6f + settings.nowPlayingGlowStrength.coerceIn(0.18f, 1.4f) * 0.22f), fontFamily = font, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Табы ──────────────────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(tabs) { tab ->
                val i = tabs.indexOf(tab)
                val sel = activeTab == i
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (sel) c.accent else c.bgCard)
                        .clickable { activeTab = i }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(tab, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Содержимое таба ───────────────────────────────────────────────────
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
            label = "typographyTab"
        ) { tab ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.bgElevated)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (tab) {
                    // ──── Таб 0: Плеер ────────────────────────────────────────
                    0 -> {
                        TypoSlider("Заголовок трека", "${settings.playerTitleSize.toInt()} sp", settings.playerTitleSize, 14f..36f, Icons.Rounded.Title) {
                            viewModel.updateSettings(settings.copy(playerTitleSize = it))
                        }
                        TypoSlider("Имя исполнителя", "${settings.playerArtistSize.toInt()} sp", settings.playerArtistSize, 10f..24f, Icons.Rounded.Person) {
                            viewModel.updateSettings(settings.copy(playerArtistSize = it))
                        }
                        TypoSlider("Таймкод", "${settings.playerTimeSize.toInt()} sp", settings.playerTimeSize, 9f..18f, Icons.Rounded.AccessTime) {
                            viewModel.updateSettings(settings.copy(playerTimeSize = it))
                        }
                    }

                    // ──── Таб 1: Список ───────────────────────────────────────
                    1 -> {
                        TypoSlider("Заголовок трека", "${settings.trackListTitleSize.toInt()} sp", settings.trackListTitleSize, 10f..24f, Icons.Rounded.Title) {
                            viewModel.updateSettings(settings.copy(trackListTitleSize = it))
                        }
                        TypoSlider("Имя исполнителя", "${settings.trackListArtistSize.toInt()} sp", settings.trackListArtistSize, 9f..20f, Icons.Rounded.Person) {
                            viewModel.updateSettings(settings.copy(trackListArtistSize = it))
                        }

                        // Плотность строк
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FormatListBulleted, null, tint = c.accent.copy(0.9f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Плотность списка", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Компактно", "Обычно", "Просторно").forEachIndexed { i, label ->
                                    val sel = settings.trackItemDensity == i
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) c.accent else c.bgCard)
                                            .clickable { viewModel.updateSettings(settings.copy(trackItemDensity = i)) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }

                        // Скругление карточки
                        TypoSlider("Скругление карточки", "${settings.trackItemCornerRadius.toInt()} dp", settings.trackItemCornerRadius, 0f..28f, Icons.Rounded.RoundedCorner) {
                            viewModel.updateSettings(settings.copy(trackItemCornerRadius = it))
                        }
                        TypoSlider("Размер обложки", "${settings.trackArtSize.toInt()} dp", settings.trackArtSize, 36f..72f, Icons.Rounded.Album) {
                            viewModel.updateSettings(settings.copy(trackArtSize = it))
                        }
                        TypoSlider("Внутренние отступы", "${(settings.trackItemPaddingScale * 100).toInt()}%", settings.trackItemPaddingScale, 0.82f..1.35f, Icons.Rounded.UnfoldMore) {
                            viewModel.updateSettings(settings.copy(trackItemPaddingScale = it))
                        }
                    }

                    // ──── Таб 2: Стиль ────────────────────────────────────────
                    2 -> {
                        TypoSlider("Межбуквенный интервал", "${(settings.letterSpacingEm * 100).toInt()}%", settings.letterSpacingEm, -0.05f..0.2f, Icons.Rounded.SpaceBar) {
                            viewModel.updateSettings(settings.copy(letterSpacingEm = it))
                        }
                        TypoSlider("Межстрочный интервал", "${(settings.lineHeightScale * 100).toInt()}%", settings.lineHeightScale, 0.8f..1.8f, Icons.Rounded.FormatLineSpacing) {
                            viewModel.updateSettings(settings.copy(lineHeightScale = it))
                        }
                        TypoSlider("Прозрачность метаданных", "${(settings.trackMetaOpacity * 100).toInt()}%", settings.trackMetaOpacity, 0.35f..1f, Icons.Rounded.Opacity) {
                            viewModel.updateSettings(settings.copy(trackMetaOpacity = it))
                        }
                        TypoSlider("Прозрачность заголовка", "${(settings.trackTitleOpacity * 100).toInt()}%", settings.trackTitleOpacity, 0.35f..1f, Icons.Rounded.Opacity) {
                            viewModel.updateSettings(settings.copy(trackTitleOpacity = it))
                        }
                        TypoSlider("Межбуквенный артиста", "${(settings.artistLetterSpacingEm * 100).toInt()}%", settings.artistLetterSpacingEm, -0.05f..0.18f, Icons.Rounded.SpaceBar) {
                            viewModel.updateSettings(settings.copy(artistLetterSpacingEm = it))
                        }
                        TypoSlider("Интервал title/meta", "${(settings.trackMetaSpacingScale * 100).toInt()}%", settings.trackMetaSpacingScale, 0.7f..1.8f, Icons.Rounded.FormatLineSpacing) {
                            viewModel.updateSettings(settings.copy(trackMetaSpacingScale = it))
                        }

                        OrbToggleRow("Жирные заголовки", "Усиленное начертание для треков", settings.boldTitles, Icons.Rounded.FormatBold, c.accent) {
                            viewModel.updateSettings(settings.copy(boldTitles = it))
                        }
                        OrbToggleRow("ПРОПИСНЫЕ БУКВЫ", "Заглавные буквы в заголовках", settings.uppercaseTitles, Icons.Rounded.TextFormat, c.accentVar) {
                            viewModel.updateSettings(settings.copy(uppercaseTitles = it))
                        }
                        OrbToggleRow("Курсив заголовка", "Лёгкий наклон для названия трека", settings.trackTitleItalic, Icons.Rounded.FormatItalic, c.accentVar) {
                            viewModel.updateSettings(settings.copy(trackTitleItalic = it))
                        }
                        OrbToggleRow("ПРОПИСНЫЕ МЕТАДАННЫЕ", "Артист и альбом прописными", settings.trackMetaUppercase, Icons.Rounded.TextFormat, c.accentMuted) {
                            viewModel.updateSettings(settings.copy(trackMetaUppercase = it))
                        }
                        OrbToggleRow("Тень текста", "Лёгкое свечение под текстом", settings.textShadowEnabled, Icons.Rounded.Flare, c.accentMuted) {
                            viewModel.updateSettings(settings.copy(textShadowEnabled = it))
                        }
                        if (settings.textShadowEnabled) {
                            TypoSlider("Интенсивность тени", "${(settings.textShadowIntensity * 100).toInt()}%", settings.textShadowIntensity, 0.1f..1f, Icons.Rounded.Opacity) {
                                viewModel.updateSettings(settings.copy(textShadowIntensity = it))
                            }
                        }

                        // Стиль имени исполнителя
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Person, null, tint = c.accent.copy(0.9f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Стиль исполнителя", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Обычный", "Курсив", "Заглушён").forEachIndexed { i, label ->
                                    val sel = settings.artistNameStyle == i
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) c.accentVar else c.bgCard)
                                            .clickable { viewModel.updateSettings(settings.copy(artistNameStyle = i)) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FormatBold, null, tint = c.accent.copy(0.9f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Вес заголовка", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Reg", "Med", "Semi", "Bold", "Black").forEachIndexed { i, label ->
                                    val sel = settings.trackTitleWeightMode == i
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) c.accent else c.bgCard)
                                            .clickable { viewModel.updateSettings(settings.copy(trackTitleWeightMode = i)) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Tune, null, tint = c.accent.copy(0.9f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Вес метаданных", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Reg", "Med", "Semi", "Bold").forEachIndexed { i, label ->
                                    val sel = settings.trackMetaWeightMode == i
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) c.accentVar else c.bgCard)
                                            .clickable { viewModel.updateSettings(settings.copy(trackMetaWeightMode = i)) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FormatAlignLeft, null, tint = c.accent.copy(0.9f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Выравнивание текста", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Слева", "Центр", "Справа").forEachIndexed { i, label ->
                                    val sel = settings.trackTextAlign == i
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) c.accent else c.bgCard)
                                            .clickable { viewModel.updateSettings(settings.copy(trackTextAlign = i)) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                        OrbToggleRow("Капсула метаданных", "Артист и альбом в мягкой стеклянной капсуле", settings.trackMetaCapsule, Icons.Rounded.CropSquare, c.accentVar) {
                            viewModel.updateSettings(settings.copy(trackMetaCapsule = it))
                        }
                    }

                    // ──── Таб 3: Отображение ──────────────────────────────────
                    3 -> {
                        OrbToggleRow("Подсветка текущего трека", "Акцентная подсветка активной строки", settings.glowOnNowPlaying, Icons.Rounded.Highlight, c.accent) {
                            viewModel.updateSettings(settings.copy(glowOnNowPlaying = it))
                        }
                        OrbToggleRow("Две строки названия", "Длинные треки могут занимать 2 строки", settings.trackTitleTwoLines, Icons.Rounded.FormatAlignLeft, c.accentVar) {
                            viewModel.updateSettings(settings.copy(trackTitleTwoLines = it))
                        }
                        if (settings.glowOnNowPlaying) {
                            TypoSlider("Сила подсветки", "${(settings.nowPlayingGlowStrength * 100).toInt()}%", settings.nowPlayingGlowStrength, 0.18f..1.4f, Icons.Rounded.Highlight) {
                                viewModel.updateSettings(settings.copy(nowPlayingGlowStrength = it))
                            }
                        }
                        OrbToggleRow("Порядковый номер", "Показывать № трека в списке", settings.showTrackNumber, Icons.Rounded.Tag, c.accentVar) {
                            viewModel.updateSettings(settings.copy(showTrackNumber = it))
                        }
                        OrbToggleRow("Длительность в списке", "Показывать время трека справа", settings.showDurationInList, Icons.Rounded.Timer, c.accentMuted) {
                            viewModel.updateSettings(settings.copy(showDurationInList = it))
                        }
                        OrbToggleRow("Битрейт в списке", "Показывать качество файла (kbps)", settings.showBitrateInList, Icons.Rounded.HighQuality, c.textSecondary) {
                            viewModel.updateSettings(settings.copy(showBitrateInList = it))
                        }
                        OrbToggleRow("Альбом в списке", "Показывать название альбома рядом с исполнителем", settings.showAlbumInList, Icons.Rounded.Album, c.accentVar) {
                            viewModel.updateSettings(settings.copy(showAlbumInList = it))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Timer, null, tint = c.accent.copy(0.9f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Стиль длительности", color = c.textPrimary, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Текст", "Капсула", "Акцент").forEachIndexed { i, label ->
                                    val sel = settings.durationBadgeStyle == i
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) c.accentMuted else c.bgCard)
                                            .clickable { viewModel.updateSettings(settings.copy(durationBadgeStyle = i)) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = if (sel) c.bgDeep else c.textSecondary, fontFamily = font, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ── Слайдер типографики ───────────────────────────────────────────────────────
@Composable
private fun TypoSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    onValueChange: (Float) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = colors.onSurface, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(valueLabel, color = colors.primary, fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value, onValueChange = onValueChange, valueRange = range,
                modifier = Modifier.fillMaxWidth().height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.surfaceVariant
                )
            )
        }
    }
}
