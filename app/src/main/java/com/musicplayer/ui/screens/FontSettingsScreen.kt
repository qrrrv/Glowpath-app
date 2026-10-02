package com.musicplayer.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.ui.theme.AppFontList
import com.musicplayer.ui.theme.DefaultAppFont
import com.musicplayer.ui.theme.DefaultAppFontId
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.ui.theme.fontFamilyFromFile
import com.musicplayer.viewmodel.MusicViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Экран «Шрифт» — полноэкранная страница в стиле Material 3.
// Раньше это был Dialog внутри SettingsScreen.kt (FontPickerDialog).
// Маршрут навигации: "font_settings".
// ─────────────────────────────────────────────────────────────────────────────

private data class FontBrowserEntry(
    val file: File,
    val isDirectory: Boolean
)

private data class FontDirectorySuggestion(
    val directory: File,
    val fontCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val appFont = LocalAppFontFamily.current
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val currentFontId = settings.selectedFontId
    val currentCustomFontUri = settings.customFontUri
    val isCustomFont = currentCustomFontUri.isNotBlank()

    // ── Текущий выбранный шрифт (для карточки-превью) ────────────────────────
    val builtInFont = remember(currentFontId) {
        AppFontList.firstOrNull { it.id == currentFontId } ?: DefaultAppFont
    }
    val activeFamily: FontFamily = remember(currentFontId, currentCustomFontUri) {
        if (isCustomFont) fontFamilyFromFile(currentCustomFontUri) else builtInFont.family
    }
    val activeName = if (isCustomFont) {
        File(currentCustomFontUri).nameWithoutExtension.replace(Regex("_\\d+$"), "")
    } else {
        builtInFont.displayName
    }
    val activeSource = when {
        isCustomFont -> "Свой шрифт · ${File(currentCustomFontUri).extension.uppercase()}"
        builtInFont.id == DefaultAppFontId -> "Встроенный · по умолчанию"
        else -> "Встроенный · Google Fonts"
    }

    // ── Состояние файлового проводника ───────────────────────────────────────
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

    var scanRequest by remember { mutableIntStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    val suggestedDirectories by produceState(
        initialValue = emptyList<FontDirectorySuggestion>(),
        scanRequest
    ) {
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

    // Системная кнопка «назад»: сначала поднимаемся по папкам, потом выходим с экрана
    BackHandler(enabled = pathHistory.isNotEmpty()) {
        goBackInBrowser()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Шрифт",
                        fontFamily = appFont,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = colors.background,
                    scrolledContainerColor = colors.surfaceContainer,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── Превью текущего шрифта ───────────────────────────────────────
            item(key = "preview") {
                FontPreviewCard(
                    fontName = activeName,
                    sourceLabel = activeSource,
                    previewFamily = activeFamily,
                    showReset = isCustomFont,
                    onReset = {
                        viewModel.updateSettings(
                            settings.copy(
                                customFontUri = "",
                                selectedFontId = DefaultAppFontId
                            )
                        )
                    }
                )
            }

            // ── Встроенные шрифты ────────────────────────────────────────────
            item(key = "header_builtin") {
                FontSectionHeader(
                    title = "Встроенные шрифты",
                    subtitle = "Применяются сразу"
                )
            }
            itemsIndexed(AppFontList, key = { _, font -> "builtin_${font.id}" }) { index, font ->
                val selected = !isCustomFont && currentFontId == font.id
                FontListRow(
                    shape = groupedItemShape(index, AppFontList.size),
                    selected = selected,
                    onClick = {
                        viewModel.updateSettings(
                            settings.copy(
                                selectedFontId = font.id,
                                customFontUri = ""
                            )
                        )
                    },
                    leading = { FontBadge(family = font.family, selected = selected) },
                    headline = {
                        Text(
                            font.displayName,
                            fontFamily = font.family,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supporting = {
                        Text(
                            if (font.id == DefaultAppFontId) "По умолчанию" else "Google Fonts",
                            fontFamily = appFont,
                            maxLines = 1
                        )
                    },
                    trailing = { RadioButton(selected = selected, onClick = null) }
                )
            }

            // ── Свой шрифт с устройства ──────────────────────────────────────
            item(key = "header_custom") {
                FontSectionHeader(
                    title = "Свой шрифт",
                    subtitle = "Выберите файл .ttf или .otf на устройстве"
                )
            }
            item(key = "browser_controls") {
                BrowserControlsCard(
                    filterQuery = filterQuery,
                    onFilterChange = { filterQuery = it },
                    isScanning = isScanning,
                    onScan = { scanRequest += 1 },
                    suggestedDirectories = suggestedDirectories,
                    rootDirectories = rootDirectories,
                    currentDirPath = currentDirPath,
                    onOpenDirectory = { openDirectory(it) },
                    onGoBack = { goBackInBrowser() },
                    onGoUp = {
                        currentDir.parentFile
                            ?.takeIf { it.exists() && it.isDirectory }
                            ?.let { openDirectory(it) }
                    }
                )
            }

            if (entries.isEmpty()) {
                item(key = "browser_empty") {
                    BrowserEmptyState(hasSuggestions = suggestedDirectories.isNotEmpty())
                }
            } else {
                itemsIndexed(entries, key = { _, entry -> "entry_${entry.file.absolutePath}" }) { index, entry ->
                    val shape = groupedItemShape(index, entries.size)
                    if (entry.isDirectory) {
                        val immediateFonts = remember(entry.file.absolutePath) { countFontFiles(entry.file) }
                        FontListRow(
                            shape = shape,
                            selected = false,
                            onClick = { openDirectory(entry.file) },
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(colors.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = colors.onSecondaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            headline = {
                                Text(
                                    entry.file.name.ifBlank { entry.file.absolutePath },
                                    fontFamily = appFont,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supporting = {
                                Text(
                                    if (immediateFonts > 0) "Шрифтов внутри: $immediateFonts" else "Открыть папку",
                                    fontFamily = appFont,
                                    maxLines = 1
                                )
                            },
                            trailing = {
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                            }
                        )
                    } else {
                        val previewFont = rememberFontPreviewFamily(entry.file)
                        val selected = isCustomFont &&
                            File(currentCustomFontUri).name == buildSafeFontFileName(entry.file)
                        FontListRow(
                            shape = shape,
                            selected = selected,
                            onClick = {
                                val copiedPath = importFontFile(context, entry.file)
                                if (copiedPath != null) {
                                    viewModel.updateSettings(settings.copy(customFontUri = copiedPath))
                                } else {
                                    Toast.makeText(context, "Не удалось открыть шрифт", Toast.LENGTH_SHORT).show()
                                }
                            },
                            leading = { FontBadge(family = previewFont, selected = selected) },
                            headline = {
                                Text(
                                    entry.file.nameWithoutExtension,
                                    fontFamily = previewFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supporting = {
                                Column {
                                    Text(
                                        "Съешь ещё · The quick brown fox",
                                        fontFamily = previewFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${entry.file.extension.uppercase()} · ${formatFileSize(entry.file.length())}",
                                        fontFamily = appFont,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            },
                            trailing = { RadioButton(selected = selected, onClick = null) }
                        )
                    }
                }
            }
        }
    }
}

// ── Карточка с превью текущего шрифта ────────────────────────────────────────
@Composable
private fun FontPreviewCard(
    fontName: String,
    sourceLabel: String,
    previewFamily: FontFamily,
    showReset: Boolean,
    onReset: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val appFont = LocalAppFontFamily.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Aa",
                    fontFamily = previewFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        fontName,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = previewFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        sourceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = appFont,
                        color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                "Съешь ещё этих мягких французских булок, да выпей чаю.",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = previewFamily
            )
            Text(
                "The quick brown fox jumps over the lazy dog. 0123456789",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = previewFamily,
                color = colors.onPrimaryContainer.copy(alpha = 0.85f)
            )
            if (showReset) {
                FilledTonalButton(onClick = onReset) {
                    Icon(
                        Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Сбросить к ${DefaultAppFont.displayName}",
                        fontFamily = appFont
                    )
                }
            }
        }
    }
}

// ── Заголовок раздела ────────────────────────────────────────────────────────
@Composable
private fun FontSectionHeader(
    title: String,
    subtitle: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val appFont = LocalAppFontFamily.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = appFont,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = appFont,
                color = colors.onSurfaceVariant
            )
        }
    }
}

// ── Строка списка (Material 3 ListItem в тональной плашке) ──────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontListRow(
    shape: Shape,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    headline: @Composable () -> Unit,
    supporting: @Composable (() -> Unit)?,
    trailing: @Composable (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
        label = "fontRowContainer"
    )
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = headline,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = trailing,
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
                supportingColor = if (selected) {
                    colors.onSecondaryContainer.copy(alpha = 0.78f)
                } else {
                    colors.onSurfaceVariant
                },
                leadingIconColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                trailingIconColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
            )
        )
    }
}

