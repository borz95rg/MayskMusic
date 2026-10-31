package com.maysk.music.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.maysk.music.data.PlayerStyle
import com.maysk.music.data.Track
import com.maysk.music.data.VisualizerStyle
import com.maysk.music.data.formatTime
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.SleepTimerDialog
import com.maysk.music.ui.components.TextInputDialog
import com.maysk.music.ui.components.TrackDetailsDialog
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.ui.theme.LocalUi
import java.io.File
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val ui = LocalUi.current
    val player = a.container.player
    val state by player.state.collectAsState()
    val position by player.position.collectAsState()
    val lib by a.container.library.library.collectAsState()
    val favorites by a.container.userData.favorites.collectAsState()
    val sleep by a.container.sleepTimer.state.collectAsState()
    val track: Track? = state.currentId?.let { lib.byId[it] }
    val isFav = state.currentId != null && state.currentId in favorites

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    // Перетаскивание ползунка
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxSize().background(c.bgBottom).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        // Размытый фон из обложки
        Artwork(state.artUri, Modifier.fillMaxSize().blur(60.dp), corner = 0.dp, seed = state.currentId ?: 0L, iconSize = 1.dp)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(c.bgTop.copy(alpha = 0.72f), c.bgBottom.copy(alpha = 0.92f), c.bgBottom)
                )
            )
        )

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Верхняя панель
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", tint = c.text, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Сейчас играет", color = c.textDim, style = MaterialTheme.typography.labelMedium)
                    if (sleep.active) Text(
                        if (sleep.endOfTrack) "⏾ до конца трека" else "⏾ таймер сна",
                        color = c.accent, style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { onClose(); a.nav.push(Route.Equalizer) }) { Icon(Icons.Rounded.Equalizer, "Эквалайзер", tint = c.text) }
            }

            if (showLyrics && track != null) {
                LyricsPanel(track, position, Modifier.weight(1f)) { showLyrics = false }
            } else {
                // Обложка
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight) * (if (ui.player == PlayerStyle.MINIMAL) 0.7f else 0.96f)
                    ArtworkView(state.artUri, state.currentId ?: 0L, state.isPlaying, ui.player, Modifier.size(side))
                    if (ui.visualizer != VisualizerStyle.OFF) {
                        VisualizerView(ui.visualizer, state.isPlaying, Modifier.size(side).align(Alignment.Center))
                    }
                }
            }

            // Название и избранное
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.title.ifEmpty { "Ничего не играет" }, color = c.text, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.artist, color = c.textDim, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = track != null) { track?.let { onClose(); a.nav.push(Route.ArtistR(it.artist)) } })
                    if (track != null && !ui.simpleMode) Text(track.album, color = c.textDim.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onClose(); a.nav.push(Route.AlbumR(track.albumId)) })
                }
                IconButton(onClick = { state.currentId?.let { a.container.userData.toggleFavorite(it) } }, enabled = state.currentId != null) {
                    Icon(if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isFav) "Убрать из любимого" else "В любимое",
                        tint = if (isFav) c.accent3 else c.text, modifier = Modifier.size(30.dp))
                }
            }

            // Прогресс
            val dur = state.durationMs.coerceAtLeast(1L)
            val shown = if (seeking) seekValue else (position.toFloat() / dur).coerceIn(0f, 1f)
            Slider(
                value = shown,
                onValueChange = { seeking = true; seekValue = it },
                onValueChangeFinished = { player.seekTo((seekValue * dur).toLong()); seeking = false },
                colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.accent, inactiveTrackColor = c.text.copy(alpha = 0.18f)),
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
                Text(formatTime(if (seeking) (seekValue * dur).toLong() else position), color = c.textDim, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(formatTime(state.durationMs), color = c.textDim, style = MaterialTheme.typography.labelMedium)
            }

            // Управление
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, if (state.shuffle) "Перемешивание включено" else "Перемешивание выключено",
                        tint = if (state.shuffle) c.accent else c.textDim)
                }
                IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Предыдущий", tint = c.text, modifier = Modifier.size(40.dp))
                }
                val big = if (ui.simpleMode) 88.dp else 76.dp
                Box(
                    Modifier
                        .size(big)
                        .then(if (c.glow) Modifier.shadow(24.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.button, c.accent3)))
                        .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Пауза" else "Играть",
                        tint = Color.White, modifier = Modifier.size(44.dp))
                }
                IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Следующий", tint = c.text, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when (state.repeatMode) { Player.REPEAT_MODE_ONE -> "Повтор трека"; Player.REPEAT_MODE_ALL -> "Повтор всех"; else -> "Повтор выключен" },
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) c.accent else c.textDim,
                    )
                }
            }

            // Нижние кнопки
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                BottomAction(Icons.Rounded.Notes, "Текст", active = showLyrics) { showLyrics = !showLyrics }
                BottomAction(Icons.Rounded.QueueMusic, "Очередь") { showQueue = true }
                BottomAction(Icons.Rounded.GraphicEq, "Эффекты") { onClose(); a.nav.push(Route.Equalizer) }
                BottomAction(Icons.Rounded.Timer, "Таймер", active = sleep.active) { showTimer = true }
                Box {
                    BottomAction(Icons.Rounded.MoreHoriz, "Ещё") { moreMenu = true }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        if (track != null) {
                            DropdownMenuItem(text = { Text("Добавить в плейлист") }, onClick = { moreMenu = false; a.addToPlaylist(listOf(track)) })
                            DropdownMenuItem(text = { Text("Информация о файле") }, onClick = { moreMenu = false; showDetails = true })
                        }
                        PlayerStyle.entries.forEach { s ->
                            DropdownMenuItem(text = { Text("Вид плеера: ${s.title}" + if (s == ui.player) " ✓" else "") }, onClick = {
                                moreMenu = false; a.container.settings.update { it.copy(player = s) }
                            })
                        }
                        VisualizerStyle.entries.forEach { s ->
                            DropdownMenuItem(text = { Text("Визуализация: ${s.title}" + if (s == ui.visualizer) " ✓" else "") }, onClick = {
                                moreMenu = false; a.container.settings.update { it.copy(visualizer = s) }
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showQueue) QueueSheet { showQueue = false }
    if (showTimer) SleepTimerDialog { showTimer = false }
    if (showDetails && track != null) TrackDetailsDialog(track) { showDetails = false }
}

@Composable
private fun BottomAction(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val c = LocalMaysk.current
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (active) c.accent.copy(alpha = 0.3f) else c.card.copy(alpha = c.glassAlpha))
                .border(1.dp, c.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (active) c.accent else c.text, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = c.textDim, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ArtworkView(art: Any?, seed: Long, playing: Boolean, style: PlayerStyle, modifier: Modifier) {
    val c = LocalMaysk.current
    val ui = LocalUi.current
    val anim = ui.animationsEnabled
    val scale by animateFloatAsState(if (playing || !anim) 1f else 0.9f, tween(if (anim) (350 / ui.animSpeed).toInt() else 0), label = "scale")
    when (style) {
        PlayerStyle.VINYL -> {
            val angle = remember { Animatable(0f) }
            LaunchedEffect(playing, anim) {
                if (playing && anim) {
                    val dur = (12000 / ui.animSpeed.coerceAtLeast(0.25f)).toInt()
                    while (isActive) {
                        angle.snapTo(angle.value % 360f)
                        angle.animateTo(angle.value + 360f, tween(dur, easing = LinearEasing))
                    }
                }
            }
            Box(modifier.scale(scale), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF222222), Color(0xFF050505))))
                        .border(2.dp, c.accent.copy(alpha = 0.5f), CircleShape)
                )
                Artwork(art, Modifier.squareFraction(0.62f).rotate(angle.value % 360f), corner = 1000.dp, seed = seed, iconSize = 64.dp)
                Box(Modifier.size(18.dp).clip(CircleShape).background(c.bgBottom))
            }
        }
        else -> {
            Box(
                modifier.scale(scale)
                    .then(if (c.glow) Modifier.shadow(30.dp, RoundedCornerShape(28.dp), ambientColor = c.accent, spotColor = c.accent) else Modifier)
            ) {
                Artwork(art, Modifier.fillMaxSize(), corner = 28.dp, seed = seed, iconSize = 96.dp)
            }
        }
    }
}

