package com.maysk.music.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoverJobState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val current: String = "",
    val missingCount: Int? = null,
    val message: String? = null,
)

/** Поиск недостающих обложек альбомов в Cover Art Archive (по кнопке пользователя). */
class CoverFinder(
    private val context: Context,
    private val store: UserDataStore,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
) {
    private val _state = MutableStateFlow(CoverJobState())
    val state: StateFlow<CoverJobState> = _state.asStateFlow()
    private var job: Job? = null

    private fun missing(lib: Library): List<Album> = lib.albums.filter { a ->
        a.title != "Без альбома" && a.id !in store.albumCovers.value && !OnlineMeta.hasSystemArt(context, a.id)
    }

    fun countMissing(lib: Library) {
        if (_state.value.running) return
        scope.launch {
            val n = withContext(Dispatchers.IO) { missing(lib).size }
            _state.value = _state.value.copy(missingCount = n)
        }
    }

    fun start(lib: Library) {
        if (_state.value.running) return
        job = scope.launch {
            _state.value = CoverJobState(running = true, current = "Проверка альбомов…")
            val albums = withContext(Dispatchers.IO) { missing(lib) }
            var found = 0
            albums.forEachIndexed { i, a ->
                if (!isActive) return@launch
                _state.value = _state.value.copy(done = i, total = albums.size, found = found, current = a.title)
                val path = withContext(Dispatchers.IO) {
                    runCatching {
                        OnlineMeta.findRelease(a.title, a.artist)?.let { OnlineMeta.downloadCover(context, it, "album_${a.id}") }
                    }.getOrNull()
                }
                if (path != null) {
                    store.setAlbumCover(a.id, path)
                    found++
                }
            }
            if (found > 0) onChanged()
            _state.value = CoverJobState(
                running = false, done = albums.size, total = albums.size, found = found,
                missingCount = albums.size - found,
                message = if (albums.isEmpty()) "У всех альбомов уже есть обложки" else "Найдено обложек: $found из ${albums.size}",
            )
        }
    }

    /** Найти обложку для одного альбома. */
    fun findOne(album: Album, onResult: (Boolean) -> Unit) {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    OnlineMeta.findRelease(album.title, album.artist)?.let { OnlineMeta.downloadCover(context, it, "album_${album.id}") }
                }.getOrNull()
            }
            if (path != null) { store.setAlbumCover(album.id, path); onChanged() }
            onResult(path != null)
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(running = false, message = "Остановлено")
    }
}
