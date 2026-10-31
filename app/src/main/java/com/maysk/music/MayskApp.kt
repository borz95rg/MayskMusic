package com.maysk.music

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import com.maysk.music.data.CoverFinder
import com.maysk.music.data.LibraryRepository
import com.maysk.music.data.SettingsStore
import com.maysk.music.data.UserDataStore
import com.maysk.music.playback.AudioEffects
import com.maysk.music.playback.PlayerController
import com.maysk.music.playback.SleepTimer
import com.maysk.music.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MayskApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Простой контейнер зависимостей (вместо Hilt — меньше генерации кода, быстрее сборка). */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(context)
    val userData = UserDataStore(context)
    val library = LibraryRepository(context, settings, scope)
    val effects = AudioEffects(userData)

    /** Одна аудиосессия на всё время работы — к ней привязаны эквалайзер и визуализатор. */
    val audioSessionId: Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()

    /** Ссылка на ExoPlayer, пока работает сервис воспроизведения. */
    @Volatile
    var exoPlayer: ExoPlayer? = null

    val sleepTimer: SleepTimer = SleepTimer(
        scope = scope,
        onFire = { player.pause() },
        setPauseAtEnd = { exoPlayer?.pauseAtEndOfMediaItems = it },
    )

    val player: PlayerController = PlayerController(
        context = context,
        store = userData,
        scope = scope,
        libraryProvider = { library.library.value },
        onEndOfTrackPause = { sleepTimer.onEndOfTrackReached() },
    )

    val updates: UpdateManager = UpdateManager(context, settings, scope)

    val covers: CoverFinder = CoverFinder(context, userData, scope) { library.applyFilters() }

    init {
        scope.launch {
            library.library.collectLatest { if (it.loaded) player.tryRestore() }
        }
    }

    fun saveQueueNow() {
        player.flushStats()
        player.saveQueue()
    }
}
