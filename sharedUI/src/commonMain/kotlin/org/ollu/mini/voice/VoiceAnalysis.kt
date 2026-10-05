package org.ollu.mini.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * What one short slice of microphone audio sounds like.
 *
 * [voicing] is how periodic the slice is, 0..1: a voice — above all a vowel —
 * repeats with every beat of the vocal folds, while a clap, a tap on the
 * screen, traffic or a fan do not. [f1] and [f2] are the first two resonances
 * of the mouth, which is what tells «а» from «у»; zero when there is no voice
 * to measure.
 */
data class FrameFeatures(
    val loudness: Float,
    val voicing:  Float = 0f,
    val pitchHz:  Float = 0f,
    val f1:       Float = 0f,
    val f2:       Float = 0f,
    /** Front-vowel energy over back-vowel energy; see [VoiceAnalysis.frontness]. */
    val frontness: Float = 0f
)

/**
 * Plain signal processing, no recognition model and nothing sent anywhere:
 * periodicity by autocorrelation, and the mouth's resonances from a linear
 * prediction of the spectrum. Enough to hear that a child is using their
 * voice and, for the five vowels, which one.
 */
object VoiceAnalysis {

    /** Children's voices sit higher than adults'; a squeal can reach 800 Hz. */
    private const val MIN_PITCH = 75f
    private const val MAX_PITCH = 800f

    fun analyze(samples: FloatArray, count: Int, sampleRate: Int): FrameFeatures {
        val n = minOf(count, samples.size)
        if (n < sampleRate / 50) return FrameFeatures(loudnessOf(samples, n))
        val x = FloatArray(n)
        var mean = 0.0
        for (i in 0 until n) mean += samples[i]
        mean /= n
        for (i in 0 until n) x[i] = (samples[i] - mean).toFloat()

        val loudness = loudnessOf(x, n)
        val (voicing, pitch) = periodicity(x, sampleRate)
        // Resonances only mean something where there is a voice to shape.
        if (voicing < 0.4f) return FrameFeatures(loudness, voicing, pitch)
        val front = frontness(x, sampleRate)
        val (f1, f2) = formants(x, sampleRate, front)
        return FrameFeatures(loudness, voicing, pitch, f1, f2, front)
    }

    /**
     * The strongest repetition in the pitch range, normalised so a perfectly
     * periodic signal scores 1 whatever its level.
     */
    internal fun periodicity(x: FloatArray, rate: Int): Pair<Float, Float> {
        val minLag = (rate / MAX_PITCH).toInt().coerceAtLeast(2)
        val maxLag = (rate / MIN_PITCH).toInt().coerceAtMost(x.size / 2)
        if (maxLag <= minLag) return 0f to 0f
        val span = x.size - maxLag
        var e0 = 0.0
        for (i in 0 until span) e0 += x[i] * x[i]
        if (e0 <= 1e-9) return 0f to 0f
        val corr = DoubleArray(maxLag + 1)
        var best = 0.0
        for (lag in minLag..maxLag) {
            var r = 0.0
            var el = 0.0
            for (i in 0 until span) {
                r += x[i] * x[i + lag]
                el += x[i + lag] * x[i + lag]
            }
            if (el <= 1e-9) continue
            corr[lag] = r / sqrt(e0 * el)
            if (corr[lag] > best) best = corr[lag]
        }
        // A voice repeats at its period and at every multiple of it, all about
        // equally well. The shortest strong repeat is the pitch; taking the
        // strongest one halved it an octave at a time.
        var pitchLag = 0
        for (lag in minLag + 1 until maxLag) {
            if (corr[lag] >= 0.85 * best && corr[lag] >= corr[lag - 1] && corr[lag] >= corr[lag + 1]) {
                pitchLag = lag
                break
            }
        }
        val pitch = if (pitchLag > 0) rate.toFloat() / pitchLag else 0f
        return best.toFloat().coerceIn(0f, 1f) to pitch
    }

