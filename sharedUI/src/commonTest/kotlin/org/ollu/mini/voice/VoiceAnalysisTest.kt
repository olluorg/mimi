package org.ollu.mini.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vowels synthesised the classic way — a pulse train through the mouth's
 * resonances — so the analysis can be checked without recordings.
 */
class VoiceAnalysisTest {

    private val rate = 16_000

    /** A voiced vowel: glottal pulses at [pitch] shaped by formants. */
    private fun vowel(f1: Double, f2: Double, f3: Double, pitch: Double = 280.0, n: Int = 800, seed: Int = 1): FloatArray {
        val rnd = Random(seed)
        var source = DoubleArray(n) { i ->
            // A slightly jittered sawtooth: harmonics falling off like a real voice.
            val phase = (i * pitch / rate + rnd.nextDouble(-0.002, 0.002)) % 1.0
            1.0 - 2.0 * phase
        }
        for ((f, bw) in listOf(f1 to 90.0, f2 to 120.0, f3 to 180.0)) source = resonate(source, f, bw)
        val peak = source.maxOf { kotlin.math.abs(it) }
        return FloatArray(n) { (source[it] / peak * 0.3).toFloat() }
    }

    private fun resonate(x: DoubleArray, f: Double, bw: Double): DoubleArray {
        val r = exp(-PI * bw / rate)
        val c = 2 * r * cos(2 * PI * f / rate)
        val d = -r * r
        val y = DoubleArray(x.size)
        for (i in x.indices) {
            y[i] = x[i] + c * (if (i > 0) y[i - 1] else 0.0) + d * (if (i > 1) y[i - 2] else 0.0)
        }
        return y
    }

    private fun noise(n: Int = 800, level: Float = 0.2f): FloatArray {
        val rnd = Random(9)
        return FloatArray(n) { rnd.nextFloat() * 2 * level - level }
    }

    private fun heard(samples: FloatArray): VowelClassifier.Vowel? {
        // Several frames of the same vowel, as a held «а-а» would give.
        val frames = (0 until 6).map { VoiceAnalysis.analyze(samples, samples.size, rate) }
        return VowelClassifier.classifyUtterance(frames)
    }

    // Children's formant values, roughly: the centres the classifier expects,
    // nudged off-centre so the test is not simply echoing the table.
    @Test fun `а is heard as а`() = assertEquals(VowelClassifier.Vowel.A, heard(vowel(980.0, 1600.0, 3600.0)))
    @Test fun `о is heard as о`() = assertEquals(VowelClassifier.Vowel.O, heard(vowel(700.0, 1150.0, 3400.0)))
    @Test fun `у is heard as у`() = assertEquals(VowelClassifier.Vowel.U, heard(vowel(430.0, 1000.0, 3300.0)))
    @Test fun `и is heard as и`() = assertEquals(VowelClassifier.Vowel.I, heard(vowel(390.0, 2900.0, 3800.0)))
    @Test fun `э is heard as э`() = assertEquals(VowelClassifier.Vowel.E, heard(vowel(680.0, 2300.0, 3500.0)))

    @Test
    fun `a larger speaker is still matched by scale`() {
        // An adult woman's «а» is about fifteen percent lower throughout.
        assertEquals(VowelClassifier.Vowel.A, heard(vowel(850.0, 1400.0, 2900.0, pitch = 210.0)))
    }

    @Test
    fun `a vowel is strongly voiced and noise is not`() {
        val voiced = VoiceAnalysis.analyze(vowel(980.0, 1600.0, 3600.0), 800, rate)
        val hiss = VoiceAnalysis.analyze(noise(), 800, rate)
        assertTrue(voiced.voicing > 0.7f, "voicing of a vowel: ${voiced.voicing}")
        assertTrue(hiss.voicing < 0.4f, "voicing of noise: ${hiss.voicing}")
        assertTrue(voiced.pitchHz in 250f..310f, "pitch: ${voiced.pitchHz}")
    }

    @Test
    fun `noise names no vowel`() {
        val frames = (0 until 6).map { VoiceAnalysis.analyze(noise(), 800, rate) }
        assertEquals(null, VowelClassifier.classifyUtterance(frames))
    }

    /**
     * Many children, not one: voices from low to squeaky and mouths from
     * small to larger. Not every one has to be named — "not sure" costs the
     * child nothing — but a wrong name would give a bonus for the wrong
     * sound, so those must stay rare.
     */
    @Test
    fun `vowels across many voices are mostly named and rarely misnamed`() {
        val centres = mapOf(
            VowelClassifier.Vowel.A to Triple(1000.0, 1650.0, 3600.0),
            VowelClassifier.Vowel.O to Triple(680.0, 1100.0, 3400.0),
            VowelClassifier.Vowel.U to Triple(440.0, 950.0, 3300.0),
            VowelClassifier.Vowel.I to Triple(400.0, 3000.0, 3900.0),
            VowelClassifier.Vowel.E to Triple(700.0, 2350.0, 3500.0)
        )
        var right = 0; var wrong = 0; var unsure = 0
        val errors = mutableListOf<String>()
        for ((expected, f) in centres) for (pitch in listOf(220.0, 260.0, 300.0, 340.0, 390.0)) for (scale in listOf(0.88, 1.0, 1.1)) {
            val got = heard(vowel(f.first * scale, f.second * scale, f.third * scale, pitch = pitch, seed = pitch.toInt()))
            when (got) {
                expected -> right++
                null -> unsure++
                else -> { wrong++; errors += "$expected@${pitch.toInt()}Hz×$scale→$got" }
            }
        }
        val total = right + wrong + unsure
        println("VOWELS right=$right unsure=$unsure wrong=$wrong of $total ${errors.take(12)}")
        assertTrue(right >= total * 0.75, "named correctly only $right of $total")
        assertTrue(wrong <= total * 0.08, "misnamed $wrong of $total: $errors")
    }
}
