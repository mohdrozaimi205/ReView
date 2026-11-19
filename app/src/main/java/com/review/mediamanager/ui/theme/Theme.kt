package com.review.mediamanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.review.mediamanager.domain.AppTheme

object Aurora {
    val Mint = Color(0xFF7CF5D0)
    val Lavender = Color(0xFFB79CFF)
    val Coral = Color(0xFFFF8FA3)
    val Amber = Color(0xFFFFC46B)
    val TextHi = Color(0xFFF4F4F7)
    val TextMid = Color(0xFFA6A7B4)
    val TextLow = Color(0xFF6B6C78)
    val brand = Brush.linearGradient(listOf(Mint, Lavender))
    val brandSoft = Brush.linearGradient(listOf(Mint.copy(alpha = 0.18f), Lavender.copy(alpha = 0.18f)))
}

@Immutable
data class ReViewPalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val stroke: Color
)

val LocalPalette = staticCompositionLocalOf {
    ReViewPalette(Color.Black, Color(0xFF0C0C11), Color(0xFF16161E), Color(0x14FFFFFF))
}

fun paletteFor(theme: AppTheme) = when (theme) {
    AppTheme.AMOLED -> ReViewPalette(Color(0xFF000000), Color(0xFF0C0C11), Color(0xFF16161E), Color(0x14FFFFFF))
    AppTheme.GRAPHITE -> ReViewPalette(Color(0xFF121214), Color(0xFF1C1C20), Color(0xFF26262C), Color(0x1AFFFFFF))
    AppTheme.MIDNIGHT -> ReViewPalette(Color(0xFF05070F), Color(0xFF0D1222), Color(0xFF151C33), Color(0x1A9FB4FF))
}

private val display = FontFamily.SansSerif

private val ReViewType = Typography(
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = display, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = display, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = display, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
)

@Composable
fun ReViewTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val p = paletteFor(theme)
    val scheme = darkColorScheme(
        primary = Aurora.Mint,
        onPrimary = Color(0xFF00201A),
        secondary = Aurora.Lavender,
        onSecondary = Color(0xFF1B0F3A),
        tertiary = Aurora.Coral,
        background = p.background,
        onBackground = Aurora.TextHi,
        surface = p.surface,
        onSurface = Aurora.TextHi,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = Aurora.TextMid,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHigh,
        outline = Color(0x33FFFFFF),
        outlineVariant = p.stroke,
        error = Color(0xFFFF6B7A)
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = ReViewType, content = content)
    }
}
