package com.grsks.printerremote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grsks.printerremote.data.AppFont
import com.grsks.printerremote.data.CardStyle
import com.grsks.printerremote.data.NothingAccent
import com.grsks.printerremote.data.ThemeMode
import com.grsks.printerremote.data.ThemeSettings

enum class AppStyle { NOTHING, MATERIAL }

/** Какая из двух версий приложения собрана (задаётся build flavor). */
val isNothing: Boolean get() = FlavorStyle.style == AppStyle.NOTHING

/** Фирменный красный. Используется точечно: индикаторы, опасные действия, ошибки. */
val NothingRed = Color(0xFFD71921)

val LocalCardStyle = staticCompositionLocalOf { CardStyle.FILLED }

val ColorScheme.accent: Color get() = tertiary

/** Тёмная ли тема с учётом выбора пользователя. */
@Composable
fun resolveDark(settings: ThemeSettings): Boolean = when (settings.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PrinterRemoteTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = resolveDark(settings)
    val context = LocalContext.current
    val scheme = when {
        isNothing -> nothingScheme(dark, settings.nothingAccent)
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (dark && settings.amoled) base.toAmoled() else base
        }
        else -> schemeFromSeed(settings.seedColor, dark, settings.amoled)
    }
    CompositionLocalProvider(LocalCardStyle provides settings.cardStyle) {
        MaterialTheme(
            colorScheme = scheme,
            typography = if (isNothing) nothingTypography() else materialTypography(settings.font),
            shapes = if (isNothing) NothingShapes else materialShapes(settings.cornerDp),
            content = content,
        )
    }
}

// ---------------------------------------------------------------- Nothing

private fun nothingScheme(dark: Boolean, accent: NothingAccent): ColorScheme {
    val accentColor = when (accent) {
        NothingAccent.RED -> NothingRed
        NothingAccent.AMBER -> Color(0xFFFFB020)
        NothingAccent.WHITE -> if (dark) Color(0xFFFFFFFF) else Color(0xFF0A0A0A)
    }
    val onAccent = if (accent == NothingAccent.RED) Color.White else if (dark) Color.Black else Color.White
    return if (dark) {
        darkColorScheme(
            primary = Color(0xFFFFFFFF),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFF1C1C1C),
            onPrimaryContainer = Color(0xFFFFFFFF),
            secondary = Color(0xFFB5B5B5),
            onSecondary = Color(0xFF000000),
            secondaryContainer = Color(0xFF1C1C1C),
            onSecondaryContainer = Color(0xFFFFFFFF),
            tertiary = accentColor,
            onTertiary = onAccent,
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF141414),
            onSurfaceVariant = Color(0xFF8C8C8C),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222),
            outline = Color(0xFF3A3A3A),
            outlineVariant = Color(0xFF262626),
            error = NothingRed,
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFF2A0B0D),
            onErrorContainer = Color(0xFFFFB4B6),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0A0A0A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE4E4E1),
            onPrimaryContainer = Color(0xFF0A0A0A),
            secondary = Color(0xFF4A4A4A),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE4E4E1),
            onSecondaryContainer = Color(0xFF0A0A0A),
            tertiary = accentColor,
            onTertiary = onAccent,
            background = Color(0xFFF2F2EF),
            onBackground = Color(0xFF0A0A0A),
            surface = Color(0xFFF2F2EF),
            onSurface = Color(0xFF0A0A0A),
            surfaceVariant = Color(0xFFE9E9E6),
            onSurfaceVariant = Color(0xFF6B6B6B),
            surfaceContainer = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFFAFAF8),
            surfaceContainerHighest = Color(0xFFE4E4E1),
            outline = Color(0xFFB4B4B0),
            outlineVariant = Color(0xFFDADAD6),
            error = NothingRed,
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFE1E2),
            onErrorContainer = Color(0xFF5C0A0E),
        )
    }
}

private val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private fun nothingTypography(): Typography {
    val mono = FlavorStyle.labelFont
    return Typography(
        displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 60.sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
        labelMedium = TextStyle(fontFamily = mono, fontSize = 11.sp, letterSpacing = 1.2.sp),
        labelSmall = TextStyle(fontFamily = mono, fontSize = 10.sp, letterSpacing = 1.2.sp),
    )
}

// ---------------------------------------------------------------- Material 3