    /**
     * F1 and F2 from the peaks of an LPC spectral envelope — the standard way
     * to read vowel quality, and cheap enough to run twenty times a second.
     */
    internal fun formants(x: FloatArray, rate: Int, front: Float = frontness(x, rate)): Pair<Float, Float> {
        // A 32 ms window from the middle of the slice, pre-emphasised so the
        // weaker upper resonance is not drowned by the first.
        val order = (2 + rate / 1000).coerceIn(10, 18)
        val peaks = envelopePeaks(x, rate, order, preEmphasis = 0.97)
        val f1 = peaks.firstOrNull { it >= 200.0 } ?: return 0f to 0f
        var f2 = peaks.firstOrNull { it >= f1 + 250.0 } ?: return f1.toFloat() to 0f

        // Rounded back vowels — «у», «о» — have a second resonance that is
        // both close to the first and weak, so the peak search skips it and
        // lands on a front vowel's: «у» came out as «и». The treble tells them
        // apart: a front vowel has real energy up there, a back one none.
        if (f1 < 700.0 && f2 > 2000.0 && front < FRONT_VOWEL_MIN) {
            // Too close to the first to show as a peak of its own — and in a
            // child's high voice the nearest peak is often just a harmonic.
            // Back vowels keep their second resonance about twice the first.
            f2 = f1 * 2.1
        } else if (f2 < 1800.0 && front >= FRONT_VOWEL_MIN * 2) {
            // The opposite slip: a front vowel whose low harmonic was taken
            // for its second resonance, when the energy says it is high up.
            peaks.firstOrNull { it >= 1800.0 }?.let { f2 = it }
        }
        return f1.toFloat() to f2.toFloat()
    }

    /**
     * Where the second resonance lives: energy where a front vowel puts it
     * (1800–3500 Hz, «и», «э») over where a back vowel does (600–1500 Hz,
     * «у», «о»). Harder to fool than peak-picking when a child's high voice
     * leaves only a few harmonics to go on, and unlike overall treble it does
     * not depend on how bright the voice itself is.
     */
    internal fun frontness(x: FloatArray, rate: Int): Float {
        var back = 0.0
        var front = 0.0
        var f = 600.0
        while (f <= 3500.0) {
            val power = dftPower(x, f, rate)
            if (f <= 1500.0) back += power else if (f >= 1800.0) front += power
            f += 25.0
        }
        return if (back <= 0.0) 1f else (front / back).toFloat()
    }

    /**
     * Power at one frequency, by Goertzel's recurrence: one multiply per
     * sample instead of a sine and a cosine, which matters twenty times a
     * second on a low-end tablet or in a browser.
     */
    private fun dftPower(x: FloatArray, freq: Double, rate: Int): Double {
        val coeff = 2 * cos(2 * PI * freq / rate)
        var s1 = 0.0
        var s2 = 0.0
        for (v in x) {
            val s0 = v + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }

    /** Front-zone energy at least this share of back-zone energy means a front vowel. */
    const val FRONT_VOWEL_MIN = 0.05f

    private fun envelopePeaks(x: FloatArray, rate: Int, order: Int, preEmphasis: Double): List<Double> {
        // A 32 ms window from the middle of the slice.
        val len = minOf(x.size, rate * 32 / 1000)
        val start = (x.size - len) / 2
        val w = DoubleArray(len)
        for (i in 0 until len) {
            val s = x[start + i] - if (i > 0) preEmphasis * x[start + i - 1] else 0.0
            w[i] = s * (0.54 - 0.46 * cos(2 * PI * i / (len - 1)))
        }
        val a = lpc(w, order) ?: return emptyList()
        val freqs = generateSequence(120.0) { it + 20.0 }.takeWhile { it <= 4600.0 }.toList()
        val env = freqs.map { f -> envelope(a, f, rate) }
        val peaks = mutableListOf<Double>()
        for (i in 1 until env.size - 1) {
            if (env[i] > env[i - 1] && env[i] >= env[i + 1]) peaks += freqs[i]
        }
        return peaks
    }

    /** Levinson–Durbin; null when the frame is too flat to model. */
    private fun lpc(w: DoubleArray, order: Int): DoubleArray? {
        val r = DoubleArray(order + 1)
        for (k in 0..order) {
            var s = 0.0
            for (i in 0 until w.size - k) s += w[i] * w[i + k]
            r[k] = s
        }
        if (r[0] <= 1e-12) return null
        r[0] *= 1.0001  // a touch of white noise keeps the recursion stable
        val a = DoubleArray(order + 1).also { it[0] = 1.0 }
        var err = r[0]
        for (i in 1..order) {
            var acc = r[i]
            for (j in 1 until i) acc += a[j] * r[i - j]
            val k = -acc / err
            val prev = a.copyOf()
            for (j in 1 until i) a[j] = prev[j] + k * prev[i - j]
            a[i] = k
            err *= (1 - k * k)
            if (err <= 0) return null
        }
        return a
    }

    private fun envelope(a: DoubleArray, freq: Double, rate: Int): Double {
        val omega = 2 * PI * freq / rate
        var re = 0.0
        var im = 0.0
        for (k in a.indices) {
            re += a[k] * cos(omega * k)
            im -= a[k] * kotlin.math.sin(omega * k)
        }
        return 1.0 / (re * re + im * im)
    }
}

/**
 * Which of the five first vowels a set of resonances is closest to.
 *
 * Centres are for children's voices, which resonate higher than adults'
 * because the mouth is smaller. Every speaker differs by an overall scale, so
 * the match is tried at a few scales and the closest wins; when two vowels are
 * nearly as close, or none is close at all, the answer is "not sure" — which
 * costs the child nothing, since any voice already counts.
 */
object VowelClassifier {

