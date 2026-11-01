package com.maysk.music.data

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Library(
    val tracks: List<Track> = emptyList(),
    val allFolders: List<MusicFolder> = emptyList(),
    val loaded: Boolean = false,
) {
    val byId: Map<Long, Track> by lazy { tracks.associateBy { it.id } }
    val albums: List<Album> by lazy {
        tracks.groupBy { it.albumId }.map { (id, t) ->
            val sorted = t.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() }))
            Album(id, t.first().album, t.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "", t.maxOf { it.year }, sorted)
        }.sortedBy { it.title.lowercase() }
    }
    val artists: List<Artist> by lazy {
        tracks.groupBy { it.artist }.map { (n, t) -> Artist(n, t.sortedByMode(TrackSort.ALBUM)) }.sortedBy { it.name.lowercase() }
    }
    val genres: List<Genre> by lazy {
        tracks.filter { it.genre.isNotBlank() }.groupBy { it.genre.trim() }
            .map { (g, t) -> Genre(g, t.sortedByMode(TrackSort.TITLE)) }.sortedBy { it.name.lowercase() }
    }
    val folders: List<MusicFolder> by lazy {
        tracks.groupBy { it.folderPath }.map { (p, t) -> MusicFolder(p, t.sortedByMode(TrackSort.TITLE)) }
            .sortedBy { it.name.lowercase() }
    }
}

class LibraryRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var raw: List<Track> = emptyList()
    private var scanJob: Job? = null
    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { rescan(debounceMs = 2500) }
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) { rescan(debounceMs = 2500) }
    }

    fun start() {
        if (!observerRegistered) {
            try {
                context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
                observerRegistered = true
            } catch (_: Exception) { }
        }
        rescan()
    }

    fun rescan(debounceMs: Long = 0) {
        scanJob?.cancel()
        scanJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _scanning.value = true
            val tracks = withContext(Dispatchers.IO) { MusicScanner.scan(context) }
            raw = tracks
            applyFilters()
            _scanning.value = false
        }
    }

    /** Пересчитать библиотеку после изменения исключённых папок/фильтров без повторного сканирования. */
    fun applyFilters() {
        val s = settings.current
        val all = raw.groupBy { it.folderPath }.map { (p, t) -> MusicFolder(p, t) }.sortedBy { it.name.lowercase() }
        val visible = raw.filter { t ->
            t.durationMs >= s.minDurationSec * 1000L &&
                t.folderPath !in s.excludedFolders &&
                !(s.hideSystemFolders && isSystemFolder(t.folderPath))
        }
        _library.value = Library(visible, all, loaded = true)
    }

    companion object {
        private val SYSTEM_MARKERS = listOf(
            "/whatsapp", "/telegram", "/viber", "/notifications", "/ringtones", "/alarms",
            "/android/data", "/android/media", "/recordings", "/call", "/voice", "/sounds/",
            "/vk/", "/miui/sound_recorder",
        )

        fun isSystemFolder(path: String): Boolean {
            val p = path.lowercase() + "/"
            return SYSTEM_MARKERS.any { p.contains(it) }
        }
    }
}

object MusicScanner {
    fun scan(context: Context): List<Track> {
        val result = ArrayList<Track>(2048)
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val hasGenre = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (hasGenre) cols += MediaStore.Audio.Media.GENRE
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                cols.toTypedArray(),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 60000",
                null,
                null,
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iGenre = if (hasGenre) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    val artist = c.getString(iArtist)
                    val name = c.getString(iName) ?: path.substringAfterLast('/')
                    val title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                    val trackRaw = c.getInt(iTrack)
                    result += Track(
                        id = c.getLong(iId),
                        title = title,
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Неизвестный исполнитель" else artist,
                        album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "Без альбома",
                        albumId = c.getLong(iAlbumId),
                        durationMs = c.getLong(iDur),
                        path = path,
                        folderPath = path.substringBeforeLast('/', ""),
                        dateAdded = c.getLong(iDate),
                        genre = if (iGenre >= 0) c.getString(iGenre) ?: "" else "",
                        trackNo = if (trackRaw >= 1000) trackRaw % 1000 else trackRaw,
                        year = c.getInt(iYear),
                        size = c.getLong(iSize),
                        mime = c.getString(iMime) ?: "",
                    )
                }
            }
        } catch (e: SecurityException) {
            // нет разрешения — вернём пустую библиотеку
        } catch (e: Exception) {
            // повреждённая база MediaStore — не падаем
        }
        return result
    }
}
