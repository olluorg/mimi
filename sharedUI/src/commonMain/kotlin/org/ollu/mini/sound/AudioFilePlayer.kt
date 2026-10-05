package org.ollu.mini.sound

interface AudioFilePlayer {
    // resourcePath: path relative to files/catalog/ in composeResources
    fun playResourceFile(resourcePath: String) = play(resourcePath)

    /**
     * Plays a catalog file.
     *
     * [maxMs] cuts it short — a cow's reward moo is twelve seconds long, and a
     * speech game has moved on to the next word long before then. [onDone] is
     * called exactly once when playback ends for any reason: finished, cut,
     * or failed. The microphone relies on it to know when the app has stopped
     * talking, so an implementation that cannot tell must call it anyway.
     */
    fun play(resourcePath: String, maxMs: Long = 0L, onDone: () -> Unit = {})

    /** False where files cannot be played at all, so callers can fall back. */
    val isAvailable: Boolean get() = true
}

expect fun createAudioFilePlayer(): AudioFilePlayer

/** Wraps a callback so it runs at most once, whichever ending gets there first. */
internal class Once(private val action: () -> Unit) {
    private var done = false
    fun run() {
        if (done) return
        done = true
        action()
    }
}
