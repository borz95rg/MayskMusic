package com.maysk.music.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SleepState(val endAt: Long? = null, val endOfTrack: Boolean = false) {
    val active: Boolean get() = endAt != null || endOfTrack
}

class SleepTimer(
    private val scope: CoroutineScope,
    private val onFire: () -> Unit,
    private val setPauseAtEnd: (Boolean) -> Unit,
) {
    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(minutes: Int) {
        cancel()
        val end = System.currentTimeMillis() + minutes * 60_000L
        _state.value = SleepState(endAt = end)
        job = scope.launch {
            delay(minutes * 60_000L)
            onFire()
            _state.value = SleepState()
        }
    }

    fun endOfTrack() {
        cancel()
        setPauseAtEnd(true)
        _state.value = SleepState(endOfTrack = true)
    }

    /** Вызывается, когда плеер встал на паузу в конце трека. */
    fun onEndOfTrackReached() {
        if (_state.value.endOfTrack) cancel()
    }

    fun cancel() {
        job?.cancel()
        job = null
        setPauseAtEnd(false)
        _state.value = SleepState()
    }
}
