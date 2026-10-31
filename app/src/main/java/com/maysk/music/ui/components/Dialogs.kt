package com.maysk.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.Track
import com.maysk.music.data.formatTime
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.Route
import com.maysk.music.ui.theme.LocalMaysk

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "Название",
    confirm: String = "Сохранить",
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.heightIn(min = 160.dp, max = 320.dp)),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Удалить", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun AddToPlaylistDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val store = actions.container.userData
    val playlists by store.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog("Новый плейлист", onDismiss = { creating = false }) { name ->
            store.createPlaylist(name, tracks.map { it.id })
            actions.toast("Создан плейлист «${name.ifBlank { "Новый плейлист" }}»")
            creating = false
            onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size == 1) "Добавить в плейлист" else "Добавить ${tracks.size} треков") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    DialogRow(Icons.Rounded.Add, "Новый плейлист") { creating = true }
                }
                items(playlists, key = { it.id }) { p ->
                    DialogRow(Icons.Rounded.QueueMusic, "${p.name} (${p.trackIds.size})") {
                        store.addToPlaylist(p.id, tracks.map { it.id })
                        actions.toast("Добавлено в «${p.name}»")
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun DialogRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LocalMaysk.current.accent)
        Spacer(Modifier.width(14.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Меню действий для одного или нескольких треков. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(tracks: List<Track>, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val c = LocalMaysk.current
    val favorites by actions.container.userData.favorites.collectAsState()
    var details by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val single = tracks.singleOrNull()

    if (details && single != null) {
        TrackDetailsDialog(single) { details = false }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            if (single != null) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(single.artUri, Modifier.size(56.dp), seed = single.albumId)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(single.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(single.artist, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Text("Выбрано: ${tracks.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            val allFav = tracks.all { it.id in favorites }
            SheetItem(Icons.Rounded.PlayArrow, "Воспроизвести") { actions.play(tracks); onDismiss() }
            SheetItem(Icons.Rounded.SkipNext, "Играть следующим") {
                actions.container.player.playNext(tracks); actions.toast("Будет играть следующим"); onDismiss()
            }
            SheetItem(Icons.Rounded.QueueMusic, "Добавить в очередь") {
                actions.container.player.addToQueue(tracks); actions.toast("Добавлено в очередь"); onDismiss()
            }
            SheetItem(Icons.Rounded.PlaylistAdd, "Добавить в плейлист") { onDismiss(); actions.addToPlaylist(tracks) }
            SheetItem(if (allFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (allFav) "Убрать из любимого" else "В любимое") {
                actions.container.userData.setFavorite(tracks.map { it.id }, !allFav); onDismiss()
            }
            if (single != null) {
                SheetItem(Icons.Rounded.Album, "Альбом: ${single.album}") { onDismiss(); actions.nav.push(Route.AlbumR(single.albumId)) }
                SheetItem(Icons.Rounded.Person, "Исполнитель: ${single.artist}") { onDismiss(); actions.nav.push(Route.ArtistR(single.artist)) }
                SheetItem(Icons.Rounded.Info, "Информация о файле") { details = true }
            }
            SheetItem(Icons.Rounded.Delete, "Удалить с устройства", danger = true) { onDismiss(); actions.delete(tracks) }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalMaysk.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else c.accent)
        Spacer(Modifier.width(18.dp))
        Text(text, color = if (danger) MaterialTheme.colorScheme.error else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TrackDetailsDialog(t: Track, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.title) },
        text = {
            Column {
                Detail("Исполнитель", t.artist)
                Detail("Альбом", t.album)
                if (t.genre.isNotBlank()) Detail("Жанр", t.genre)
                if (t.year > 0) Detail("Год", t.year.toString())
                Detail("Длительность", formatTime(t.durationMs))
                Detail("Формат", t.mime.substringAfter('/').uppercase())
                Detail("Размер", "%.1f МБ".format(t.size / 1_048_576.0))
                Detail("Файл", t.path)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun Detail(k: String, v: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(k, style = MaterialTheme.typography.labelSmall, color = LocalMaysk.current.textDim)
        Text(v, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val timer = actions.container.sleepTimer
    val state by timer.state.collectAsState()
    val options = listOf(15 to "15 минут", 30 to "30 минут", 45 to "45 минут", 60 to "60 минут", 90 to "90 минут", -1 to "До конца трека", 0 to "Выключить")
    var selected by remember {
        mutableStateOf(
            when {
                state.endOfTrack -> -1
                state.endAt != null -> -2
                else -> 0
            }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер сна") },
        text = {
            Column {
                state.endAt?.let {
                    val left = ((it - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                    Text("Осталось ≈ $left мин", color = LocalMaysk.current.accent, modifier = Modifier.padding(bottom = 8.dp))
                }
                options.forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { selected = v }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == v, onClick = { selected = v })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    selected > 0 -> { timer.start(selected); actions.toast("Музыка остановится через $selected мин") }
                    selected == -1 -> { timer.endOfTrack(); actions.toast("Остановка после текущего трека") }
                    selected == 0 -> timer.cancel()
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ChoiceChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalMaysk.current
    Row(modifier.horizontalScrollSafe(), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Text(
                label,
                color = if (sel) androidx.compose.ui.graphics.Color.White else c.textDim,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) c.accent else c.card.copy(alpha = c.glassAlpha))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun Modifier.horizontalScrollSafe(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))
