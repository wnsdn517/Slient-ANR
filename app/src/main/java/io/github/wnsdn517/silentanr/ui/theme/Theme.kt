package io.github.wnsdn517.silentanr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens. Violet accent on cool neutrals; cards are borderless rounded surfaces that sit
 * one step above the page background. Semantic colors are reserved for ANR outcomes and states.
 */
@Immutable
data class Tokens(
    val bg: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val neutral: Color,
    val chart: List<Color>,
)

private val LightTokens = Tokens(
    bg = Color(0xFFF5F4FA),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEDEBF5),
    outline = Color(0xFFE7E4F0),
    text = Color(0xFF1A1823),
    textMuted = Color(0xFF6C6880),
    accent = Color(0xFF6B4EE6),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFECE6FF),
    success = Color(0xFF16A06B),
    warning = Color(0xFFD08410),
    danger = Color(0xFFE5484D),
    info = Color(0xFF2F80C8),
    neutral = Color(0xFF8C889C),
    chart = listOf(Color(0xFF6B4EE6), Color(0xFF2F80C8), Color(0xFFD08410), Color(0xFFE5484D), Color(0xFFD65DB1), Color(0xFF8C889C)),
)

private val DarkTokens = Tokens(
    bg = Color(0xFF0F0E14),
    surface = Color(0xFF1A1922),
    surfaceMuted = Color(0xFF26242F),
    outline = Color(0xFF2E2C38),
    text = Color(0xFFEEECF5),
    textMuted = Color(0xFFA19DB2),
    accent = Color(0xFFA78BFA),
    onAccent = Color(0xFF1A1233),
    accentSoft = Color(0xFF2D2550),
    success = Color(0xFF4ADE9B),
    warning = Color(0xFFF2B84B),
    danger = Color(0xFFFF6B6E),
    info = Color(0xFF6CB4EE),
    neutral = Color(0xFF8F8B9E),
    chart = listOf(Color(0xFFA78BFA), Color(0xFF6CB4EE), Color(0xFFF2B84B), Color(0xFFFF6B6E), Color(0xFFF08BD5), Color(0xFF8F8B9E)),
)

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(50)
}

val LocalTokens = staticCompositionLocalOf { LightTokens }

object AppTheme {
    val tokens: Tokens @Composable get() = LocalTokens.current
}

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)

/** Figures that line up in columns; used for every count and measurement. */
val Figures = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun SilentAnrTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val t = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) {
        darkColorScheme(
            primary = t.accent, onPrimary = t.onAccent, primaryContainer = t.accentSoft, onPrimaryContainer = t.text,
            secondary = t.accent, onSecondary = t.onAccent, secondaryContainer = t.accentSoft, onSecondaryContainer = t.text,
            background = t.bg, onBackground = t.text, surface = t.surface, onSurface = t.text,
            surfaceVariant = t.surfaceMuted, onSurfaceVariant = t.textMuted, outline = t.outline, outlineVariant = t.outline,
            error = t.danger, surfaceContainer = t.surface, surfaceContainerLow = t.surface,
            surfaceContainerHigh = t.surfaceMuted, surfaceContainerHighest = t.surfaceMuted,
        )
    } else {
        lightColorScheme(
            primary = t.accent, onPrimary = t.onAccent, primaryContainer = t.accentSoft, onPrimaryContainer = t.text,
            secondary = t.accent, onSecondary = t.onAccent, secondaryContainer = t.accentSoft, onSecondaryContainer = t.text,
            background = t.bg, onBackground = t.text, surface = t.surface, onSurface = t.text,
            surfaceVariant = t.surfaceMuted, onSurfaceVariant = t.textMuted, outline = t.outline, outlineVariant = t.outline,
            error = t.danger, surfaceContainer = t.surface, surfaceContainerLow = t.surface,
            surfaceContainerHigh = t.surfaceMuted, surfaceContainerHighest = t.surfaceMuted,
        )
    }
    CompositionLocalProvider(LocalTokens provides t) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = Shapes(small = Radius.sm, medium = Radius.md, large = Radius.lg),
            content = content,
        )
    }
}
