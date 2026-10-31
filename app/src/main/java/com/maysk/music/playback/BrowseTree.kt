package com.maysk.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.maysk.music.data.Library
import com.maysk.music.data.Playlist
import com.maysk.music.data.Track

/**
 * Дерево каталога для Android Auto и других внешних медиа-клиентов.
 * ID папок: root, tracks, albums, album:<id>, artists, artist:<name>, playlists, pl:<id>, favorites, recent.
 * ID трека внутри папки: "<папка>|<trackId>" — чтобы при выборе трека играла вся папка.
 */
object BrowseTree {
    const val ROOT = "root"

    fun folder(id: String, title: String, subtitle: String? = null, art: Uri? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(art)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    fun playable(parent: String, t: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId("$parent|${t.id}")
            .setUri(t.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(t.uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.artUri)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

    fun rootItem(): MediaItem = folder(ROOT, "Maysk Music")

    fun rootChildren(lib: Library, playlists: List<Playlist>, favorites: Set<Long>): List<MediaItem> = listOf(
        folder("recent", "Недавно слушали"),
        folder("favorites", "Любимое", "${lib.tracks.count { it.id in favorites }} треков"),
        folder("playlists", "Плейлисты", "${playlists.size}"),
        folder("albums", "Альбомы", "${lib.albums.size}"),
        folder("artists", "Исполнители", "${lib.artists.size}"),
        folder("tracks", "Все треки", "${lib.tracks.size}"),
    )

    /** Треки для «играбельной» папки (или null, если это папка с подпапками). */
    fun tracksFor(
        parent: String,
        lib: Library,
        playlists: List<Playlist>,
        favorites: Set<Long>,
        recent: List<Long>,
    ): List<Track>? = when {
        parent == "tracks" -> lib.tracks.sortedBy { it.title.lowercase() }
        parent == "favorites" -> lib.tracks.filter { it.id in favorites }
        parent == "recent" -> recent.mapNotNull { lib.byId[it] }
        parent.startsWith("album:") -> parent.removePrefix("album:").toLongOrNull()?.let { id -> lib.albums.firstOrNull { it.id == id }?.tracks }
        parent.startsWith("artist:") -> parent.removePrefix("artist:").let { n -> lib.artists.firstOrNull { it.name == n }?.tracks }
        parent.startsWith("pl:") -> parent.removePrefix("pl:").toLongOrNull()?.let { id ->
            playlists.firstOrNull { it.id == id }?.trackIds?.mapNotNull { lib.byId[it] }
        }
        else -> null
    }

    fun children(
        parent: String,
        lib: Library,
        playlists: List<Playlist>,
        favorites: Set<Long>,
        recent: List<Long>,
    ): List<MediaItem> = when (parent) {
        ROOT -> rootChildren(lib, playlists, favorites)
        "albums" -> lib.albums.map { folder("album:${it.id}", it.title, it.artist, it.artUri) }
        "artists" -> lib.artists.map { folder("artist:${it.name}", it.name, "${it.tracks.size} треков", it.artUri) }
        "playlists" -> playlists.map { folder("pl:${it.id}", it.name, "${it.trackIds.size} треков") }
        else -> tracksFor(parent, lib, playlists, favorites, recent)?.map { playable(parent, it) } ?: emptyList()
    }

    fun <T> page(list: List<T>, page: Int, pageSize: Int): List<T> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return list
        val from = (page.coerceAtLeast(0).toLong() * pageSize).coerceAtMost(list.size.toLong()).toInt()
        val to = (from + pageSize).coerceAtMost(list.size)
        return list.subList(from, to)
    }
}
