package com.maysk.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.Track
import com.maysk.music.data.formatTime
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.MixKind
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.ChoiceChipRow
import com.maysk.music.ui.components.ConfirmDialog
import com.maysk.music.ui.components.EmptyState
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.IconTile
import com.maysk.music.ui.components.SectionHeader
import com.maysk.music.ui.components.SleepTimerDialog
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.ui.theme.LocalUi
import java.util.Calendar

private data class Tile(val title: String, val sub: String?, val icon: ImageVector, val tint: Color, val action: () -> Unit)

@Composable
fun MyMusicScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val fav by a.container.userData.favorites.collectAsState()
    val history by a.container.userData.history.collectAsState()
    val sleep by a.container.sleepTimer.state.collectAsState()
    var timer by remember { mutableStateOf(false) }
    val cols = if (LocalUi.current.simpleMode) 2 else 3

    val tiles = listOf(
        Tile("Maysk AI", null, Icons.Rounded.AutoAwesome, c.accent3) { a.nav.push(Route.Ai) },
        Tile("Голос", null, Icons.Rounded.Mic, c.accent2) { a.voice() },
        Tile("Любимое", "${lib.tracks.count { it.id in fav }}", Icons.Rounded.Favorite, c.accent3) { a.nav.push(Route.MixR(MixKind.FAVORITES)) },
        Tile("История", "${history.size}", Icons.Rounded.History, c.accent2) { a.nav.push(Route.MixR(MixKind.HISTORY)) },
        Tile("Часто слушаю", null, Icons.Rounded.TrendingUp, c.accent) { a.nav.push(Route.MixR(MixKind.FREQUENT)) },
        Tile("Новые", null, Icons.Rounded.NewReleases, Color(0xFF14B8A6)) { a.nav.push(Route.MixR(MixKind.RECENT_ADDED)) },
        Tile("Папки", "${lib.folders.size}", Icons.Rounded.Folder, c.accent) { a.nav.push(Route.Folders) },
        Tile("Альбомы", "${lib.albums.size}", Icons.Rounded.Album, c.accent) { a.nav.push(Route.LibraryTab(1)) },
        Tile("Исполнители", "${lib.artists.size}", Icons.Rounded.Person, c.accent2) { a.nav.push(Route.LibraryTab(2)) },
        Tile("Жанры", "${lib.genres.size}", Icons.Rounded.Category, Color(0xFFF97316)) { a.nav.push(Route.LibraryTab(3)) },
        Tile("Обрезать трек", null, Icons.Rounded.ContentCut, Color(0xFFEC4899)) { a.nav.push(Route.Trim) },
        Tile("Из видео", null, Icons.Rounded.VideoFile, Color(0xFF8B5CF6)) { a.nav.push(Route.ExtractAudio) },
        Tile("Статистика", null, Icons.Rounded.BarChart, c.accent3) { a.nav.push(Route.Stats) },
        Tile("Эквалайзер", null, Icons.Rounded.Equalizer, c.accent) { a.nav.push(Route.Equalizer) },
        Tile("Таймер сна", if (sleep.active) "включён" else null, Icons.Rounded.Timer, c.accent2) { timer = true },
        Tile("Оформление", null, Icons.Rounded.Palette, c.accent3) { a.nav.push(Route.Appearance) },
        Tile("Дубликаты", null, Icons.Rounded.ContentCopy, Color(0xFFEAB308)) { a.nav.push(Route.Duplicates) },
        Tile("Настройки", null, Icons.Rounded.Settings, c.textDim) { a.nav.push(Route.Settings) },
    )

    Column(Modifier.fillMaxSize()) {
        TopBar("Моя музыка")
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tiles, key = { it.title }) { t ->
                IconTile(t.title, t.sub, t.icon, t.tint, Modifier.aspectRatio(1f), onClick = t.action)
            }
        }
    }
    if (timer) SleepTimerDialog { timer = false }
}

// ---------------------------------------------------------------------------
// Статистика (только реальные данные из истории прослушивания)
// ---------------------------------------------------------------------------

