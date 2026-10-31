package com.maysk.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.maysk.music.data.HistoryEntry
import com.maysk.music.data.Library
import com.maysk.music.data.QueueState
import com.maysk.music.data.Track
import com.maysk.music.data.UserDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val connected: Boolean = false,
    val currentId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val artUri: Uri? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Индексы окон в порядке воспроизведения (учитывает shuffle). */
    val order: List<Int> = emptyList(),
    /** mediaId по индексу окна. */
    val items: List<String> = emptyList(),
    val currentIndex: Int = -1,
) {
    val hasMedia: Boolean get() = items.isNotEmpty()
}

class PlayerController(
    private val context: Context,
    private val store: UserDataStore,
    private val scope: CoroutineScope,
    private val libraryProvider: () -> Library,
    private val onEndOfTrackPause: () -> Unit,
) {
    private var controller: MediaController? = null
    private var connecting = false

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var pollJob: Job? = null
    private var pendingAction: (() -> Unit)? = null
    private var restored = false

    // Статистика прослушивания
    private var statId: Long? = null
    private var statStart = 0L
    private var statListened = 0L
    private var lastTick = 0L
    private var lastQueueSave = 0L

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = try { future.get() } catch (e: Exception) { null } ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { refresh() }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { onTrackChanged() }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) onEndOfTrackPause()
                }
            })
            refresh()
            onTrackChanged()
            tryRestore()
            startPolling()
            pendingAction?.invoke()
            pendingAction = null
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            lastTick = System.currentTimeMillis()
            while (isActive) {
                val c = controller
                val now = System.currentTimeMillis()
                if (c != null) {
                    _position.value = c.currentPosition
                    if (c.isPlaying) statListened += (now - lastTick).coerceIn(0, 2000)
                    if (now - lastQueueSave > 15_000) { saveQueue(); lastQueueSave = now }
                }
                lastTick = now
                delay(if (c?.isPlaying == true) 500L else 1500L)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val tl = c.currentTimeline
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        val order = ArrayList<Int>(items.size)
        if (!tl.isEmpty) {
            var i = tl.getFirstWindowIndex(c.shuffleModeEnabled)
            var guard = 0
            while (i != C.INDEX_UNSET && guard < items.size) {
                order += i
                i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
                guard++
            }
        }
        val item = c.currentMediaItem
        val meta = item?.mediaMetadata
        val id = item?.mediaId?.toLongOrNull()
        val track = id?.let { libraryProvider().byId[it] }
        _state.value = PlaybackState(
            connected = true,
            currentId = id,
            title = track?.title ?: meta?.title?.toString() ?: "",
            artist = track?.artist ?: meta?.artist?.toString() ?: "",
            artUri = track?.artUri ?: meta?.artworkUri,
            isPlaying = c.isPlaying,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (track?.durationMs ?: 0L),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            order = if (order.size == items.size) order else items.indices.toList(),
            items = items,
            currentIndex = c.currentMediaItemIndex,
        )
        _position.value = c.currentPosition
    }

    private fun onTrackChanged() {
        flushStats()
        val id = controller?.currentMediaItem?.mediaId?.toLongOrNull()
        statId = id
        statStart = System.currentTimeMillis()
        statListened = 0
        saveQueue()
    }

    fun flushStats() {
        val id = statId ?: return
        if (statListened > 0) store.addHistory(HistoryEntry(id, statStart, statListened))
        statListened = 0
        statStart = System.currentTimeMillis()
    }

    fun saveQueue() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val ids = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        if (ids.size != c.mediaItemCount) return // внешние файлы не сохраняем
        store.saveQueue(QueueState(ids, c.currentMediaItemIndex, c.currentPosition, c.shuffleModeEnabled, c.repeatMode))
    }

    /** Восстановление очереди после перезапуска (вызывается и после загрузки библиотеки). */
    fun tryRestore() {
        val c = controller ?: return
        val lib = libraryProvider()
        if (restored || !lib.loaded) return
        restored = true
        if (c.mediaItemCount > 0) return
        val q = store.queue
        val tracks = q.ids.mapNotNull { lib.byId[it] }
        if (tracks.isEmpty()) return
        val curId = q.ids.getOrNull(q.index)
        val idx = tracks.indexOfFirst { it.id == curId }.coerceAtLeast(0)
        c.setMediaItems(tracks.map { it.toMediaItem() }, idx, if (idx >= 0 && tracks[idx].id == curId) q.positionMs else 0L)
        c.shuffleModeEnabled = q.shuffle
        c.repeatMode = q.repeatMode
        c.prepare()
    }

    // ------------------- Команды -------------------

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: run { pendingAction = { playTracks(tracks, startIndex, shuffle) }; connect(); return }
        if (tracks.isEmpty()) return
        val list = if (shuffle) smartShuffle(tracks) else tracks
        val start = if (shuffle) 0 else startIndex.coerceIn(0, tracks.lastIndex)
        c.shuffleModeEnabled = false
        c.setMediaItems(list.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun playUri(uri: Uri, title: String) {
        val c = controller ?: run { pendingAction = { playUri(uri, title) }; connect(); return }
        val item = MediaItem.Builder()
            .setMediaId("ext:$uri")
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playTracks(tracks); return }
        c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem() })
    }

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playTracks(tracks); return }
        c.addMediaItems(tracks.map { it.toMediaItem() })
    }

    fun togglePlay() {
        val c = controller ?: run { pendingAction = { togglePlay() }; connect(); return }
        if (c.mediaItemCount == 0) {
            val lib = libraryProvider()
            if (lib.tracks.isNotEmpty()) playTracks(lib.tracks, shuffle = true)
            return
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() { controller?.pause() }
    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); _position.value = ms }
    fun skipToIndex(windowIndex: Int) { controller?.let { it.seekTo(windowIndex, 0); it.play() } }
    fun removeAt(windowIndex: Int) { controller?.removeMediaItem(windowIndex) }
    fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }
    fun clearQueue() { controller?.let { it.stop(); it.clearMediaItems() } }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun release() {
        flushStats()
        saveQueue()
        pollJob?.cancel()
        controller?.release()
        controller = null
    }

    companion object {
        /** «Умное» перемешивание: избегает двух подряд треков одного исполнителя. */
        fun smartShuffle(tracks: List<Track>): List<Track> {
            val pool = tracks.shuffled().toMutableList()
            val out = ArrayList<Track>(pool.size)
            while (pool.isNotEmpty()) {
                val lastArtist = out.lastOrNull()?.artist
                val idx = pool.indexOfFirst { it.artist != lastArtist }.let { if (it < 0) 0 else it }
                out += pool.removeAt(idx)
            }
            return out
        }
    }
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri)
            .setIsPlayable(true)
            .build()
    )
    .build()
