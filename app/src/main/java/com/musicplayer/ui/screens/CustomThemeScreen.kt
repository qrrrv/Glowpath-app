package com.musicplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.data.AppTheme
import com.musicplayer.data.CustomThemeColors
import com.musicplayer.data.InterfaceStyle
import com.musicplayer.data.RepeatMode
import com.musicplayer.ui.components.AnimatedPlaybackControls
import com.musicplayer.ui.components.ToggleSegmentButton
import com.musicplayer.ui.theme.LocalAppColors
import com.musicplayer.ui.theme.LocalAppFontFamily
import com.musicplayer.viewmodel.MusicViewModel
import kotlin.math.roundToInt

// ── Preset mixes ──────────────────────────────────────────────────────────────
private data class ThemePreset(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val colors: CustomThemeColors)

private val PRESETS = listOf(
    ThemePreset("Шоколад", Icons.Rounded.LocalCafe, CustomThemeColors(
        bgDeep = 0xFF1C1008, bgSurface = 0xFF2A1A0A, bgCard = 0xFF321E0D, bgElevated = 0xFF3D2612,
        accent = 0xFFE8B88A, accentVar = 0xFFD4956A, accentMuted = 0xFF8B6040,
        textPrimary = 0xFFF0DCC0, textSecondary = 0xFFB8956A, textDisabled = 0xFF6B4E30,
        divider = 0xFF3D2612
    )),
    ThemePreset("Неон", Icons.Rounded.Bolt, CustomThemeColors(
        bgDeep = 0xFF050510, bgSurface = 0xFF0A0A1E, bgCard = 0xFF0F0F2A, bgElevated = 0xFF14143A,
        accent = 0xFF00FFD1, accentVar = 0xFF00BFFF, accentMuted = 0xFF008870,
        textPrimary = 0xFFE0FFFF, textSecondary = 0xFF00FFD1, textDisabled = 0xFF224A44,
        divider = 0xFF0F0F2A
    )),
    ThemePreset("Розовое золото", Icons.Rounded.FavoriteBorder, CustomThemeColors(
        bgDeep = 0xFF1A0E0E, bgSurface = 0xFF2A1818, bgCard = 0xFF3A2222, bgElevated = 0xFF4A2C2C,
        accent = 0xFFE8A598, accentVar = 0xFFD4846A, accentMuted = 0xFF9A5550,
        textPrimary = 0xFFFFF0EE, textSecondary = 0xFFE8A598, textDisabled = 0xFF7A4A48,
        divider = 0xFF3A2222
    )),
    ThemePreset("Арктика", Icons.Rounded.AcUnit, CustomThemeColors(
        bgDeep = 0xFF050C14, bgSurface = 0xFF0A1822, bgCard = 0xFF102030, bgElevated = 0xFF162840,
        accent = 0xFFAADEFF, accentVar = 0xFF70C8F0, accentMuted = 0xFF3A8098,
        textPrimary = 0xFFECF8FF, textSecondary = 0xFFAADEFF, textDisabled = 0xFF2A5870,
        divider = 0xFF102030
    )),
    ThemePreset("Янтарь", Icons.Rounded.WbSunny, CustomThemeColors(
        bgDeep = 0xFF120A00, bgSurface = 0xFF1E1000, bgCard = 0xFF2C1800, bgElevated = 0xFF3C2200,
        accent = 0xFFFFCA28, accentVar = 0xFFFFA000, accentMuted = 0xFFA06800,
        textPrimary = 0xFFFFF8E1, textSecondary = 0xFFFFCA28, textDisabled = 0xFF7A5000,
        divider = 0xFF2C1800
    )),
    ThemePreset("Изумруд", Icons.Rounded.Spa, CustomThemeColors(
        bgDeep = 0xFF021008, bgSurface = 0xFF051A0E, bgCard = 0xFF082416, bgElevated = 0xFF0D301E,
        accent = 0xFF50E3A4, accentVar = 0xFF1DB97A, accentMuted = 0xFF0D6642,
        textPrimary = 0xFFE0FFF3, textSecondary = 0xFF50E3A4, textDisabled = 0xFF1A5038,
        divider = 0xFF082416
    )),
    ThemePreset("Полночь", Icons.Rounded.Brightness3, CustomThemeColors(
        bgDeep = 0xFF02040E, bgSurface = 0xFF060A1E, bgCard = 0xFF0A102E, bgElevated = 0xFF10183E,
        accent = 0xFF7986CB, accentVar = 0xFF5C6BC0, accentMuted = 0xFF3A4880,
        textPrimary = 0xFFE8EAF6, textSecondary = 0xFF9FA8DA, textDisabled = 0xFF3A4060,
        divider = 0xFF0A102E
    )),
    ThemePreset("AMOLED", Icons.Rounded.PhoneAndroid, CustomThemeColors(
        bgDeep = 0xFF000000, bgSurface = 0xFF050505, bgCard = 0xFF0C0C0C, bgElevated = 0xFF151515,
        accent = 0xFFFF4081, accentVar = 0xFFE91E63, accentMuted = 0xFF881040,
        textPrimary = 0xFFFFFFFF, textSecondary = 0xFFFF80AB, textDisabled = 0xFF444444,
        divider = 0xFF0C0C0C
    )),
    ThemePreset("Лаванда", Icons.Rounded.AutoAwesome, CustomThemeColors(
        bgDeep = 0xFF140F22, bgSurface = 0xFF1F1833, bgCard = 0xFF2B2145, bgElevated = 0xFF382B5B,
        accent = 0xFFD7C4FF, accentVar = 0xFFB39DDB, accentMuted = 0xFF7D6AAE,
        textPrimary = 0xFFF3EEFF, textSecondary = 0xFFD7C4FF, textDisabled = 0xFF5D5478,
        divider = 0xFF2B2145
    )),
    ThemePreset("Рубиновый", Icons.Rounded.Diamond, CustomThemeColors(
        bgDeep = 0xFF18080C, bgSurface = 0xFF281018, bgCard = 0xFF3A1622, bgElevated = 0xFF4C2030,
        accent = 0xFFFF6B8A, accentVar = 0xFFD94B70, accentMuted = 0xFF8A3550,
        textPrimary = 0xFFFFEEF2, textSecondary = 0xFFFFA7B8, textDisabled = 0xFF71404D,
        divider = 0xFF3A1622
    )),
    ThemePreset("Стальной", Icons.Rounded.Shield, CustomThemeColors(
        bgDeep = 0xFF0B1116, bgSurface = 0xFF131B22, bgCard = 0xFF1B2630, bgElevated = 0xFF24323E,
        accent = 0xFF87A9C3, accentVar = 0xFF5F8FB5, accentMuted = 0xFF466476,
        textPrimary = 0xFFEAF3F8, textSecondary = 0xFFB4C7D6, textDisabled = 0xFF50626D,
        divider = 0xFF1B2630
    )),
    ThemePreset("Матча", Icons.Rounded.Eco, CustomThemeColors(
        bgDeep = 0xFF09120C, bgSurface = 0xFF111D14, bgCard = 0xFF18281C, bgElevated = 0xFF223425,
        accent = 0xFFA6D98A, accentVar = 0xFF7FCB63, accentMuted = 0xFF527A46,
        textPrimary = 0xFFF2F9ED, textSecondary = 0xFFC7E8B9, textDisabled = 0xFF58705A,
        divider = 0xFF18281C
    )),
    ThemePreset("Пустыня", Icons.Rounded.Landscape, CustomThemeColors(
        bgDeep = 0xFF171008, bgSurface = 0xFF24180E, bgCard = 0xFF342215, bgElevated = 0xFF44301E,
        accent = 0xFFF2C078, accentVar = 0xFFD89A52, accentMuted = 0xFF8E683D,
        textPrimary = 0xFFFFF2DE, textSecondary = 0xFFE7C89A, textDisabled = 0xFF6F5A42,
        divider = 0xFF342215
    )),
    ThemePreset("Орхидейный", Icons.Rounded.AutoAwesome, CustomThemeColors(
        bgDeep = 0xFF060617, bgSurface = 0xFF0D1030, bgCard = 0xFF15184A, bgElevated = 0xFF1D2162,
        accent = 0xFF89A6FF, accentVar = 0xFFE08BFF, accentMuted = 0xFF5D4DA0,
        textPrimary = 0xFFF1F3FF, textSecondary = 0xFFC9D2FF, textDisabled = 0xFF66709B,
        divider = 0xFF15184A
    )),
    ThemePreset("Нежно-розовый", Icons.Rounded.LocalFlorist, CustomThemeColors(
        bgDeep = 0xFF1A0D16, bgSurface = 0xFF271420, bgCard = 0xFF341B2B, bgElevated = 0xFF462438,
        accent = 0xFFFFA7C6, accentVar = 0xFFFFC97D, accentMuted = 0xFF9B5E79,
        textPrimary = 0xFFFFF0F6, textSecondary = 0xFFFFC9D9, textDisabled = 0xFF725567,
        divider = 0xFF341B2B
    )),
    ThemePreset("Аквамариновый", Icons.Rounded.WaterDrop, CustomThemeColors(
        bgDeep = 0xFF041517, bgSurface = 0xFF082024, bgCard = 0xFF0D2C31, bgElevated = 0xFF12393F,
        accent = 0xFF68E6DA, accentVar = 0xFF8BC6FF, accentMuted = 0xFF3D7B85,
        textPrimary = 0xFFE9FFFD, textSecondary = 0xFFAEE9E3, textDisabled = 0xFF547174,
        divider = 0xFF0D2C31
    )),
    ThemePreset("Терракотовый", Icons.Rounded.LocalFireDepartment, CustomThemeColors(
        bgDeep = 0xFF170705, bgSurface = 0xFF24100B, bgCard = 0xFF341610, bgElevated = 0xFF451F18,
        accent = 0xFFFF7247, accentVar = 0xFFFFA23D, accentMuted = 0xFF8D4A33,
        textPrimary = 0xFFFFF1EA, textSecondary = 0xFFFFC3A9, textDisabled = 0xFF79584E,
        divider = 0xFF341610
    )),
    ThemePreset("Слоновая кость", Icons.Rounded.LightMode, CustomThemeColors(
        bgDeep = 0xFFF2E7D6, bgSurface = 0xFFFBF4EA, bgCard = 0xFFFFFFFF, bgElevated = 0xFFF6EBDD,
        accent = 0xFFB06A3D, accentVar = 0xFFD48B4A, accentMuted = 0xFFC9A17E,
        textPrimary = 0xFF38261A, textSecondary = 0xFF6E5140, textDisabled = 0xFF9C8778,
        divider = 0xFFE7D8C8, isLightTheme = true
    )),
    ThemePreset("Лимонный", Icons.Rounded.WbSunny, CustomThemeColors(
        bgDeep = 0xFF101A10, bgSurface = 0xFF172416, bgCard = 0xFF1F311C, bgElevated = 0xFF284027,
        accent = 0xFFFFD54F, accentVar = 0xFF9BE15D, accentMuted = 0xFF6E8F42,
        textPrimary = 0xFFF8FFE8, textSecondary = 0xFFD4E6AA, textDisabled = 0xFF6F8461,
        divider = 0xFF223222
    )),
    ThemePreset("Морозный циан", Icons.Rounded.AcUnit, CustomThemeColors(
        bgDeep = 0xFF08131A, bgSurface = 0xFF10202A, bgCard = 0xFF17303C, bgElevated = 0xFF21414F,
        accent = 0xFF7FE7FF, accentVar = 0xFFB8F4FF, accentMuted = 0xFF53899A,
        textPrimary = 0xFFF0FDFF, textSecondary = 0xFFC9E9EE, textDisabled = 0xFF688792,
        divider = 0xFF17303C
    )),
    ThemePreset("Медный", Icons.Rounded.Flare, CustomThemeColors(
        bgDeep = 0xFF180C08, bgSurface = 0xFF24130E, bgCard = 0xFF331B14, bgElevated = 0xFF45241A,
        accent = 0xFFFF8E5A, accentVar = 0xFFFFC16A, accentMuted = 0xFF8D553B,
        textPrimary = 0xFFFFF1EA, textSecondary = 0xFFFFD1B5, textDisabled = 0xFF7D6255,
        divider = 0xFF331B14
    )),
    ThemePreset("Бирюзовый", Icons.Rounded.WaterDrop, CustomThemeColors(
        bgDeep = 0xFF071617, bgSurface = 0xFF0C2325, bgCard = 0xFF133235, bgElevated = 0xFF1A4347,
        accent = 0xFF4EE4D5, accentVar = 0xFF7EDBFF, accentMuted = 0xFF4A8D90,
        textPrimary = 0xFFEFFFFD, textSecondary = 0xFFB7F0EA, textDisabled = 0xFF648689,
        divider = 0xFF133235
    )),
    ThemePreset("Сливовый", Icons.Rounded.AutoAwesome, CustomThemeColors(
        bgDeep = 0xFF130916, bgSurface = 0xFF1F1025, bgCard = 0xFF2D1836, bgElevated = 0xFF3C2149,
        accent = 0xFFD68BFF, accentVar = 0xFFFF9AD7, accentMuted = 0xFF84589D,
        textPrimary = 0xFFFBF1FF, textSecondary = 0xFFE2BEF5, textDisabled = 0xFF756282,
        divider = 0xFF2D1836
    )),
    ThemePreset("Жемчужный", Icons.Rounded.LightMode, CustomThemeColors(
        bgDeep = 0xFFF5EEE8, bgSurface = 0xFFFCF7F2, bgCard = 0xFFFFFFFF, bgElevated = 0xFFF3ECE6,
        accent = 0xFF8E6FD1, accentVar = 0xFFCE8CA9, accentMuted = 0xFFC8B9D6,
        textPrimary = 0xFF332A34, textSecondary = 0xFF695B70, textDisabled = 0xFF9A8B9E,
        divider = 0xFFE5DBE3, isLightTheme = true
    )),
    ThemePreset("Оливковый", Icons.Rounded.Eco, CustomThemeColors(
        bgDeep = 0xFF11150A, bgSurface = 0xFF1A2010, bgCard = 0xFF252D17, bgElevated = 0xFF313C1F,
        accent = 0xFFC3D96B, accentVar = 0xFFE3C76C, accentMuted = 0xFF7A8B47,
        textPrimary = 0xFFF8FCE8, textSecondary = 0xFFD8E0B0, textDisabled = 0xFF76805E,
        divider = 0xFF252D17
    )),
)

