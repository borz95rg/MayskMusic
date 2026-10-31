package com.maysk.music.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.maysk.music.MainActivity
import com.maysk.music.MayskApp
import com.maysk.music.widget.PlayerWidget

/**
 * Фоновое воспроизведение: ExoPlayer + MediaSession.
 * Media3 сам показывает уведомление с управлением, работает экран блокировки,
 * Bluetooth-кнопки, аудиофокус (звонки) и пауза при отключении наушников.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MayskApp
        val container = app.container

        val player = ExoPlayer.Builder(this)
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

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Контроллеры передают MediaItem без URI — восстанавливаем его по ID из MediaStore.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri
                            ?: item.mediaId.toLongOrNull()?.let {
                                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
                            }
                        if (uri == null) item else item.buildUpon().setUri(uri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        val container = (application as MayskApp).container
        container.saveQueueNow()
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
