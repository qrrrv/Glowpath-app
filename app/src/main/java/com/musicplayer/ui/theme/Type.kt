package com.musicplayer.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.musicplayer.R
import java.io.File

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val montserrat = GoogleFont("Montserrat")
private val nunito = GoogleFont("Nunito")
private val rubik = GoogleFont("Rubik")
private val inter = GoogleFont("Inter")
private val manrope = GoogleFont("Manrope")
private val outfit = GoogleFont("Outfit")
private val plusJakarta = GoogleFont("Plus Jakarta Sans")
private val raleway = GoogleFont("Raleway")

private fun googleFontFamily(font: GoogleFont): FontFamily = FontFamily(
    Font(googleFont = font, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = font, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = font, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = font, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = font, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = font, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = font, fontProvider = provider, weight = FontWeight.Black),
)

val MontserratFamily = googleFontFamily(montserrat)
val NunitoFamily = googleFontFamily(nunito)
val RubikFamily = googleFontFamily(rubik)
val InterFamily = googleFontFamily(inter)
val ManropeFamily = googleFontFamily(manrope)
val OutfitFamily = googleFontFamily(outfit)
val PlusJakartaFamily = googleFontFamily(plusJakarta)
val RalewayFamily = googleFontFamily(raleway)

data class AppFont(val id: String, val displayName: String, val family: FontFamily)

const val DefaultAppFontId = "manrope"

val DefaultAppFont = AppFont(DefaultAppFontId, "Manrope", ManropeFamily)
val DefaultAppFontFamily: FontFamily = DefaultAppFont.family

val AppFontList = listOf(
    DefaultAppFont,
    AppFont("nunito", "Nunito", NunitoFamily),
    AppFont("rubik", "Rubik", RubikFamily),
    AppFont("inter", "Inter", InterFamily),
    AppFont("outfit", "Outfit", OutfitFamily),
    AppFont("plus_jakarta", "Plus Jakarta Sans", PlusJakartaFamily),
    AppFont("raleway", "Raleway", RalewayFamily),
    AppFont("montserrat", "Montserrat", MontserratFamily),
)

fun fontFamilyById(id: String): FontFamily =
    AppFontList.firstOrNull { it.id == id }?.family ?: DefaultAppFontFamily

/**
 * Загружает пользовательский шрифт из файла на диске.
 * [filePath] — абсолютный путь к .ttf/.otf файлу во внутреннем хранилище.
 * Возвращает встроенный дефолтный шрифт при любой ошибке.
 */
fun fontFamilyFromFile(filePath: String): FontFamily {
    if (filePath.isBlank()) return DefaultAppFontFamily
    return try {
        val file = File(filePath)
        if (!file.exists()) return DefaultAppFontFamily
        @Suppress("DEPRECATION")
        FontFamily(Typeface.createFromFile(file))
    } catch (_: Exception) {
        DefaultAppFontFamily
    }
}

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.02).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    displayMedium = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.01).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    displaySmall = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    headlineLarge = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.01).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.005).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    headlineSmall = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    titleLarge = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (0.01).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    titleSmall = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (0.01).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    bodyLarge = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (0.01).em
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (0.01).em
    ),
    bodySmall = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (0.02).em
    ),
    labelLarge = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (0.01).em
    ),
    labelMedium = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (0.03).em
    ),
    labelSmall = TextStyle(
        fontFamily = DefaultAppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = (0.04).em
    )
)

val LocalAppFontFamily = compositionLocalOf<FontFamily> { DefaultAppFontFamily }

fun Typography.withFontFamily(fontFamily: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily)
)
