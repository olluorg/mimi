package org.ollu.mini.voice

/**
 * Turns a stream of analysed audio slices into what a speech game cares
 * about: "the child is making a sound, this loud" and "the child said
 * something" — and, when it was one of the first vowels, which.
 *
 * Built for the hardest case: short, quiet, unclear sounds from a child who
 * may never have used their voice on purpose before.
 *
 *  - A voice is recognised by being *periodic* — the vocal folds beating —
 *    not just by being loud. A quiet «а-а» counts, at a lower bar than any
 *    other sound; a loud clap, a tap on the screen, a door, traffic or a fan
 *    do not, because none of them repeat like a voice.
 *  - The room's own noise is followed continuously, so a noisy room raises
 *    the bar within seconds and a quiet one lowers it.
 *  - A sound has to last [minUtteranceMs]; an utterance ends after
 *    [endSilenceMs] of quiet, so "ма… ма" with a breath is one attempt.
 *
 * While the app itself is talking the input is ignored ([feed] with muted):
 * the loudest voice in the room at that moment is the device.
 */
class VoiceDetector(
    private val minThreshold:   Float = 0.25f,
    private val aboveNoise:     Float = 0.18f,
    private val minUtteranceMs: Long  = 250L,
    private val endSilenceMs:   Long  = 350L,
    private val levelEveryMs:   Long  = 120L
) {
    sealed interface Output {
        /** The voice right now, 0..1 above the threshold. */
        data class Level(val level: Float) : Output

        /**
         * One finished attempt. [vowel] is «а», «о», «у», «и» or «э» when the
         * attempt was clearly one of them, and null otherwise — which is
         * often, and never held against the child.
         */
        data class Utterance(val durationMs: Long, val peak: Float, val vowel: String? = null) : Output
    }

    private var noiseFloor   = -1f      // unknown until the first reading
    private var speaking     = false
    private var startMs      = 0L
    private var lastLoudMs   = 0L
    private var lastLevelMs: Long? = null
    private var peak         = 0f
    private val frames       = mutableListOf<FrameFeatures>()
    private var calibrating  = CALIBRATION_FRAMES

    companion object {
        /**
         * A detector tuned by the adult's sensitivity setting, 0..1. The middle
         * matches the defaults; the ends suit a whisperer in a quiet room and a
         * busy classroom.
         */
        fun forSensitivity(sensitivity: Float): VoiceDetector {
            val s = sensitivity.coerceIn(0f, 1f)
            return VoiceDetector(
                minThreshold = 0.36f - 0.22f * s,
                aboveNoise   = 0.26f - 0.16f * s
            )
        }

        /** How periodic a slice must be to count as a voice at the lower bar. */
        const val VOICED = 0.55f

        /** Below this a sound is certainly not a voice, however loud. */
        const val UNVOICED = 0.3f

        private const val PEAK_MARGIN = 0.05f

        /** A voice held this many slices counts even without a clear peak. */
        private const val SUSTAINED_FRAMES = 4

        /**
         * The first slices after the microphone opens only measure the room.
         * Without them the room's own hum, met before anything was known
         * about it, was taken for a voice that never stopped.
         */
        private const val CALIBRATION_FRAMES = 8
    }

    /** The level a sound of uncertain voicing has to reach to count. */
    val threshold: Float get() = maxOf(minThreshold, noiseFloor.coerceAtLeast(0f) + aboveNoise)

    /** The lower bar for a sound that is clearly a voice. */
    val voicedThreshold: Float get() = maxOf(minThreshold * 0.6f, noiseFloor.coerceAtLeast(0f) + aboveNoise * 0.5f)

    /**
     * Loudness only, for callers with nothing else to go on: judged at the
     * stricter bar, as a sound that may or may not be a voice.
     */
    fun feed(level: Float, nowMs: Long, muted: Boolean = false): List<Output> =
        feed(FrameFeatures(level, voicing = UNVOICED), nowMs, muted)

    fun feed(frame: FrameFeatures, nowMs: Long, muted: Boolean = false): List<Output> {
        if (muted) {
            // Whatever was building up is the app's own voice, not the child's.
            speaking = false
            frames.clear()
            return emptyList()
        }
        val level = frame.loudness
        if (calibrating > 0) {
            calibrating--
            trackNoise(level)
            return emptyList()
        }
        val isVoice = when {
            frame.voicing >= VOICED   -> level >= voicedThreshold
            frame.voicing >= UNVOICED -> level >= threshold
            else                      -> false
        }
        if (!isVoice) trackNoise(level)

        if (isVoice) {
            if (!speaking) {
                speaking = true
                startMs  = nowMs
                peak     = 0f
                frames.clear()
            }
            lastLoudMs = nowMs
            peak = maxOf(peak, level)
            frames += frame
            val sinceLevel = lastLevelMs?.let { nowMs - it }
            if (sinceLevel == null || sinceLevel >= levelEveryMs) {
                lastLevelMs = nowMs
                val gate = voicedThreshold
                val above = ((level - gate) / (1f - gate)).coerceIn(0.05f, 1f)
                return listOf(Output.Level(above))
            }
            return emptyList()
        }

        if (!speaking) return emptyList()
        if (nowMs - lastLoudMs < endSilenceMs) return emptyList()

        speaking = false
        val duration = lastLoudMs - startMs
        // Either it rose clearly above the room at least once, or it was a
        // voice held long enough that there is no mistaking it.
        val voiced = frames.count { it.voicing >= VOICED }
        val clear = peak >= threshold + PEAK_MARGIN || voiced >= SUSTAINED_FRAMES
        val vowel = VowelClassifier.classifyUtterance(frames)?.letter
        frames.clear()
        return if (duration >= minUtteranceMs && clear) listOf(Output.Utterance(duration, peak, vowel)) else emptyList()
    }

    /**
     * Follows the quietest the room gets: drops at once when it is quieter,
     * creeps up slowly when it is louder. A fan or traffic that is always there
     * lifts the floor within seconds; a child's voice is kept out of it
     * entirely, so talking does not raise the bar against itself.
     */
    private fun trackNoise(level: Float) {
        noiseFloor = when {
            noiseFloor < 0f    -> level
            level < noiseFloor -> noiseFloor + (level - noiseFloor) * 0.3f
            speaking           -> noiseFloor + (level - noiseFloor) * 0.001f
            else               -> noiseFloor + (level - noiseFloor) * 0.005f
        }
    }
}
