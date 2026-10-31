package com.maysk.music.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class EqState(
    val enabled: Boolean = false,
    val profile: String = "Flat",
    val bandLevels: List<Int> = emptyList(), // в миллибелах, по полосам устройства
    val bassBoost: Int = 0, // 0..1000
    val virtualizer: Int = 0, // 0..1000
    val loudnessGainMb: Int = 0, // 0..1500
    val bassEnabled: Boolean = false,
    val virtEnabled: Boolean = false,
    val loudnessEnabled: Boolean = false,
    val customProfiles: Map<String, List<Int>> = emptyMap(),
)

/**
 * Пользовательские данные: плейлисты, избранное, история, тексты, очередь, эквалайзер.
 * Хранятся в JSON во внутренней памяти приложения (атомарная запись — без потери данных).
 */
class UserDataStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "userdata.json"))

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _lyrics = MutableStateFlow<Map<Long, String>>(emptyMap())
    val lyrics: StateFlow<Map<Long, String>> = _lyrics.asStateFlow()

    private val _folderNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val folderNames: StateFlow<Map<String, String>> = _folderNames.asStateFlow()

    private val _folderCovers = MutableStateFlow<Map<String, String>>(emptyMap())
    val folderCovers: StateFlow<Map<String, String>> = _folderCovers.asStateFlow()

    private val _albumCovers = MutableStateFlow<Map<Long, String>>(emptyMap())
    val albumCovers: StateFlow<Map<Long, String>> = _albumCovers.asStateFlow()

    private val _eq = MutableStateFlow(EqState())
    val eq: StateFlow<EqState> = _eq.asStateFlow()

    var queue: QueueState = QueueState()
        private set

    init { load() }

    // ---------- Плейлисты ----------
    fun createPlaylist(name: String, ids: List<Long> = emptyList()): Playlist {
        val p = Playlist(System.currentTimeMillis(), name.trim().ifEmpty { "Новый плейлист" }, ids.distinct())
        _playlists.value = _playlists.value + p
        persist()
        return p
    }

    fun renamePlaylist(id: Long, name: String) = editPlaylist(id) { it.copy(name = name.trim().ifEmpty { it.name }) }
    fun deletePlaylist(id: Long) { _playlists.value = _playlists.value.filter { it.id != id }; persist() }
    fun setPlaylistCover(id: Long, path: String?) = editPlaylist(id) { it.copy(coverPath = path) }
    fun addToPlaylist(id: Long, ids: List<Long>) = editPlaylist(id) { p -> p.copy(trackIds = p.trackIds + ids.filter { it !in p.trackIds }) }
    fun removeFromPlaylist(id: Long, trackId: Long) = editPlaylist(id) { p -> p.copy(trackIds = p.trackIds - trackId) }
    fun setPlaylistTracks(id: Long, ids: List<Long>) = editPlaylist(id) { it.copy(trackIds = ids) }
    fun movePlaylistTrack(id: Long, from: Int, to: Int) = editPlaylist(id) { p ->
        if (from !in p.trackIds.indices || to !in p.trackIds.indices) p
        else p.copy(trackIds = p.trackIds.toMutableList().also { l -> l.add(to, l.removeAt(from)) })
    }

    private fun editPlaylist(id: Long, f: (Playlist) -> Playlist) {
        _playlists.value = _playlists.value.map { if (it.id == id) f(it) else it }
        persist()
    }

    // ---------- Избранное ----------
    fun toggleFavorite(id: Long) {
        val cur = _favorites.value
        _favorites.value = if (id in cur) cur - id else cur + id
        persist()
    }

    fun setFavorite(ids: Collection<Long>, fav: Boolean) {
        _favorites.value = if (fav) _favorites.value + ids else _favorites.value - ids.toSet()
        persist()
    }

    // ---------- История / статистика ----------
    fun addHistory(entry: HistoryEntry) {
        if (entry.listenedMs < 3000) return
        _history.value = (_history.value + entry).takeLast(5000)
        persist()
    }

    fun clearHistory() { _history.value = emptyList(); persist() }

    // ---------- Тексты песен ----------
    fun setLyrics(trackId: Long, text: String?) {
        _lyrics.value = if (text.isNullOrBlank()) _lyrics.value - trackId else _lyrics.value + (trackId to text)
        persist()
    }

    // ---------- Папки ----------
    fun renameFolder(path: String, name: String?) {
        _folderNames.value = if (name.isNullOrBlank()) _folderNames.value - path else _folderNames.value + (path to name.trim())
        persist()
    }

    fun setFolderCover(path: String, cover: String?) {
        _folderCovers.value = if (cover == null) _folderCovers.value - path else _folderCovers.value + (path to cover)
        persist()
    }

    // ---------- Обложки альбомов ----------
    fun setAlbumCover(albumId: Long, path: String?) {
        _albumCovers.value = if (path == null) _albumCovers.value - albumId else _albumCovers.value + (albumId to path)
        ArtOverrides.albums = _albumCovers.value
        persist()
    }

    // ---------- Эквалайзер ----------
    fun updateEq(f: (EqState) -> EqState) { _eq.value = f(_eq.value); persist() }

    // ---------- Очередь ----------
    fun saveQueue(q: QueueState) { queue = q; persist() }

    // ---------- Обложки ----------
    /** Копирует выбранное изображение во внутреннюю память, чтобы обложка не пропала. */
    fun importCover(uri: Uri, key: String): String? = try {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val out = File(dir, "${key.hashCode()}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        out.absolutePath
    } catch (e: Exception) { null }

    // ---------- Сериализация ----------
    @Synchronized
    private fun persist() {
        val json = toJson()
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(json.toString().toByteArray())
            file.finishWrite(stream)
        } catch (e: Exception) {
            if (stream != null) file.failWrite(stream)
        }
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("playlists", JSONArray().apply {
            _playlists.value.forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name).put("created", p.created)
                    .put("cover", p.coverPath ?: "")
                    .put("tracks", JSONArray(p.trackIds)))
            }
        })
        o.put("favorites", JSONArray(_favorites.value.toList()))
        o.put("history", JSONArray().apply {
            _history.value.forEach { put(JSONArray().put(it.trackId).put(it.time).put(it.listenedMs)) }
        })
        o.put("lyrics", JSONObject().apply { _lyrics.value.forEach { (k, v) -> put(k.toString(), v) } })
        o.put("folderNames", JSONObject().apply { _folderNames.value.forEach { (k, v) -> put(k, v) } })
        o.put("folderCovers", JSONObject().apply { _folderCovers.value.forEach { (k, v) -> put(k, v) } })
        o.put("albumCovers", JSONObject().apply { _albumCovers.value.forEach { (k, v) -> put(k.toString(), v) } })
        val e = _eq.value
        o.put("eq", JSONObject().put("enabled", e.enabled).put("profile", e.profile)
            .put("bands", JSONArray(e.bandLevels)).put("bass", e.bassBoost).put("virt", e.virtualizer)
            .put("loud", e.loudnessGainMb).put("bassOn", e.bassEnabled).put("virtOn", e.virtEnabled)
            .put("loudOn", e.loudnessEnabled)
            .put("custom", JSONObject().apply { e.customProfiles.forEach { (k, v) -> put(k, JSONArray(v)) } }))
        o.put("queue", JSONObject().put("ids", JSONArray(queue.ids)).put("index", queue.index)
            .put("pos", queue.positionMs).put("shuffle", queue.shuffle).put("repeat", queue.repeatMode))
        return o
    }

    private fun load() {
        val o = try { JSONObject(String(file.readFully())) } catch (e: Exception) { return }
        applyJson(o, includeQueue = true)
    }

    /** Восстановление из резервной копии. */
    fun importJson(o: JSONObject) { applyJson(o, includeQueue = false); persist() }

    private fun applyJson(o: JSONObject, includeQueue: Boolean) {
        o.optJSONArray("playlists")?.let { arr ->
            _playlists.value = (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                Playlist(
                    id = p.getLong("id"),
                    name = p.getString("name"),
                    trackIds = p.optJSONArray("tracks").toLongList(),
                    coverPath = p.optString("cover").ifEmpty { null },
                    created = p.optLong("created", System.currentTimeMillis()),
                )
            }
        }
        o.optJSONArray("favorites")?.let { _favorites.value = it.toLongList().toSet() }
        o.optJSONArray("history")?.let { arr ->
            _history.value = (0 until arr.length()).mapNotNull { i ->
                val h = arr.optJSONArray(i) ?: return@mapNotNull null
                HistoryEntry(h.getLong(0), h.getLong(1), h.getLong(2))
            }
        }
        o.optJSONObject("lyrics")?.let { l -> _lyrics.value = l.keys().asSequence().associate { it.toLong() to l.getString(it) } }
        o.optJSONObject("folderNames")?.let { f -> _folderNames.value = f.keys().asSequence().associateWith { f.getString(it) } }
        o.optJSONObject("folderCovers")?.let { f -> _folderCovers.value = f.keys().asSequence().associateWith { f.getString(it) } }
        o.optJSONObject("albumCovers")?.let { f ->
            _albumCovers.value = f.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to f.getString(k) } }.toMap()
            ArtOverrides.albums = _albumCovers.value
        }
        o.optJSONObject("eq")?.let { e ->
            val custom = e.optJSONObject("custom")
            _eq.value = EqState(
                enabled = e.optBoolean("enabled"),
                profile = e.optString("profile", "Flat"),
                bandLevels = e.optJSONArray("bands").toLongList().map { it.toInt() },
                bassBoost = e.optInt("bass"),
                virtualizer = e.optInt("virt"),
                loudnessGainMb = e.optInt("loud"),
                bassEnabled = e.optBoolean("bassOn"),
                virtEnabled = e.optBoolean("virtOn"),
                loudnessEnabled = e.optBoolean("loudOn"),
                customProfiles = custom?.keys()?.asSequence()?.associateWith { k ->
                    custom.getJSONArray(k).toLongList().map { it.toInt() }
                } ?: emptyMap(),
            )
        }
        if (includeQueue) o.optJSONObject("queue")?.let { q ->
            queue = QueueState(
                ids = q.optJSONArray("ids").toLongList(),
                index = q.optInt("index"),
                positionMs = q.optLong("pos"),
                shuffle = q.optBoolean("shuffle"),
                repeatMode = q.optInt("repeat"),
            )
        }
    }
}

private fun JSONArray?.toLongList(): List<Long> =
    if (this == null) emptyList() else (0 until length()).map { getLong(it) }
