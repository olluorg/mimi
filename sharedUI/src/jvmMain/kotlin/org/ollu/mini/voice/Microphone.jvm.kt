package org.ollu.mini.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

@Composable
actual fun rememberMicrophoneLevels(): MicrophoneLevels = remember { JvmMicrophone() }

/**
 * Loudness from the default input line. The desktop build is mostly where
 * scenes are tried out, so it matters that the voice games work there too;
 * the operating system asks for microphone permission itself where it must.
 */
private class JvmMicrophone : MicrophoneLevels {
    override val isSupported = true

    @Volatile private var running = false

    override fun start(onFrame: (FloatArray, Int) -> Unit, onUnavailable: () -> Unit) {
        if (running) return
        running = true
        Thread({
            val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)
            val line: TargetDataLine = runCatching {
                AudioSystem.getTargetDataLine(format).also { it.open(format); it.start() }
            }.getOrElse {
                running = false
                onUnavailable()
                return@Thread
            }
            val bytes = ByteArray(SAMPLE_RATE.toInt() / 20 * 2)   // 50 ms
            try {
                while (running) {
                    val read = line.read(bytes, 0, bytes.size)
                    if (read <= 0) continue
                    val samples = FloatArray(read / 2) { i ->
                        val lo = bytes[2 * i].toInt() and 0xFF
                        val hi = bytes[2 * i + 1].toInt()
                        ((hi shl 8) or lo).toShort() / 32768f
                    }
                    onFrame(samples, SAMPLE_RATE.toInt())
                }
            } finally {
                runCatching { line.stop() }
                line.close()
            }
        }, "mimi-microphone").apply { isDaemon = true; start() }
    }

    override fun stop() {
        running = false
    }

    private companion object {
        const val SAMPLE_RATE = 16000f
    }
}
