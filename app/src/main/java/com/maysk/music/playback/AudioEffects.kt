package com.maysk.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.maysk.music.data.EqState
import com.maysk.music.data.UserDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqCapabilities(
    val available: Boolean = false,
    val bandFreqsHz: List<Int> = emptyList(),
    val minMb: Int = -1500,
    val maxMb: Int = 1500,
    val bassSupported: Boolean = false,
    val virtSupported: Boolean = false,
    val loudnessSupported: Boolean = false,
)

/**
 * Работает поверх стандартных аудиоэффектов Android (android.media.audiofx),
 * привязанных к аудиосессии плеера. Если устройство не поддерживает эффект —
 * соответствующий переключатель в UI скрывается/блокируется.
 */
class AudioEffects(private val store: UserDataStore) {
    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null

    private val _caps = MutableStateFlow(EqCapabilities())
    val caps: StateFlow<EqCapabilities> = _caps.asStateFlow()

    fun attach(sessionId: Int) {
        release()
        equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virt = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loud = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        val eq = equalizer
        _caps.value = if (eq != null) {
            val range = runCatching { eq.bandLevelRange }.getOrNull()
            val n = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
            EqCapabilities(
                available = n > 0,
                bandFreqsHz = (0 until n).map { runCatching { eq.getCenterFreq(it.toShort()) / 1000 }.getOrDefault(0) },
                minMb = range?.getOrNull(0)?.toInt() ?: -1500,
                maxMb = range?.getOrNull(1)?.toInt() ?: 1500,
                bassSupported = bass?.let { runCatching { it.strengthSupported }.getOrDefault(false) } ?: false,
                virtSupported = virt?.let { runCatching { it.strengthSupported }.getOrDefault(false) } ?: false,
                loudnessSupported = loud != null,
            )
        } else EqCapabilities(
            bassSupported = bass != null,
            virtSupported = virt != null,
            loudnessSupported = loud != null,
        )
        apply(store.eq.value)
    }

    fun apply(s: EqState) {
        runCatching {
            equalizer?.let { eq ->
                eq.setEnabled(s.enabled)
                val caps = _caps.value
                s.bandLevels.forEachIndexed { i, lvl ->
                    if (i < caps.bandFreqsHz.size) eq.setBandLevel(i.toShort(), lvl.coerceIn(caps.minMb, caps.maxMb).toShort())
                }
            }
        }
        runCatching {
            bass?.let { b ->
                b.setEnabled(s.enabled && s.bassEnabled)
                if (b.strengthSupported) b.setStrength(s.bassBoost.coerceIn(0, 1000).toShort())
            }
        }
        runCatching {
            virt?.let { v ->
                v.setEnabled(s.enabled && s.virtEnabled)
                if (v.strengthSupported) v.setStrength(s.virtualizer.coerceIn(0, 1000).toShort())
            }
        }
        runCatching {
            loud?.let { l ->
                l.setTargetGain(s.loudnessGainMb.coerceIn(0, 2000))
                l.setEnabled(s.enabled && s.loudnessEnabled)
            }
        }
    }

    fun update(f: (EqState) -> EqState) {
        store.updateEq(f)
        apply(store.eq.value)
    }

    /** Уровни профиля для текущих полос устройства (интерполяция по частоте). */
    fun levelsForProfile(name: String): List<Int> {
        val caps = _caps.value
        store.eq.value.customProfiles[name]?.let { if (it.size == caps.bandFreqsHz.size) return it }
        val curve = PRESETS[name] ?: return caps.bandFreqsHz.map { 0 }
        return caps.bandFreqsHz.map { hz -> (interpolate(curve, hz) * 100).toInt().coerceIn(caps.minMb, caps.maxMb) }
    }

    private fun interpolate(curve: List<Float>, hz: Int): Float {
        // curve задана для 10 стандартных частот
        val f = hz.coerceAtLeast(20).toDouble()
        val logs = REF_FREQS.map { Math.log10(it.toDouble()) }
        val x = Math.log10(f)
        if (x <= logs.first()) return curve.first()
        if (x >= logs.last()) return curve.last()
        val i = logs.indexOfLast { it <= x }
        val t = ((x - logs[i]) / (logs[i + 1] - logs[i])).toFloat()
        return curve[i] + (curve[i + 1] - curve[i]) * t
    }

    fun release() {
        listOfNotNull(equalizer, bass, virt, loud).forEach { runCatching { it.release() } }
        equalizer = null; bass = null; virt = null; loud = null
    }

    companion object {
        private val REF_FREQS = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /** Кривые в дБ для 10 стандартных частот. */
        val PRESETS: Map<String, List<Float>> = linkedMapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Pop" to listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f),
            "Rock" to listOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f),
            "Rap" to listOf(6f, 5f, 3f, 1f, -1f, 0f, 1f, 2f, 2f, 3f),
            "Hip-Hop" to listOf(5f, 5f, 3f, 2f, -1f, -1f, 1f, 0f, 2f, 3f),
            "Jazz" to listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
            "Classical" to listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f),
            "Electronic" to listOf(5f, 4f, 1f, 0f, -2f, 2f, 1f, 2f, 4f, 5f),
            "Vocal" to listOf(-2f, -3f, -2f, 1f, 3f, 4f, 3f, 1f, 0f, -1f),
            "Acoustic" to listOf(4f, 3f, 2f, 1f, 2f, 2f, 3f, 3f, 2f, 1f),
            "Deep Bass" to listOf(8f, 7f, 5f, 3f, 0f, -1f, -1f, 0f, 0f, 1f),
            "Podcast" to listOf(-4f, -3f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -2f),
        )
    }
}
