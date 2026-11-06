package com.maysk.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.AppSettings
import com.maysk.music.data.BackgroundStyle
import com.maysk.music.data.MiniPlayerStyle
import com.maysk.music.data.PlayerStyle
import com.maysk.music.data.ThemePreset
import com.maysk.music.data.VisualizerStyle
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.components.AppBackground
import com.maysk.music.ui.components.ChoiceChipRow
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.GradientButton
import com.maysk.music.ui.components.TextInputDialog
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.AccentPalette
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.ui.theme.LocalUi
import com.maysk.music.ui.theme.buildColors

@Composable
fun AppearanceScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val saved by a.container.settings.state.collectAsState()
    var draft by remember { mutableStateOf(saved) }
    val ctx = LocalContext.current
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) { }
            val next = draft.copy(customBgUri = uri.toString(), background = BackgroundStyle.IMAGE)
            draft = next
            a.container.settings.update {
                it.copy(customBgUri = next.customBgUri, background = next.background)
            }
        }
    }
    var tab by remember { mutableIntStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    var customThemes by remember { mutableStateOf(a.container.settings.customThemes()) }
    val changed = draft != saved

    Column(Modifier.fillMaxSize()) {
        TopBar("Оформление", onBack = { a.nav.pop() })
        ChoiceChipRow(listOf("Тема", "Цвет", "Плеер", "Интерфейс"), tab, { tab = it }, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("Предпросмотр", color = c.textDim, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            ThemePreview(draft)
            Spacer(Modifier.height(16.dp))

            when (tab) {
                0 -> {
                    ThemePreset.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            row.forEach { p ->
                                PresetCard(p, draft.theme == p, Modifier.weight(1f)) { draft = draft.copy(theme = p) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    SwitchLine("Светлая/тёмная как в системе", "В светлом режиме системы — Light Modern", draft.followSystem) { draft = draft.copy(followSystem = it) }
                    if (customThemes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Мои темы", color = c.text, style = MaterialTheme.typography.titleMedium)
                        customThemes.forEach { ct ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { draft = ct.settings.copy(excludedFolders = saved.excludedFolders, customUpdateRepo = saved.customUpdateRepo) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(ct.settings.accent)))
                                Spacer(Modifier.width(12.dp))
                                Text(ct.name, color = c.text, modifier = Modifier.weight(1f))
                                IconButton(onClick = { a.container.settings.deleteCustomTheme(ct.name); customThemes = a.container.settings.customThemes() }) {
                                    Icon(Icons.Rounded.Delete, "Удалить тему", tint = c.textDim)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Label("Основной цвет (акценты)")
                    ColorRow(draft.accent) { draft = draft.copy(accent = it) }
                    Label("Цвет кнопок")
                    ColorRow(draft.buttonColor) { draft = draft.copy(buttonColor = it) }
                    Label("Прозрачность карточек: ${(draft.cardAlpha * 100).toInt()}%")
                    Slider(draft.cardAlpha, { draft = draft.copy(cardAlpha = it) }, valueRange = 0.02f..0.4f)
                    Label("Скругление: ${draft.corner} dp")
                    Slider(draft.corner.toFloat(), { draft = draft.copy(corner = it.toInt()) }, valueRange = 4f..32f)
                    Label("Фон приложения")
                    ChoiceChipRow(BackgroundStyle.entries.map { it.title }, draft.background.ordinal, {
                        val bg = BackgroundStyle.entries[it]
                        draft = draft.copy(background = bg)
                        // Сразу применяем обои/фото, чтобы было видно без «Сохранить»
                        if (bg == BackgroundStyle.WALLPAPER || bg == BackgroundStyle.IMAGE) {
                            a.container.settings.update { s -> s.copy(background = bg, customBgUri = draft.customBgUri) }
                        }
                    })
                    if (draft.background == BackgroundStyle.IMAGE) {
                        Text(
                            if (draft.customBgUri.isBlank()) "Выберите фото из галереи — оно станет фоном приложения"
                            else "Фото выбрано. Можно заменить.",
                            color = LocalMaysk.current.textDim,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(onClick = { pickBg.launch("image/*") }) {
                                Text("Выбрать фото")
                            }
                            if (draft.customBgUri.isNotBlank()) {
                                androidx.compose.material3.TextButton(onClick = { draft = draft.copy(customBgUri = "") }) {
                                    Text("Убрать")
                                }
                            }
                        }
                    }
                    if (draft.background == BackgroundStyle.WALLPAPER) {
                        Text(
                            "Будут использованы текущие обои телефона (может не работать на некоторых оболочках).",
                            color = LocalMaysk.current.textDim,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                    SwitchLine("Мягкое свечение", "Неоновое свечение кнопок и обложек", draft.glow) { draft = draft.copy(glow = it) }
                }
                2 -> {
                    Label("Полноэкранный плеер")
                    ChoiceChipRow(PlayerStyle.entries.map { it.title }, draft.player.ordinal, { draft = draft.copy(player = PlayerStyle.entries[it]) })
                    Label("Мини-плеер")
                    ChoiceChipRow(MiniPlayerStyle.entries.map { it.title }, draft.miniPlayer.ordinal, { draft = draft.copy(miniPlayer = MiniPlayerStyle.entries[it]) })
                    Label("Размер обложек")
                    ChoiceChipRow(listOf("Маленькие", "Средние", "Большие"), draft.coverSize, { draft = draft.copy(coverSize = it) })
                    Label("Визуализация в плеере")
                    ChoiceChipRow(VisualizerStyle.entries.map { it.title }, draft.visualizer.ordinal, { draft = draft.copy(visualizer = VisualizerStyle.entries[it]) })
                    Text("Для визуализации Android попросит разрешение «Микрофон» — звук не записывается, анализируется только воспроизводимая музыка.",
                        color = c.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                else -> {
                    Label("Сетка альбомов")
                    ChoiceChipRow(listOf("2×2", "3×3", "4×4"), (draft.grid - 2).coerceIn(0, 2), { draft = draft.copy(grid = it + 2) })
                    Label("Размер текста: ${(draft.fontScale * 100).toInt()}%")
                    Slider(draft.fontScale, { draft = draft.copy(fontScale = it) }, valueRange = 0.85f..1.4f, steps = 10)
                    Label("Анимации")
                    val speeds = listOf(0f, 0.6f, 1f, 1.6f)
                    ChoiceChipRow(listOf("Выкл", "Медленно", "Обычно", "Быстро"), speeds.indexOf(draft.animSpeed).let { if (it < 0) 2 else it }, { draft = draft.copy(animSpeed = speeds[it]) })
                    SwitchLine("Простой режим", "Крупный текст и кнопки, минимум лишнего", draft.simpleMode) { draft = draft.copy(simpleMode = it) }
                    SwitchLine("Высокий контраст", "Более яркий текст и границы", draft.highContrast) { draft = draft.copy(highContrast = it) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCard(Modifier.weight(1f).height(48.dp), onClick = { naming = true }) {
                Text("Сохранить тему", color = c.text, modifier = Modifier.align(Alignment.Center))
            }
            GradientButton(if (changed) "Применить" else "Применено", Modifier.weight(1f), icon = Icons.Rounded.Check, enabled = changed) {
                a.container.settings.update { draft.copy(excludedFolders = it.excludedFolders, customUpdateRepo = it.customUpdateRepo) }
                a.toast("Оформление применено")
            }
        }
    }

    if (naming) {
        TextInputDialog("Название темы", onDismiss = { naming = false }) { name ->
            val n = name.trim().ifEmpty { "Моя тема" }
            a.container.settings.saveCustomTheme(n, draft)
            customThemes = a.container.settings.customThemes()
            naming = false
            a.toast("Тема «$n» сохранена")
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalMaysk.current.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

@Composable
private fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalMaysk.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text)
            Text(subtitle, color = c.textDim, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Composable
private fun ColorRow(selected: Long, onPick: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AccentPalette.forEach { col ->
            val sel = col == selected
            Box(
                Modifier.weight(1f).height(30.dp).clip(CircleShape).background(Color(col))
                    .border(if (sel) 3.dp else 0.dp, Color.White, CircleShape)
                    .clickable { onPick(col) },
                contentAlignment = Alignment.Center,
            ) { if (sel) Icon(Icons.Rounded.Check, "Выбран", tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun PresetCard(p: ThemePreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val col = buildColors(LocalUi.current.copy(theme = p, followSystem = false), null, true)
    GlassCard(modifier.height(96.dp), onClick = onClick, highlight = selected) {
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(col.bgTop, col.accent.copy(alpha = 0.55f), col.bgBottom))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(p.title, color = col.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.subtitle, color = col.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Rounded.Check, "Выбрано", tint = col.accent, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

/** Мини-копия интерфейса в выбранных настройках. */
@Composable
private fun ThemePreview(s: AppSettings) {
    val colors = buildColors(s, null, isSystemInDarkTheme())
    CompositionLocalProvider(LocalMaysk provides colors, LocalUi provides s) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(colors.corner)).border(1.dp, colors.border, RoundedCornerShape(colors.corner))) {
            AppBackground {
                Column(Modifier.padding(12.dp)) {
                    Text("Maysk Music", color = colors.text, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(colors.accent2, colors.accent3))))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Название трека", color = colors.accent, style = MaterialTheme.typography.bodyMedium)
                                Text("Исполнитель", color = colors.textDim, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Rounded.Pause, null, tint = colors.text)
                            Icon(Icons.Rounded.SkipNext, null, tint = colors.text)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Кнопка", Modifier.fillMaxWidth()) {}
                }
            }
        }
    }
}

