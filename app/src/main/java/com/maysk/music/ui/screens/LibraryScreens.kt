package com.maysk.music.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.Track
import com.maysk.music.data.TrackSort
import com.maysk.music.data.formatTime
import com.maysk.music.data.sortedByMode
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.MixKind
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.ChoiceChipRow
import com.maysk.music.ui.components.EmptyState
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.GradientButton
import com.maysk.music.ui.components.SectionHeader
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.components.TrackRow
import com.maysk.music.ui.mixTracks
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.ui.theme.LocalUi

// ---------------------------------------------------------------------------
// Общий список треков с выделением, сортировкой и поиском
// ---------------------------------------------------------------------------

class SelectionState {
    val ids = mutableStateListOf<Long>()
    val active: Boolean get() = ids.isNotEmpty()
    fun toggle(id: Long) { if (id in ids) ids.remove(id) else ids.add(id) }
    fun clear() = ids.clear()
}

@Composable
fun SelectionBar(sel: SelectionState, all: List<Track>) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val chosen = all.filter { it.id in sel.ids }
    TopBar(title = "Выбрано: ${sel.ids.size}", onBack = null, titleContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { sel.clear() }) { Icon(Icons.Rounded.Close, "Отменить", tint = c.text) }
            Text("Выбрано: ${sel.ids.size}", color = c.text, style = MaterialTheme.typography.titleMedium)
        }
    }) {
        IconButton(onClick = { sel.ids.clear(); sel.ids.addAll(all.map { it.id }) }) { Icon(Icons.Rounded.SelectAll, "Выбрать все", tint = c.text) }
        IconButton(onClick = { a.play(chosen); sel.clear() }) { Icon(Icons.Rounded.PlayArrow, "Воспроизвести", tint = c.text) }
        IconButton(onClick = { a.addToPlaylist(chosen); sel.clear() }) { Icon(Icons.Rounded.PlaylistAdd, "В плейлист", tint = c.text) }
        IconButton(onClick = { a.openMenu(chosen); sel.clear() }) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = c.text) }
    }
}

/** Добавляет в LazyColumn строки треков. */
fun LazyListScope.trackItems(
    tracks: List<Track>,
    currentId: Long?,
    isPlaying: Boolean,
    sel: SelectionState,
    onPlay: (Int) -> Unit,
    onMenu: (Track) -> Unit,
) {
    items(tracks.size, key = { tracks[it].id }) { i ->
        val t = tracks[i]
        TrackRow(
            track = t,
            isCurrent = t.id == currentId,
            isPlaying = isPlaying,
            selected = t.id in sel.ids,
            selectionMode = sel.active,
            onClick = { if (sel.active) sel.toggle(t.id) else onPlay(i) },
            onLongClick = { sel.toggle(t.id) },
            onMenu = { onMenu(t) },
        )
    }
}

@Composable
fun PlayShuffleRow(tracks: List<Track>) {
    val a = LocalActions.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientButton("Слушать", Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, enabled = tracks.isNotEmpty()) { a.play(tracks) }
        GlassCard(Modifier.weight(1f).height(48.dp), onClick = { if (tracks.isNotEmpty()) a.shuffle(tracks) }) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shuffle, null, tint = LocalMaysk.current.accent)
                Spacer(Modifier.width(8.dp))
                Text("Перемешать", color = LocalMaysk.current.text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SortMenuButton(sort: TrackSort, onSort: (TrackSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.Sort, "Сортировка", tint = LocalMaysk.current.text) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TrackSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSort(s); open = false },
                )
            }
        }
    }
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String = "Название, исполнитель, альбом", autoFocus: Boolean = false) {
    val focus = remember { FocusRequester() }
    val c = LocalMaysk.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Очистить") } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.border,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(focus),
    )
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

fun List<Track>.filterQuery(q: String): List<Track> {
    val s = q.trim().lowercase()
    if (s.isEmpty()) return this
    return filter { it.title.lowercase().contains(s) || it.artist.lowercase().contains(s) || it.album.lowercase().contains(s) }
}