// ── Плашка «Aa» с образцом шрифта ────────────────────────────────────────────
@Composable
private fun FontBadge(
    family: FontFamily,
    selected: Boolean
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary else colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Aa",
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant
        )
    }
}

// Скругления для сгруппированного списка: большие по краям группы, малые между элементами
private fun groupedItemShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer, topEnd = outer, bottomEnd = inner, bottomStart = inner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = inner, topEnd = inner, bottomEnd = outer, bottomStart = outer
        )
        else -> RoundedCornerShape(inner)
    }
}

// ── Панель управления проводником: фильтр, автопоиск, папки, путь ───────────
@Composable
private fun BrowserControlsCard(
    filterQuery: String,
    onFilterChange: (String) -> Unit,
    isScanning: Boolean,
    onScan: () -> Unit,
    suggestedDirectories: List<FontDirectorySuggestion>,
    rootDirectories: List<File>,
    currentDirPath: String,
    onOpenDirectory: (File) -> Unit,
    onGoBack: () -> Unit,
    onGoUp: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val appFont = LocalAppFontFamily.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = onFilterChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                placeholder = {
                    Text("Фильтр по названию", fontFamily = appFont)
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(onClick = { onFilterChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить фильтр")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = colors.surfaceContainerHigh,
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = colors.primary
                )
            )

            FilledTonalButton(
                onClick = onScan,
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isScanning) "Ищу папки со шрифтами…" else "Найти папки со шрифтами",
                    fontFamily = appFont
                )
            }

            if (suggestedDirectories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Найдено: ${suggestedDirectories.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = appFont,
                        color = colors.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestedDirectories.forEach { suggestion ->
                            val selected = suggestion.directory.absolutePath == currentDirPath
                            FilterChip(
                                selected = selected,
                                onClick = { onOpenDirectory(suggestion.directory) },
                                label = {
                                    Text(
                                        "${suggestion.directory.name.ifBlank { suggestion.directory.absolutePath }} · ${suggestion.fontCount}",
                                        fontFamily = appFont,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (rootDirectories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Быстрые папки",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = appFont,
                        color = colors.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rootDirectories.forEach { directory ->
                            FilterChip(
                                selected = currentDirPath == directory.absolutePath,
                                onClick = { onOpenDirectory(directory) },
                                label = {
                                    Text(
                                        directory.name.ifBlank { directory.absolutePath },
                                        fontFamily = appFont,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Навигация по папкам + текущий путь
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад по папкам")
                    }
                    IconButton(onClick = onGoUp) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Папка выше")
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 12.dp)
                    ) {
                        Text(
                            "Текущая папка",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = appFont,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            currentDirPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = appFont,
                            color = colors.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── Пустое состояние: в папке нет шрифтов ────────────────────────────────────
@Composable
private fun BrowserEmptyState(hasSuggestions: Boolean) {
    val colors = MaterialTheme.colorScheme
    val appFont = LocalAppFontFamily.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.TextFields,
                    contentDescription = null,
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "В этой папке шрифтов нет",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = appFont,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                if (hasSuggestions) {
                    "Откройте одну из найденных папок выше или запустите поиск ещё раз."
                } else {
                    "Откройте другую папку или нажмите «Найти папки со шрифтами»."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = appFont,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Хелперы файлового проводника (перенесены из SettingsScreen.kt без изменений)
// ─────────────────────────────────────────────────────────────────────────────

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

private fun loadFontFamilyOrNull(filePath: String): FontFamily? {
    if (filePath.isBlank()) return null
    return try {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) null
        else FontFamily(android.graphics.Typeface.createFromFile(file))
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
private fun rememberFontPreviewFamily(sourceFile: File): FontFamily {
    val context = LocalContext.current
    val previewFont by produceState(
        initialValue = DefaultAppFont.family,
        key1 = sourceFile.absolutePath
    ) {
        value = withContext(Dispatchers.IO) {
            loadFontFamilyOrNull(sourceFile.absolutePath)
                ?: cachePreviewFontFile(context, sourceFile)?.let(::loadFontFamilyOrNull)
                ?: DefaultAppFont.family
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
