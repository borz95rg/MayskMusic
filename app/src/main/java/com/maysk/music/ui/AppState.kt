package com.maysk.music.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.maysk.music.AppContainer
import com.maysk.music.data.HistoryEntry
import com.maysk.music.data.Library
import com.maysk.music.data.Track

sealed interface Route {
    data object Home : Route
    data object Tracks : Route
    data object Folders : Route
    data object Playlists : Route
    data object Radio : Route
    data object MyMusic : Route
    data class AlbumR(val id: Long) : Route
    data class ArtistR(val name: String) : Route
    data class GenreR(val name: String) : Route
    data class FolderR(val path: String) : Route
    data class PlaylistR(val id: Long) : Route
    data class MixR(val kind: MixKind) : Route
    data class LibraryTab(val tab: Int) : Route
    data object Search : Route
    data object Settings : Route
    data object Appearance : Route
    data object Equalizer : Route
    data object Stats : Route
    data object Duplicates : Route
    data object FolderManager : Route
    data object Ai : Route
    data class Similar(val trackId: Long) : Route
    data class Mood(val kind: com.maysk.music.data.MoodKind) : Route
    data object Trim : Route
    data object ExtractAudio : Route
}

/** Нижняя навигация: Главная · Треки · Плейлисты · Радио · Моя музыка */
val RootRoutes = listOf(Route.Home, Route.Tracks, Route.Playlists, Route.Radio, Route.MyMusic)

class Nav {
    var tab by mutableIntStateOf(0)
        private set
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()

    fun push(r: Route) { stack.add(r) }

    fun pop(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        if (tab != 0) { selectTab(0); return true }
        return false
    }

    fun selectTab(i: Int) {
        tab = i
        stack.clear()
        stack.add(RootRoutes[i])
    }
}

/** Действия, доступные с любого экрана. */
class UiActions(
    val container: AppContainer,
    val nav: Nav,
    val openPlayer: () -> Unit,
    val openMenu: (List<Track>) -> Unit,
    val addToPlaylist: (List<Track>) -> Unit,
    val delete: (List<Track>) -> Unit,
    val pickImage: ((Uri) -> Unit) -> Unit,
    val toast: (String) -> Unit,
    /** Запросить у системы право изменять файлы (Android 10+), затем выполнить действие. */
    val requestWrite: (List<Track>, () -> Unit) -> Unit,
    /** Выполнить файловую операцию над треками в фоне, затем обновить библиотеку. */
    val fileJob: (tracks: List<Track>, needsWrite: Boolean, successMessage: String, op: (Track) -> com.maysk.music.data.OpResult) -> Unit,
    val editTags: (List<Track>) -> Unit,
    val moveCopy: (List<Track>, Boolean) -> Unit,
    val voice: () -> Unit,
) {
    fun play(list: List<Track>, index: Int = 0) {
        container.player.playTracks(list, index)
    }

    fun shuffle(list: List<Track>) {
        container.player.playTracks(list, 0, shuffle = true)
    }
}

val LocalActions = staticCompositionLocalOf<UiActions> { error("UiActions not provided") }

// ------------------- Автоматические подборки -------------------

enum class MixKind(val title: String, val subtitle: String) {
    FAVORITES("Любимые", "Треки с сердечком"),
    RECENT_ADDED("Недавно добавленные", "Новое в библиотеке"),
    HISTORY("Недавно слушали", "История воспроизведения"),
    FREQUENT("Часто слушаю", "По времени прослушивания"),
    FORGOTTEN("Давно не слушал", "Пора вспомнить"),
    ROAD("Для дороги", "Любимое и частое вперемешку"),
    WORKOUT("Для тренировок", "Энергичные жанры из тегов"),
    SLEEP("Для сна", "Спокойные жанры из тегов"),
    CALM("Спокойная музыка", "Джаз, акустика, чилл"),
}

private val ENERGETIC = listOf("rock", "metal", "electro", "dance", "edm", "house", "techno", "drum", "dubstep", "trap", "hip", "rap", "punk", "phonk", "workout", "рок", "рэп", "танц")
private val SLEEPY = listOf("ambient", "classical", "piano", "new age", "sleep", "lo-fi", "lofi", "instrumental", "meditation", "классика")
private val CALM = listOf("jazz", "acoustic", "chill", "soul", "folk", "blues", "lounge", "bossa", "soft", "джаз")

fun mixTracks(kind: MixKind, lib: Library, favorites: Set<Long>, history: List<HistoryEntry>): List<Track> {
    val byId = lib.byId
    fun genre(keys: List<String>) = lib.tracks.filter { t -> val g = t.genre.lowercase(); g.isNotBlank() && keys.any { g.contains(it) } }
    return when (kind) {
        MixKind.FAVORITES -> lib.tracks.filter { it.id in favorites }.sortedBy { it.title.lowercase() }
        MixKind.RECENT_ADDED -> lib.tracks.sortedByDescending { it.dateAdded }.take(100)
        MixKind.HISTORY -> history.asReversed().map { it.trackId }.distinct().mapNotNull { byId[it] }.take(100)
        MixKind.FREQUENT -> history.groupBy { it.trackId }.mapValues { e -> e.value.sumOf { it.listenedMs } }
            .entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }.take(50)
        MixKind.FORGOTTEN -> {
            val monthAgo = System.currentTimeMillis() - 30L * 24 * 3600_000
            val last = history.groupBy { it.trackId }.mapValues { e -> e.value.maxOf { it.time } }
            val old = last.filter { it.value < monthAgo }.keys.mapNotNull { byId[it] }
            val never = lib.tracks.filter { it.id !in last }.shuffled().take(50)
            (old + never).distinctBy { it.id }.take(50)
        }
        MixKind.ROAD -> {
            val freq = mixTracks(MixKind.FREQUENT, lib, favorites, history)
            val fav = lib.tracks.filter { it.id in favorites }
            (fav + freq + genre(ENERGETIC)).distinctBy { it.id }.shuffled().take(80)
        }
        MixKind.WORKOUT -> genre(ENERGETIC).shuffled().take(100)
        MixKind.SLEEP -> genre(SLEEPY).shuffled().take(100)
        MixKind.CALM -> (genre(CALM) + genre(SLEEPY)).distinctBy { it.id }.shuffled().take(100)
    }
}

@Composable
fun rememberActions(): UiActions = LocalActions.current
