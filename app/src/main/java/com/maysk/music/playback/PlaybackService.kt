package com.maysk.music.playback

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.maysk.music.AppContainer
import com.maysk.music.MainActivity
import com.maysk.music.MayskApp
import com.maysk.music.data.Library
import com.maysk.music.data.SmartSearch
import com.maysk.music.widget.PlayerWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Фоновое воспроизведение: ExoPlayer + MediaLibrarySession.
 * Media3 сам показывает уведомление с управлением, работает экран блокировки,
 * Bluetooth-кнопки, аудиофокус (звонки) и пауза при отключении наушников.
 * MediaLibrarySession даёт каталог для Android Auto и голосовой поиск Google Ассистента.
 */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private lateinit var container: AppContainer
    private val balance = BalanceProcessor()
    private var fadeJob: Job? = null
    private var settingsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as MayskApp).container

        // Если сервис запущен из Android Auto без открытия приложения — загружаем библиотеку.
        if (!container.library.library.value.loaded && hasAudioPermission()) container.library.start()

        // Свой набор рендереров: только аудио, с процессором баланса L/R.
        val sink = DefaultAudioSink.Builder(this)
            .setAudioProcessors(arrayOf(balance))
            .build()
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<Renderer>(MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener, sink))
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.setAudioSessionId(container.audioSessionId)
        container.effects.attach(container.audioSessionId)
        container.exoPlayer = player

        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                PlayerWidget.updateAll(this@PlaybackService, container)
            }
        })

        settingsJob = container.scope.launch {
            container.settings.state.collect { balance.balance = it.balance }
        }
        startFadeLoop(player)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
    }

    private fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Плавный переход: громкость плавно растёт в начале трека и плавно затухает в конце.
     * Длительность задаётся в настройках эквалайзера (0 — выключено).
     */
    private fun startFadeLoop(player: ExoPlayer) {
        fadeJob?.cancel()
        fadeJob = container.scope.launch {
            while (isActive) {
                val fadeMs = container.settings.current.crossfadeSec * 1000L
                if (fadeMs <= 0L) {
                    if (player.volume != 1f) player.volume = 1f
                    delay(500)
                    continue
                }
                if (player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    var vol = 1f
                    if (dur != C.TIME_UNSET && dur > fadeMs * 3) {
                        val fadeIn = (pos.toFloat() / fadeMs).coerceIn(0f, 1f)
                        val fadeOut = ((dur - pos).toFloat() / fadeMs).coerceIn(0f, 1f)
                        vol = minOf(fadeIn, fadeOut).coerceAtLeast(0.05f)
                    }
                    if (kotlin.math.abs(player.volume - vol) > 0.01f) player.volume = vol
                    delay(80)
                } else {
                    delay(300)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Каталог для Android Auto / голосовой поиск
    // ------------------------------------------------------------------

    private fun <T : Any> whenLoaded(block: (Library) -> T): ListenableFuture<T> {
        val lib = container.library.library.value
        if (lib.loaded) return Futures.immediateFuture(block(lib))
        val future = SettableFuture.create<T>()
        container.scope.launch {
            val loaded = withTimeoutOrNull(15_000) { container.library.library.first { it.loaded } }
            future.set(block(loaded ?: container.library.library.value))
        }
        return future
    }

    private fun recentIds(): List<Long> =
        container.userData.history.value.asReversed().map { it.trackId }.distinct().take(100)

    /** Превращает запросы контроллера (ID из каталога, поиск Ассистента, внешние файлы) в играбельные MediaItem. */
    private fun resolve(lib: Library, items: List<MediaItem>): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        for (item in items) {
            val query = item.requestMetadata.searchQuery
            val id = item.mediaId
            when {
                query != null -> {
                    val r = SmartSearch.find(lib, container.userData.playlists.value, container.userData.favorites.value, query)
                    out += r.tracks.map { it.toMediaItem() }
                }
                id.startsWith("ext:") -> {
                    val uri = item.requestMetadata.mediaUri
                    out += if (uri != null) item.buildUpon().setUri(uri).build() else item
                }
                else -> {
                    val trackId = id.substringAfterLast('|').toLongOrNull()
                    val track = trackId?.let { lib.byId[it] }
                    when {
                        track != null -> out += track.toMediaItem()
                        trackId != null -> out += item.buildUpon()
                            .setUri(item.requestMetadata.mediaUri
                                ?: ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId))
                            .build()
                        item.requestMetadata.mediaUri != null -> out += item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
                    }
                }
            }
        }
        return out
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(BrowseTree.rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = whenLoaded { lib ->
            val all = BrowseTree.children(parentId, lib, container.userData.playlists.value, container.userData.favorites.value, recentIds())
            LibraryResult.ofItemList(ImmutableList.copyOf(BrowseTree.page(all, page, pageSize)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = whenLoaded { lib ->
            val track = mediaId.substringAfterLast('|').toLongOrNull()?.let { lib.byId[it] }
            if (track != null) LibraryResult.ofItem(track.toMediaItem(), null)
            else LibraryResult.ofItem(BrowseTree.folder(mediaId, mediaId.substringAfter(':')), null)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            whenLoaded { lib ->
                val r = SmartSearch.find(lib, container.userData.playlists.value, container.userData.favorites.value, query)
                session.notifySearchResultChanged(browser, query, r.tracks.size, params)
                r.tracks.size
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = whenLoaded { lib ->
            val r = SmartSearch.find(lib, container.userData.playlists.value, container.userData.favorites.value, query)
            val items = r.tracks.map { BrowseTree.playable("search", it) }
            LibraryResult.ofItemList(ImmutableList.copyOf(BrowseTree.page(items, page, pageSize)), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = whenLoaded { lib ->
            resolve(lib, mediaItems).toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = whenLoaded { lib ->
            val single = mediaItems.singleOrNull()
            val parent = single?.mediaId?.takeIf { it.contains('|') }?.substringBeforeLast('|')
            val contextTracks = parent?.let {
                BrowseTree.tracksFor(it, lib, container.userData.playlists.value, container.userData.favorites.value, recentIds())
            }
            if (single != null && contextTracks != null && contextTracks.isNotEmpty()) {
                // Выбран трек в папке Android Auto — ставим в очередь всю папку.
                val id = single.mediaId.substringAfterLast('|').toLongOrNull()
                val idx = contextTracks.indexOfFirst { it.id == id }.coerceAtLeast(0)
                MediaSession.MediaItemsWithStartPosition(contextTracks.map { it.toMediaItem() }, idx, startPositionMs)
            } else {
                val resolved = resolve(lib, mediaItems)
                val idx = if (startIndex == C.INDEX_UNSET || resolved.size != mediaItems.size) 0 else startIndex
                val pos = if (resolved.size != mediaItems.size) C.TIME_UNSET else startPositionMs
                MediaSession.MediaItemsWithStartPosition(resolved, idx, pos)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        container.saveQueueNow()
        fadeJob?.cancel()
        settingsJob?.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        container.exoPlayer = null
        container.effects.release()
        PlayerWidget.updateAll(this, container)
        super.onDestroy()
    }
}