// ── Helper: Color ↔ Long ──────────────────────────────────────────────────────
// Colors stored as Long containing ARGB packed int (0xAARRGGBB) cast to Long
private fun Color.toLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
private fun Long.toComposeColor(): Color = Color(this.toInt())
private fun blendLong(base: Long, target: Color, amount: Float): Long =
    lerp(Color(base.toInt()), target, amount.coerceIn(0f, 1f)).toLong()

// ── ARGB channel extraction ───────────────────────────────────────────────────
private fun Long.red(): Int   = ((this shr 16) and 0xFF).toInt()
private fun Long.green(): Int = ((this shr 8)  and 0xFF).toInt()
private fun Long.blue(): Int  = ((this)         and 0xFF).toInt()
private fun Long.alpha(): Int = ((this shr 24)  and 0xFF).toInt()

private fun buildColor(a: Int, r: Int, g: Int, b: Int): Long =
    (((a.toLong() and 0xFF) shl 24) or
     ((r.toLong() and 0xFF) shl 16) or
     ((g.toLong() and 0xFF) shl 8)  or
      (b.toLong() and 0xFF)) and 0xFFFFFFFFL

// ── Main Screen ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    val customColors by viewModel.customThemeColors.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Local draft — we apply only when saved
    var draft by remember { mutableStateOf(customColors) }
    var showColorEditor by remember { mutableStateOf(false) }
    var editingColorKey by remember { mutableStateOf("") }
    var editingColorValue by remember { mutableStateOf(0L) }
    var showFullPreview by remember { mutableStateOf(false) }

    fun saveAndApply() {
        viewModel.updateCustomTheme(draft)
        viewModel.updateSettings(
            settings.copy(
                theme = AppTheme.CUSTOM,
                useDynamicColors = false
            )
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Назад")
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Своя тема",
                            fontFamily = font,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp
                        )
                        Text(
                            "Редактор тонов, форм и акцентов Material 3",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = font,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { saveAndApply() },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Применить", fontFamily = font, fontSize = 13.sp)
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
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Предпросмотр плеера",
                            color = c.textSecondary,
                            fontFamily = font,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Сверху компактный вид, полный экран открывается отдельно",
                            color = c.textSecondary,
                            fontFamily = font,
                            fontSize = 11.sp
                        )
                    }
                    FilledTonalButton(
                        onClick = { showFullPreview = true },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInFull, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Развернуть", fontFamily = font, fontSize = 12.sp)
                    }
                }
                LivePreviewCard(
                    draft = draft,
                    expanded = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
                )
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 28.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
                            )
                            Text(
                                "Настройки темы",
                                color = c.textPrimary,
                                fontFamily = font,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Меняй параметры снизу и сразу смотри на плеер сверху",
                                color = c.textSecondary,
                                fontFamily = font,
                                fontSize = 11.sp
                            )
                        }
                    }

                    item {
                        Text(
                            "Готовые миксы",
                            color = c.textSecondary,
                            fontFamily = font,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        PresetRow(presets = PRESETS, onPresetSelected = { draft = it })
                    }

                    item {
                        PaletteActionsSection(
                            draft = draft,
                            onDraftChange = { draft = it }
                        )
                    }

                    item {
                        ThemeRecipeSection(
                            draft = draft,
                            onDraftChange = { draft = it }
                        )
                    }

                    item {
                        ColorSection(
                            title = "Фон",
                            entries = listOf(
                                ColorEntry("Глубокий фон", draft.bgDeep) { draft = draft.copy(bgDeep = it) },
                                ColorEntry("Поверхность", draft.bgSurface) { draft = draft.copy(bgSurface = it) },
                                ColorEntry("Карточки", draft.bgCard) { draft = draft.copy(bgCard = it) },
                                ColorEntry("Приподнятый", draft.bgElevated) { draft = draft.copy(bgElevated = it) },
                            ),
                            onEditColor = { key, value ->
                                editingColorKey = key
                                editingColorValue = value
                                showColorEditor = true
                            }
                        )
                    }

                    item {
                        ColorSection(
                            title = "Акцент",
                            entries = listOf(
                                ColorEntry("Основной акцент", draft.accent) { draft = draft.copy(accent = it) },
                                ColorEntry("Вариант акцента", draft.accentVar) { draft = draft.copy(accentVar = it) },
                                ColorEntry("Приглушённый", draft.accentMuted) { draft = draft.copy(accentMuted = it) },
                            ),
                            onEditColor = { key, value ->
                                editingColorKey = key
                                editingColorValue = value
                                showColorEditor = true
                            }
                        )
                    }

                    item {
                        ColorSection(
                            title = "Текст",
                            entries = listOf(
                                ColorEntry("Основной текст", draft.textPrimary) { draft = draft.copy(textPrimary = it) },
                                ColorEntry("Второстепенный", draft.textSecondary) { draft = draft.copy(textSecondary = it) },
                                ColorEntry("Отключённый", draft.textDisabled) { draft = draft.copy(textDisabled = it) },
                                ColorEntry("Разделитель", draft.divider) { draft = draft.copy(divider = it) },
                            ),
                            onEditColor = { key, value ->
                                editingColorKey = key
                                editingColorValue = value
                                showColorEditor = true
                            }
                        )
                    }

                    item {
                        StyleSection(draft = draft, onDraftChange = { draft = it })
                    }

                    item {
                        ThemeEngineSection(draft = draft, onDraftChange = { draft = it })
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = c.bgCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (draft.isLightTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    null,
                                    tint = c.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Светлая тема",
                                        color = c.textPrimary,
                                        fontFamily = font,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Влияет на статус-бар и системные элементы",
                                        color = c.textSecondary,
                                        fontFamily = font,
                                        fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = draft.isLightTheme,
                                    onCheckedChange = { draft = draft.copy(isLightTheme = it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = c.bgDeep,
                                        checkedTrackColor = c.accent,
                                        uncheckedThumbColor = c.textDisabled,
                                        uncheckedTrackColor = c.bgElevated
                                    )
                                )
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { draft = CustomThemeColors() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Сбросить к стандартным", fontFamily = font)
                        }
                    }
                }
            }
        }
    }

    // ── Color editor dialog ───────────────────────────────────────────────────
    if (showColorEditor) {
        ColorEditorDialog(
            title        = editingColorKey,
            initialColor = editingColorValue,
            onColorChange = { newColor ->
                // Map key back to draft field
                draft = when (editingColorKey) {
                    "Глубокий фон"   -> draft.copy(bgDeep       = newColor)
                    "Поверхность"    -> draft.copy(bgSurface    = newColor)
                    "Карточки"       -> draft.copy(bgCard       = newColor)
                    "Приподнятый"    -> draft.copy(bgElevated   = newColor)
                    "Основной акцент"-> draft.copy(accent       = newColor)
                    "Вариант акцента"-> draft.copy(accentVar    = newColor)
                    "Приглушённый"   -> draft.copy(accentMuted  = newColor)
                    "Основной текст" -> draft.copy(textPrimary  = newColor)
                    "Второстепенный" -> draft.copy(textSecondary= newColor)
                    "Отключённый"    -> draft.copy(textDisabled = newColor)
                    "Разделитель"    -> draft.copy(divider      = newColor)
                    else             -> draft
                }
            },
            onDismiss = { showColorEditor = false }
        )
    }

    if (showFullPreview) {
        ThemePreviewDialog(
            draft = draft,
            onDismiss = { showFullPreview = false }
        )
    }
}

