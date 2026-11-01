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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maysk.music.data.Track
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.MixKind
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.EmptyState
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.SectionHeader
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.mixTracks
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.ui.theme.LocalUi
import java.util.Calendar

@Composable
fun coverDp(): Dp = when (LocalUi.current.coverSize) { 0 -> 104.dp; 2 -> 156.dp; else -> 128.dp }

@Composable
fun HomeScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val scanning by a.container.library.scanning.collectAsState()
    val favorites by a.container.userData.favorites.collectAsState()
    val history by a.container.userData.history.collectAsState()
    val playlists by a.container.userData.playlists.collectAsState()
    val simple = LocalUi.current.simpleMode

    val recentPlayed = remember(lib, history) { mixTracks(MixKind.HISTORY, lib, favorites, history).take(20) }
    val recentAdded = remember(lib) { lib.tracks.sortedByDescending { it.dateAdded }.take(20) }
    val favTracks = remember(lib, favorites) { lib.tracks.filter { it.id in favorites } }
    val topAlbums = remember(lib, history) {
        val plays = history.groupBy { lib.byId[it.trackId]?.albumId }.mapValues { e -> e.value.sumOf { it.listenedMs } }
        lib.albums.sortedByDescending { plays[it.id] ?: 0L }.take(15)
    }
    val mixes = remember(lib, favorites, history) {
        MixKind.entries.filter { it != MixKind.HISTORY }
            .map { it to mixTracks(it, lib, favorites, history) }
            .filter { it.second.isNotEmpty() }
    }
    val weekMs = remember(history) {
        val from = System.currentTimeMillis() - 7L * 24 * 3600_000
        history.filter { it.time >= from }.sumOf { it.listenedMs }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "",
            titleContent = {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.text)) { append("Maysk ") }
                        withStyle(SpanStyle(brush = Brush.horizontalGradient(listOf(c.accent2, c.accent, c.accent3)))) { append("Music") }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
        ) {
            if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent)
            IconButton(onClick = { a.voice() }) { Icon(Icons.Rounded.Mic, "Голосовая команда", tint = c.text) }
            IconButton(onClick = { a.nav.push(Route.Search) }) { Icon(Icons.Rounded.Search, "Поиск", tint = c.text) }
            IconButton(onClick = { a.nav.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Настройки", tint = c.text) }
        }

        if (lib.loaded && lib.tracks.isEmpty()) {
            EmptyState(
                Icons.Rounded.LibraryMusic,
                "Музыка не найдена",
                "Скопируйте аудиофайлы в память телефона или проверьте исключённые папки.",
                "Обновить библиотеку",
            ) { a.container.library.rescan() }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                HeroCard(
                    title = greeting(),
                    subtitle = if (history.isEmpty()) "Перемешать всю библиотеку · ${lib.tracks.size} треков" else "Подборка из любимого и частого",
                ) {
                    val road = mixes.firstOrNull { it.first == MixKind.ROAD }?.second
                    if (!road.isNullOrEmpty()) a.shuffle(road) else a.shuffle(lib.tracks)
                }
            }

            if (recentPlayed.isNotEmpty()) {
                item { SectionHeader("Недавно слушали", "Все") { a.nav.push(Route.MixR(MixKind.HISTORY)) } }
                item { TrackCarousel(recentPlayed) }
            }

            item { SectionHeader("Плейлисты", "Все") { a.nav.selectTab(2) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        PlaylistChip("Любимое", "${favTracks.size}", Icons.Rounded.Favorite, c.accent3) { a.nav.push(Route.MixR(MixKind.FAVORITES)) }
                    }
                    items(playlists, key = { it.id }) { p ->
                        PlaylistChip(p.name, "${p.trackIds.size}", Icons.Rounded.QueueMusic, c.accent2) { a.nav.push(Route.PlaylistR(p.id)) }
                    }
                }
            }

            if (recentAdded.isNotEmpty()) {
                item { SectionHeader("Недавно добавленные", "Все") { a.nav.push(Route.MixR(MixKind.RECENT_ADDED)) } }
                item { TrackCarousel(recentAdded) }
            }

            if (!simple && mixes.isNotEmpty()) {
                item { SectionHeader("Подборки для вас") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(mixes, key = { it.first.name }) { (kind, list) ->
                            MixCard(kind, list) { a.nav.push(Route.MixR(kind)) }
                        }
                    }
                }
            }

            if (topAlbums.isNotEmpty()) {
                item { SectionHeader("Альбомы", "Все") { a.nav.push(Route.LibraryTab(1)) } }
                item {
                    val size = coverDp()
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(topAlbums, key = { it.id }) { al ->
                            Column(Modifier.width(size).clickable { a.nav.push(Route.AlbumR(al.id)) }) {
                                Artwork(al.artUri, Modifier.size(size), corner = 14.dp, seed = al.id, iconSize = 36.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(al.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(al.artist, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (lib.folders.isNotEmpty()) {
                item { SectionHeader("Папки", "Все") { a.nav.push(Route.Folders) } }
                item {
                    val names by a.container.userData.folderNames.collectAsState()
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(lib.folders.sortedByDescending { it.tracks.size }.take(10), key = { it.path }) { f ->
                            PlaylistChip(names[f.path] ?: f.name, "${f.tracks.size}", Icons.Rounded.Folder, c.accent) {
                                a.nav.push(Route.FolderR(f.path))
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Статистика", "Подробнее") { a.nav.push(Route.Stats) } }
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp), onClick = { a.nav.push(Route.Stats) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BarChart, null, tint = c.accent, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("За 7 дней: %.1f ч".format(weekMs / 3_600_000.0), color = c.text, style = MaterialTheme.typography.titleMedium)
                            Text("В библиотеке ${lib.tracks.size} треков · ${lib.albums.size} альбомов · ${lib.artists.size} исполнителей", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { a.container.library.rescan(); a.toast("Сканирование библиотеки…") }) {
                        Icon(Icons.Rounded.Refresh, "Обновить библиотеку", tint = c.textDim)
                    }
                }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

@Composable
private fun HeroCard(title: String, subtitle: String, onPlay: () -> Unit) {
    val c = LocalMaysk.current
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(170.dp), onClick = onPlay) {
        // Нарисованный «горный» пейзаж в фирменных цветах
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(Brush.verticalGradient(listOf(c.accent2.copy(alpha = 0.55f), c.accent.copy(alpha = 0.45f), c.accent3.copy(alpha = 0.35f))))
                drawCircle(Color.White.copy(alpha = 0.18f), radius = size.height * 0.22f, center = Offset(size.width * 0.75f, size.height * 0.32f))
                val far = Path().apply {
                    moveTo(0f, size.height * 0.75f)
                    lineTo(size.width * 0.22f, size.height * 0.42f)
                    lineTo(size.width * 0.38f, size.height * 0.62f)
                    lineTo(size.width * 0.58f, size.height * 0.28f)
                    lineTo(size.width * 0.8f, size.height * 0.6f)
                    lineTo(size.width, size.height * 0.45f)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(far, SolidColor(Color(0xFF1B1036).copy(alpha = 0.75f)))
                val near = Path().apply {
                    moveTo(0f, size.height * 0.88f)
                    lineTo(size.width * 0.3f, size.height * 0.66f)
                    lineTo(size.width * 0.55f, size.height * 0.85f)
                    lineTo(size.width * 0.82f, size.height * 0.7f)
                    lineTo(size.width, size.height * 0.82f)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(near, SolidColor(Color(0xFF0B0718).copy(alpha = 0.9f)))
            }
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(16.dp).size(54.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.button, c.accent3))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PlayArrow, "Играть", tint = Color.White, modifier = Modifier.size(32.dp)) }
    }
}

@Composable
fun TrackCarousel(tracks: List<Track>) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val size = coverDp()
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tracks.size) { i ->
            val t = tracks[i]
            Column(Modifier.width(size).clip(RoundedCornerShape(14.dp)).clickable { a.play(tracks, i) }) {
                Artwork(t.artUri, Modifier.size(size), corner = 14.dp, seed = t.albumId, iconSize = 36.dp)
                Spacer(Modifier.height(6.dp))
                Text(t.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(t.artist, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlaylistChip(title: String, count: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    val c = LocalMaysk.current
    GlassCard(Modifier.width(112.dp).aspectRatio(1f), onClick = onClick) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.30f), Color.Transparent))))
        Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            Text("($count)", color = c.textDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MixCard(kind: MixKind, tracks: List<Track>, onClick: () -> Unit) {
    val c = LocalMaysk.current
    GlassCard(Modifier.width(188.dp), onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tracks.take(2).forEach { t ->
                    Artwork(t.artUri, Modifier.weight(1f).aspectRatio(1f), corner = 12.dp, seed = t.albumId)
                }
                if (tracks.size < 2) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(kind.title, color = c.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, fontWeight = FontWeight.SemiBold)
            Text("${tracks.size} треков · ${kind.subtitle}", color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}
