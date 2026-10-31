package com.maysk.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.FileOps
import com.maysk.music.data.OnlineMeta
import com.maysk.music.data.OpResult
import com.maysk.music.data.TagField
import com.maysk.music.data.Track
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.theme.LocalMaysk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Редактор тегов одного трека, с подбором данных из MusicBrainz. */
@Composable
fun TagEditorDialog(track: Track, onDismiss: () -> Unit) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf<TagField, String>() }
    var original by remember { mutableStateOf<Map<TagField, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<OnlineMeta.Suggestion>?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(track.id) {
        val tags = withContext(Dispatchers.IO) { FileOps.readTags(context, track) }
        original = tags
        values.clear(); values.putAll(tags)
        loading = false
    }

    if (!FileOps.canEditTags(track)) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Теги") },
            text = { Text("Формат .${track.extension} не поддерживает редактирование тегов.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать теги") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(track.fileName, color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Чтение тегов…")
                    }
                } else {
                    TagField.entries.forEach { f ->
                        OutlinedTextField(
                            value = values[f].orEmpty(),
                            onValueChange = { values[f] = it },
                            label = { Text(f.label) },
                            singleLine = true,
                            keyboardOptions = if (f == TagField.YEAR || f == TagField.TRACK) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = {
                        searching = true; searchError = null
                        scope.launch {
                            val title = values[TagField.TITLE].orEmpty().ifBlank { FileOps.guessFromFilename(track)?.title ?: track.title }
                            val artist = values[TagField.ARTIST].orEmpty().ifBlank { FileOps.guessFromFilename(track)?.artist.orEmpty() }
                            val res = withContext(Dispatchers.IO) { runCatching { OnlineMeta.searchRecordings(title, artist.ifBlank { null }) } }
                            res.onSuccess { suggestions = it; if (it.isEmpty()) searchError = "Ничего не найдено" }
                                .onFailure { searchError = "Нет связи с MusicBrainz: ${it.message ?: ""}" }
                            searching = false
                        }
                    }, enabled = !searching) {
                        Icon(Icons.Rounded.Search, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (searching) "Поиск…" else "Найти теги онлайн (MusicBrainz)")
                    }
                    searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    suggestions?.takeIf { it.isNotEmpty() }?.let { list ->
                        Text("Нажмите на вариант, чтобы заполнить поля:", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                        list.forEach { s ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                    values[TagField.TITLE] = s.title
                                    values[TagField.ARTIST] = s.artist
                                    if (s.album.isNotBlank()) values[TagField.ALBUM] = s.album
                                    if (s.year.isNotBlank()) values[TagField.YEAR] = s.year
                                    if (s.trackNo.isNotBlank()) values[TagField.TRACK] = s.trackNo
                                }.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text("${s.title} — ${s.artist}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(s.album, s.year).filter { it.isNotBlank() }.joinToString(" · "), color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading, onClick = {
                val changes = TagField.entries.filter { values[it].orEmpty() != original[it].orEmpty() }.associateWith { values[it].orEmpty() }
                onDismiss()
                if (changes.isEmpty()) a.toast("Изменений нет")
                else a.fileJob(listOf(track), true, "Теги сохранены") { t -> FileOps.writeTagsOp(context, t, changes) }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Пакетное редактирование: пустое поле = не менять. */
@Composable
fun BatchTagDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    val fields = listOf(TagField.ARTIST, TagField.ALBUM, TagField.ALBUM_ARTIST, TagField.GENRE, TagField.YEAR)
    val values = remember { mutableStateMapOf<TagField, String>() }
    val editable = tracks.filter { FileOps.canEditTags(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Теги: ${tracks.size} треков") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Заполните только то, что нужно изменить у всех выбранных треков. Пустые поля останутся без изменений.",
                    color = c.textDim, style = MaterialTheme.typography.bodySmall)
                if (editable.size < tracks.size) {
                    Text("Не поддерживается форматом: ${tracks.size - editable.size}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                fields.forEach { f ->
                    OutlinedTextField(
                        value = values[f].orEmpty(),
                        onValueChange = { values[f] = it },
                        label = { Text(f.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val changes = values.filterValues { it.isNotBlank() }.toMap()
                onDismiss()
                if (changes.isEmpty() || editable.isEmpty()) a.toast("Изменений нет")
                else a.fileJob(editable, true, "Теги обновлены: ${editable.size}") { t -> FileOps.writeTagsOp(context, t, changes) }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Перемещение или копирование треков в папку. */
@Composable
fun MoveCopyDialog(tracks: List<Track>, copy: Boolean, onDismiss: () -> Unit) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    val lib by a.container.library.library.collectAsState()
    val targets = remember(lib) { FileOps.targetFolders(lib) }
    var creating by remember { mutableStateOf(false) }
    val verb = if (copy) "Копировать" else "Переместить"

    fun run(target: String) {
        onDismiss()
        val op: (Track) -> OpResult = { t -> if (copy) FileOps.copy(context, t, target) else FileOps.move(context, t, target) }
        // Копирование создаёт новые файлы приложения — разрешение на изменение исходных не нужно.
        a.fileJob(tracks, !copy, (if (copy) "Скопировано" else "Перемещено") + " в $target", op)
    }

    if (creating) {
        TextInputDialog("Новая папка в Music", label = "Название папки", confirm = verb, onDismiss = { creating = false }) { name ->
            creating = false
            run("Music/${FileOps.sanitizeFolder(name)}/")
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$verb ${if (tracks.size == 1) "трек" else "${tracks.size} треков"}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    FolderLine(Icons.Rounded.CreateNewFolder, "Новая папка в Music…", c.accent) { creating = true }
                }
                items(targets) { rel ->
                    FolderLine(Icons.Rounded.Folder, rel.trimEnd('/'), c.text) { run(rel) }
                }
                item {
                    Text(
                        "Android хранит музыку только в папках Music, Download, Podcasts, Audiobooks и т.п." +
                            if (!copy) " Система попросит подтвердить изменение файлов." else "",
                        color = c.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun FolderLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LocalMaysk.current.accent)
        Spacer(Modifier.width(12.dp))
        Text(text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Восстановление тегов из имён файлов «Исполнитель - Название». */
@Composable
fun TagRepairDialog(onDismiss: () -> Unit) {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    val lib by a.container.library.library.collectAsState()
    val candidates = remember(lib) { FileOps.tagRepairCandidates(lib).filter { FileOps.canEditTags(it.track) } }
    val selected = remember(candidates) { mutableStateListOf<Long>().apply { addAll(candidates.map { it.track.id }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Восстановить теги") },
        text = {
            if (candidates.isEmpty()) Text("Треков с пустыми тегами и понятным именем файла не найдено.")
            else Column {
                Text("Исполнитель и название будут взяты из имени файла. Снимите галочки с лишних.",
                    color = c.textDim, style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(candidates, key = { it.track.id }) { g ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (g.track.id in selected) selected.remove(g.track.id) else selected.add(g.track.id)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = g.track.id in selected, onCheckedChange = {
                                if (it) selected.add(g.track.id) else selected.remove(g.track.id)
                            })
                            Column(Modifier.weight(1f)) {
                                Text("${g.artist} — ${g.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(g.track.fileName, color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (candidates.isNotEmpty()) TextButton(enabled = selected.isNotEmpty(), onClick = {
                val chosen = candidates.filter { it.track.id in selected }
                val byId = chosen.associateBy { it.track.id }
                onDismiss()
                a.fileJob(chosen.map { it.track }, true, "Теги восстановлены: ${chosen.size}") { t ->
                    val g = byId.getValue(t.id)
                    val changes = mutableMapOf(TagField.ARTIST to g.artist, TagField.TITLE to g.title)
                    g.trackNo?.let { changes[TagField.TRACK] = it.toString() }
                    FileOps.writeTagsOp(context, t, changes)
                }
            }) { Text("Применить (${selected.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (candidates.isEmpty()) "OK" else "Отмена") } },
    )
}
