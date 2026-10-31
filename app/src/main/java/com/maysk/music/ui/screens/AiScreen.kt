package com.maysk.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.data.MoodKind
import com.maysk.music.data.Recommender
import com.maysk.music.data.Track
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.GradientButton
import com.maysk.music.ui.components.TagRepairDialog
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk

/** Раздел Maysk AI: умные функции поверх вашей библиотеки. Всё необязательно, всё с подтверждением. */
@Composable
fun AiScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val lib by a.container.library.library.collectAsState()
    val playback by a.container.player.state.collectAsState()
    val coverState by a.container.covers.state.collectAsState()
    var repair by remember { mutableStateOf(false) }
    val current = playback.currentId?.let { lib.byId[it] }
    val repairCount = remember(lib) { com.maysk.music.data.FileOps.tagRepairCandidates(lib).size }

    LaunchedEffect(lib) { if (lib.loaded) a.container.covers.countMissing(lib) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Maysk AI", onBack = { a.nav.pop() }) {
            IconButton(onClick = { a.voice() }) { Icon(Icons.Rounded.Mic, "Голосовая команда", tint = c.text) }
        }
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Умные функции работают на телефоне по вашей библиотеке и истории прослушивания. " +
                        "Интернет нужен только для подбора тегов и обложек. Файлы меняются только после вашего подтверждения.",
                    color = c.textDim, style = MaterialTheme.typography.bodySmall,
                )
            }

            item {
                AiCard(Icons.Rounded.AutoAwesome, "Похожие треки", "По исполнителю, жанру, году и тому, что вы слушаете вместе") {
                    if (current != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Artwork(current.artUri, Modifier.size(44.dp), corner = 8.dp, seed = current.albumId)
                            Spacer(Modifier.width(10.dp))
                            Text("${current.title} — ${current.artist}", color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        GradientButton("Похожие на текущий", Modifier.fillMaxWidth()) { a.nav.push(Route.Similar(current.id)) }
                    } else {
                        Text("Включите любой трек — и здесь появятся похожие. Также: меню трека ⋮ → «Похожие треки».", color = c.textDim)
                    }
                }
            }

            item {
                AiCard(Icons.Rounded.PlaylistAdd, "Плейлист по настроению", "Подбор по жанрам из тегов, любимому и частому") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MoodKind.entries.toList(), key = { it.name }) { m ->
                            GlassCard(Modifier.width(130.dp), onClick = { a.nav.push(Route.Mood(m)) }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(m.title, color = c.text, style = MaterialTheme.typography.titleMedium)
                                    Text(m.subtitle, color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }

            item {
                AiCard(Icons.Rounded.Shuffle, "Умное перемешивание", "Не ставит подряд два трека одного исполнителя. Используется во всех кнопках «Перемешать».") {
                    GradientButton("Перемешать всю музыку", Modifier.fillMaxWidth(), enabled = lib.tracks.isNotEmpty()) { a.shuffle(lib.tracks) }
                }
            }

            item {
                AiCard(Icons.Rounded.Edit, "Восстановление тегов", "Если у файла нет тегов, исполнитель и название берутся из имени «Исполнитель - Название»") {
                    Text(if (repairCount == 0) "Треков без тегов не найдено" else "Можно исправить: $repairCount", color = c.text)
                    Spacer(Modifier.height(8.dp))
                    GradientButton("Проверить и исправить", Modifier.fillMaxWidth(), enabled = repairCount > 0) { repair = true }
                    Spacer(Modifier.height(6.dp))
                    Text("Подбор тегов из интернета (MusicBrainz): меню трека ⋮ → «Редактировать теги» → «Найти теги онлайн».",
                        color = c.textDim, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                AiCard(Icons.Rounded.Image, "Обложки альбомов", "Поиск недостающих обложек в Cover Art Archive. Обложки сохраняются в приложении, файлы не меняются.") {
                    when {
                        coverState.running -> {
                            Text("${coverState.done + 1} из ${coverState.total}: ${coverState.current}", color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (coverState.total > 0) coverState.done.toFloat() / coverState.total else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Найдено: ${coverState.found}", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { a.container.covers.cancel() }) { Text("Остановить") }
                        }
                        else -> {
                            coverState.message?.let { Text(it, color = c.accent) }
                            val missing = coverState.missingCount
                            Text(
                                when (missing) { null -> "Проверка…"; 0 -> "У всех альбомов есть обложки"; else -> "Без обложки: $missing альбомов" },
                                color = c.text,
                            )
                            Spacer(Modifier.height(8.dp))
                            GradientButton("Найти обложки", Modifier.fillMaxWidth(), enabled = (missing ?: 0) > 0) { a.container.covers.start(lib) }
                        }
                    }
                }
            }

            item {
                AiCard(Icons.Rounded.ContentCopy, "Поиск дубликатов", "Одинаковые треки в разных файлах и форматах") {
                    GradientButton("Открыть", Modifier.fillMaxWidth()) { a.nav.push(Route.Duplicates) }
                }
            }

            item {
                AiCard(Icons.Rounded.Mic, "Голосовое управление", "«Включи Imagine Dragons», «альбом Evolve», «плейлист В машине», «пауза», «дальше», «таймер 30 минут». " +
                    "Также работает «Окей, Google, включи … в Maysk Music» и Android Auto.") {
                    GradientButton("Сказать команду", Modifier.fillMaxWidth(), icon = Icons.Rounded.Mic) { a.voice() }
                }
            }
        }
    }
    if (repair) TagRepairDialog { repair = false }
}

@Composable
private fun AiCard(icon: ImageVector, title: String, subtitle: String, content: @Composable () -> Unit) {
    val c = LocalMaysk.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, color = c.text, style = MaterialTheme.typography.titleMedium)
            }
            Text(subtitle, color = c.textDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            content()
        }
    }
}

