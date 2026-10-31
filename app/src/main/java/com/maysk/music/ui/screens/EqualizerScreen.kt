package com.maysk.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maysk.music.playback.AudioEffects
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.GradientButton
import com.maysk.music.ui.components.TextInputDialog
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk

@Composable
fun EqualizerScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val fx = a.container.effects
    val caps by fx.caps.collectAsState()
    val eq by a.container.userData.eq.collectAsState()
    var profileMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val levels = if (eq.bandLevels.size == caps.bandFreqsHz.size) eq.bandLevels else caps.bandFreqsHz.map { 0 }
    val profiles = AudioEffects.PRESETS.keys.toList() + eq.customProfiles.keys

    Column(Modifier.fillMaxSize()) {
        TopBar("Эквалайзер", onBack = { a.nav.pop() }) {
            Switch(
                checked = eq.enabled,
                onCheckedChange = { v -> fx.update { it.copy(enabled = v, bandLevels = if (it.bandLevels.isEmpty()) levels else it.bandLevels) } },
                colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = "Включить эффекты" },
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            if (!caps.available) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Эквалайзер на этом устройстве недоступен (или занят системным аудиоэффектом). " +
                            "Доступные эффекты показаны ниже.",
                        color = c.textDim, modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                // Профиль
                Box {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { profileMenu = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Профиль: ${eq.profile}", color = c.text, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.ExpandMore, null, tint = c.text)
                        }
                    }
                    DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                        profiles.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                profileMenu = false
                                val l = fx.levelsForProfile(name)
                                fx.update { it.copy(profile = name, bandLevels = l, enabled = true) }
                            })
                        }
                        if (eq.profile in eq.customProfiles) {
                            DropdownMenuItem(text = { Text("Удалить профиль «${eq.profile}»", color = MaterialTheme.colorScheme.error) }, onClick = {
                                profileMenu = false
                                fx.update { it.copy(customProfiles = it.customProfiles - it.profile, profile = "Flat", bandLevels = fx.levelsForProfile("Flat")) }
                            })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            caps.bandFreqsHz.forEachIndexed { i, hz ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("%+.0f".format(levels.getOrElse(i) { 0 } / 100f), color = c.textDim, style = MaterialTheme.typography.labelSmall)
                                    VerticalSlider(
                                        value = levels.getOrElse(i) { 0 }.toFloat(),
                                        range = caps.minMb.toFloat()..caps.maxMb.toFloat(),
                                        enabled = eq.enabled,
                                        label = "Полоса ${formatHz(hz)}",
                                    ) { v ->
                                        val newLevels = levels.toMutableList().also { it[i] = v.toInt() }
                                        fx.update { it.copy(bandLevels = newLevels, profile = "Пользовательский") }
                                    }
                                    Text(formatHz(hz), color = c.textDim, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    EffectRow("Bass Boost", "Усиление басов", caps.bassSupported, eq.bassEnabled, eq.bassBoost / 1000f, eq.enabled,
                        onToggle = { v -> fx.update { it.copy(bassEnabled = v, enabled = true) } },
                        onValue = { v -> fx.update { it.copy(bassBoost = (v * 1000).toInt()) } })
                    EffectRow("Virtualizer", "Объёмный звук в наушниках", caps.virtSupported, eq.virtEnabled, eq.virtualizer / 1000f, eq.enabled,
                        onToggle = { v -> fx.update { it.copy(virtEnabled = v, enabled = true) } },
                        onValue = { v -> fx.update { it.copy(virtualizer = (v * 1000).toInt()) } })
                    EffectRow("Усиление громкости", "Для тихих записей (до +15 дБ)", caps.loudnessSupported, eq.loudnessEnabled, eq.loudnessGainMb / 1500f, eq.enabled,
                        onToggle = { v -> fx.update { it.copy(loudnessEnabled = v, enabled = true) } },
                        onValue = { v -> fx.update { it.copy(loudnessGainMb = (v * 1500).toInt()) } })
                }
            }
            Spacer(Modifier.height(12.dp))
            BalanceFadeCard()
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("Сохранить профиль", Modifier.weight(1f), enabled = caps.available) { saving = true }
                GlassCard(Modifier.weight(1f).height(48.dp), onClick = {
                    fx.update { it.copy(profile = "Flat", bandLevels = fx.levelsForProfile("Flat"), bassEnabled = false, virtEnabled = false, loudnessEnabled = false) }
                }) {
                    Text("Сбросить", color = c.text, modifier = Modifier.align(Alignment.Center))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Эффекты работают через встроенный аудиодвижок Android. Бесшовное воспроизведение (gapless) " +
                    "включено автоматически для форматов, которые его поддерживают.",
                color = c.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (saving) {
        TextInputDialog("Название профиля", onDismiss = { saving = false }) { name ->
            val n = name.trim().ifEmpty { "Мой профиль" }
            fx.update { it.copy(customProfiles = it.customProfiles + (n to levels), profile = n) }
            saving = false
            a.toast("Профиль «$n» сохранён")
        }
    }
}

@Composable
private fun BalanceFadeCard() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val settings by a.container.settings.state.collectAsState()
    var bal by remember(settings.balance) { mutableStateOf(settings.balance) }
    var fade by remember(settings.crossfadeSec) { mutableStateOf(settings.crossfadeSec.toFloat()) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Баланс L/R", color = c.text, modifier = Modifier.weight(1f))
                Text(
                    when {
                        kotlin.math.abs(bal) < 0.02f -> "По центру"
                        bal < 0 -> "Левее ${(-bal * 100).toInt()}%"
                        else -> "Правее ${(bal * 100).toInt()}%"
                    },
                    color = c.textDim, style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("L", color = c.textDim)
                Slider(
                    value = bal,
                    onValueChange = { bal = if (kotlin.math.abs(it) < 0.04f) 0f else it },
                    onValueChangeFinished = { a.container.settings.update { it.copy(balance = bal) } },
                    valueRange = -1f..1f,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("R", color = c.textDim)
            }
            if (bal != 0f) {
                Text(
                    "Сбросить в центр",
                    color = c.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { bal = 0f; a.container.settings.update { it.copy(balance = 0f) } }.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Плавный переход между треками", color = c.text, modifier = Modifier.weight(1f))
                Text(if (fade < 1f) "Выкл" else "${fade.toInt()} сек", color = c.textDim, style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = fade,
                onValueChange = { fade = it },
                onValueChangeFinished = { a.container.settings.update { it.copy(crossfadeSec = fade.toInt()) } },
                valueRange = 0f..12f,
                steps = 11,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
            )
            Text(
                "Конец трека плавно затихает, следующий плавно нарастает. Баланс работает для стерео-записей.",
                color = c.textDim, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatHz(hz: Int): String = if (hz >= 1000) "%.1fk".format(hz / 1000f).replace(".0k", "k") else "$hz"

@Composable
private fun EffectRow(
    title: String,
    subtitle: String,
    supported: Boolean,
    on: Boolean,
    value: Float,
    masterOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onValue: (Float) -> Unit,
) {
    val c = LocalMaysk.current
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = if (supported) c.text else c.textDim)
                Text(if (supported) subtitle else "Не поддерживается устройством", color = c.textDim, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = on && supported,
                onCheckedChange = onToggle,
                enabled = supported,
                colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
            )
        }
        if (supported && on) {
            var local by remember(value) { mutableStateOf(value) }
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onValue(local) },
                enabled = masterOn,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
            )
        }
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    label: String,
    onChange: (Float) -> Unit,
) {
    val c = LocalMaysk.current
    val heightDp = 190.dp
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shown = dragValue ?: value
    val cb by rememberUpdatedState(onChange)
    fun toValue(y: Float, h: Float): Float {
        val f = (1f - (y / h)).coerceIn(0f, 1f)
        return range.start + f * (range.endInclusive - range.start)
    }
    Canvas(
        Modifier
            .width(34.dp)
            .height(heightDp)
            .semantics { contentDescription = label }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { o -> dragValue = toValue(o.y, size.height.toFloat()) },
                    onDragEnd = { dragValue?.let { cb(it) }; dragValue = null },
                    onDragCancel = { dragValue = null },
                ) { change, _ ->
                    dragValue = toValue(change.position.y, size.height.toFloat())
                }
            }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> cb(toValue(o.y, size.height.toFloat())) }
            }
    ) {
        val f = ((shown - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val trackW = 6.dp.toPx()
        val x = size.width / 2
        drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(x - trackW / 2, 0f),
            size = Size(trackW, size.height),
            cornerRadius = CornerRadius(trackW / 2, trackW / 2),
        )
        val y = size.height * (1f - f)
        val mid = size.height / 2
        val top = minOf(y, mid)
        val bottom = maxOf(y, mid)
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(c.accent3, c.accent, c.accent2)),
            topLeft = Offset(x - trackW / 2, top),
            size = Size(trackW, (bottom - top).coerceAtLeast(1f)),
            cornerRadius = CornerRadius(trackW / 2, trackW / 2),
            alpha = if (enabled) 1f else 0.35f,
        )
        drawCircle(if (enabled) Color.White else Color.Gray, radius = 9.dp.toPx(), center = Offset(x, y))
        drawCircle(c.accent, radius = 5.dp.toPx(), center = Offset(x, y))
    }
}