/** Универсальный экран списка треков (альбом, исполнитель, папка, подборка...). */
@Composable
fun TrackListScreen(
    title: String,
    tracks: List<Track>,
    subtitle: String? = null,
    headerArt: Any? = null,
    headerSeed: Long = 0,
    sortable: Boolean = true,
    actions: @Composable () -> Unit = {},
    emptyText: String = "Здесь пока пусто",
) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val playback by a.container.player.state.collectAsState()
    val sel = remember { SelectionState() }
    var sort by remember { mutableStateOf<TrackSort?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val shown = remember(tracks, sort, query) { (sort?.let { tracks.sortedByMode(it) } ?: tracks).filterQuery(query) }

    Column(Modifier.fillMaxSize()) {
        if (sel.active) SelectionBar(sel, shown)
        else TopBar(title, onBack = { a.nav.pop() }) {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Rounded.Search, "Поиск", tint = c.text) }
            if (sortable) SortMenuButton(sort ?: TrackSort.TITLE) { sort = it }
            actions()
        }
        if (searching) SearchField(query, { query = it }, autoFocus = true)
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (headerArt != null) {
                        Artwork(headerArt, Modifier.size(96.dp), corner = 16.dp, seed = headerSeed, iconSize = 40.dp)
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, color = c.text, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            subtitle ?: "${tracks.size} треков · ${formatTime(tracks.sumOf { it.durationMs })}",
                            color = c.textDim, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item { PlayShuffleRow(shown) }
            if (shown.isEmpty()) item { EmptyState(Icons.Rounded.LibraryMusic, emptyText) }
            trackItems(shown, playback.currentId, playback.isPlaying, sel, onPlay = { a.play(shown, it) }, onMenu = { a.openMenu(listOf(it)) })
        }
    }
}

// ---------------------------------------------------------------------------
// Вкладка «Треки»: все треки / альбомы / исполнители / жанры
// ---------------------------------------------------------------------------

@Composable
fun TracksScreen(initialTab: Int, showBack: Boolean = false) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val playback by a.container.player.state.collectAsState()
    var tab by remember { mutableIntStateOf(initialTab) }
    var sort by remember { mutableStateOf(TrackSort.TITLE) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val sel = remember { SelectionState() }
    val tracks = remember(lib, sort, query) { lib.tracks.sortedByMode(sort).filterQuery(query) }

    Column(Modifier.fillMaxSize()) {
        if (sel.active) SelectionBar(sel, tracks)
        else TopBar("Треки", onBack = if (showBack) fun() { a.nav.pop() } else null) {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Rounded.Search, "Поиск", tint = c.text) }
            if (tab == 0) SortMenuButton(sort) { sort = it }
        }
        ChoiceChipRow(listOf("Все треки", "Альбомы", "Исполнители", "Жанры"), tab, { tab = it; sel.clear() }, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        if (searching) SearchField(query, { query = it }, autoFocus = true)

        when (tab) {
            0 -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (!lib.loaded) item { Text("Сканирование…", color = c.textDim, modifier = Modifier.padding(16.dp)) }
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { if (tracks.isNotEmpty()) a.shuffle(tracks) }.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, tint = c.accent)
                        Spacer(Modifier.width(14.dp))
                        Text("Перемешать все", color = c.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${tracks.size}", color = c.textDim)
                    }
                }
                trackItems(tracks, playback.currentId, playback.isPlaying, sel, onPlay = { a.play(tracks, it) }, onMenu = { a.openMenu(listOf(it)) })
            }
            1 -> AlbumsGrid(query)
            2 -> ArtistsList(query)
            else -> GenresList(query)
        }
    }
}

