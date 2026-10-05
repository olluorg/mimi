package org.ollu.mini.voice

import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Knows whether the app itself is making a sound, so the microphone can ignore it.
 *
 * Without this a speech game answers itself: the model word comes out of the
 * speaker, the microphone hears a voice, and the turn is marked as the child's.
 * Guessing how long a sound lasts was not enough — a cow's reward moo runs
 * twelve seconds and kept "answering" the next word — so each sound is
 * tracked from the moment it is asked for until its player says it has
 * ended, plus a short tail for the room's echo.
 */
object PlaybackGuard {
    private val origin = TimeSource.Monotonic.markNow()

    /** Sounds playing now: token → when to give up on it if its end is never reported. */
    @Volatile private var active: Map<Long, Long> = emptyMap()
    @Volatile private var quietAfterMs = 0L
    @Volatile private var nextToken = 0L

    fun nowMs(): Long = origin.elapsedNow().inWholeMilliseconds

    /**
     * A sound is about to play. Covers the player's start-up as well, which on
     * Android is long enough for the first syllable to get through otherwise.
     * [expectedMs] bounds the wait if the end is never reported.
     */
    fun begin(expectedMs: Long = UNKNOWN_LENGTH_MS): Long {
        val token = nextToken++
        active = active + (token to nowMs() + expectedMs + START_UP_MS)
        return token
    }

    fun end(token: Long) {
        active = active - token
        markPlaying(ECHO_TAIL_MS)
    }

    /** For sounds whose end cannot be observed: a synthesised voice, a tone. */
    fun markPlaying(durationMs: Long) {
        val until = nowMs() + durationMs
        if (until > quietAfterMs) quietAfterMs = until
    }

    fun isPlaying(nowMs: Long = nowMs()): Boolean =
        nowMs < quietAfterMs || active.values.any { nowMs < it }

    /** The room keeps ringing for a moment after the speaker stops. */
    private const val ECHO_TAIL_MS = 500L
    private const val START_UP_MS = 600L
    private const val UNKNOWN_LENGTH_MS = 15_000L
}
