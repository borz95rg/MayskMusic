package com.maysk.music.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.Playlist
import com.maysk.music.data.Track
import com.maysk.music.data.TrackSort
import com.maysk.music.data.formatTime
import com.maysk.music.data.sortedByMode
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.MixKind
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.ConfirmDialog
import com.maysk.music.ui.components.EmptyState
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.SectionHeader
import com.maysk.music.ui.components.TextInputDialog
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.components.TrackRow
import com.maysk.music.ui.mixTracks
import com.maysk.music.ui.theme.LocalMaysk
import java.io.File

@Composable
fun PlaylistsScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    val lib by a.container.library.library.collectAsState()
    val playlists by a.container.userData.playlists.collectAsState()
    val fav by a.container.userData.favorites.collectAsState()
    val history by a.container.userData.history.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val mixes = remember(lib, fav, history) {
        MixKind.entries.map { it to mixTracks(it, lib, fav, history).size }.filter { it.second > 0 }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val (name, ids) = PlaylistIO.import(context, uri, lib.tracks)
            if (ids.isEmpty()) a.toast("В плейлисте не найдено треков из вашей библиотеки")
            else {
                val p = a.container.userData.createPlaylist(name, ids)
                a.toast("Импортировано: ${ids.size} треков")
                a.nav.push(Route.PlaylistR(p.id))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Плейлисты") {
            IconButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.FolderOpen, "Импорт M3U / PLS", tint = c.text) }
            IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Создать плейлист", tint = c.text) }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (mixes.isNotEmpty()) {
                item { SectionHeader("Автоподборки") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(mixes, key = { it.first.name }) { (kind, count) ->
                            GlassCard(Modifier.width(150.dp), onClick = { a.nav.push(Route.MixR(kind)) }) {
                                Column(Modifier.padding(14.dp)) {
                                    Icon(Icons.Rounded.QueueMusic, null, tint = c.accent)
                                    Spacer(Modifier.size(8.dp))
                                    Text(kind.title, color = c.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$count треков", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Мои плейлисты (${playlists.size})", "Создать") { creating = true } }
            if (playlists.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.QueueMusic, "Плейлистов пока нет", "Создайте свой или импортируйте файл M3U", "Создать плейлист") { creating = true }
                }
            }
            items(playlists, key = { it.id }) { p ->
                val tracks = p.trackIds.mapNotNull { lib.byId[it] }
                Row(
                    Modifier.fillMaxWidth().clickable { a.nav.push(Route.PlaylistR(p.id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistCover(p, tracks, 58)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tracks.size} треков · ${formatTime(tracks.sumOf { it.durationMs })}", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (creating) {
        TextInputDialog("Новый плейлист", onDismiss = { creating = false }) { name ->
            val p = a.container.userData.createPlaylist(name)
            creating = false
            a.nav.push(Route.PlaylistR(p.id))
        }
    }
}

@Composable
fun PlaylistCover(p: Playlist, tracks: List<Track>, sizeDp: Int) {
    val model: Any? = p.coverPath?.let { File(it) } ?: tracks.firstOrNull()?.artUri
    Artwork(model, Modifier.size(sizeDp.dp), corner = 12.dp, seed = p.id, iconSize = (sizeDp / 2).dp)
}

@Composable
fun PlaylistScreen(id: Long) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    val store = a.container.userData
    val lib by a.container.library.library.collectAsState()
    val playlists by store.playlists.collectAsState()
    val playback by a.container.player.state.collectAsState()
    val p = playlists.firstOrNull { it.id == id }
    if (p == null) { MissingScreen(); return }
    val tracks = remember(p, lib) { p.trackIds.mapNotNull { lib.byId[it] } }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sel = remember { SelectionState() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) a.toast(if (PlaylistIO.export(context, uri, tracks)) "Плейлист сохранён" else "Не удалось сохранить файл")
    }

    Column(Modifier.fillMaxSize()) {
        if (sel.active) SelectionBar(sel, tracks)
        else TopBar(p.name, onBack = { a.nav.pop() }) {
            IconButton(onClick = { editing = !editing }) {
                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, if (editing) "Готово" else "Изменить порядок", tint = c.text)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = c.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text("Сменить обложку") }, onClick = {
                        menu = false
                        a.pickImage { uri -> store.importCover(uri, "pl_$id")?.let { store.setPlaylistCover(id, it) } }
                    })
                    if (p.coverPath != null) DropdownMenuItem(text = { Text("Убрать обложку") }, onClick = { menu = false; store.setPlaylistCover(id, null) })
                    DropdownMenuItem(text = { Text("Добавить в очередь") }, onClick = { menu = false; a.container.player.addToQueue(tracks); a.toast("Добавлено в очередь") })
                    TrackSort.entries.forEach { s ->
                        DropdownMenuItem(text = { Text("Сортировать: ${s.label.lowercase()}") }, onClick = {
                            menu = false
                            store.setPlaylistTracks(id, tracks.sortedByMode(s).map { it.id })
                        })
                    }
                    DropdownMenuItem(text = { Text("Экспорт в M3U8") }, onClick = { menu = false; exporter.launch("${p.name}.m3u8") })
                    DropdownMenuItem(text = { Text("Удалить плейлист") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlaylistCover(p, tracks, 96)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.text, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${tracks.size} треков · ${formatTime(tracks.sumOf { it.durationMs })}", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { PlayShuffleRow(tracks) }
            if (tracks.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.QueueMusic, "Плейлист пуст", "Добавляйте треки через меню ⋮ → «Добавить в плейлист»", "Открыть все треки") {
                        a.nav.push(Route.LibraryTab(0))
                    }
                }
            }
            items(tracks.size, key = { tracks[it].id }) { i ->
                val t = tracks[i]
                TrackRow(
                    track = t,
                    isCurrent = t.id == playback.currentId,
                    isPlaying = playback.isPlaying,
                    selected = t.id in sel.ids,
                    selectionMode = sel.active,
                    onClick = { if (sel.active) sel.toggle(t.id) else a.play(tracks, i) },
                    onLongClick = { sel.toggle(t.id) },
                    onMenu = if (editing) null else fun() { a.openMenu(listOf(t)) },
                    trailing = {
                        if (editing) Row {
                            IconButton(onClick = { store.movePlaylistTrack(id, i, i - 1) }, enabled = i > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Выше", tint = c.text)
                            }
                            IconButton(onClick = { store.movePlaylistTrack(id, i, i + 1) }, enabled = i < tracks.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Ниже", tint = c.text)
                            }
                            IconButton(onClick = { store.removeFromPlaylist(id, t.id) }) {
                                Icon(Icons.Rounded.Close, "Убрать из плейлиста", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                )
            }
        }
    }

    if (rename) {
        TextInputDialog("Переименовать плейлист", initial = p.name, onDismiss = { rename = false }) {
            store.renamePlaylist(id, it); rename = false
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Удалить плейлист?", "Плейлист «${p.name}» будет удалён. Сами музыкальные файлы останутся на устройстве.",
            onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            store.deletePlaylist(id)
            a.nav.pop()
        }
    }
}

/** Импорт/экспорт плейлистов M3U, M3U8 и PLS. */
object PlaylistIO {
    fun import(context: Context, uri: Uri, library: List<Track>): Pair<String, List<Long>> {
        val name = displayName(context, uri)?.substringBeforeLast('.') ?: "Импорт"
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        } catch (e: Exception) { "" }
        val entries = text.lineSequence()
            .map { it.trim().removePrefix("﻿") }
            .mapNotNull { line ->
                when {
                    line.isEmpty() || line.startsWith("#") || line.startsWith("[") -> null
                    line.matches(Regex("(?i)^file\\d+=.*")) -> line.substringAfter('=')
                    line.contains('=') && !line.contains('/') && !line.contains('\\') -> null // прочие ключи PLS
                    else -> line
                }
            }
            .map { it.replace('\\', '/') }
            .toList()
        val byPath = library.associateBy { it.path.lowercase() }
        val byName = library.groupBy { it.path.substringAfterLast('/').lowercase() }
        val ids = entries.mapNotNull { e ->
            val lower = e.lowercase().removePrefix("file://")
            byPath[lower]
                ?: library.firstOrNull { it.path.lowercase().endsWith(lower.trimStart('.', '/')) }
                ?: byName[lower.substringAfterLast('/')]?.firstOrNull()
        }.map { it.id }.distinct()
        return name to ids
    }

    fun export(context: Context, uri: Uri, tracks: List<Track>): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val sb = StringBuilder("#EXTM3U\n")
            tracks.forEach { t ->
                sb.append("#EXTINF:").append(t.durationMs / 1000).append(',').append(t.artist).append(" - ").append(t.title).append('\n')
                sb.append(t.path).append('\n')
            }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        true
    } catch (e: Exception) { false }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) { null }
}