@Composable
fun AlbumsGrid(query: String) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val cols = LocalUi.current.grid.coerceIn(2, 4).let { if (LocalUi.current.simpleMode) 2 else it }
    val albums = remember(lib, query) {
        val q = query.trim().lowercase()
        lib.albums.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { al ->
            Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { a.nav.push(Route.AlbumR(al.id)) }) {
                Artwork(al.artUri, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp, seed = al.id, iconSize = 36.dp)
                Spacer(Modifier.height(6.dp))
                Text(al.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(al.artist, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ArtistsList(query: String) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val artists = remember(lib, query) {
        val q = query.trim().lowercase()
        lib.artists.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(artists, key = { it.name }) { ar ->
            Row(
                Modifier.fillMaxWidth().clickable { a.nav.push(Route.ArtistR(ar.name)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(ar.artUri, Modifier.size(54.dp), corner = 27.dp, seed = ar.name.hashCode().toLong())
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(ar.name, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text("${ar.albumCount} альб. · ${ar.tracks.size} треков", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Rounded.Person, null, tint = c.textDim)
            }
        }
    }
}

@Composable
fun GenresList(query: String) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val genres = remember(lib, query) {
        val q = query.trim().lowercase()
        lib.genres.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    }
    if (genres.isEmpty()) {
        EmptyState(Icons.Rounded.Category, "Жанры не найдены", "В тегах ваших файлов не указан жанр (или Android ниже 11).")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(genres, key = { it.name }) { g ->
            Row(
                Modifier.fillMaxWidth().clickable { a.nav.push(Route.GenreR(g.name)) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Category, null, tint = c.accent)
                Spacer(Modifier.width(16.dp))
                Text(g.name, color = c.text, modifier = Modifier.weight(1f))
                Text("${g.tracks.size}", color = c.textDim)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Детальные экраны
// ---------------------------------------------------------------------------

@Composable
fun AlbumScreen(id: Long) {
    val a = LocalActions.current
    val lib by a.container.library.library.collectAsState()
    val album = lib.albums.firstOrNull { it.id == id }
    if (album == null) { MissingScreen(); return }
    val covers by a.container.userData.albumCovers.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    TrackListScreen(
        title = album.title,
        tracks = album.tracks,
        subtitle = "${album.artist}${if (album.year > 0) " · ${album.year}" else ""} · ${album.tracks.size} треков",
        headerArt = album.artUri,
        headerSeed = album.id,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = LocalMaysk.current.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (searching) "Поиск обложки…" else "Найти обложку онлайн") }, enabled = !searching, onClick = {
                        menu = false
                        searching = true
                        a.toast("Ищу обложку «${album.title}»…")
                        a.container.covers.findOne(album) { ok ->
                            searching = false
                            a.toast(if (ok) "Обложка найдена" else "Обложка не найдена")
                        }
                    })
                    DropdownMenuItem(text = { Text("Выбрать обложку из галереи") }, onClick = {
                        menu = false
                        a.pickImage { uri ->
                            a.container.userData.importCover(uri, "album_${album.id}")?.let {
                                a.container.userData.setAlbumCover(album.id, it)
                                a.container.library.applyFilters()
                            }
                        }
                    })
                    if (album.id in covers) DropdownMenuItem(text = { Text("Вернуть исходную обложку") }, onClick = {
                        menu = false
                        a.container.userData.setAlbumCover(album.id, null)
                        a.container.library.applyFilters()
                    })
                    DropdownMenuItem(text = { Text("Редактировать теги альбома") }, onClick = { menu = false; a.editTags(album.tracks) })
                }
            }
        },
    )
}

@Composable
fun ArtistScreen(name: String) {
    val a = LocalActions.current
    val lib by a.container.library.library.collectAsState()
    val artist = lib.artists.firstOrNull { it.name == name }
    if (artist == null) { MissingScreen(); return }
    TrackListScreen(
        title = artist.name,
        tracks = artist.tracks,
        subtitle = "${artist.albumCount} альбомов · ${artist.tracks.size} треков",
        headerArt = artist.artUri,
    )
}

@Composable
fun GenreScreen(name: String) {
    val a = LocalActions.current
    val lib by a.container.library.library.collectAsState()
    val g = lib.genres.firstOrNull { it.name == name }
    if (g == null) { MissingScreen(); return }
    TrackListScreen(title = g.name, tracks = g.tracks)
}

@Composable
fun MixScreen(kind: MixKind) {
    val a = LocalActions.current
    val lib by a.container.library.library.collectAsState()
    val fav by a.container.userData.favorites.collectAsState()
    val history by a.container.userData.history.collectAsState()
    // История и любимое пересчитываются сразу, «случайные» подборки — только при смене библиотеки.
    val dynamicKey: Any = when (kind) {
        MixKind.FAVORITES -> fav
        MixKind.HISTORY, MixKind.FREQUENT -> history
        else -> lib
    }
    val tracks = remember(lib, dynamicKey) { mixTracks(kind, lib, fav, history) }
    TrackListScreen(
        title = kind.title,
        tracks = tracks,
        subtitle = "${kind.subtitle} · ${tracks.size} треков",
        sortable = kind != MixKind.HISTORY,
        emptyText = when (kind) {
            MixKind.FAVORITES -> "Нажмите ♥ в плеере, чтобы добавить трек сюда"
            MixKind.HISTORY, MixKind.FREQUENT -> "Послушайте немного музыки — здесь появится история"
            else -> "Подходящих треков пока нет"
        },
    )
}

@Composable
fun MissingScreen() {
    val a = LocalActions.current
    Column(Modifier.fillMaxSize()) {
        TopBar("", onBack = { a.nav.pop() })
        EmptyState(Icons.Rounded.LibraryMusic, "Не найдено", "Возможно, файлы были удалены или перемещены.")
    }
}

// ---------------------------------------------------------------------------
// Поиск
// ---------------------------------------------------------------------------

@Composable
fun SearchScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val playback by a.container.player.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val sel = remember { SelectionState() }
    val q = query.trim().lowercase()
    val tracks = remember(lib, q) { if (q.isEmpty()) emptyList() else lib.tracks.filterQuery(q).take(200) }
    val albums = remember(lib, q) { if (q.isEmpty()) emptyList() else lib.albums.filter { it.title.lowercase().contains(q) }.take(20) }
    val artists = remember(lib, q) { if (q.isEmpty()) emptyList() else lib.artists.filter { it.name.lowercase().contains(q) }.take(20) }

    Column(Modifier.fillMaxSize()) {
        if (sel.active) SelectionBar(sel, tracks) else TopBar("Поиск", onBack = { a.nav.pop() })
        SearchField(query, { query = it }, autoFocus = true)
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            if (q.isNotEmpty() && tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "Ничего не найдено", "Попробуйте другой запрос") }
            }
            if (artists.isNotEmpty()) {
                item { SectionHeader("Исполнители") }
                items(artists, key = { "ar_" + it.name }) { ar ->
                    Row(
                        Modifier.fillMaxWidth().clickable { a.nav.push(Route.ArtistR(ar.name)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(ar.artUri, Modifier.size(46.dp), corner = 23.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(ar.name, color = c.text, modifier = Modifier.weight(1f))
                        Text("${ar.tracks.size}", color = c.textDim)
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item { SectionHeader("Альбомы") }
                items(albums, key = { "al_" + it.id }) { al ->
                    Row(
                        Modifier.fillMaxWidth().clickable { a.nav.push(Route.AlbumR(al.id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(al.artUri, Modifier.size(46.dp), corner = 10.dp, seed = al.id)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(al.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(al.artist, color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
            if (tracks.isNotEmpty()) {
                item { SectionHeader("Треки") }
                trackItems(tracks, playback.currentId, playback.isPlaying, sel, onPlay = { a.play(tracks, it) }, onMenu = { a.openMenu(listOf(it)) })
            }
        }
    }
}