private fun materialTypography(font: AppFont): Typography {
    val family = when (font) {
        AppFont.DEFAULT -> FontFamily.Default
        AppFont.SERIF -> FontFamily.Serif
        AppFont.MONO -> FontFamily.Monospace
        AppFont.CURSIVE -> FontFamily.Cursive
    }
    if (font == AppFont.DEFAULT) return Typography()
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = d.headlineLarge.copy(fontFamily = family),
        headlineMedium = d.headlineMedium.copy(fontFamily = family),
        headlineSmall = d.headlineSmall.copy(fontFamily = family),
        titleLarge = d.titleLarge.copy(fontFamily = family),
        titleMedium = d.titleMedium.copy(fontFamily = family),
        titleSmall = d.titleSmall.copy(fontFamily = family),
        bodyLarge = d.bodyLarge.copy(fontFamily = family),
        bodyMedium = d.bodyMedium.copy(fontFamily = family),
        bodySmall = d.bodySmall.copy(fontFamily = family),
        labelLarge = d.labelLarge.copy(fontFamily = family),
        labelMedium = d.labelMedium.copy(fontFamily = family),
        labelSmall = d.labelSmall.copy(fontFamily = family),
    )
}

private fun materialShapes(base: Int): Shapes {
    val r = base.coerceIn(0, 40)
    fun c(f: Float) = RoundedCornerShape((r * f).dp)
    return Shapes(
        extraSmall = c(0.25f),
        small = c(0.5f),
        medium = c(0.75f),
        large = c(1f),
        extraLarge = c(1.5f),
    )
}

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0F0F0F),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1E1E1E),
)

/**
 * Полная схема Material 3 из одного цвета-зерна. Упрощённая замена генератору HCT:
 * оттенок берётся из зерна, светлота ступенями как в тональных палитрах M3.
 */
fun schemeFromSeed(seed: Int, dark: Boolean, amoled: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed, hsv)
    val h = hsv[0]
    val s = hsv[1].coerceIn(0.45f, 0.9f)
    fun tone(hue: Float, sat: Float, l: Float) = Color.hsl((hue + 360f) % 360f, sat.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    val tertiaryHue = h + 60f
    val neutral = 0.10f

    return if (dark) {
        val bg = if (amoled) 0f else 0.07f
        val step = if (amoled) listOf(0.04f, 0.07f, 0.10f, 0.13f) else listOf(0.10f, 0.13f, 0.16f, 0.20f)
        darkColorScheme(
            primary = tone(h, s, 0.78f),
            onPrimary = tone(h, s, 0.15f),
            primaryContainer = tone(h, s * 0.8f, 0.28f),
            onPrimaryContainer = tone(h, s, 0.90f),
            secondary = tone(h, 0.25f, 0.78f),
            onSecondary = tone(h, 0.25f, 0.18f),
            secondaryContainer = tone(h, 0.2f, 0.28f),
            onSecondaryContainer = tone(h, 0.25f, 0.90f),
            tertiary = tone(tertiaryHue, s * 0.8f, 0.78f),
            onTertiary = tone(tertiaryHue, s, 0.15f),
            tertiaryContainer = tone(tertiaryHue, s * 0.7f, 0.28f),
            onTertiaryContainer = tone(tertiaryHue, s, 0.90f),
            background = tone(h, neutral, bg),
            onBackground = tone(h, 0.08f, 0.90f),
            surface = tone(h, neutral, bg),
            onSurface = tone(h, 0.08f, 0.90f),
            surfaceVariant = tone(h, 0.12f, 0.26f),
            onSurfaceVariant = tone(h, 0.12f, 0.78f),
            surfaceContainerLowest = tone(h, neutral, if (amoled) 0f else 0.05f),
            surfaceContainerLow = tone(h, neutral, step[0]),
            surfaceContainer = tone(h, neutral, step[1]),
            surfaceContainerHigh = tone(h, neutral, step[2]),
            surfaceContainerHighest = tone(h, neutral, step[3]),
            outline = tone(h, 0.10f, 0.55f),
            outlineVariant = tone(h, 0.12f, 0.28f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = tone(h, s, 0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(h, s, 0.90f),
            onPrimaryContainer = tone(h, s, 0.12f),
            secondary = tone(h, 0.25f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = tone(h, 0.30f, 0.90f),
            onSecondaryContainer = tone(h, 0.30f, 0.12f),
            tertiary = tone(tertiaryHue, s * 0.8f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = tone(tertiaryHue, s, 0.90f),
            onTertiaryContainer = tone(tertiaryHue, s, 0.12f),
            background = tone(h, 0.30f, 0.985f),
            onBackground = tone(h, 0.10f, 0.10f),
            surface = tone(h, 0.30f, 0.985f),
            onSurface = tone(h, 0.10f, 0.10f),
            surfaceVariant = tone(h, 0.25f, 0.91f),
            onSurfaceVariant = tone(h, 0.12f, 0.30f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(h, 0.30f, 0.965f),
            surfaceContainer = tone(h, 0.30f, 0.94f),
            surfaceContainerHigh = tone(h, 0.28f, 0.92f),
            surfaceContainerHighest = tone(h, 0.26f, 0.90f),
            outline = tone(h, 0.10f, 0.46f),
            outlineVariant = tone(h, 0.20f, 0.80f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    }
}
