package org.ollu.mini.voice

import androidx.compose.runtime.Composable
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Short slices of microphone audio, analysed on the spot and thrown away.
 *
 * Speech games need to know that the child made a sound — and, for the first
 * vowels, which one. Each slice lives for the few milliseconds it takes to
 * measure its loudness, its periodicity and its resonances: nothing is
 * stored, nothing is recognised as words, nothing leaves the device.
 */
interface MicrophoneLevels {
    /**
     * False where the platform gives no microphone at all. Speech games still
     * work there through the adult's "сказал(а)" button.
     */
    val isSupported: Boolean

    /**
     * Starts delivering audio in slices of about 50 ms, as samples in -1..1
     * with their sample rate — a fresh array each time, free to keep. Asks
     * for permission first where the platform requires it; [onUnavailable] is
     * called if it is refused or the microphone cannot be opened.
     *
     * Callbacks may arrive on any thread, and on a background one the slice
     * can be analysed right there, off the UI.
     */
    fun start(onFrame: (samples: FloatArray, sampleRate: Int) -> Unit, onUnavailable: () -> Unit)

    fun stop()
}

@Composable
expect fun rememberMicrophoneLevels(): MicrophoneLevels

/** For platforms without a microphone: the adult's button is the only input. */
object NoMicrophone : MicrophoneLevels {
    override val isSupported = false
    override fun start(onFrame: (samples: FloatArray, sampleRate: Int) -> Unit, onUnavailable: () -> Unit) =
        onUnavailable()
    override fun stop() = Unit
}

/**
 * Loudness from raw samples in -1..1, mapped so a quiet room reads near 0 and
 * a child talking close to the device reads well above the detector's
 * threshold. -55 dBFS and below is silence, -15 dBFS and above is full scale.
 */
fun loudnessOf(samples: FloatArray, count: Int = samples.size): Float {
    if (count <= 0) return 0f
    var sum = 0.0
    for (i in 0 until count) sum += samples[i] * samples[i]
    return loudnessOfRms(sqrt(sum / count).toFloat())
}

fun loudnessOfRms(rms: Float): Float {
    if (rms <= 1e-6f) return 0f
    val db = 20f * log10(rms)
    return ((db + 55f) / 40f).coerceIn(0f, 1f)
}