@Composable
private fun SavePlaylistButton(name: String, tracks: List<Track>) {
    val a = LocalActions.current
    IconButton(onClick = {
        if (tracks.isEmpty()) a.toast("Список пуст")
        else {
            a.container.userData.createPlaylist(name, tracks.map { it.id })
            a.toast("Сохранено как плейлист «$name»")
        }
    }) { Icon(Icons.Rounded.PlaylistAdd, "Сохранить как плейлист", tint = LocalMaysk.current.text) }
}

@Composable
fun SimilarScreen(trackId: Long) {
    val a = LocalActions.current
    val lib by a.container.library.library.collectAsState()
    val history by a.container.userData.history.collectAsState()
    val fav by a.container.userData.favorites.collectAsState()
    val seed = lib.byId[trackId]
    if (seed == null) { MissingScreen(); return }
    val tracks = remember(seed, lib) { listOf(seed) + Recommender.similar(seed, lib, history, fav) }
    TrackListScreen(
        title = "Похожие: ${seed.title}",
        tracks = tracks,
        subtitle = "${seed.artist} · ${tracks.size - 1} похожих треков",
        headerArt = seed.artUri,
        headerSeed = seed.albumId,
        sortable = false,
        actions = { SavePlaylistButton("Похожие на ${seed.title}", tracks) },
        emptyText = "Похожих треков не найдено — добавьте жанры в теги или послушайте больше музыки",
    )
}

@Composable
fun MoodScreen(kind: MoodKind) {
    val a = LocalActions.current
    val lib by a.container.library.library.collectAsState()
    val history by a.container.userData.history.collectAsState()
    val fav by a.container.userData.favorites.collectAsState()
    val tracks = remember(kind, lib) { Recommender.mood(kind, lib, history, fav) }
    TrackListScreen(
        title = "Настроение: ${kind.title}",
        tracks = tracks,
        subtitle = "${kind.subtitle} · ${tracks.size} треков",
        sortable = false,
        actions = { SavePlaylistButton("Настроение: ${kind.title}", tracks) },
        emptyText = "Нет подходящих треков: у файлов не указан жанр. Заполните жанр через «Редактировать теги».",
    )
}

