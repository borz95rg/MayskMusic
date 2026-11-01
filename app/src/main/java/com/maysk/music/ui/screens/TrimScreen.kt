package com.maysk.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.FileOps
import com.maysk.music.data.Track
import com.maysk.music.data.formatTime
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.EmptyState
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrimScreen(initialTrackId: Long = -1L) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val ctx = LocalContext.current
    val lib by a.container.library.library.collectAsState()
    val playerState by a.container.player.state.collectAsState()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Track?>(null) }
    var startMs by remember { mutableStateOf(0f) }
    var endMs by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
   var saveName by remember(selected) {
    mutableStateOf(
        selected?.let { t ->
            t.title.take(40).ifBlank { t.fileName.substringBeforeLast('.') }
        } ?: ""
    )
}
    var outFormat by remember { mutableStateOf("m4a") }  // m4a | mp3
    var showSaveDialog by remember { mutableStateOf(false) }

    // Текущий трек из плеера или initialTrackId
    LaunchedEffect(lib.tracks, initialTrackId, playerState.currentId) {
        if (selected != null) return@LaunchedEffect
        val id = when {
            initialTrackId > 0 -> initialTrackId
            playerState.currentId != null -> playerState.currentId
            else -> null
        }
        if (id != null) {
            val t = lib.tracks.find { it.id == id }
            if (t != null) {
                selected = t
                startMs = 0f
                endMs = t.durationMs.toFloat().coerceAtLeast(1000f)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Обрезать трек", onBack = {
            if (selected != null && initialTrackId <= 0 && playerState.currentId == null) {
                selected = null
            } else {
                a.nav.pop()
            }
        })

        if (lib.tracks.isEmpty()) {
            EmptyState(
                Icons.Rounded.ContentCut,
                "Нет треков",
                "Добавьте музыку в библиотеку, чтобы обрезать",
            )
            return@Column
        }

        val track = selected
        if (track == null) {
            Text(
                "Выберите трек",
                color = c.textDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(lib.tracks.take(300), key = { it.id }) { t ->
                    GlassCard(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = t
                                startMs = 0f
                                endMs = t.durationMs.toFloat().coerceAtLeast(1000f)
                            },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(t.artUri, Modifier.size(48.dp), corner = 10.dp, seed = t.albumId)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${t.artist} · ${formatTime(t.durationMs)}",
                                    color = c.textDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.artUri, Modifier.size(64.dp), corner = 12.dp, seed = track.albumId)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = c.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatTime(track.durationMs), color = c.textDim, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text("Фрагмент", color = c.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                val max = track.durationMs.toFloat().coerceAtLeast(1000f)
                RangeSlider(
                    value = startMs..endMs,
                    onValueChange = { range ->
                        startMs = range.start.coerceIn(0f, max - 500f)
                        endMs = range.endInclusive.coerceIn(startMs + 500f, max)
                    },
                    valueRange = 0f..max,
                    colors = SliderDefaults.colors(
                        thumbColor = c.accent,
                        activeTrackColor = c.accent,
                        inactiveTrackColor = c.text.copy(alpha = 0.15f),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(startMs.toLong()), color = c.textDim, style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatTime((endMs - startMs).toLong()),
                        color = c.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(formatTime(endMs.toLong()), color = c.textDim, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = {
                        a.container.player.playTracks(listOf(track), 0)
                        a.container.player.seekTo(startMs.toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Прослушать фрагмент")
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Копия сохранится в Music/Maysk/. Оригинал не меняется.",
                    color = c.textDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))

                if (busy) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = c.accent, strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Обрезка…", color = c.textDim)
                    }
                } else {
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.ContentCut, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранить…")
                    }
                }
            }
        }
    }

    if (showSaveDialog && track != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) showSaveDialog = false },
            title = { Text("Сохранить обрезку") },
            text = {
                Column {
                    Text("Название", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Формат", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = outFormat == "m4a",
                            onClick = { outFormat = "m4a" },
                            label = { Text("M4A (AAC)") },
                        )
                        FilterChip(
                            selected = outFormat == "mp3",
                            onClick = { outFormat = "mp3" },
                            label = { Text("MP3") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (outFormat == "mp3")
                            "На Android файл будет AAC в контейнере M4A (расширение .m4a) — так надёжнее. Качество как у MP3 128k."
                        else
                            "M4A (AAC) — лучшая совместимость с обрезкой.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textDim,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                FileOps.trimTrack(
                                    ctx, track, startMs.toLong(), endMs.toLong(),
                                    asCopy = true,
                                    customName = saveName,
                                    format = outFormat,
                                )
                            }
                            busy = false
                            if (result.error != null) {
                                a.toast(result.error)
                            } else {
                                a.toast("Сохранено в Music/Maysk/")
                                a.container.library.rescan()
                                a.nav.pop()
                            }
                        }
                    },
                    enabled = saveName.isNotBlank(),
                ) { Text("Сохранить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveDialog = false }) { Text("Отмена") }
            },
        )
    }
}
