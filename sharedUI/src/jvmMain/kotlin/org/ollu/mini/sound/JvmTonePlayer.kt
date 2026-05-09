package org.ollu.mini.sound

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

actual fun createTonePlayer(): TonePlayer = JvmTonePlayer()

private class JvmTonePlayer : TonePlayer {
    private val sampleRate = 44100

    override fun playSequence(notes: NoteSequence) {
        Thread {
            val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            val info   = DataLine.Info(SourceDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) return@Thread
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(format, sampleRate / 10 * 2)
            line.start()
            for ((freq, durationMs) in notes) {
                line.write(synthesize(freq, durationMs), 0, sampleRate * durationMs / 1000 * 2)
            }
            line.drain()
            line.close()
        }.apply { isDaemon = true; start() }
    }

    private fun synthesize(freqHz: Float, durationMs: Int): ByteArray {
        val numSamples  = sampleRate * durationMs / 1000
        val fadeSamples = min(sampleRate * 25 / 1000, numSamples / 4)
        val buf = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val envelope = when {
                i < fadeSamples              -> i.toDouble() / fadeSamples
                i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                else                         -> 1.0
            }
            val raw = if (freqHz > 0f) sin(2.0 * PI * freqHz * i / sampleRate) * 0.45 * envelope else 0.0
            val s   = (raw * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buf[2 * i]     = (s.toInt() and 0xFF).toByte()
            buf[2 * i + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
        }
        return buf
    }
}
