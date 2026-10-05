package org.ollu.mini.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceDetectorTest {

    /** Feeds [level] every 50 ms from [fromMs] for [durationMs]; returns everything emitted. */
    private fun VoiceDetector.run(level: Float, fromMs: Long, durationMs: Long, muted: Boolean = false) =
        (fromMs until fromMs + durationMs step 50).flatMap { feed(level, it, muted) }

    @Test
    fun `a sustained voice becomes one utterance`() {
        val detector = VoiceDetector()
        val out = detector.run(0.02f, 0, 1000) +
            detector.run(0.7f, 1000, 600) +
            detector.run(0.02f, 1600, 600)
        val utterances = out.filterIsInstance<VoiceDetector.Output.Utterance>()
        assertEquals(1, utterances.size)
        assertTrue(utterances.single().durationMs in 500..600)
        assertTrue(out.any { it is VoiceDetector.Output.Level })
    }

    @Test
    fun `a click is too short to count as speaking`() {
        val detector = VoiceDetector()
        val out = detector.run(0.02f, 0, 500) + detector.run(0.9f, 500, 50) + detector.run(0.02f, 550, 600)
        assertTrue(out.none { it is VoiceDetector.Output.Utterance })
    }

    @Test
    fun `a breath between syllables does not split the attempt`() {
        val detector = VoiceDetector()
        val out = detector.run(0.6f, 0, 300) + detector.run(0.02f, 300, 200) +
            detector.run(0.6f, 500, 300) + detector.run(0.02f, 800, 600)
        assertEquals(1, out.count { it is VoiceDetector.Output.Utterance }, "«ма… ма» is one attempt")
    }

    @Test
    fun `the app own voice is ignored while it plays`() {
        val detector = VoiceDetector()
        val out = detector.run(0.8f, 0, 800, muted = true) + detector.run(0.02f, 800, 600)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `a noisy room raises the bar`() {
        val detector = VoiceDetector()
        val quiet = detector.threshold
        detector.run(0.3f * 0.5f, 0, 20_000)
        assertTrue(detector.threshold > quiet, "steady background noise should lift the threshold")
    }

    @Test
    fun `loudness maps silence to zero and a near voice to the top`() {
        assertEquals(0f, loudnessOfRms(0f))
        assertEquals(1f, loudnessOfRms(0.5f))
        assertTrue(loudnessOfRms(0.01f) in 0.2f..0.5f)
    }

    @Test
    fun `a room that is noisy from the start is not a voice`() {
        val detector = VoiceDetector()
        // Fan noise hovering around the old threshold, with small dips.
        val out = (0L until 20_000L step 50).flatMap { t ->
            detector.feed(if ((t / 700) % 2 == 0L) 0.27f else 0.22f, t)
        }
        assertTrue(out.none { it is VoiceDetector.Output.Utterance }, "steady noise must never answer a turn")
    }

    @Test
    fun `a child speaking over that noise is still heard`() {
        val detector = VoiceDetector()
        detector.run(0.25f, 0, 5000)
        val out = detector.run(0.8f, 5000, 500) + detector.run(0.25f, 5500, 600)
        assertEquals(1, out.count { it is VoiceDetector.Output.Utterance })
    }

    @Test
    fun `the app is guarded until its sound reports the end`() {
        val token = PlaybackGuard.begin(expectedMs = 60_000)
        assertTrue(PlaybackGuard.isPlaying())
        PlaybackGuard.end(token)
        assertTrue(PlaybackGuard.isPlaying(), "the echo tail still counts")
        assertTrue(!PlaybackGuard.isPlaying(PlaybackGuard.nowMs() + 2_000))
    }

    private fun VoiceDetector.frames(frame: FrameFeatures, fromMs: Long, durationMs: Long) =
        (fromMs until fromMs + durationMs step 50).flatMap { feed(frame, it) }

    private val silence = FrameFeatures(0.03f, voicing = 0.1f)

    @Test
    fun `a quiet but clearly voiced vowel counts`() {
        val detector = VoiceDetector()
        // Below the bar for an unknown sound, above the lower bar for a voice.
        val quietVowel = FrameFeatures(0.2f, voicing = 0.9f)
        val out = detector.frames(silence, 0, 2000) + detector.frames(quietVowel, 2000, 600) +
            detector.frames(silence, 2600, 600)
        assertEquals(1, out.count { it is VoiceDetector.Output.Utterance })
    }

    @Test
    fun `a loud clap or knock is not a voice`() {
        val detector = VoiceDetector()
        val knock = FrameFeatures(0.9f, voicing = 0.15f)
        val out = detector.frames(silence, 0, 2000) + detector.frames(knock, 2000, 400) +
            detector.frames(silence, 2400, 600)
        assertTrue(out.none { it is VoiceDetector.Output.Utterance })
    }

    @Test
    fun `the vowel heard is reported with the attempt`() {
        val detector = VoiceDetector()
        val u = FrameFeatures(0.5f, voicing = 0.95f, f1 = 440f, f2 = 950f, frontness = 0.001f)
        val out = detector.frames(silence, 0, 1000) + detector.frames(u, 1000, 600) + detector.frames(silence, 1600, 600)
        val said = out.filterIsInstance<VoiceDetector.Output.Utterance>().single()
        assertEquals("у", said.vowel)
    }
}
