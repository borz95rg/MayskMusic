package com.maysk.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maysk.music.data.FileOps
import com.maysk.music.data.Track
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.LocalStrings
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
fun coverDp(): Dp = when (LocalUi.current.coverSize) { 0 -> 96.dp; 2 -> 140.dp; else -> 112.dp }

fun Track.displayTitle(): String {
    val raw = title.trim()
    if (raw.isEmpty()) return fileName.substringBeforeLast('.').ifBlank { "Без названия" }
    if (Regex("^\\d{1,3}_\\d{8,}").containsMatchIn(raw) ||
        Regex("^\\d{10,}").matches(raw) ||
        (artist == "Неизвестный исполнитель" && raw.length > 28 && raw.all { it.isDigit() || it == '_' || it == '-' || it == '.' })
    ) {
        FileOps.guessFromFilename(this)?.title?.takeIf { it.isNotBlank() }?.let { return it }
        val cleaned = fileName.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        return cleaned.ifBlank { "Без названия" }.take(48)
    }
    return raw
}

fun Track.displayArtist(): String {
    if (artist.isNotBlank() && artist != "Неизвестный исполнитель") return artist
    FileOps.guessFromFilename(this)?.artist?.takeIf { it.isNotBlank() }?.let { return it }
    return "Неизвестный исполнитель"
}

fun Track.isUntagged(): Boolean =
    artist == "Неизвестный исполнитель" ||
        title == fileName.substringBeforeLast('.') ||
        Regex("^\\d{1,3}_\\d{8,}").containsMatchIn(title)

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 6 -> "Доброй ночи"
        h < 12 -> "Доброе утро"
        h < 18 -> "Добрый день"
        else -> "Добрый вечер"
    }
}

@Composable
fun HomeScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val scanning by a.container.library.scanning.collectAsState()
    val favorites by a.container.userData.favorites.collectAsState()
    val history by a.container.userData.history.collectAsState()
    val str = LocalStrings.current

    val recentPlayed = remember(lib, history) { mixTracks(MixKind.HISTORY, lib, favorites, history).take(20) }
    val recentAdded = remember(lib) { lib.tracks.sortedByDescending { it.dateAdded }.take(20) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Maysk",
            actions = {
                androidx.compose.material3.IconButton(onClick = { a.container.library.rescan() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = str.rescanLibrary, tint = c.text)
                }
            },
        )

        if (lib.tracks.isEmpty() && !scanning) {
            EmptyState(
                Icons.Rounded.MusicNote,
                str.musicNotFound,
                "Добавьте файлы в папки музыки и обновите библиотеку",
            ) { a.container.library.rescan() }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                HeroCard(
                    title = greeting(),
                    subtitle = if (history.isEmpty()) "Перемешать · ${lib.tracks.size} треков"
                    else "Недавно слушали и новое",
                ) {
                    a.shuffle(lib.tracks)
                }
            }

            if (recentPlayed.isNotEmpty()) {
                item { SectionHeader(str.recentlyPlayed, str.all) { a.nav.push(Route.MixR(MixKind.HISTORY)) } }
                item { TrackCarousel(recentPlayed) }
            }

            if (recentAdded.isNotEmpty()) {
                item { SectionHeader(str.recentlyAdded, str.all) { a.nav.push(Route.MixR(MixKind.RECENT_ADDED)) } }
                item { TrackCarousel(recentAdded) }
            }
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, onPlay: () -> Unit) {
    val c = LocalMaysk.current
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(148.dp), onClick = onPlay) {
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
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(tracks.size) { i ->
            val t = tracks[i]
            Column(
                Modifier
                    .width(size)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { a.play(tracks, i) },
            ) {
                Artwork(t.artUri, Modifier.size(size), corner = 16.dp, seed = t.albumId, iconSize = 40.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    t.displayTitle(),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    t.displayArtist(),
                    color = c.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PlaylistChip(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    val c = LocalMaysk.current
    GlassCard(Modifier.width(118.dp).aspectRatio(1f), onClick = onClick) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), Color.Transparent))))
        Column(
            Modifier.align(Alignment.Center).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Text("($count)", color = c.textDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MixCard(kind: MixKind, tracks: List<Track>, onClick: () -> Unit) {
    val c = LocalMaysk.current
    GlassCard(Modifier.width(196.dp), onClick = onClick) {
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
