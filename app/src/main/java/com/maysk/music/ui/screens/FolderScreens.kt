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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.LibraryRepository
import com.maysk.music.data.MusicFolder
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.MixKind
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.EmptyState
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.IconTile
import com.maysk.music.ui.components.SectionHeader
import com.maysk.music.ui.components.TextInputDialog
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.ui.theme.LocalUi
import java.io.File

@Composable
fun FoldersScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val fav by a.container.userData.favorites.collectAsState()
    val names by a.container.userData.folderNames.collectAsState()
    val covers by a.container.userData.folderCovers.collectAsState()
    var menuFor by remember { mutableStateOf<MusicFolder?>(null) }
    var renameFor by remember { mutableStateOf<MusicFolder?>(null) }
    val cols = if (LocalUi.current.simpleMode) 1 else 2

    Column(Modifier.fillMaxSize()) {
        TopBar("Папки") {
            IconButton(onClick = { a.nav.push(Route.FolderManager) }) { Icon(Icons.Rounded.Tune, "Управление папками", tint = c.text) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                IconTile("Все песни", "(${lib.tracks.size})", Icons.Rounded.LibraryMusic, c.accent, Modifier.aspectRatio(1.25f)) {
                    a.nav.push(Route.LibraryTab(0))
                }
            }
            item {
                IconTile("Любимое", "(${lib.tracks.count { it.id in fav }})", Icons.Rounded.Favorite, c.accent3, Modifier.aspectRatio(1.25f)) {
                    a.nav.push(Route.MixR(MixKind.FAVORITES))
                }
            }
            if (lib.folders.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Rounded.Folder, "Папки с музыкой не найдены", "Проверьте исключённые папки", "Управление папками") {
                        a.nav.push(Route.FolderManager)
                    }
                }
            }
            items(lib.folders, key = { it.path }) { f ->
                val cover = covers[f.path]
                val isDownloads = f.name.equals("Download", true) || f.name.equals("Downloads", true)
                FolderCard(
                    title = names[f.path] ?: f.name,
                    count = f.tracks.size,
                    cover = cover?.let { File(it) } ?: f.tracks.firstOrNull()?.artUri,
                    seed = f.path.hashCode().toLong(),
                    icon = if (isDownloads) Icons.Rounded.FileDownload else Icons.Rounded.Folder,
                    onClick = { a.nav.push(Route.FolderR(f.path)) },
                    onLongClick = { menuFor = f },
                )
            }
        }
    }

    menuFor?.let { f ->
        FolderActionsDialog(
            f,
            onDismiss = { menuFor = null },
            onRename = { renameFor = f; menuFor = null },
        )
    }
    renameFor?.let { f ->
        TextInputDialog("Название в приложении", initial = names[f.path] ?: f.name, onDismiss = { renameFor = null }) {
            a.container.userData.renameFolder(f.path, it.takeIf { n -> n.isNotBlank() && n != f.name })
            renameFor = null
        }
    }
}

@Composable
private fun FolderCard(
    title: String,
    count: Int,
    cover: Any?,
    seed: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = LocalMaysk.current
    GlassCard(Modifier.aspectRatio(1.25f), onClick = onClick, onLongClick = onLongClick) {
        Artwork(cover, Modifier.matchParentSize(), corner = 0.dp, seed = seed, iconSize = 1.dp)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.75f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("($count)", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onLongClick, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Rounded.MoreVert, "Действия с папкой", tint = Color.White)
        }
    }
}

