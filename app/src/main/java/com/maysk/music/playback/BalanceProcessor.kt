package com.maysk.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Баланс L/R: уменьшает громкость одного из каналов стерео-сигнала.
 * Работает с PCM 16 бит и float. Для моно и других форматов отключается.
 */
class BalanceProcessor : BaseAudioProcessor() {
    /** -1 = только левый канал, 0 = центр, 1 = только правый. */
    @Volatile
    var balance: Float = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.channelCount == 2 &&
            (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)
        return if (supported) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val b = balance.coerceIn(-1f, 1f)
        val left = if (b > 0f) 1f - b else 1f
        val right = if (b < 0f) 1f + b else 1f
        val out = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (input.remaining() >= 8) {
                out.putFloat(input.getFloat() * left)
                out.putFloat(input.getFloat() * right)
            }
        } else {
            while (input.remaining() >= 4) {
                out.putShort((input.getShort() * left).toInt().toShort())
                out.putShort((input.getShort() * right).toInt().toShort())
            }
        }
        // остаток (неполный кадр) просто копируем
        while (input.hasRemaining()) out.put(input.get())
        out.flip()
    }
}
