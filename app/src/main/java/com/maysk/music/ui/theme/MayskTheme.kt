package com.maysk.music.ui.theme

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
import com.maysk.music.data.AppSettings
import com.maysk.design.Backdrop
import com.maysk.design.MayskDesignTheme
import com.maysk.music.data.BackgroundStyle
import com.maysk.music.data.ThemePreset

/** Палитра и базовые компоненты — общая дизайн-система Maysk (модуль :design). */
typealias MayskColors = com.maysk.design.MayskColors
val LocalMaysk = com.maysk.design.LocalMaysk
val LocalUi = staticCompositionLocalOf { AppSettings() }

val AccentPalette = listOf(
    0xFF8B5CF6, 0xFF3B82F6, 0xFFEC4899, 0xFF14B8A6, 0xFF22C55E, 0xFFF97316,
    0xFFEF4444, 0xFFEAB308, 0xFF06B6D4, 0xFFA855F7,
)

fun buildColors(s: AppSettings, dynamicAccent: Color?, systemDark: Boolean): MayskColors {
    val preset = if (s.followSystem && !systemDark) ThemePreset.LIGHT else s.theme
    val userAccent = Color(s.accent)
    val accent = if (preset == ThemePreset.DYNAMIC && dynamicAccent != null) dynamicAccent else userAccent
    val button = if (preset == ThemePreset.DYNAMIC && dynamicAccent != null) dynamicAccent else Color(s.buttonColor)
    val pink = Color(0xFFEC4899)
    val blue = Color(0xFF3B82F6)
    val base = when (preset) {
        ThemePreset.AURORA -> MayskColors(false, Color(0xFF140B33), Color(0xFF05030F), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.10f),
            accent, blue, pink, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background.backdrop(), button, s.highContrast)
        ThemePreset.AMOLED -> MayskColors(false, Color.Black, Color.Black, Color.White, (s.cardAlpha * 0.6f), Color.White.copy(alpha = 0.06f),
            accent, blue, pink, Color.White, Color.White.copy(alpha = 0.6f), false, s.corner.dp, Backdrop.SOLID, button, s.highContrast)
        ThemePreset.NEON -> MayskColors(false, Color(0xFF0E0430), Color(0xFF03010C), Color(0xFFB026FF), s.cardAlpha + 0.04f, accent.copy(alpha = 0.35f),
            accent, Color(0xFF22D3EE), Color(0xFFFF2BD6), Color.White, Color.White.copy(alpha = 0.7f), true, s.corner.dp, s.background.backdrop(), button, s.highContrast)
        ThemePreset.DYNAMIC -> MayskColors(false, lerpDark(accent, 0.22f), Color(0xFF040309), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.10f),
            accent, blue, pink, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background.backdrop(), button, s.highContrast)
        ThemePreset.MINIMAL -> MayskColors(false, Color(0xFF121218), Color(0xFF121218), Color.White, 0.05f, Color.White.copy(alpha = 0.05f),
            accent, blue, pink, Color.White, Color.White.copy(alpha = 0.6f), false, s.corner.dp, Backdrop.SOLID, button, s.highContrast)
        ThemePreset.CLASSIC -> MayskColors(false, Color(0xFF181818), Color(0xFF101010), Color(0xFF2A2A2A), 0.9f, Color.Transparent,
            accent, blue, pink, Color.White, Color(0xFFB3B3B3), false, s.corner.dp, Backdrop.SOLID, button, s.highContrast)
        ThemePreset.LIGHT -> MayskColors(true, Color(0xFFF4F1FF), Color(0xFFE9E4FB), Color.White, 0.75f, Color(0x1A000000),
            accent, blue, pink, Color(0xFF15122B), Color(0xFF15122B).copy(alpha = 0.6f), false, s.corner.dp, s.background.backdrop(), button, s.highContrast)
    }
    return if (s.highContrast) base.copy(
        textDim = if (base.isLight) Color(0xFF15122B) else Color.White.copy(alpha = 0.9f),
        border = if (base.isLight) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.35f),
        glassAlpha = if (base.isLight) 0.95f else (base.glassAlpha + 0.08f).coerceAtMost(1f),
    ) else base
}

private fun lerpDark(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, 1f)

fun BackgroundStyle.backdrop(): Backdrop = when (this) {
    BackgroundStyle.GRADIENT -> Backdrop.GRADIENT
    BackgroundStyle.AURORA -> Backdrop.AURORA
    BackgroundStyle.SOLID -> Backdrop.SOLID
}

@Composable
fun MayskTheme(settings: AppSettings, colors: MayskColors, content: @Composable () -> Unit) {
    val extraScale = settings.fontScale * (if (settings.simpleMode) 1.2f else 1f)
    CompositionLocalProvider(LocalUi provides settings) {
        MayskDesignTheme(colors, extraScale, content)
    }
}
