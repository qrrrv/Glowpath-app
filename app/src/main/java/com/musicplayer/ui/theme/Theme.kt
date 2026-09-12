package com.musicplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes as MaterialShapes
import androidx.compose.runtime.remember
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.musicplayer.data.AppTheme
import com.musicplayer.data.InterfaceStyle

// CompositionLocal для доступа к цветам текущей темы везде в приложении
data class AppColors(
    val bgDeep: Color,
    val bgSurface: Color,
    val bgCard: Color,
    val bgElevated: Color,
    val accent: Color,
    val accentVar: Color,
    val accentMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val divider: Color
)

// Compatibility bridge for old screens that still reference bgCard/accent/textPrimary names.
// The source of truth is Material 3 ColorScheme; these aliases keep migration incremental.
val ColorScheme.bgDeep: Color
    get() = background

val ColorScheme.bgSurface: Color
    get() = surface

val ColorScheme.bgCard: Color
    get() = surfaceContainerLow

val ColorScheme.bgElevated: Color
    get() = surfaceContainerHigh

val ColorScheme.accent: Color
    get() = primary

val ColorScheme.accentVar: Color
    get() = secondary

val ColorScheme.accentMuted: Color
    get() = tertiary

val ColorScheme.textPrimary: Color
    get() = onSurface

val ColorScheme.textSecondary: Color
    get() = onSurfaceVariant

val ColorScheme.textDisabled: Color
    get() = onSurfaceVariant.copy(alpha = 0.72f)

val ColorScheme.divider: Color
    get() = outlineVariant

// CompositionLocal для стиля (радиусы, толщина ползунка) из CustomThemeColors
data class AppStyle(
    val interfaceStyle: InterfaceStyle = InterfaceStyle.MATERIAL3,
    val buttonCornerRadius: Float = 20f,
    val cardCornerRadius: Float   = 20f,
    val sliderTrackHeight: Float  = 4f,
    val surfaceAlpha: Float       = 1f,
    val surfaceBorderAlpha: Float = 0.18f,
    val glassHighlightAlpha: Float = 0f
)

val LocalAppColors = compositionLocalOf {
    AppColors(
        bgDeep       = BrownDeep,       bgSurface   = BrownSurface,
        bgCard       = BrownCard,       bgElevated  = BrownElevated,
        accent       = AccentPeach,     accentVar   = AccentAmber,
        accentMuted  = AccentMuted,
        textPrimary  = TextPrimary,     textSecondary = TextSecondary,
        textDisabled = TextDisabled,    divider     = DividerColor
    )
}

val LocalAppStyle = compositionLocalOf { AppStyle() }

private fun Color.blendWith(target: Color, amount: Float): Color =
    lerp(this, target, amount.coerceIn(0f, 1f))

private fun readableOn(color: Color): Color =
    if (color.luminance() > 0.52f) Color.Black else Color.White

private fun AppColors.withInterfaceStyle(
    interfaceStyle: InterfaceStyle,
    isLight: Boolean
): AppColors = when (interfaceStyle) {
    InterfaceStyle.CLASSIC -> this
    InterfaceStyle.MATERIAL3 -> copy(
        bgSurface = bgSurface.blendWith(accent, if (isLight) 0.05f else 0.09f),
        bgCard = bgCard.blendWith(accentVar, if (isLight) 0.08f else 0.13f),
        bgElevated = bgElevated.blendWith(accent, if (isLight) 0.11f else 0.17f),
        accentMuted = accentMuted.blendWith(accent, 0.16f),
        textSecondary = textSecondary.blendWith(accent, 0.12f),
        divider = divider.blendWith(accent, 0.18f)
    )
    InterfaceStyle.LIQUID_GLASS -> copy(
        bgSurface = bgSurface.blendWith(Color.White, if (isLight) 0.12f else 0.05f).copy(alpha = if (isLight) 0.96f else 0.9f),
        bgCard = bgCard.blendWith(Color.White, if (isLight) 0.18f else 0.08f).copy(alpha = if (isLight) 0.9f else 0.82f),
        bgElevated = bgElevated.blendWith(accent, if (isLight) 0.18f else 0.12f).copy(alpha = if (isLight) 0.96f else 0.9f),
        accent = accent.blendWith(Color.White, 0.08f),
        accentVar = accentVar.blendWith(Color.White, 0.12f),
        accentMuted = accentMuted.blendWith(accent, 0.22f),
        textSecondary = textSecondary.blendWith(Color.White, if (isLight) 0.02f else 0.08f),
        divider = accent.blendWith(Color.White, 0.18f).copy(alpha = if (isLight) 0.22f else 0.3f)
    )
}

private fun appStyleFor(
    interfaceStyle: InterfaceStyle,
    customColors: com.musicplayer.data.CustomThemeColors? = null
): AppStyle {
    val preset = when (interfaceStyle) {
        InterfaceStyle.CLASSIC -> AppStyle(
            interfaceStyle = interfaceStyle,
            buttonCornerRadius = 50f,
            cardCornerRadius = 16f,
            sliderTrackHeight = 4f,
            surfaceAlpha = 1f,
            surfaceBorderAlpha = 0.18f,
            glassHighlightAlpha = 0f
        )
        InterfaceStyle.MATERIAL3 -> AppStyle(
            interfaceStyle = interfaceStyle,
            buttonCornerRadius = 20f,
            cardCornerRadius = 20f,
            sliderTrackHeight = 4f,
            surfaceAlpha = 1f,
            surfaceBorderAlpha = 0.12f,
            glassHighlightAlpha = 0f
        )
        InterfaceStyle.LIQUID_GLASS -> AppStyle(
            interfaceStyle = interfaceStyle,
            buttonCornerRadius = 28f,
            cardCornerRadius = 28f,
            sliderTrackHeight = 6f,
            surfaceAlpha = 0.86f,
            surfaceBorderAlpha = 0.28f,
            glassHighlightAlpha = 0.2f
        )
    }

    return if (customColors != null) {
        preset.copy(
            buttonCornerRadius = customColors.buttonCornerRadius,
            cardCornerRadius = customColors.cardCornerRadius,
            sliderTrackHeight = customColors.sliderTrackHeight,
            surfaceAlpha = customColors.surfaceAlpha,
            surfaceBorderAlpha = customColors.surfaceBorderAlpha
        )
    } else preset
}