private fun Modifier.squareFraction(f: Float): Modifier = this.then(Modifier.fillMaxWidth(f).aspectRatio(1f))

// ---------------------------------------------------------------------------
// Визуализатор (android.media.audiofx.Visualizer, нужно разрешение «Микрофон»)
// ---------------------------------------------------------------------------

@Composable
private fun VisualizerView(style: VisualizerStyle, playing: Boolean, modifier: Modifier) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) {
            a.toast("Без этого разрешения визуализация недоступна")
            a.container.settings.update { it.copy(visualizer = VisualizerStyle.OFF) }
        }
    }
    if (!granted) {
        Box(modifier, contentAlignment = Alignment.BottomCenter) {
            TextButton(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Разрешить доступ к аудио для визуализации", color = c.text, textAlign = TextAlign.Center)
            }
        }
        return
    }
    var bars by remember { mutableStateOf(FloatArray(32)) }
    val sessionId = a.container.audioSessionId
    DisposableEffect(sessionId, playing) {
        var vis: Visualizer? = null
        if (playing) {
            vis = try {
                Visualizer(sessionId).apply {
                    setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024))
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null) bars = fftToBars(fft, bars)
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    setEnabled(true)
                }
            } catch (e: Exception) { null }
        }
        onDispose {
            try { vis?.setEnabled(false); vis?.release() } catch (_: Exception) { }
            bars = FloatArray(32)
        }
    }
    val values = bars
    Canvas(modifier) {
        when (style) {
            VisualizerStyle.SPECTRUM -> {
                val h = size.height * 0.28f
                val w = size.width / values.size
                values.forEachIndexed { i, v ->
                    val bh = (v * h).coerceAtLeast(2f)
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(c.accent3, c.accent, c.accent2), startY = size.height - bh, endY = size.height),
                        topLeft = Offset(i * w + w * 0.15f, size.height - bh),
                        size = Size(w * 0.7f, bh),
                        cornerRadius = CornerRadius(w * 0.35f, w * 0.35f),
                        alpha = 0.9f,
                    )
                }
            }
            VisualizerStyle.WAVES -> {
                val mid = size.height * 0.88f
                val step = size.width / (values.size - 1)
                for (layer in 0..2) {
                    val amp = size.height * (0.12f - layer * 0.03f)
                    var prev = Offset(0f, mid)
                    values.forEachIndexed { i, v ->
                        val p = Offset(i * step, mid - v * amp * (if (i % 2 == 0) 1f else -1f))
                        if (i > 0) drawLine(listOf(c.accent, c.accent3, c.accent2)[layer], prev, p, strokeWidth = 4f - layer, alpha = 0.8f - layer * 0.2f)
                        prev = p
                    }
                }
            }
            VisualizerStyle.CIRCLE -> {
                val center = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2 * 1.02f
                drawCircle(c.accent.copy(alpha = 0.6f), radius = r, center = center, style = Stroke(3f))
                values.forEachIndexed { i, v ->
                    val ang = (i.toFloat() / values.size) * 2 * PI.toFloat()
                    val len = 6f + v * size.minDimension * 0.12f
                    val s = Offset(center.x + cos(ang) * r, center.y + sin(ang) * r)
                    val e = Offset(center.x + cos(ang) * (r + len), center.y + sin(ang) * (r + len))
                    drawLine(if (i % 2 == 0) c.accent3 else c.accent2, s, e, strokeWidth = 5f)
                }
            }
            VisualizerStyle.OFF -> {}
        }
    }
}