    enum class Vowel(val letter: String, val f1: Float, val f2: Float) {
        A("а", 1000f, 1650f),
        O("о", 680f, 1100f),
        U("у", 440f, 950f),
        I("и", 400f, 3000f),
        E("э", 700f, 2350f)
    }

    private val SCALES = floatArrayOf(0.75f, 0.85f, 0.95f, 1.05f, 1.15f)

    /** How far off the best match may be, in squared log-frequency. */
    private const val MAX_DISTANCE = 0.09f

    /** The runner-up must be this much further away for the answer to count. */
    private const val MIN_MARGIN = 1.35f

    private val FRONT = setOf(Vowel.I, Vowel.E)
    private val BACK = setOf(Vowel.U, Vowel.O)

    /**
     * [frontness] is a second opinion from the energy itself: a front vowel
     * named where the energy sits at the back, or the other way round, is a
     * measuring slip, and "not sure" is the honest answer to it.
     */
    fun classify(f1: Float, f2: Float, frontness: Float? = null): Vowel? {
        if (f1 <= 0f || f2 <= 0f) return null
        val best = Vowel.entries.map { v ->
            v to SCALES.minOf { s -> distance(f1, f2, v.f1 * s, v.f2 * s) }
        }.sortedBy { it.second }
        val (vowel, d) = best[0]
        if (d > MAX_DISTANCE) return null
        if (best[1].second < d * MIN_MARGIN) return null
        if (frontness != null) {
            if (vowel in FRONT && frontness < VoiceAnalysis.FRONT_VOWEL_MIN) return null
            if (vowel in BACK && frontness > VoiceAnalysis.FRONT_VOWEL_MIN) return null
        }
        return vowel
    }

    private fun distance(f1: Float, f2: Float, c1: Float, c2: Float): Float {
        val d1 = ln(f1 / c1)
        val d2 = ln(f2 / c2)
        // The second resonance matters less to perception than the first, and
        // is the harder of the two to measure in a child's voice.
        return d1 * d1 + 0.7f * d2 * d2
    }

    /** The vowel across a whole utterance: the median of its voiced frames. */
    fun classifyUtterance(frames: List<FrameFeatures>): Vowel? {
        val voiced = frames.filter { it.f1 > 0f && it.f2 > 0f }
        if (voiced.size < 3) return null
        val f1 = voiced.map { it.f1 }.sorted()[voiced.size / 2]
        val f2 = voiced.map { it.f2 }.sorted()[voiced.size / 2]
        val front = voiced.map { it.frontness }.sorted()[voiced.size / 2]
        // A vowel held steady is a vowel; one sliding all over is a word or a
        // babble, and naming a single vowel for it would be a guess.
        val steady = voiced.count { abs(ln(it.f1 / f1)) < 0.25f } >= voiced.size * 0.6f
        return if (steady) classify(f1, f2, front) else null
    }
}