private fun shapesForInterfaceStyle(
    interfaceStyle: InterfaceStyle,
    customColors: com.musicplayer.data.CustomThemeColors? = null
): MaterialShapes {
    val baseCorner = when (interfaceStyle) {
        InterfaceStyle.CLASSIC -> 16f
        InterfaceStyle.MATERIAL3 -> 18f
        InterfaceStyle.LIQUID_GLASS -> 26f
    }
    val mediumCorner = customColors?.cardCornerRadius ?: baseCorner
    val largeCorner = (mediumCorner + 8f).coerceAtMost(38f)
    val extraLargeCorner = (mediumCorner + 14f).coerceAtMost(46f)
    return MaterialShapes(
        extraSmall = RoundedCornerShape((mediumCorner * 0.35f).coerceAtLeast(6f).dp),
        small = RoundedCornerShape((mediumCorner * 0.55f).coerceAtLeast(10f).dp),
        medium = RoundedCornerShape(mediumCorner.dp),
        large = RoundedCornerShape(largeCorner.dp),
        extraLarge = RoundedCornerShape(extraLargeCorner.dp)
    )
}

fun appColorsForTheme(
    theme: AppTheme,
    customColors: com.musicplayer.data.CustomThemeColors? = null
): AppColors = when (theme) {
    AppTheme.MATERIAL_YOU -> AppColors(
        bgDeep       = LightBg,         bgSurface   = LightSurface,
        bgCard       = LightCard,       bgElevated  = LightElevated,
        accent       = LightAccent,     accentVar   = LightAccentVar,
        accentMuted  = LightAccentMuted,
        textPrimary  = LightTextPrimary, textSecondary = LightTextSecondary,
        textDisabled = LightTextDisabled, divider    = LightCard
    )
    AppTheme.BLOOMEE -> AppColors(
        bgDeep       = BloomeeDeep,       bgSurface   = BloomeeSurface,
        bgCard       = BloomeeCard,       bgElevated  = BloomeeElevated,
        accent       = BloomeeAccent,     accentVar   = BloomeeAccentVar,
        accentMuted  = BloomeeAccentMuted,
        textPrimary  = BloomeeTextPrimary, textSecondary = BloomeeTextSecondary,
        textDisabled = BloomeeTextDisabled, divider  = BloomeeElevated
    )
    AppTheme.DARK_BROWN -> AppColors(
        bgDeep       = BrownDeep,       bgSurface   = BrownSurface,
        bgCard       = BrownCard,       bgElevated  = BrownElevated,
        accent       = AccentPeach,     accentVar   = AccentAmber,
        accentMuted  = AccentMuted,
        textPrimary  = TextPrimary,     textSecondary = TextSecondary,
        textDisabled = TextDisabled,    divider     = DividerColor
    )
    AppTheme.DARK_BLACK -> AppColors(
        bgDeep       = BlackDeep,       bgSurface   = BlackSurface,
        bgCard       = BlackCard,       bgElevated  = BlackElevated,
        accent       = BlackAccent,     accentVar   = BlackAccentVar,
        accentMuted  = BlackAccentMuted,
        textPrimary  = BlackTextPrimary, textSecondary = BlackTextSecondary,
        textDisabled = BlackTextDisabled, divider    = BlackCard
    )
    AppTheme.LIGHT -> AppColors(
        bgDeep       = LightBg,         bgSurface   = LightSurface,
        bgCard       = LightCard,       bgElevated  = LightElevated,
        accent       = LightAccent,     accentVar   = LightAccentVar,
        accentMuted  = LightAccentMuted,
        textPrimary  = LightTextPrimary, textSecondary = LightTextSecondary,
        textDisabled = LightTextDisabled, divider    = LightCard
    )
    AppTheme.PURPLE -> AppColors(
        bgDeep       = PurpleDeep,      bgSurface   = PurpleSurface,
        bgCard       = PurpleCard,      bgElevated  = PurpleElevated,
        accent       = PurpleAccent,    accentVar   = PurpleAccentVar,
        accentMuted  = PurpleAccentMuted,
        textPrimary  = PurpleTextPrimary, textSecondary = PurpleTextSecondary,
        textDisabled = PurpleTextDisabled, divider   = PurpleCard
    )
    AppTheme.PINK -> AppColors(
        bgDeep       = PinkDeep,        bgSurface   = PinkSurface,
        bgCard       = PinkCard,        bgElevated  = PinkElevated,
        accent       = PinkAccent,      accentVar   = PinkAccentVar,
        accentMuted  = PinkAccentMuted,
        textPrimary  = PinkTextPrimary, textSecondary = PinkTextSecondary,
        textDisabled = PinkTextDisabled, divider    = PinkCard
    )
    AppTheme.OCEAN -> AppColors(
        bgDeep       = OceanDeep,       bgSurface   = OceanSurface,
        bgCard       = OceanCard,       bgElevated  = OceanElevated,
        accent       = OceanAccent,     accentVar   = OceanAccentVar,
        accentMuted  = OceanAccentMuted,
        textPrimary  = OceanTextPrimary, textSecondary = OceanTextSecondary,
        textDisabled = OceanTextDisabled, divider   = OceanCard
    )
    AppTheme.FOREST -> AppColors(
        bgDeep       = ForestDeep,      bgSurface   = ForestSurface,
        bgCard       = ForestCard,      bgElevated  = ForestElevated,
        accent       = ForestAccent,    accentVar   = ForestAccentVar,
        accentMuted  = ForestAccentMuted,
        textPrimary  = ForestTextPrimary, textSecondary = ForestTextSecondary,
        textDisabled = ForestTextDisabled, divider  = ForestCard
    )
    AppTheme.SUNSET -> AppColors(
        bgDeep       = SunsetDeep,      bgSurface   = SunsetSurface,
        bgCard       = SunsetCard,      bgElevated  = SunsetElevated,
        accent       = SunsetAccent,    accentVar   = SunsetAccentVar,
        accentMuted  = SunsetAccentMuted,
        textPrimary  = SunsetTextPrimary, textSecondary = SunsetTextSecondary,
        textDisabled = SunsetTextDisabled, divider  = SunsetCard
    )
    AppTheme.MIDNIGHT -> AppColors(
        bgDeep       = MidnightDeep,    bgSurface   = MidnightSurface,
        bgCard       = MidnightCard,    bgElevated  = MidnightElevated,
        accent       = MidnightAccent,  accentVar   = MidnightAccentVar,
        accentMuted  = MidnightAccentMuted,
        textPrimary  = MidnightTextPrimary, textSecondary = MidnightTextSecondary,
        textDisabled = MidnightTextDisabled, divider = MidnightCard
    )
    // ── New themes ────────────────────────────────────────────────────────
    AppTheme.NEON -> AppColors(
        bgDeep       = NeonDeep,        bgSurface   = NeonSurface,
        bgCard       = NeonCard,        bgElevated  = NeonElevated,
        accent       = NeonAccent,      accentVar   = NeonAccentVar,
        accentMuted  = NeonAccentMuted,
        textPrimary  = NeonTextPrimary, textSecondary = NeonTextSecondary,
        textDisabled = NeonTextDisabled, divider    = NeonCard
    )
    AppTheme.ROSE_GOLD -> AppColors(
        bgDeep       = RoseGoldDeep,    bgSurface   = RoseGoldSurface,
        bgCard       = RoseGoldCard,    bgElevated  = RoseGoldElevated,
        accent       = RoseGoldAccent,  accentVar   = RoseGoldAccentVar,
        accentMuted  = RoseGoldAccentMuted,
        textPrimary  = RoseGoldTextPrimary, textSecondary = RoseGoldTextSecondary,
        textDisabled = RoseGoldTextDisabled, divider = RoseGoldCard
    )
    AppTheme.ARCTIC -> AppColors(
        bgDeep       = ArcticDeep,      bgSurface   = ArcticSurface,
        bgCard       = ArcticCard,      bgElevated  = ArcticElevated,
        accent       = ArcticAccent,    accentVar   = ArcticAccentVar,
        accentMuted  = ArcticAccentMuted,
        textPrimary  = ArcticTextPrimary, textSecondary = ArcticTextSecondary,
        textDisabled = ArcticTextDisabled, divider  = ArcticCard
    )
    AppTheme.AMBER -> AppColors(
        bgDeep       = AmberDeep,       bgSurface   = AmberSurface,
        bgCard       = AmberCard,       bgElevated  = AmberElevated,
        accent       = AmberAccent,     accentVar   = AmberAccentVar,
        accentMuted  = AmberAccentMuted,
        textPrimary  = AmberTextPrimary, textSecondary = AmberTextSecondary,
        textDisabled = AmberTextDisabled, divider   = AmberCard
    )
    AppTheme.EMERALD -> AppColors(
        bgDeep       = EmeraldDeep,     bgSurface   = EmeraldSurface,
        bgCard       = EmeraldCard,     bgElevated  = EmeraldElevated,
        accent       = EmeraldAccent,   accentVar   = EmeraldAccentVar,
        accentMuted  = EmeraldAccentMuted,
        textPrimary  = EmeraldTextPrimary, textSecondary = EmeraldTextSecondary,
        textDisabled = EmeraldTextDisabled, divider = EmeraldCard
    )
    AppTheme.AMOLED -> AppColors(
        bgDeep       = AmoledDeep,      bgSurface   = AmoledSurface,
        bgCard       = AmoledCard,      bgElevated  = AmoledElevated,
        accent       = AmoledAccent,    accentVar   = AmoledAccentVar,
        accentMuted  = AmoledAccentMuted,
        textPrimary  = AmoledTextPrimary, textSecondary = AmoledTextSecondary,
        textDisabled = AmoledTextDisabled, divider  = AmoledCard
    )
    AppTheme.LAVENDER -> AppColors(
        bgDeep       = LavenderDeep,    bgSurface   = LavenderSurface,
        bgCard       = LavenderCard,    bgElevated  = LavenderElevated,
        accent       = LavenderAccent,  accentVar   = LavenderAccentVar,
        accentMuted  = LavenderAccentMuted,
        textPrimary  = LavenderTextPrimary, textSecondary = LavenderTextSecondary,
        textDisabled = LavenderTextDisabled, divider = LavenderCard
    )
    AppTheme.RUBY -> AppColors(
        bgDeep       = RubyDeep,        bgSurface   = RubySurface,
        bgCard       = RubyCard,        bgElevated  = RubyElevated,
        accent       = RubyAccent,      accentVar   = RubyAccentVar,
        accentMuted  = RubyAccentMuted,
        textPrimary  = RubyTextPrimary, textSecondary = RubyTextSecondary,
        textDisabled = RubyTextDisabled, divider = RubyCard
    )
    AppTheme.STEEL -> AppColors(
        bgDeep       = SteelDeep,       bgSurface   = SteelSurface,
        bgCard       = SteelCard,       bgElevated  = SteelElevated,
        accent       = SteelAccent,     accentVar   = SteelAccentVar,
        accentMuted  = SteelAccentMuted,
        textPrimary  = SteelTextPrimary, textSecondary = SteelTextSecondary,
        textDisabled = SteelTextDisabled, divider = SteelCard
    )
    AppTheme.MATCHA -> AppColors(
        bgDeep       = MatchaDeep,      bgSurface   = MatchaSurface,
        bgCard       = MatchaCard,      bgElevated  = MatchaElevated,
        accent       = MatchaAccent,    accentVar   = MatchaAccentVar,
        accentMuted  = MatchaAccentMuted,
        textPrimary  = MatchaTextPrimary, textSecondary = MatchaTextSecondary,
        textDisabled = MatchaTextDisabled, divider = MatchaCard
    )
    AppTheme.DESERT -> AppColors(
        bgDeep       = DesertDeep,      bgSurface   = DesertSurface,
        bgCard       = DesertCard,      bgElevated  = DesertElevated,
        accent       = DesertAccent,    accentVar   = DesertAccentVar,
        accentMuted  = DesertAccentMuted,
        textPrimary  = DesertTextPrimary, textSecondary = DesertTextSecondary,
        textDisabled = DesertTextDisabled, divider = DesertCard
    )
    AppTheme.COBALT -> AppColors(
        bgDeep       = CobaltDeep,      bgSurface   = CobaltSurface,
        bgCard       = CobaltCard,      bgElevated  = CobaltElevated,
        accent       = CobaltAccent,    accentVar   = CobaltAccentVar,
        accentMuted  = CobaltAccentMuted,
        textPrimary  = CobaltTextPrimary, textSecondary = CobaltTextSecondary,
        textDisabled = CobaltTextDisabled, divider = CobaltCard
    )
    AppTheme.CHERRY -> AppColors(
        bgDeep       = CherryDeep,      bgSurface   = CherrySurface,
        bgCard       = CherryCard,      bgElevated  = CherryElevated,
        accent       = CherryAccent,    accentVar   = CherryAccentVar,
        accentMuted  = CherryAccentMuted,
        textPrimary  = CherryTextPrimary, textSecondary = CherryTextSecondary,
        textDisabled = CherryTextDisabled, divider = CherryCard
    )
    AppTheme.MOCHA -> AppColors(
        bgDeep       = MochaDeep,       bgSurface   = MochaSurface,
        bgCard       = MochaCard,       bgElevated  = MochaElevated,
        accent       = MochaAccent,     accentVar   = MochaAccentVar,
        accentMuted  = MochaAccentMuted,
        textPrimary  = MochaTextPrimary, textSecondary = MochaTextSecondary,
        textDisabled = MochaTextDisabled, divider = MochaCard
    )
    AppTheme.AURORA -> AppColors(
        bgDeep       = AuroraDeep,      bgSurface   = AuroraSurface,
        bgCard       = AuroraCard,      bgElevated  = AuroraElevated,
        accent       = AuroraAccent,    accentVar   = AuroraAccentVar,
        accentMuted  = AuroraAccentMuted,
        textPrimary  = AuroraTextPrimary, textSecondary = AuroraTextSecondary,
        textDisabled = AuroraTextDisabled, divider = AuroraCard
    )
    AppTheme.COSMOS -> AppColors(
        bgDeep       = CosmosDeep,      bgSurface   = CosmosSurface,
        bgCard       = CosmosCard,      bgElevated  = CosmosElevated,
        accent       = CosmosAccent,    accentVar   = CosmosAccentVar,
        accentMuted  = CosmosAccentMuted,
        textPrimary  = CosmosTextPrimary, textSecondary = CosmosTextSecondary,
        textDisabled = CosmosTextDisabled, divider = CosmosCard
    )
    AppTheme.SUNRISE -> AppColors(
        bgDeep       = SunriseDeep,     bgSurface   = SunriseSurface,
        bgCard       = SunriseCard,     bgElevated  = SunriseElevated,
        accent       = SunriseAccent,   accentVar   = SunriseAccentVar,
        accentMuted  = SunriseAccentMuted,
        textPrimary  = SunriseTextPrimary, textSecondary = SunriseTextSecondary,
        textDisabled = SunriseTextDisabled, divider = SunriseCard
    )
    AppTheme.GRAPHITE -> AppColors(
        bgDeep       = GraphiteDeep,    bgSurface   = GraphiteSurface,
        bgCard       = GraphiteCard,    bgElevated  = GraphiteElevated,
        accent       = GraphiteAccent,  accentVar   = GraphiteAccentVar,
        accentMuted  = GraphiteAccentMuted,
        textPrimary  = GraphiteTextPrimary, textSecondary = GraphiteTextSecondary,
        textDisabled = GraphiteTextDisabled, divider = GraphiteCard
    )
    AppTheme.SAKURA -> AppColors(
        bgDeep       = SakuraDeep,      bgSurface   = SakuraSurface,
        bgCard       = SakuraCard,      bgElevated  = SakuraElevated,
        accent       = SakuraAccent,    accentVar   = SakuraAccentVar,
        accentMuted  = SakuraAccentMuted,
        textPrimary  = SakuraTextPrimary, textSecondary = SakuraTextSecondary,
        textDisabled = SakuraTextDisabled, divider = SakuraCard
    )
    AppTheme.LAGOON -> AppColors(
        bgDeep       = LagoonDeep,      bgSurface   = LagoonSurface,
        bgCard       = LagoonCard,      bgElevated  = LagoonElevated,
        accent       = LagoonAccent,    accentVar   = LagoonAccentVar,
        accentMuted  = LagoonAccentMuted,
        textPrimary  = LagoonTextPrimary, textSecondary = LagoonTextSecondary,
        textDisabled = LagoonTextDisabled, divider = LagoonCard
    )
    AppTheme.VOLCANO -> AppColors(
        bgDeep       = VolcanoDeep,     bgSurface   = VolcanoSurface,
        bgCard       = VolcanoCard,     bgElevated  = VolcanoElevated,
        accent       = VolcanoAccent,   accentVar   = VolcanoAccentVar,
        accentMuted  = VolcanoAccentMuted,
        textPrimary  = VolcanoTextPrimary, textSecondary = VolcanoTextSecondary,
        textDisabled = VolcanoTextDisabled, divider = VolcanoCard
    )
    AppTheme.IVORY -> AppColors(
        bgDeep = Color(0xFFF2E7D6),
        bgSurface = Color(0xFFFBF4EA),
        bgCard = Color(0xFFFFFFFF),
        bgElevated = Color(0xFFF6EBDD),
        accent = Color(0xFFB06A3D),
        accentVar = Color(0xFFD48B4A),
        accentMuted = Color(0xFFC9A17E),
        textPrimary = Color(0xFF38261A),
        textSecondary = Color(0xFF6E5140),
        textDisabled = Color(0xFF9C8778),
        divider = Color(0xFFE7D8C8)
    )
    AppTheme.CITRUS -> AppColors(
        bgDeep = Color(0xFF101A10),
        bgSurface = Color(0xFF172416),
        bgCard = Color(0xFF1F311C),
        bgElevated = Color(0xFF284027),
        accent = Color(0xFFFFD54F),
        accentVar = Color(0xFF9BE15D),
        accentMuted = Color(0xFF6E8F42),
        textPrimary = Color(0xFFF8FFE8),
        textSecondary = Color(0xFFD4E6AA),
        textDisabled = Color(0xFF6F8461),
        divider = Color(0xFF223222)
    )
    AppTheme.FROST -> AppColors(
        bgDeep = Color(0xFF08131A),
        bgSurface = Color(0xFF10202A),
        bgCard = Color(0xFF17303C),
        bgElevated = Color(0xFF21414F),
        accent = Color(0xFF7FE7FF),
        accentVar = Color(0xFFB8F4FF),
        accentMuted = Color(0xFF53899A),
        textPrimary = Color(0xFFF0FDFF),
        textSecondary = Color(0xFFC9E9EE),
        textDisabled = Color(0xFF688792),
        divider = Color(0xFF17303C)
    )
    AppTheme.EMBER -> AppColors(
        bgDeep = Color(0xFF180C08),
        bgSurface = Color(0xFF24130E),
        bgCard = Color(0xFF331B14),
        bgElevated = Color(0xFF45241A),
        accent = Color(0xFFFF8E5A),
        accentVar = Color(0xFFFFC16A),
        accentMuted = Color(0xFF8D553B),
        textPrimary = Color(0xFFFFF1EA),
        textSecondary = Color(0xFFFFD1B5),
        textDisabled = Color(0xFF7D6255),
        divider = Color(0xFF331B14)
    )
    AppTheme.TURQUOISE -> AppColors(
        bgDeep = Color(0xFF071617),
        bgSurface = Color(0xFF0C2325),
        bgCard = Color(0xFF133235),
        bgElevated = Color(0xFF1A4347),
        accent = Color(0xFF4EE4D5),
        accentVar = Color(0xFF7EDBFF),
        accentMuted = Color(0xFF4A8D90),
        textPrimary = Color(0xFFEFFFFD),
        textSecondary = Color(0xFFB7F0EA),
        textDisabled = Color(0xFF648689),
        divider = Color(0xFF133235)
    )
    AppTheme.PLUM -> AppColors(
        bgDeep = Color(0xFF130916),
        bgSurface = Color(0xFF1F1025),
        bgCard = Color(0xFF2D1836),
        bgElevated = Color(0xFF3C2149),
        accent = Color(0xFFD68BFF),
        accentVar = Color(0xFFFF9AD7),
        accentMuted = Color(0xFF84589D),
        textPrimary = Color(0xFFFBF1FF),
        textSecondary = Color(0xFFE2BEF5),
        textDisabled = Color(0xFF756282),
        divider = Color(0xFF2D1836)
    )
    AppTheme.PEARL -> AppColors(
        bgDeep = Color(0xFFF5EEE8),
        bgSurface = Color(0xFFFCF7F2),
        bgCard = Color(0xFFFFFFFF),
        bgElevated = Color(0xFFF3ECE6),
        accent = Color(0xFF8E6FD1),
        accentVar = Color(0xFFCE8CA9),
        accentMuted = Color(0xFFC8B9D6),
        textPrimary = Color(0xFF332A34),
        textSecondary = Color(0xFF695B70),
        textDisabled = Color(0xFF9A8B9E),
        divider = Color(0xFFE5DBE3)
    )
    AppTheme.OLIVE -> AppColors(
        bgDeep = Color(0xFF11150A),
        bgSurface = Color(0xFF1A2010),
        bgCard = Color(0xFF252D17),
        bgElevated = Color(0xFF313C1F),
        accent = Color(0xFFC3D96B),
        accentVar = Color(0xFFE3C76C),
        accentMuted = Color(0xFF7A8B47),
        textPrimary = Color(0xFFF8FCE8),
        textSecondary = Color(0xFFD8E0B0),
        textDisabled = Color(0xFF76805E),
        divider = Color(0xFF252D17)
    )
    AppTheme.SAPPHIRE -> AppColors(
        bgDeep = Color(0xFF07111D),
        bgSurface = Color(0xFF0D1A2C),
        bgCard = Color(0xFF13253E),
        bgElevated = Color(0xFF1B3152),
        accent = Color(0xFF77B8FF),
        accentVar = Color(0xFFA1D7FF),
        accentMuted = Color(0xFF4C6F96),
        textPrimary = Color(0xFFF1F7FF),
        textSecondary = Color(0xFFC6DCF8),
        textDisabled = Color(0xFF6D7F96),
        divider = Color(0xFF13253E)
    )
    AppTheme.MINT -> AppColors(
        bgDeep = Color(0xFF081814),
        bgSurface = Color(0xFF0F2620),
        bgCard = Color(0xFF15352D),
        bgElevated = Color(0xFF1D473D),
        accent = Color(0xFF73F1C4),
        accentVar = Color(0xFFAAF6E0),
        accentMuted = Color(0xFF4E8D7B),
        textPrimary = Color(0xFFF0FFF9),
        textSecondary = Color(0xFFC8F0E3),
        textDisabled = Color(0xFF6E8F86),
        divider = Color(0xFF15352D)
    )
    AppTheme.BRONZE -> AppColors(
        bgDeep = Color(0xFF170C08),
        bgSurface = Color(0xFF24140E),
        bgCard = Color(0xFF341D14),
        bgElevated = Color(0xFF45271B),
        accent = Color(0xFFC9874A),
        accentVar = Color(0xFFE7B16E),
        accentMuted = Color(0xFF8E613D),
        textPrimary = Color(0xFFFFF2E8),
        textSecondary = Color(0xFFE7C6A8),
        textDisabled = Color(0xFF826756),
        divider = Color(0xFF341D14)
    )
    AppTheme.WINE -> AppColors(
        bgDeep = Color(0xFF16070D),
        bgSurface = Color(0xFF240D16),
        bgCard = Color(0xFF341320),
        bgElevated = Color(0xFF461C2B),
        accent = Color(0xFFF0678E),
        accentVar = Color(0xFFFFA5B9),
        accentMuted = Color(0xFF905066),
        textPrimary = Color(0xFFFFF0F4),
        textSecondary = Color(0xFFF0C7D2),
        textDisabled = Color(0xFF86646F),
        divider = Color(0xFF341320)
    )
    AppTheme.CUSTOM -> {
        val cc = customColors ?: com.musicplayer.data.CustomThemeColors()
        AppColors(
            bgDeep        = Color(cc.bgDeep),
            bgSurface     = Color(cc.bgSurface),
            bgCard        = Color(cc.bgCard),
            bgElevated    = Color(cc.bgElevated),
            accent        = Color(cc.accent),
            accentVar     = Color(cc.accentVar),
            accentMuted   = Color(cc.accentMuted),
            textPrimary   = Color(cc.textPrimary),
            textSecondary = Color(cc.textSecondary),
            textDisabled  = Color(cc.textDisabled),
            divider       = Color(cc.divider)
        )
    }
}