@Composable
private fun LivePreviewCard(
    draft: CustomThemeColors,
    expanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg     = draft.bgDeep.toComposeColor()
    val surface = draft.bgSurface.toComposeColor()
    val card   = draft.bgCard.toComposeColor()
    val elevated = draft.bgElevated.toComposeColor()
    val accent = draft.accent.toComposeColor()
    val text   = draft.textPrimary.toComposeColor()
    val sec    = draft.textSecondary.toComposeColor()
    val muted  = draft.accentMuted.toComposeColor()
    val accVar = draft.accentVar.toComposeColor()
    val font   = LocalAppFontFamily.current
    val cardCorner = (draft.cardCornerRadius + 8f).coerceIn(20f, 34f)
    val buttonCorner = draft.buttonCornerRadius.coerceIn(18f, 30f)
    val previewSurface = surface.copy(alpha = draft.surfaceAlpha.coerceIn(0.68f, 1f))
    val previewBorder = accent.copy(alpha = draft.surfaceBorderAlpha.coerceIn(0.06f, 0.45f))
    val artSize = if (expanded) 228.dp else 136.dp
    val contentSpacing = if (expanded) 14.dp else 8.dp
    val titleSize = if (expanded) 20.sp else 16.sp
    val subtitleSize = if (expanded) 13.sp else 11.sp
    val controlsHeight = if (expanded) 80.dp else 60.dp
    val activePrimaryContent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val activeSecondaryContent = if (accVar.luminance() > 0.5f) Color.Black else Color.White
    var previewPlaying by remember { mutableStateOf(true) }
    var previewShuffle by remember { mutableStateOf(false) }
    var previewRepeatMode by remember { mutableStateOf(RepeatMode.NONE) }
    var previewFavourite by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                previewBorder,
                RoundedCornerShape(cardCorner.dp)
            ),
        shape = RoundedCornerShape(cardCorner.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.background(
                if (draft.useGradientBg) {
                    Brush.verticalGradient(
                        listOf(
                            lerp(surface, accent, 0.10f),
                            card.copy(alpha = draft.surfaceAlpha.coerceIn(0.72f, 1f)),
                            bg,
                            lerp(bg, Color.Black, if (draft.isLightTheme) 0.02f else 0.16f)
                        )
                    )
                } else {
                    Brush.verticalGradient(listOf(previewSurface, previewSurface))
                }
            )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 10.dp)
                    .size(240.dp)
                    .blur(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = 52.dp)
                    .size(160.dp)
                    .blur(40.dp)
                    .clip(CircleShape)
                    .background(accVar.copy(alpha = 0.14f))
            )
            Column(
                modifier = Modifier.padding(
                    horizontal = if (expanded) 16.dp else 12.dp,
                    vertical = if (expanded) 14.dp else 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {},
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = elevated.copy(alpha = 0.72f),
                            contentColor = text
                        )
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = elevated.copy(alpha = 0.72f)
                        ) {
                            Text(
                                "Плеер",
                                color = accent,
                                fontFamily = font,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {},
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = elevated.copy(alpha = 0.72f),
                            contentColor = text
                        )
                    ) {
                        Icon(Icons.Rounded.MoreVert, null)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape((cardCorner * 0.8f).dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accent.copy(alpha = 0.28f),
                                    accVar.copy(alpha = 0.16f),
                                    card
                                )
                            )
                        )
                        .border(
                            1.dp,
                            previewBorder,
                            RoundedCornerShape((cardCorner * 0.8f).dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        accent.copy(alpha = 0.16f),
                                        card.copy(alpha = 0.10f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(if (expanded) 82.dp else 62.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(elevated.copy(alpha = 0.52f))
                            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Album,
                            null,
                            tint = accent,
                            modifier = Modifier.size(if (expanded) 34.dp else 28.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "After Hours",
                        color = text,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize
                    )
                    Text(
                        "The Weeknd",
                        color = sec,
                        fontFamily = font,
                        fontSize = subtitleSize
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("1:23", color = sec, fontFamily = font, fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        Text("3:45", color = sec, fontFamily = font, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(draft.sliderTrackHeight.dp.coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(50))
                            .background(muted.copy(alpha = 0.20f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.38f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                            .background(accent)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = if (expanded) 92.dp else 64.dp)
                                .size(if (expanded) 12.dp else 9.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                }

                AnimatedPlaybackControls(
                    isPlayingProvider = { previewPlaying },
                    onPrevious = {},
                    onPlayPause = { previewPlaying = !previewPlaying },
                    onNext = {},
                    modifier = Modifier.fillMaxWidth(),
                    height = controlsHeight,
                    colorPlayPause = accent,
                    tintPlayPauseIcon = activePrimaryContent,
                    colorOtherButtons = elevated.copy(alpha = if (expanded) 0.86f else 0.78f),
                    tintOtherIcons = text,
                    playPauseCornerPlaying = buttonCorner.dp.coerceIn(14.dp, 32.dp),
                    playPauseCornerPaused = (buttonCorner * 0.56f).dp.coerceIn(10.dp, 22.dp),
                    iconSize = if (expanded) 32.dp else 26.dp,
                    playPauseIconSize = if (expanded) 36.dp else 28.dp
                )

                if (expanded) {
                    PreviewBottomToggleRow(
                        shuffleEnabled = previewShuffle,
                        repeatMode = previewRepeatMode,
                        isFavourite = previewFavourite,
                        accent = accent,
                        accentVariant = accVar,
                        elevated = elevated,
                        text = text,
                        secondaryText = sec,
                        activePrimaryContent = activePrimaryContent,
                        activeSecondaryContent = activeSecondaryContent,
                        onShuffleToggle = { previewShuffle = !previewShuffle },
                        onRepeatToggle = {
                            previewRepeatMode = when (previewRepeatMode) {
                                RepeatMode.NONE -> RepeatMode.ALL
                                RepeatMode.ALL -> RepeatMode.ONE
                                RepeatMode.ONE -> RepeatMode.NONE
                            }
                        },
                        onFavouriteToggle = { previewFavourite = !previewFavourite }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PreviewMetricChip(
                            label = "Тон ${(draft.surfaceTintStrength * 100f).roundToInt()}%",
                            tint = accent,
                            font = font,
                            modifier = Modifier.weight(1f)
                        )
                        PreviewMetricChip(
                            label = "Глубина ${(draft.surfaceToneMix * 100f).roundToInt()}%",
                            tint = accVar,
                            font = font,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBottomToggleRow(
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isFavourite: Boolean,
    accent: Color,
    accentVariant: Color,
    elevated: Color,
    text: Color,
    secondaryText: Color,
    activePrimaryContent: Color,
    activeSecondaryContent: Color,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavouriteToggle: () -> Unit
) {
    val repeatIcon = when (repeatMode) {
        RepeatMode.NONE -> Icons.Rounded.Repeat
        RepeatMode.ALL -> Icons.Rounded.Repeat
        RepeatMode.ONE -> Icons.Rounded.RepeatOne
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(elevated.copy(alpha = 0.86f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ToggleSegmentButton(
                active = shuffleEnabled,
                icon = Icons.Rounded.Shuffle,
                contentDescription = "Перемешать",
                onClick = onShuffleToggle,
                activeColor = accent,
                activeContentColor = activePrimaryContent,
                inactiveColor = elevated.copy(alpha = 0.98f),
                inactiveContentColor = secondaryText,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ToggleSegmentButton(
                active = repeatMode != RepeatMode.NONE,
                icon = repeatIcon,
                contentDescription = "Повтор",
                onClick = onRepeatToggle,
                activeColor = accentVariant,
                activeContentColor = activeSecondaryContent,
                inactiveColor = elevated.copy(alpha = 0.98f),
                inactiveContentColor = secondaryText,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ToggleSegmentButton(
                active = isFavourite,
                icon = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Избранное",
                onClick = onFavouriteToggle,
                activeColor = text.copy(alpha = 0.92f),
                activeContentColor = bgContrastingColor(text),
                inactiveColor = elevated.copy(alpha = 0.98f),
                inactiveContentColor = secondaryText,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

private fun bgContrastingColor(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePreviewDialog(
    draft: CustomThemeColors,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val font = LocalAppFontFamily.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.background,
                            titleContentColor = colors.onSurface
                        ),
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = "Свернуть")
                            }
                        },
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Полный предпросмотр",
                                    fontFamily = font,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    "Здесь видно плеер целиком с теми же кнопками, что и в приложении",
                                    color = colors.onSurfaceVariant,
                                    fontFamily = font,
                                    fontSize = 11.sp
                                )
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LivePreviewCard(
                        draft = draft,
                        expanded = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewMetricChip(
    label: String,
    tint: Color,
    font: androidx.compose.ui.text.font.FontFamily?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = tint,
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

// ── Preset Row ────────────────────────────────────────────────────────────────
@Composable
private fun PresetRow(
    presets: List<ThemePreset>,
    onPresetSelected: (CustomThemeColors) -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(presets.size) { i ->
            val preset = presets[i]
            Surface(
                modifier = Modifier.clickable { onPresetSelected(preset.colors) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(preset.colors.accent.toComposeColor().copy(alpha = 0.16f))
                            .border(1.dp, preset.colors.accent.toComposeColor().copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = preset.icon,
                            contentDescription = preset.name,
                            tint = preset.colors.accent.toComposeColor(),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            preset.name,
                            color = c.textPrimary,
                            fontFamily = font,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Готовая палитра",
                            color = c.textSecondary,
                            fontFamily = font,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteActionsSection(
    draft: CustomThemeColors,
    onDraftChange: (CustomThemeColors) -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current

    fun softenBg(value: Long) = blendLong(value, Color.White, 0.08f)
    fun deepenBg(value: Long) = blendLong(value, Color.Black, 0.12f)
    fun warm(value: Long) = blendLong(value, Color(0xFFFFC36A), 0.18f)
    fun cool(value: Long) = blendLong(value, Color(0xFF8FD5FF), 0.18f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Быстрые правки",
            color = c.textSecondary,
            fontFamily = font,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickPaletteButton("Светлее фон", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        bgDeep = softenBg(draft.bgDeep),
                        bgSurface = softenBg(draft.bgSurface),
                        bgCard = softenBg(draft.bgCard),
                        bgElevated = softenBg(draft.bgElevated)
                    )
                )
            }
            QuickPaletteButton("Глубже фон", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        bgDeep = deepenBg(draft.bgDeep),
                        bgSurface = deepenBg(draft.bgSurface),
                        bgCard = deepenBg(draft.bgCard),
                        bgElevated = deepenBg(draft.bgElevated)
                    )
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickPaletteButton("Усилить акцент", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        accent = blendLong(draft.accent, Color.White, 0.1f),
                        accentVar = blendLong(draft.accentVar, Color.White, 0.08f),
                        accentMuted = blendLong(draft.accentMuted, Color(draft.accent.toInt()), 0.12f)
                    )
                )
            }
            QuickPaletteButton("Мягкий акцент", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        accent = blendLong(draft.accent, Color(draft.bgCard.toInt()), 0.18f),
                        accentVar = blendLong(draft.accentVar, Color(draft.bgCard.toInt()), 0.15f),
                        accentMuted = blendLong(draft.accentMuted, Color(draft.bgSurface.toInt()), 0.16f)
                    )
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickPaletteButton("Тёплый уклон", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        accent = warm(draft.accent),
                        accentVar = warm(draft.accentVar),
                        bgSurface = warm(draft.bgSurface),
                        bgCard = warm(draft.bgCard)
                    )
                )
            }
            QuickPaletteButton("Холодный уклон", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        accent = cool(draft.accent),
                        accentVar = cool(draft.accentVar),
                        bgSurface = cool(draft.bgSurface),
                        bgCard = cool(draft.bgCard)
                    )
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickPaletteButton("Чище текст", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        textPrimary = blendLong(draft.textPrimary, if (draft.isLightTheme) Color.Black else Color.White, 0.10f),
                        textSecondary = blendLong(draft.textSecondary, if (draft.isLightTheme) Color.Black else Color.White, 0.08f),
                        textDisabled = blendLong(draft.textDisabled, if (draft.isLightTheme) Color.Black else Color.White, 0.05f)
                    )
                )
            }
            QuickPaletteButton("Больше неона", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        accent = blendLong(draft.accent, Color.White, 0.16f),
                        accentVar = blendLong(draft.accentVar, Color.White, 0.18f),
                        accentMuted = blendLong(draft.accentMuted, draft.accent.toComposeColor(), 0.22f),
                        surfaceTintStrength = (draft.surfaceTintStrength + 0.08f).coerceAtMost(1.8f),
                        containerVibrance = (draft.containerVibrance + 0.08f).coerceAtMost(1.8f)
                    )
                )
            }
        }
    }
}

@Composable
private fun ThemeRecipeSection(
    draft: CustomThemeColors,
    onDraftChange: (CustomThemeColors) -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Рецепты атмосферы",
            color = c.textSecondary,
            fontFamily = font,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickPaletteButton("Студийное тепло", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        surfaceTintStrength = 1.12f,
                        surfaceToneMix = 1.18f,
                        containerVibrance = 1.06f,
                        buttonCornerRadius = 26f,
                        cardCornerRadius = 24f,
                        useGradientBg = true
                    )
                )
            }
            QuickPaletteButton("Воздушное стекло", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        surfaceAlpha = 0.78f,
                        surfaceBorderAlpha = 0.30f,
                        surfaceTintStrength = 1.42f,
                        sliderTrackHeight = 5f,
                        useGradientBg = true
                    )
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickPaletteButton("Моно люкс", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        accentVar = blendLong(draft.accent, Color.White, 0.04f),
                        accentMuted = blendLong(draft.bgCard, Color.White, 0.10f),
                        textSecondary = blendLong(draft.textPrimary, Color.Black, if (draft.isLightTheme) 0.18f else 0.08f),
                        surfaceTintStrength = 0.82f,
                        containerVibrance = 0.76f
                    )
                )
            }
            QuickPaletteButton("Ночной импульс", Modifier.weight(1f), c, font) {
                onDraftChange(
                    draft.copy(
                        bgDeep = blendLong(draft.bgDeep, Color.Black, 0.16f),
                        bgSurface = blendLong(draft.bgSurface, Color.Black, 0.12f),
                        accent = blendLong(draft.accent, Color.White, 0.12f),
                        accentVar = blendLong(draft.accentVar, Color.White, 0.14f),
                        surfaceTintStrength = 1.52f,
                        containerVibrance = 1.34f
                    )
                )
            }
        }
    }
}

@Composable
private fun QuickPaletteButton(
    label: String,
    modifier: Modifier = Modifier,
    c: com.musicplayer.ui.theme.AppColors,
    font: androidx.compose.ui.text.font.FontFamily?,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Text(label, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Color Section ─────────────────────────────────────────────────────────────
private data class ColorEntry(
    val label: String,
    val colorLong: Long,
    val onSet: (Long) -> Unit
)

@Composable
private fun ColorSection(
    title: String,
    entries: List<ColorEntry>,
    onEditColor: (key: String, value: Long) -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Column {
        Text(
            title,
            color = c.textSecondary,
            fontFamily = font,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                entries.forEachIndexed { idx, entry ->
                    ListItem(
                        modifier = Modifier.clickable { onEditColor(entry.label, entry.colorLong) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(entry.colorLong.toComposeColor())
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            )
                        },
                        headlineContent = {
                            Text(
                                entry.label,
                                color = c.textPrimary,
                                fontFamily = font,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supportingContent = {
                            Text(
                                "#%06X".format(entry.colorLong.and(0xFFFFFF)),
                                color = c.textSecondary,
                                fontFamily = font,
                                fontSize = 11.sp
                            )
                        },
                        trailingContent = {
                            Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    )
                    if (idx < entries.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Style Section ─────────────────────────────────────────────────────────────
@Composable
private fun StyleSection(
    draft: CustomThemeColors,
    onDraftChange: (CustomThemeColors) -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Column {
        Text(
            "Форма и базовый ритм",
            color = c.textSecondary,
            fontFamily = font,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Button corner radius
                SliderRow(
                    label    = "Скругление кнопок",
                    value    = draft.buttonCornerRadius,
                    range    = 0f..50f,
                    unit     = "dp",
                    preview  = {
                        Box(
                            Modifier
                                .size(width = 64.dp, height = 28.dp)
                                .clip(RoundedCornerShape(draft.buttonCornerRadius.dp))
                                .background(draft.accent.toComposeColor()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Кнопка", color = Color.White, fontFamily = font, fontSize = 9.sp)
                        }
                    },
                    onValueChange = { onDraftChange(draft.copy(buttonCornerRadius = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Card corner radius
                SliderRow(
                    label    = "Скругление карточек",
                    value    = draft.cardCornerRadius,
                    range    = 0f..32f,
                    unit     = "dp",
                    preview  = {
                        Box(
                            Modifier
                                .size(width = 48.dp, height = 36.dp)
                                .clip(RoundedCornerShape(draft.cardCornerRadius.dp))
                                .background(draft.bgElevated.toComposeColor())
                                .border(1.dp, draft.accent.toComposeColor().copy(alpha = 0.5f), RoundedCornerShape(draft.cardCornerRadius.dp))
                        )
                    },
                    onValueChange = { onDraftChange(draft.copy(cardCornerRadius = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Slider track height
                SliderRow(
                    label    = "Толщина ползунка",
                    value    = draft.sliderTrackHeight,
                    range    = 2f..12f,
                    unit     = "dp",
                    preview  = {
                        Box(
                            Modifier
                                .width(64.dp)
                                .height(draft.sliderTrackHeight.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(draft.accent.toComposeColor(), draft.accentVar.toComposeColor())
                                    )
                                )
                        )
                    },
                    onValueChange = { onDraftChange(draft.copy(sliderTrackHeight = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Gradient background toggle
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Градиентный фон",
                                color = c.textPrimary,
                                fontFamily = font,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                "Плавный переход в заголовке",
                                color = c.textSecondary,
                                fontFamily = font,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = draft.useGradientBg,
                            onCheckedChange = { onDraftChange(draft.copy(useGradientBg = it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeEngineSection(
    draft: CustomThemeColors,
    onDraftChange: (CustomThemeColors) -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Column {
        Text(
            "Глубина и поведение темы",
            color = c.textSecondary,
            fontFamily = font,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SliderRow(
                    label = "Прозрачность поверхностей",
                    value = draft.surfaceAlpha,
                    range = 0.68f..1f,
                    unit = "",
                    valueFormatter = { "${(it * 100f).roundToInt()}%" },
                    preview = {
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(draft.bgCard.toComposeColor().copy(alpha = draft.surfaceAlpha))
                                .border(1.dp, draft.accent.toComposeColor().copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                        )
                    },
                    onValueChange = { onDraftChange(draft.copy(surfaceAlpha = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SliderRow(
                    label = "Контраст рамок",
                    value = draft.surfaceBorderAlpha,
                    range = 0.06f..0.45f,
                    unit = "",
                    valueFormatter = { "${(it * 100f).roundToInt()}%" },
                    preview = {
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, draft.accent.toComposeColor().copy(alpha = draft.surfaceBorderAlpha), RoundedCornerShape(14.dp))
                        )
                    },
                    onValueChange = { onDraftChange(draft.copy(surfaceBorderAlpha = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SliderRow(
                    label = "Тонировка поверхностей",
                    value = draft.surfaceTintStrength,
                    range = 0.35f..1.8f,
                    unit = "",
                    valueFormatter = { "${(it * 100f).roundToInt()}%" },
                    preview = {
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    lerp(
                                        draft.bgSurface.toComposeColor(),
                                        draft.accent.toComposeColor(),
                                        ((draft.surfaceTintStrength - 0.35f) / 1.45f).coerceIn(0f, 1f) * 0.28f
                                    )
                                )
                        )
                    },
                    onValueChange = { onDraftChange(draft.copy(surfaceTintStrength = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SliderRow(
                    label = "Глубина фона",
                    value = draft.surfaceToneMix,
                    range = 0.4f..1.8f,
                    unit = "",
                    valueFormatter = { "${(it * 100f).roundToInt()}%" },
                    preview = {
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            draft.bgDeep.toComposeColor(),
                                            lerp(
                                                draft.bgSurface.toComposeColor(),
                                                draft.bgCard.toComposeColor(),
                                                ((draft.surfaceToneMix - 0.4f) / 1.4f).coerceIn(0f, 1f)
                                            )
                                        )
                                    )
                                )
                        )
                    },
                    onValueChange = { onDraftChange(draft.copy(surfaceToneMix = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SliderRow(
                    label = "Выразительность контейнеров",
                    value = draft.containerVibrance,
                    range = 0.4f..1.8f,
                    unit = "",
                    valueFormatter = { "${(it * 100f).roundToInt()}%" },
                    preview = {
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            draft.accent.toComposeColor().copy(alpha = 0.18f),
                                            draft.accentVar.toComposeColor().copy(alpha = 0.18f * draft.containerVibrance.coerceIn(0.4f, 1.8f))
                                        )
                                    )
                                )
                        )
                    },
                    onValueChange = { onDraftChange(draft.copy(containerVibrance = it)) }
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    preview: @Composable () -> Unit,
    valueFormatter: (Float) -> String = { "${it.roundToInt()}$unit" },
    onValueChange: (Float) -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = c.textPrimary,
                    fontFamily = font,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                preview()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = range,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    valueFormatter(value),
                    color = c.textSecondary,
                    fontFamily = font,
                    fontSize = 12.sp,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
    }
}

// ── Color Editor Dialog with RGB sliders ─────────────────────────────────────
@Composable
private fun ColorEditorDialog(
    title: String,
    initialColor: Long,
    onColorChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current

    var r by remember { mutableStateOf(initialColor.red().toFloat()) }
    var g by remember { mutableStateOf(initialColor.green().toFloat()) }
    var b by remember { mutableStateOf(initialColor.blue().toFloat()) }
    var a by remember { mutableStateOf(initialColor.alpha().toFloat()) }

    val currentColor = buildColor(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    val previewColor = currentColor.toComposeColor()

    // Harmony suggestions — complementary, analogous, triadic
    val hsvArr = FloatArray(3)
    android.graphics.Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsvArr)
    val harmonyColors = listOf(
        // Complementary
        buildHsvColor((hsvArr[0] + 180f) % 360f, hsvArr[1], hsvArr[2]),
        // Analogous +30
        buildHsvColor((hsvArr[0] + 30f) % 360f, hsvArr[1], hsvArr[2]),
        // Analogous -30
        buildHsvColor((hsvArr[0] - 30f + 360f) % 360f, hsvArr[1], hsvArr[2]),
        // Triadic
        buildHsvColor((hsvArr[0] + 120f) % 360f, hsvArr[1], hsvArr[2]),
        buildHsvColor((hsvArr[0] + 240f) % 360f, hsvArr[1], hsvArr[2]),
        // Lighter version
        buildHsvColor(hsvArr[0], (hsvArr[1] * 0.5f), minOf(hsvArr[2] * 1.4f, 1f)),
        // Darker version
        buildHsvColor(hsvArr[0], hsvArr[1], hsvArr[2] * 0.6f),
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape  = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title + preview
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = c.textPrimary,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(2.dp, c.divider, CircleShape)
                    )
                }

                // Hex display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "#%06X".format(currentColor.and(0xFFFFFF)),
                        color = previewColor,
                        fontFamily = font,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }

                // Hue spectrum bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                (0..12).map { i ->
                                    Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f)))
                                }
                            )
                        )
                )

                // RGB + Alpha sliders
                RgbSliderRow("R", r, Color(0xFFFF4444)) { r = it }
                RgbSliderRow("G", g, Color(0xFF44FF44)) { g = it }
                RgbSliderRow("B", b, Color(0xFF4444FF)) { b = it }
                RgbSliderRow("A", a, Color(0xFFCCCCCC)) { a = it }

                // Harmony suggestions
                Text(
                    "Гармоничные цвета",
                    color = c.textSecondary,
                    fontFamily = font,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    harmonyColors.forEach { hColor ->
                        val hCompose = hColor.toComposeColor()
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(hCompose)
                                .border(1.dp, c.divider, CircleShape)
                                .clickable {
                                    r = hColor.red().toFloat()
                                    g = hColor.green().toFloat()
                                    b = hColor.blue().toFloat()
                                }
                        )
                    }
                }

                // Brightness / Saturation quick controls
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Темнее" to { r *= 0.75f; g *= 0.75f; b *= 0.75f },
                        "Светлее" to {
                            r = minOf(r * 1.3f, 255f)
                            g = minOf(g * 1.3f, 255f)
                            b = minOf(b * 1.3f, 255f)
                        },
                        "Насыщ." to {
                            val arr2 = FloatArray(3)
                            android.graphics.Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), arr2)
                            arr2[1] = minOf(arr2[1] * 1.3f, 1f)
                            val px = android.graphics.Color.HSVToColor(arr2)
                            r = android.graphics.Color.red(px).toFloat()
                            g = android.graphics.Color.green(px).toFloat()
                            b = android.graphics.Color.blue(px).toFloat()
                        },
                    ).forEach { (label, action) ->
                        OutlinedButton(
                            onClick = action,
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text(label, fontFamily = font, fontSize = 11.sp)
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Отмена", fontFamily = font)
                    }
                    Button(
                        onClick = {
                            onColorChange(currentColor)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = previewColor,
                            contentColor   = if (previewColor.luminance() > 0.5f) Color.Black else Color.White
                        )
                    ) {
                        Text("Выбрать", fontFamily = font)
                    }
                }
            }
        }
    }
}

@Composable
private fun RgbSliderRow(
    label: String,
    value: Float,
    trackColor: Color,
    onValueChange: (Float) -> Unit
) {
    val c = LocalAppColors.current
    val font = LocalAppFontFamily.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = trackColor,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.width(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..255f,
                colors = SliderDefaults.colors(
                    thumbColor = trackColor,
                    activeTrackColor = trackColor,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                value.roundToInt().toString(),
                color = c.textSecondary,
                fontFamily = font,
                fontSize = 12.sp,
                modifier = Modifier.width(32.dp)
            )
        }
    }
}

private fun buildHsvColor(h: Float, s: Float, v: Float): Long {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val r = android.graphics.Color.red(argb)
    val g = android.graphics.Color.green(argb)
    val b = android.graphics.Color.blue(argb)
    return buildColor(255, r, g, b)
}
