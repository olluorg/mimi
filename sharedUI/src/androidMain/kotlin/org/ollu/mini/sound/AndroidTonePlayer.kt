package org.ollu.mini.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

actual fun createTonePlayer(): TonePlayer = AndroidTonePlayer()

private class AndroidTonePlayer : TonePlayer {
    private val sampleRate = 44100

    override fun playSequence(notes: NoteSequence) {
        Thread {
            for ((freq, durationMs) in notes) {
                playNote(freq, durationMs)
            }
        }.apply { isDaemon = true; start() }
    }

    private fun playNote(freqHz: Float, durationMs: Int) {
        val numSamples  = sampleRate * durationMs / 1000
        val fadeSamples = min(sampleRate * 25 / 1000, numSamples / 4)
        val buf = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val envelope = when {
                i < fadeSamples              -> i.toDouble() / fadeSamples
                i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                else                         -> 1.0
            }
            buf[i] = if (freqHz > 0f)
                (sin(2.0 * PI * freqHz * i / sampleRate) * 0.45 * envelope * Short.MAX_VALUE).toInt().toShort()
            else 0
        }
        val minBuf  = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(numSamples * 2, minBuf)
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufSize,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(buf, 0, numSamples)
        track.play()
        Thread.sleep(durationMs.toLong() + 50L)
        track.stop()
        track.release()
    }
}