private fun buildMaterialColorScheme(
    colors: AppColors,
    isLight: Boolean,
    interfaceStyle: InterfaceStyle,
    surfaceTintStrength: Float = 1f,
    surfaceToneMix: Float = 1f,
    containerVibrance: Float = 1f
) : ColorScheme = run {
    val tintAmount = when (interfaceStyle) {
        InterfaceStyle.CLASSIC -> if (isLight) 0.03f else 0.06f
        InterfaceStyle.MATERIAL3 -> if (isLight) 0.05f else 0.10f
        InterfaceStyle.LIQUID_GLASS -> if (isLight) 0.07f else 0.12f
    } * surfaceTintStrength.coerceIn(0.35f, 1.9f)
    val surfaceMixFactor = surfaceToneMix.coerceIn(0.4f, 1.8f)
    val containerFactor = containerVibrance.coerceIn(0.4f, 1.8f)
    val primary = colors.accent
    val secondary = colors.accentVar.blendWith(primary, if (isLight) 0.12f else 0.08f)
    val tertiary = colors.accentMuted.blendWith(colors.accentVar, if (isLight) 0.30f else 0.24f)

    val neutralBackground = if (isLight) Color(0xFFFFFBFE) else Color(0xFF141218)
    val neutralSurface = if (isLight) Color(0xFFF7F2FA) else Color(0xFF1D1B20)
    val neutralSurfaceLow = if (isLight) Color(0xFFF3EDF6) else Color(0xFF211F26)
    val neutralContainer = if (isLight) Color(0xFFEDE7F0) else Color(0xFF2A2730)
    val neutralContainerHigh = if (isLight) Color(0xFFE7E0EA) else Color(0xFF322F38)
    val neutralContainerHighest = if (isLight) Color(0xFFDCD6E0) else Color(0xFF3B3742)
    val surfaceVariantBase = if (isLight) Color(0xFFE8E0EC) else Color(0xFF49454F)
    val onSurface = if (isLight) Color(0xFF1D1B20) else Color(0xFFE6E0E9)
    val onSurfaceVariant = if (isLight) Color(0xFF49454F) else Color(0xFFCAC4D0)
    fun mix(amount: Float) = (amount * surfaceMixFactor).coerceIn(0f, 1f)
    val containerLift = ((containerFactor - 1f) * 0.18f).coerceIn(-0.16f, 0.18f)

    val background = neutralBackground
        .blendWith(colors.bgDeep, mix(if (isLight) 0.18f else 0.20f))
        .blendWith(primary, tintAmount)
    val surface = neutralSurface
        .blendWith(colors.bgSurface, mix(if (isLight) 0.12f else 0.18f))
        .blendWith(primary, tintAmount * 0.7f)
    val surfaceLowest = neutralBackground.blendWith(primary, tintAmount * 0.35f)
    val surfaceLow = neutralSurfaceLow
        .blendWith(colors.bgCard, mix(if (isLight) 0.08f else 0.14f))
        .blendWith(primary, tintAmount * 0.55f)
    val surfaceContainer = neutralContainer
        .blendWith(colors.bgCard, mix(if (isLight) 0.10f else 0.18f))
        .blendWith(secondary, tintAmount * 0.65f)
    val surfaceContainerHigh = neutralContainerHigh
        .blendWith(colors.bgElevated, mix(if (isLight) 0.14f else 0.20f))
        .blendWith(primary, tintAmount * 0.8f)
    val surfaceContainerHighest = neutralContainerHighest
        .blendWith(colors.bgElevated, mix(if (isLight) 0.18f else 0.22f))
        .blendWith(secondary, tintAmount)
    val surfaceVariant = surfaceVariantBase
        .blendWith(colors.bgCard, mix(if (isLight) 0.10f else 0.15f))
        .blendWith(secondary, tintAmount * 0.55f)

    val primaryContainer = if (isLight) {
        primary.blendWith(Color.White, (0.76f - containerLift).coerceIn(0.52f, 0.86f))
            .blendWith(surface, (0.10f + (containerFactor - 1f).coerceAtLeast(0f) * 0.05f).coerceAtMost(0.2f))
    } else {
        primary.blendWith(surfaceContainerHigh, (0.68f - containerLift).coerceIn(0.44f, 0.78f))
            .blendWith(Color.White, (0.05f + (containerFactor - 1f).coerceAtLeast(0f) * 0.03f).coerceAtMost(0.12f))
    }
    val secondaryContainer = if (isLight) {
        secondary.blendWith(Color.White, (0.78f - containerLift).coerceIn(0.54f, 0.88f))
            .blendWith(surface, (0.10f + (containerFactor - 1f).coerceAtLeast(0f) * 0.05f).coerceAtMost(0.2f))
    } else {
        secondary.blendWith(surfaceContainer, (0.70f - containerLift).coerceIn(0.46f, 0.8f))
            .blendWith(Color.White, (0.04f + (containerFactor - 1f).coerceAtLeast(0f) * 0.03f).coerceAtMost(0.12f))
    }
    val tertiaryContainer = if (isLight) {
        tertiary.blendWith(Color.White, (0.78f - containerLift).coerceIn(0.54f, 0.88f))
            .blendWith(surface, (0.08f + (containerFactor - 1f).coerceAtLeast(0f) * 0.05f).coerceAtMost(0.18f))
    } else {
        tertiary.blendWith(surfaceContainer, (0.72f - containerLift).coerceIn(0.48f, 0.82f))
            .blendWith(Color.White, (0.04f + (containerFactor - 1f).coerceAtLeast(0f) * 0.03f).coerceAtMost(0.12f))
    }
    val outline = surfaceVariant.blendWith(primary, if (isLight) 0.18f else 0.20f)
    val outlineVariant = surfaceVariant.blendWith(background, if (isLight) 0.32f else 0.24f)

    if (isLight) {
        lightColorScheme(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer),
            secondary = secondary,
            onSecondary = readableOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer),
            tertiary = tertiary,
            onTertiary = readableOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = readableOn(tertiaryContainer),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceLowest,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = Color(0xFF322F35),
            inverseOnSurface = Color(0xFFF5EFF7),
            inversePrimary = primary.blendWith(Color.White, 0.28f),
            surfaceTint = primary,
            scrim = Color.Black.copy(alpha = 0.46f),
            error = Color(0xFFBA1A1A)
        )
    } else {
        darkColorScheme(
            primary = primary.blendWith(Color.White, 0.08f),
            onPrimary = readableOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer),
            secondary = secondary.blendWith(Color.White, 0.06f),
            onSecondary = readableOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer),
            tertiary = tertiary.blendWith(Color.White, 0.04f),
            onTertiary = readableOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = readableOn(tertiaryContainer),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceLowest,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = Color(0xFFE6E0E9),
            inverseOnSurface = Color(0xFF322F35),
            inversePrimary = primary.blendWith(Color.Black, 0.18f),
            surfaceTint = primary,
            scrim = Color.Black.copy(alpha = 0.62f),
            error = Color(0xFFFFB4AB)
        )
    }
}

