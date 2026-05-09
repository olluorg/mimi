package org.ollu.mini.sound

actual fun createAudioFilePlayer(): AudioFilePlayer = object : AudioFilePlayer {
    override fun playResourceFile(resourcePath: String) {}
}
