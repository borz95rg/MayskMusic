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
import com.maysk.music.ui.LocalStrings
import com.maysk.music.ui.stringsFor
import com.maysk.music.data.BackgroundStyle
import com.maysk.music.data.ThemePreset

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
    val button: Color,
    val text: Color,
    val textDim: Color,
    val glow: Boolean,
    val corner: Dp,
    val background: BackgroundStyle,
    val highContrast: Boolean,
) {
    val cardBg: Color get() = card.copy(alpha = glassAlpha)
}

val LocalMaysk = staticCompositionLocalOf { buildColors(AppSettings(), null, true) }
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
            accent, blue, pink, button, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background, s.highContrast)
        ThemePreset.AMOLED -> MayskColors(false, Color.Black, Color.Black, Color.White, (s.cardAlpha * 0.6f), Color.White.copy(alpha = 0.06f),
            accent, blue, pink, button, Color.White, Color.White.copy(alpha = 0.6f), false, s.corner.dp, BackgroundStyle.SOLID, s.highContrast)
        ThemePreset.NEON -> MayskColors(false, Color(0xFF0E0430), Color(0xFF03010C), Color(0xFFB026FF), s.cardAlpha + 0.04f, accent.copy(alpha = 0.35f),
            accent, Color(0xFF22D3EE), Color(0xFFFF2BD6), button, Color.White, Color.White.copy(alpha = 0.7f), true, s.corner.dp, s.background, s.highContrast)
        ThemePreset.DYNAMIC -> MayskColors(false, lerpDark(accent, 0.22f), Color(0xFF040309), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.10f),
            accent, blue, pink, button, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background, s.highContrast)
        ThemePreset.MINIMAL -> MayskColors(false, Color(0xFF121218), Color(0xFF121218), Color.White, 0.05f, Color.White.copy(alpha = 0.05f),
            accent, blue, pink, button, Color.White, Color.White.copy(alpha = 0.6f), false, s.corner.dp, BackgroundStyle.SOLID, s.highContrast)
        ThemePreset.CLASSIC -> MayskColors(false, Color(0xFF181818), Color(0xFF101010), Color(0xFF2A2A2A), 0.9f, Color.Transparent,
            accent, blue, pink, button, Color.White, Color(0xFFB3B3B3), false, s.corner.dp, BackgroundStyle.SOLID, s.highContrast)
        ThemePreset.LIGHT -> MayskColors(true, Color(0xFFF4F1FF), Color(0xFFE9E4FB), Color.White, 0.75f, Color(0x1A000000),
            accent, blue, pink, button, Color(0xFF15122B), Color(0xFF15122B).copy(alpha = 0.6f), false, s.corner.dp, s.background, s.highContrast)
    }
    return if (s.highContrast) base.copy(
        textDim = if (base.isLight) Color(0xFF15122B) else Color.White.copy(alpha = 0.9f),
        border = if (base.isLight) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.35f),
        glassAlpha = if (base.isLight) 0.95f else (base.glassAlpha + 0.08f).coerceAtMost(1f),
    ) else base
}

private fun lerpDark(c: Color, f: Float) = Color(c.red * f, c.green * f, c.blue * f, 1f)

@Composable
fun MayskTheme(settings: AppSettings, colors: MayskColors, content: @Composable () -> Unit) {
    val scheme = if (colors.isLight) lightColorScheme(
        primary = colors.accent,
        secondary = colors.accent2,
        tertiary = colors.accent3,
        background = colors.bgTop,
        surface = Color.White,
        onSurface = colors.text,
        onBackground = colors.text,
        surfaceVariant = Color(0xFFE7E2F7),
        onSurfaceVariant = colors.textDim,
    ) else darkColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        secondary = colors.accent2,
        tertiary = colors.accent3,
        background = colors.bgBottom,
        surface = Color(0xFF16122B),
        surfaceContainer = Color(0xFF1B1633),
        surfaceContainerHigh = Color(0xFF221C3D),
        surfaceContainerHighest = Color(0xFF2A2347),
        onSurface = colors.text,
        onBackground = colors.text,
        surfaceVariant = Color(0xFF2A2347),
        onSurfaceVariant = colors.textDim,
    )
    val simple = settings.simpleMode
    val base = Typography()
    val typography = Typography(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge,
        bodyMedium = base.bodyMedium,
        bodySmall = base.bodySmall,
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium,
        labelSmall = base.labelSmall,
    )
    val density = LocalDensity.current
    val extraScale = settings.fontScale * (if (simple) 1.2f else 1f)
    CompositionLocalProvider(
        LocalMaysk provides colors,
        LocalUi provides settings,
        LocalStrings provides stringsFor(settings.language),
        LocalDensity provides Density(density.density, density.fontScale * extraScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