private fun materialAppColorsFrom(colorScheme: ColorScheme): AppColors = AppColors(
    bgDeep = colorScheme.background,
    bgSurface = colorScheme.surface,
    bgCard = colorScheme.surfaceContainerLow,
    bgElevated = colorScheme.surfaceContainerHigh,
    accent = colorScheme.primary,
    accentVar = colorScheme.secondary,
    accentMuted = colorScheme.tertiary,
    textPrimary = colorScheme.onSurface,
    textSecondary = colorScheme.onSurfaceVariant,
    textDisabled = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    divider = colorScheme.outlineVariant
)

private fun lerpMaterialColorScheme(
    from: ColorScheme,
    to: ColorScheme,
    fraction: Float
): ColorScheme {
    val t = fraction.coerceIn(0f, 1f)
    if (t <= 0f) return from
    if (t >= 1f) return to

    fun c(start: Color, end: Color) = lerp(start, end, t)

    return to.copy(
        primary = c(from.primary, to.primary),
        onPrimary = c(from.onPrimary, to.onPrimary),
        primaryContainer = c(from.primaryContainer, to.primaryContainer),
        onPrimaryContainer = c(from.onPrimaryContainer, to.onPrimaryContainer),
        inversePrimary = c(from.inversePrimary, to.inversePrimary),
        secondary = c(from.secondary, to.secondary),
        onSecondary = c(from.onSecondary, to.onSecondary),
        secondaryContainer = c(from.secondaryContainer, to.secondaryContainer),
        onSecondaryContainer = c(from.onSecondaryContainer, to.onSecondaryContainer),
        tertiary = c(from.tertiary, to.tertiary),
        onTertiary = c(from.onTertiary, to.onTertiary),
        tertiaryContainer = c(from.tertiaryContainer, to.tertiaryContainer),
        onTertiaryContainer = c(from.onTertiaryContainer, to.onTertiaryContainer),
        background = c(from.background, to.background),
        onBackground = c(from.onBackground, to.onBackground),
        surface = c(from.surface, to.surface),
        onSurface = c(from.onSurface, to.onSurface),
        surfaceVariant = c(from.surfaceVariant, to.surfaceVariant),
        onSurfaceVariant = c(from.onSurfaceVariant, to.onSurfaceVariant),
        surfaceTint = c(from.surfaceTint, to.surfaceTint),
        inverseSurface = c(from.inverseSurface, to.inverseSurface),
        inverseOnSurface = c(from.inverseOnSurface, to.inverseOnSurface),
        error = c(from.error, to.error),
        onError = c(from.onError, to.onError),
        errorContainer = c(from.errorContainer, to.errorContainer),
        onErrorContainer = c(from.onErrorContainer, to.onErrorContainer),
        outline = c(from.outline, to.outline),
        outlineVariant = c(from.outlineVariant, to.outlineVariant),
        scrim = c(from.scrim, to.scrim),
        surfaceBright = c(from.surfaceBright, to.surfaceBright),
        surfaceDim = c(from.surfaceDim, to.surfaceDim),
        surfaceContainer = c(from.surfaceContainer, to.surfaceContainer),
        surfaceContainerHigh = c(from.surfaceContainerHigh, to.surfaceContainerHigh),
        surfaceContainerHighest = c(from.surfaceContainerHighest, to.surfaceContainerHighest),
        surfaceContainerLow = c(from.surfaceContainerLow, to.surfaceContainerLow),
        surfaceContainerLowest = c(from.surfaceContainerLowest, to.surfaceContainerLowest)
    )
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
fun MusicPlayerTheme(
    appTheme: AppTheme = AppTheme.BLOOMEE,
    interfaceStyle: InterfaceStyle = InterfaceStyle.MATERIAL3,
    useDynamicColors: Boolean = false,
    selectedFontId: String = DefaultAppFontId,
    customFontUri: String = "",
    customColors: com.musicplayer.data.CustomThemeColors? = null,
    content: @Composable () -> Unit
) {
    val normalizedInterfaceStyle = InterfaceStyle.MATERIAL3
    val followSystemDark = isSystemInDarkTheme()
    val resolvedTheme = when (appTheme) {
        AppTheme.MATERIAL_YOU -> if (followSystemDark) AppTheme.DARK_BLACK else AppTheme.LIGHT
        else -> appTheme
    }
    val appFontFamily = remember(selectedFontId, customFontUri) {
        if (customFontUri.isNotBlank()) fontFamilyFromFile(customFontUri)
        else fontFamilyById(selectedFontId)
    }
    val isLight = when (appTheme) {
        AppTheme.MATERIAL_YOU -> !followSystemDark
        AppTheme.LIGHT -> true
        AppTheme.IVORY -> true
        AppTheme.PEARL -> true
        AppTheme.CUSTOM -> customColors?.isLightTheme == true
        else -> false
    }
    val seedColors = appColorsForTheme(resolvedTheme, customColors).withInterfaceStyle(normalizedInterfaceStyle, isLight)
    val themedTypography = remember(appFontFamily) { Typography.withFontFamily(appFontFamily) }
    val isClassicBloomee = resolvedTheme == AppTheme.BLOOMEE && normalizedInterfaceStyle == InterfaceStyle.CLASSIC
    val appStyle = remember(resolvedTheme, normalizedInterfaceStyle, customColors) {
        val baseStyle = appStyleFor(
            interfaceStyle = normalizedInterfaceStyle,
            customColors = customColors.takeIf { resolvedTheme == AppTheme.CUSTOM }
        )
        if (isClassicBloomee) {
            baseStyle.copy(
                buttonCornerRadius = 22f,
                cardCornerRadius = 20f,
                sliderTrackHeight = 6f,
                surfaceAlpha = 0.88f,
                surfaceBorderAlpha = 0.22f,
                glassHighlightAlpha = 0.18f
            )
        } else baseStyle
    }
    val shapes = remember(appTheme, normalizedInterfaceStyle, customColors) {
        if (isClassicBloomee) {
            MaterialShapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(36.dp)
            )
        } else {
            shapesForInterfaceStyle(
                interfaceStyle = normalizedInterfaceStyle,
                customColors = customColors.takeIf { resolvedTheme == AppTheme.CUSTOM }
            )
        }
    }

    val context = LocalContext.current
    val shouldUseDynamicColors = appTheme == AppTheme.MATERIAL_YOU || useDynamicColors
    val dynamicColorScheme = remember(shouldUseDynamicColors, isLight, context) {
        when {
            !shouldUseDynamicColors || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> null
            isLight -> dynamicLightColorScheme(context)
            else -> dynamicDarkColorScheme(context)
        }
    }
    val targetColorScheme = remember(seedColors, isLight, dynamicColorScheme, customColors, resolvedTheme) {
        dynamicColorScheme ?: buildMaterialColorScheme(
            colors = seedColors,
            isLight = isLight,
            interfaceStyle = InterfaceStyle.MATERIAL3,
            surfaceTintStrength = if (resolvedTheme == AppTheme.CUSTOM) customColors?.surfaceTintStrength ?: 1f else 1f,
            surfaceToneMix = if (resolvedTheme == AppTheme.CUSTOM) customColors?.surfaceToneMix ?: 1f else 1f,
            containerVibrance = if (resolvedTheme == AppTheme.CUSTOM) customColors?.containerVibrance ?: 1f else 1f
        )
    }
    val themeTransition = updateTransition(
        targetState = targetColorScheme,
        label = "appThemeTransition"
    )
    val themeProgress = themeTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (appTheme == AppTheme.CUSTOM) 260 else 760,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
            )
        },
        label = "appThemeProgress"
    ) { 1f }
    val colorScheme = remember(
        themeTransition.currentState,
        themeTransition.targetState,
        themeProgress.value
    ) {
        lerpMaterialColorScheme(
            from = themeTransition.currentState,
            to = themeTransition.targetState,
            fraction = themeProgress.value
        )
    }
    val resolvedAppColors = remember(colorScheme) { materialAppColorsFrom(colorScheme) }

    // Адаптировать цвет статус-бара под тему (как в PixelPlay)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val elevatedSurface = colorScheme.surfaceColorAtElevation(4.dp)
            val statusColor = Color(
                ColorUtils.blendARGB(
                    colorScheme.background.toArgb(),
                    elevatedSurface.toArgb(),
                    0.3f
                )
            )
            window.statusBarColor = statusColor.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            val lightSystemBars = ColorUtils.calculateLuminance(statusColor.toArgb()) > 0.5
            insetsController.isAppearanceLightStatusBars = lightSystemBars
            insetsController.isAppearanceLightNavigationBars =
                colorScheme.background.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides resolvedAppColors,
        LocalAppFontFamily provides appFontFamily,
        LocalAppStyle provides appStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = themedTypography,
            shapes      = shapes,
            content     = content
        )
    }
}
