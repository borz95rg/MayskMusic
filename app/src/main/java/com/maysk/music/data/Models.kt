package com.maysk.music.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val folderPath: String,
    val dateAdded: Long,
    val genre: String,
    val trackNo: Int,
    val year: Int,
    val size: Long,
    val mime: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = albumArtUri(albumId)
    val fileName: String get() = path.substringAfterLast('/')
    val extension: String get() = path.substringAfterLast('.', "").lowercase()
    val folderName: String get() = folderPath.substringAfterLast('/')
}

/** Обложки альбомов, выбранные пользователем или найденные онлайн (albumId -> путь к файлу). */
object ArtOverrides {
    @Volatile
    var albums: Map<Long, String> = emptyMap()
}

fun albumArtUri(albumId: Long): Uri =
    ArtOverrides.albums[albumId]?.let { Uri.fromFile(File(it)) }
        ?: ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

/** Обложка из системы (без пользовательской замены). */
fun systemAlbumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

data class Album(val id: Long, val title: String, val artist: String, val year: Int, val tracks: List<Track>) {
    val artUri: Uri get() = albumArtUri(id)
}

data class Artist(val name: String, val tracks: List<Track>) {
    val albumCount: Int get() = tracks.map { it.albumId }.distinct().size
    val artUri: Uri? get() = tracks.firstOrNull()?.artUri
}

data class Genre(val name: String, val tracks: List<Track>)

data class MusicFolder(val path: String, val tracks: List<Track>) {
    val name: String get() = path.substringAfterLast('/')
}

data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<Long>,
    val coverPath: String? = null,
    val created: Long = System.currentTimeMillis(),
)

data class HistoryEntry(val trackId: Long, val time: Long, val listenedMs: Long)

data class QueueState(
    val ids: List<Long> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
)

enum class TrackSort(val label: String) {
    TITLE("По названию"),
    ARTIST("По исполнителю"),
    ALBUM("По альбому"),
    DATE_ADDED("Недавно добавленные"),
    DURATION("По длительности"),
}

fun List<Track>.sortedByMode(sort: TrackSort): List<Track> = when (sort) {
    TrackSort.TITLE -> sortedBy { it.title.lowercase() }
    TrackSort.ARTIST -> sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNo }))
    TrackSort.ALBUM -> sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo }))
    TrackSort.DATE_ADDED -> sortedByDescending { it.dateAdded }
    TrackSort.DURATION -> sortedByDescending { it.durationMs }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
