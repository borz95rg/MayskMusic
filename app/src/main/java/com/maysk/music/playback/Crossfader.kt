package com.maysk.music.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Настоящий crossfade: за N секунд до конца трека «хвост» текущей песни
 * продолжает играть во втором (вспомогательном) плеере и плавно затихает,
 * а основной плеер (тот, которым управляют уведомление, Android Auto и приложение)
 * уже переходит к следующему треку и плавно набирает громкость. Треки звучат одновременно.
 */
class Crossfader(
    private val context: Context,
    private val scope: CoroutineScope,
    private val main: ExoPlayer,
    private val audioSessionId: Int,
    private val fadeSeconds: () -> Int,
    private val balance: () -> Float,
) {
    private var tail: ExoPlayer? = null
    private val tailBalance = BalanceProcessor()
    private var loop: Job? = null
    private var tailJob: Job? = null
    /** Основной плеер сейчас набирает громкость после перехода. */
    private var rampStart = -1L
    private var rampMs = 0L
    private var lastIndex = -1

    fun start() {
        loop?.cancel()
        main.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Пауза во время перехода — останавливаем «хвост», чтобы не играл сам по себе.
                if (!isPlaying && !main.playWhenReady) stopTail()
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                // Ручная перемотка или переключение пользователем — переход отменяется.
                if (reason == Player.DISCONTINUITY_REASON_SEEK && rampStart < 0) stopTail()
            }
        })
        loop = scope.launch {
            while (isActive) {
                val fadeMs = fadeSeconds() * 1000L
                if (fadeMs <= 0L) {
                    stopTail(); rampStart = -1
                    if (main.volume != 1f) main.volume = 1f
                    delay(500); continue
                }
                if (!main.isPlaying) { delay(250); continue }

                // Набор громкости основного плеера после перехода.
                if (rampStart >= 0) {
                    val t = ((System.currentTimeMillis() - rampStart).toFloat() / rampMs).coerceIn(0f, 1f)
                    main.volume = easeIn(t)
                    if (t >= 1f) rampStart = -1
                } else if (main.volume < 1f && tail == null) {
                    main.volume = 1f
                }

                val dur = main.duration
                val pos = main.currentPosition
                val idx = main.currentMediaItemIndex
                if (idx != lastIndex) lastIndex = idx
                if (dur != C.TIME_UNSET && dur > fadeMs * 3 && tail == null && rampStart < 0 && dur - pos in 1..fadeMs) {
                    if (main.hasNextMediaItem() && main.repeatMode != Player.REPEAT_MODE_ONE) {
                        crossfade(dur - pos)
                    } else {
                        // Последний трек: просто плавно затухаем.
                        main.volume = ((dur - pos).toFloat() / fadeMs).coerceIn(0.05f, 1f)
                    }
                }
                delay(60)
            }
        }
    }

    private suspend fun crossfade(remainingMs: Long) {
        val item = main.currentMediaItem ?: return
        val pos = main.currentPosition
        val startVolume = main.volume
        val t = obtainTail()
        t.setMediaItem(item, pos)
        t.volume = startVolume
        t.prepare()
        t.play()
        // Ждём, пока «хвост» реально зазвучит (обычно 100–300 мс), чтобы не было провала.
        var waited = 0
        while (t.playbackState != Player.STATE_READY && waited < 800) { delay(20); waited += 20 }
        if (t.playbackState != Player.STATE_READY) { stopTail(); return }
        t.seekTo(main.currentPosition)

        val fadeMs = remainingMs.coerceAtLeast(1000L)
        main.volume = 0f
        rampStart = System.currentTimeMillis()
        rampMs = fadeMs
        main.seekToNextMediaItem()

        tailJob?.cancel()
        tailJob = scope.launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                val p = ((System.currentTimeMillis() - start).toFloat() / fadeMs).coerceIn(0f, 1f)
                tail?.volume = startVolume * (1f - easeIn(p))
                if (p >= 1f || tail?.playbackState == Player.STATE_ENDED) break
                delay(40)
            }
            stopTail()
        }
    }

    private fun easeIn(t: Float): Float = t * t * (3 - 2 * t) // плавная S-кривая

    private fun obtainTail(): ExoPlayer {
        tail?.let { return it }
        tailBalance.balance = balance()
        val sink = DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(tailBalance)).build()
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<Renderer>(MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, handler, audioListener, sink))
        }
        val p = ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ false,
            )
            .build()
        // Тот же аудиосеанс — эквалайзер и эффекты действуют и на «хвост».
        p.setAudioSessionId(audioSessionId)
        tail = p
        return p
    }

    fun stopTail() {
        tailJob?.cancel(); tailJob = null
        tail?.let { it.stop(); it.release() }
        tail = null
    }

    fun release() {
        loop?.cancel()
        stopTail()
    }
}