private fun fftToBars(fft: ByteArray, prev: FloatArray): FloatArray {
    val n = fft.size / 2
    val out = FloatArray(prev.size)
    if (n < 4) return out
    for (b in out.indices) {
        // логарифмическое распределение частот
        val lo = (Math.pow(n.toDouble(), b.toDouble() / out.size)).toInt().coerceIn(1, n - 1)
        val hi = (Math.pow(n.toDouble(), (b + 1).toDouble() / out.size)).toInt().coerceIn(lo + 1, n)
        var m = 0.0
        for (k in lo until hi) {
            val re = fft[2 * k].toDouble()
            val im = fft[2 * k + 1].toDouble()
            m = maxOf(m, hypot(re, im))
        }
        val db = if (m > 0) 20 * log10(m) else 0.0
        val v = (db / 42.0).toFloat().coerceIn(0f, 1f)
        out[b] = maxOf(v, prev[b] * 0.82f) // плавное затухание
    }
    return out
}

// ---------------------------------------------------------------------------
// Очередь
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val state by a.container.player.state.collectAsState()
    val lib by a.container.library.library.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState()
    val posInOrder = state.order.indexOf(state.currentIndex).coerceAtLeast(0)
    LaunchedEffect(Unit) { if (posInOrder > 1) listState.scrollToItem(posInOrder - 1) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Очередь", style = MaterialTheme.typography.titleLarge, color = c.text)
                Text("${state.items.size} треков" + if (state.shuffle) " · перемешано" else "", color = c.textDim, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { a.container.player.clearQueue(); onDismiss() }) { Text("Очистить") }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight(0.75f), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(state.order, key = { _, w -> "q_" + w + "_" + state.items.getOrNull(w) }) { pos, window ->
                val id = state.items.getOrNull(window)?.toLongOrNull()
                val t = id?.let { lib.byId[it] }
                val current = window == state.currentIndex
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (current) c.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { a.container.player.skipToIndex(window) }
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t?.artUri, Modifier.size(44.dp), corner = 8.dp, seed = t?.albumId ?: 0L)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t?.title ?: "Файл", color = if (current) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                        Text(t?.artist ?: "", color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!state.shuffle) {
                        IconButton(onClick = { a.container.player.move(window, window - 1) }, enabled = window > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Выше", tint = c.textDim)
                        }
                        IconButton(onClick = { a.container.player.move(window, window + 1) }, enabled = window < state.items.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Ниже", tint = c.textDim)
                        }
                    }
                    IconButton(onClick = { a.container.player.removeAt(window) }, enabled = !current) {
                        Icon(Icons.Rounded.Close, "Убрать из очереди", tint = c.textDim)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Тексты песен (LRC и обычный текст)
// ---------------------------------------------------------------------------

data class LyricLine(val timeMs: Long, val text: String)

object Lrc {
    private val tag = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(text: String): Pair<List<LyricLine>, Boolean> {
        val lines = mutableListOf<LyricLine>()
        var synced = false
        text.lines().forEach { raw ->
            val matches = tag.findAll(raw).toList()
            if (matches.isEmpty()) {
                val t = raw.trim()
                if (t.isNotEmpty() && !Regex("^\\[[a-zA-Z]+:.*]$").matches(t)) lines += LyricLine(-1, t)
            } else {
                synced = true
                val content = raw.substring(matches.last().range.last + 1).trim()
                matches.forEach { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                    lines += LyricLine(min * 60_000 + sec * 1000 + ms, content)
                }
            }
        }
        return if (synced) lines.filter { it.timeMs >= 0 }.sortedBy { it.timeMs } to true else lines to false
    }
}

@Composable
private fun LyricsPanel(track: Track, position: Long, modifier: Modifier, onClose: () -> Unit) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    val store = a.container.userData
    val allLyrics by store.lyrics.collectAsState()
    var fontSize by remember { mutableFloatStateOf(20f) }
    var editing by remember { mutableStateOf(false) }

    // Пытаемся найти .lrc рядом с аудиофайлом (работает, если Android разрешает чтение)
    LaunchedEffect(track.id) {
        if (allLyrics[track.id] == null) {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val f = File(track.path.substringBeforeLast('.') + ".lrc")
                    if (f.canRead()) f.readText() else null
                } catch (e: Exception) { null }
            }
            if (!text.isNullOrBlank()) store.setLyrics(track.id, text)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (e: Exception) { null }
            if (text.isNullOrBlank()) a.toast("Не удалось прочитать файл") else { store.setLyrics(track.id, text); a.toast("Текст добавлен") }
        }
    }

    val raw = allLyrics[track.id]
    val parsed = remember(raw) { raw?.let { Lrc.parse(it) } }
    val listState = rememberLazyListState()
    val currentLine = parsed?.takeIf { it.second }?.first?.indexOfLast { it.timeMs <= position + 300 } ?: -1
    LaunchedEffect(currentLine) {
        if (currentLine >= 0) runCatching { listState.animateScrollToItem((currentLine - 2).coerceAtLeast(0)) }
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Текст песни", color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { fontSize = (fontSize - 2f).coerceAtLeast(14f) }) { Icon(Icons.Rounded.Remove, "Меньше", tint = c.text) }
            IconButton(onClick = { fontSize = (fontSize + 2f).coerceAtMost(36f) }) { Icon(Icons.Rounded.Add, "Больше", tint = c.text) }
            IconButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.FolderOpen, "Загрузить .lrc", tint = c.text) }
            IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "Ввести текст", tint = c.text) }
            if (raw != null) IconButton(onClick = { store.setLyrics(track.id, null) }) { Icon(Icons.Rounded.Delete, "Удалить текст", tint = c.text) }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть", tint = c.text) }
        }
        if (parsed == null || parsed.first.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Текст для этого трека не найден", color = c.text, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Загрузите файл .lrc (синхронизированный текст) или введите текст вручную. " +
                        "Тексты из интернета не загружаются — только ваши файлы.",
                    color = c.textDim, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 40.dp)) {
                itemsIndexed(parsed.first) { i, line ->
                    val active = i == currentLine
                    Text(
                        line.text.ifEmpty { "♪" },
                        color = if (active) c.text else c.textDim.copy(alpha = if (parsed.second) 0.55f else 0.9f),
                        fontSize = (if (active) fontSize + 4 else fontSize).sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = parsed.second && line.timeMs >= 0) { a.container.player.seekTo(line.timeMs) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (editing) {
        TextInputDialog(
            title = "Текст песни",
            initial = raw ?: "",
            label = "Текст или LRC ([мм:сс.xx] строка)",
            singleLine = false,
            onDismiss = { editing = false },
        ) { store.setLyrics(track.id, it); editing = false }
    }
}
