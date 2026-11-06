package com.maysk.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Фон экрана. */
enum class Backdrop { SOLID, GRADIENT, AURORA, AURORA_MOUNTAINS }

/** Палитра Maysk — общая для всех приложений экосистемы. */
@Immutable
data class MayskColors(
    val isLight: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val glassAlpha: Float,
    val border: Color,
    val accent: Color,
    val accent2: Color,
    val accent3: Color,
    val text: Color,
    val textDim: Color,
    val glow: Boolean,
    val corner: Dp,
    val backdrop: Backdrop = Backdrop.AURORA,
    val button: Color = accent,
    val highContrast: Boolean = false,
) {
    val cardBg: Color get() = card.copy(alpha = glassAlpha)
    val aurora: Boolean get() = backdrop == Backdrop.AURORA || backdrop == Backdrop.AURORA_MOUNTAINS
}

/** Фирменные цвета и размеры. */
object MayskTokens {
    val Violet = Color(0xFF8B5CF6)
    val Blue = Color(0xFF3B82F6)
    val Pink = Color(0xFFEC4899)
    val Cyan = Color(0xFF22D3EE)
    val Night = Color(0xFF05030F)
    val NightTop = Color(0xFF160C3A)

    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space6 = 24.dp
    val Corner = 22.dp
    val CornerSmall = 12.dp
}

val AccentPalette = listOf(0xFF8B5CF6, 0xFF3B82F6, 0xFFEC4899, 0xFF14B8A6, 0xFF22C55E, 0xFFF97316, 0xFFEF4444, 0xFFEAB308, 0xFF06B6D4, 0xFFA855F7)

object MayskDefaults {
    fun dark(accent: Color = MayskTokens.Violet) = MayskColors(
        false, MayskTokens.NightTop, MayskTokens.Night, Color.White, 0.08f, Color.White.copy(alpha = 0.10f),
        accent, MayskTokens.Blue, MayskTokens.Pink, Color.White, Color.White.copy(alpha = 0.65f), true, MayskTokens.Corner,
    )
    fun light(accent: Color = MayskTokens.Violet) = MayskColors(
        true, Color(0xFFF6F4FC), Color(0xFFEDEAF7), Color.White, 0.9f, Color(0x14000000),
        accent, MayskTokens.Blue, MayskTokens.Pink, Color(0xFF16132A), Color(0xFF16132A).copy(alpha = 0.6f), false, MayskTokens.Corner, Backdrop.SOLID,
    )
}

val LocalMaysk = staticCompositionLocalOf { MayskDefaults.dark() }

fun mayskColorScheme(colors: MayskColors): ColorScheme = if (colors.isLight) lightColorScheme(
    primary = colors.accent, secondary = colors.accent2, tertiary = colors.accent3,
    background = colors.bgTop, surface = Color.White, onSurface = colors.text, onBackground = colors.text,
    surfaceContainer = Color(0xFFF3F0FB), surfaceContainerHigh = Color(0xFFECE8F8),
    surfaceVariant = Color(0xFFE7E2F7), onSurfaceVariant = colors.textDim,
) else darkColorScheme(
    primary = colors.accent, onPrimary = Color.White, secondary = colors.accent2, tertiary = colors.accent3,
    background = colors.bgBottom, surface = Color(0xFF16122B),
    surfaceContainer = Color(0xFF1B1633), surfaceContainerHigh = Color(0xFF221C3D), surfaceContainerHighest = Color(0xFF2A2347),
    onSurface = colors.text, onBackground = colors.text, surfaceVariant = Color(0xFF2A2347), onSurfaceVariant = colors.textDim,
)

fun mayskTypography(): Typography {
    val base = Typography()
    return Typography(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge, bodyMedium = base.bodyMedium, bodySmall = base.bodySmall,
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold), labelMedium = base.labelMedium, labelSmall = base.labelSmall,
    )
}

/** Тема Maysk: палитра + Material 3 + масштаб шрифта. */
@Composable
fun MayskDesignTheme(colors: MayskColors, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalMaysk provides colors,
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
    ) {
        MaterialTheme(colorScheme = mayskColorScheme(colors), typography = mayskTypography(), content = content)
    }
}