@Composable
fun StatsScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val history by a.container.userData.history.collectAsState()
    var period by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val days = listOf(7, 30, 365)[period]
    val from = System.currentTimeMillis() - days * 24L * 3600_000
    val entries = remember(history, period) { history.filter { it.time >= from } }
    val totalMs = entries.sumOf { it.listenedMs }
    val distinct = entries.map { it.trackId }.distinct().size
    val topArtists = remember(entries, lib) {
        entries.groupBy { lib.byId[it.trackId]?.artist }.filterKeys { it != null }
            .mapValues { e -> e.value.sumOf { it.listenedMs } }.entries.sortedByDescending { it.value }.take(6)
            .map { (artist, ms) -> Triple(artist!!, ms, lib.artists.firstOrNull { it.name == artist }?.artUri) }
    }
    val topTracks = remember(entries, lib) {
        entries.groupBy { it.trackId }.mapValues { e -> e.value.size to e.value.sumOf { it.listenedMs } }
            .entries.sortedByDescending { it.value.second }.take(10)
            .mapNotNull { (id, v) -> lib.byId[id]?.let { it to v.first } }
    }
    val byDay = remember(entries) {
        val arr = LongArray(7)
        val cal = Calendar.getInstance()
        entries.forEach {
            cal.timeInMillis = it.time
            val d = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Пн = 0
            arr[d] += it.listenedMs
        }
        arr
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Статистика", onBack = { a.nav.pop() }) {
            if (history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Очистить") }
        }
        ChoiceChipRow(listOf("Неделя", "Месяц", "Год"), period, { period = it }, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        if (entries.isEmpty()) {
            EmptyState(Icons.Rounded.Headset, "Пока нет данных", "Статистика появится, когда вы послушаете музыку в Maysk Music")
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Icons.Rounded.Headset, "%.1f ч".format(totalMs / 3_600_000.0), "Прослушано", Modifier.weight(1f))
                StatCard(Icons.Rounded.MusicNote, "$distinct", "Треков", Modifier.weight(1f))
            }
            if (topArtists.isNotEmpty()) {
                SectionHeader("Топ исполнители")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(topArtists, key = { it.first }) { (name, ms, art) ->
                        Column(Modifier.width(84.dp).clickable { a.nav.push(Route.ArtistR(name)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Artwork(art, Modifier.size(72.dp), corner = 36.dp, seed = name.hashCode().toLong())
                            Text(name, color = c.text, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text(formatTime(ms), color = c.textDim, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            SectionHeader("Активность по дням недели")
            GlassCard(Modifier.fillMaxWidth()) {
                val max = (byDay.maxOrNull() ?: 1L).coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth().height(170.dp).padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { i, d ->
                        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            val f = byDay[i].toFloat() / max
                            Box(Modifier.weight(1f).width(18.dp), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    Modifier.fillMaxWidth().fillMaxHeight(f.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(6.dp))
                                        .background(Brush.verticalGradient(listOf(c.accent3, c.accent, c.accent2)))
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(d, color = c.textDim, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (topTracks.isNotEmpty()) {
                SectionHeader("Топ треки")
                topTracks.forEachIndexed { i, (t, plays) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { a.play(topTracks.map { it.first }, i) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", color = c.accent, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                        Artwork(t.artUri, Modifier.size(44.dp), corner = 8.dp, seed = t.albumId)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        Text("$plays ×", color = c.textDim)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmClear) {
        ConfirmDialog("Очистить историю?", "История прослушивания и статистика будут удалены.", "Очистить", { confirmClear = false }) {
            a.container.userData.clearHistory(); confirmClear = false
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val c = LocalMaysk.current
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, color = c.text, style = MaterialTheme.typography.headlineMedium)
            Text(label, color = c.textDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------------
// Поиск дубликатов
// ---------------------------------------------------------------------------

@Composable
fun DuplicatesScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val groups = remember(lib) {
        lib.tracks.groupBy { Triple(norm(it.title), norm(it.artist), it.durationMs / 3000) }
            .values.filter { it.size > 1 }
            .sortedByDescending { it.size }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("Дубликаты", onBack = { a.nav.pop() })
        if (groups.isEmpty()) {
            EmptyState(Icons.Rounded.ContentCopy, "Дубликаты не найдены", "Совпадения ищутся по названию, исполнителю и длительности")
            return@Column
        }
        Text(
            "Найдено групп: ${groups.size}. Удаление выполняется только после вашего подтверждения.",
            color = c.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups, key = { g -> g.first().id }) { g ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${g.first().title} — ${g.first().artist}", color = c.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        g.forEach { t -> DuplicateLine(t) }
                    }
                }
            }
        }
    }
}

private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

@Composable
private fun DuplicateLine(t: Track) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${t.mime.substringAfter('/').uppercase()} · %.1f МБ · ${formatTime(t.durationMs)}".format(t.size / 1_048_576.0),
                color = c.text, style = MaterialTheme.typography.bodySmall,
            )
            Text(t.path, color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { a.play(listOf(t)) }) { Icon(Icons.Rounded.MusicNote, "Прослушать", tint = c.accent) }
        IconButton(onClick = { a.delete(listOf(t)) }) { Icon(Icons.Rounded.Delete, "Удалить файл", tint = MaterialTheme.colorScheme.error) }
    }
}