@Composable
private fun FolderActionsDialog(f: MusicFolder, onDismiss: () -> Unit, onRename: () -> Unit) {
    val a = LocalActions.current
    val store = a.container.userData
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.name) },
        text = {
            Column {
                Text(f.path, color = LocalMaysk.current.textDim, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                ActionLine(Icons.Rounded.Edit, "Переименовать (в приложении)") { onRename() }
                ActionLine(Icons.Rounded.Image, "Сменить обложку") {
                    onDismiss()
                    a.pickImage { uri -> store.importCover(uri, f.path)?.let { store.setFolderCover(f.path, it) } }
                }
                if (store.folderCovers.value.containsKey(f.path)) {
                    ActionLine(Icons.Rounded.Refresh, "Вернуть обложку по умолчанию") { store.setFolderCover(f.path, null); onDismiss() }
                }
                ActionLine(Icons.Rounded.VisibilityOff, "Скрыть папку из библиотеки") {
                    a.container.settings.update { it.copy(excludedFolders = it.excludedFolders + f.path) }
                    a.container.library.applyFilters()
                    a.toast("Папка скрыта. Вернуть: Папки → Управление")
                    onDismiss()
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun ActionLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalMaysk.current.accent)
        Spacer(Modifier.width(14.dp))
        Text(text)
    }
}

@Composable
fun FolderScreen(path: String) {
    val a = LocalActions.current
    val lib by a.container.library.library.collectAsState()
    val names by a.container.userData.folderNames.collectAsState()
    val covers by a.container.userData.folderCovers.collectAsState()
    val folder = lib.folders.firstOrNull { it.path == path }
    if (folder == null) { MissingScreen(); return }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    TrackListScreen(
        title = names[path] ?: folder.name,
        tracks = folder.tracks,
        subtitle = "${folder.tracks.size} треков · $path",
        headerArt = covers[path]?.let { File(it) } ?: folder.tracks.firstOrNull()?.artUri,
        headerSeed = path.hashCode().toLong(),
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = LocalMaysk.current.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text("Сменить обложку") }, onClick = {
                        menu = false
                        a.pickImage { uri -> a.container.userData.importCover(uri, path)?.let { a.container.userData.setFolderCover(path, it) } }
                    })
                    DropdownMenuItem(text = { Text("Скрыть папку") }, onClick = {
                        menu = false
                        a.container.settings.update { it.copy(excludedFolders = it.excludedFolders + path) }
                        a.container.library.applyFilters()
                        a.nav.pop()
                    })
                }
            }
        },
    )
    if (rename) {
        TextInputDialog("Название в приложении", initial = names[path] ?: folder.name, onDismiss = { rename = false }) {
            a.container.userData.renameFolder(path, it.takeIf { n -> n.isNotBlank() && n != folder.name })
            rename = false
        }
    }
}

/** Выбор папок для библиотеки: включение/исключение, системные папки, минимальная длительность. */
@Composable
fun FolderManagerScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val settings by a.container.settings.state.collectAsState()
    val scanning by a.container.library.scanning.collectAsState()

    fun update(f: (com.maysk.music.data.AppSettings) -> com.maysk.music.data.AppSettings) {
        a.container.settings.update(f)
        a.container.library.applyFilters()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Управление папками", onBack = { a.nav.pop() }) {
            IconButton(onClick = { a.container.library.rescan(); a.toast("Сканирование…") }) {
                Icon(Icons.Rounded.Refresh, "Обновить библиотеку", tint = c.text)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Скрывать служебные папки", color = c.text)
                                Text("Мессенджеры, рингтоны, уведомления, записи звонков", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(settings.hideSystemFolders, { v -> update { it.copy(hideSystemFolders = v) } })
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Не показывать файлы короче ${settings.minDurationSec} сек", color = c.text)
                        Slider(
                            value = settings.minDurationSec.toFloat(),
                            onValueChange = { v -> a.container.settings.update { it.copy(minDurationSec = v.toInt()) } },
                            onValueChangeFinished = { a.container.library.applyFilters() },
                            valueRange = 0f..120f,
                            steps = 11,
                        )
                    }
                }
            }
            item { SectionHeader(if (scanning) "Сканирование…" else "Найденные папки (${lib.allFolders.size})") }
            items(lib.allFolders, key = { it.path }) { f ->
                val system = settings.hideSystemFolders && LibraryRepository.isSystemFolder(f.path)
                val included = f.path !in settings.excludedFolders && !system
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = !system) {
                            update { s -> s.copy(excludedFolders = if (included) s.excludedFolders + f.path else s.excludedFolders - f.path) }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = included,
                        enabled = !system,
                        onCheckedChange = { v ->
                            update { s -> s.copy(excludedFolders = if (v) s.excludedFolders - f.path else s.excludedFolders + f.path) }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = c.accent),
                    )
                    Icon(Icons.Rounded.FolderOpen, null, tint = if (included) c.accent else c.textDim)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = if (included) c.text else c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (system) "служебная · " else "") + "${f.tracks.size} файлов · ${f.path}",
                            color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
