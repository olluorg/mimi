package org.ollu.mini.sound

actual fun createAudioFilePlayer(): AudioFilePlayer = object : AudioFilePlayer {
    override fun play(resourcePath: String, maxMs: Long, onDone: () -> Unit) = onDone()
    override val isAvailable: Boolean get() = false
}
