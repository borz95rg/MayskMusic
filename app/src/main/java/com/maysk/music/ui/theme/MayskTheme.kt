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
        ThemePreset.OCEAN -> MayskColors(false, Color(0xFF0A1628), Color(0xFF020B16), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.10f),
            Color(0xFF38BDF8), Color(0xFF0EA5E9), Color(0xFF22D3EE), button, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background, s.highContrast)
        ThemePreset.FOREST -> MayskColors(false, Color(0xFF0C1A12), Color(0xFF040A07), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.10f),
            Color(0xFF22C55E), Color(0xFF16A34A), Color(0xFF84CC16), button, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background, s.highContrast)
        ThemePreset.SUNSET -> MayskColors(false, Color(0xFF1A0B10), Color(0xFF0C0408), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.10f),
            Color(0xFFF97316), Color(0xFFEF4444), Color(0xFFFBBF24), button, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background, s.highContrast)
        ThemePreset.ROSE -> MayskColors(false, Color(0xFF1A0A14), Color(0xFF0C050A), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.10f),
            Color(0xFFF472B6), Color(0xFFEC4899), Color(0xFFFB7185), button, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background, s.highContrast)
        ThemePreset.CYBER -> MayskColors(false, Color(0xFF0A0018), Color(0xFF020008), Color(0xFF00F0FF), s.cardAlpha + 0.04f, Color(0xFF00F0FF).copy(alpha = 0.25f),
            Color(0xFF00F0FF), Color(0xFFFF00E5), Color(0xFF7CFF00), button, Color.White, Color.White.copy(alpha = 0.7f), true, s.corner.dp, s.background, s.highContrast)
        ThemePreset.SAND -> MayskColors(true, Color(0xFFF7F0E4), Color(0xFFEDE3D0), Color.White, 0.8f, Color(0x1A000000),
            Color(0xFFD97706), Color(0xFFB45309), Color(0xFFCA8A04), button, Color(0xFF292018), Color(0xFF292018).copy(alpha = 0.6f), false, s.corner.dp, s.background, s.highContrast)
        ThemePreset.MIDNIGHT -> MayskColors(false, Color(0xFF080818), Color(0xFF02020A), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.08f),
            Color(0xFF6366F1), Color(0xFF818CF8), Color(0xFFA78BFA), button, Color.White, Color.White.copy(alpha = 0.6f), s.glow, s.corner.dp, s.background, s.highContrast)
        ThemePreset.EMERALD -> MayskColors(false, Color(0xFF061A14), Color(0xFF020C09), Color.White, s.cardAlpha, Color.White.copy(alpha = 0.10f),
            Color(0xFF10B981), Color(0xFF34D399), Color(0xFF6EE7B7), button, Color.White, Color.White.copy(alpha = 0.65f), s.glow, s.corner.dp, s.background, s.highContrast)
        ThemePreset.LAVENDER -> MayskColors(true, Color(0xFFF3EEFF), Color(0xFFE8E0FA), Color.White, 0.8f, Color(0x1A000000),
            Color(0xFF8B5CF6), Color(0xFFA78BFA), Color(0xFFC4B5FD), button, Color(0xFF1E1533), Color(0xFF1E1533).copy(alpha = 0.6f), false, s.corner.dp, s.background, s.highContrast)
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
