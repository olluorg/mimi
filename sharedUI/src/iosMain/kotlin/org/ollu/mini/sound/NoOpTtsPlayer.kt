package org.ollu.mini.sound

actual fun createTtsPlayer(): TtsPlayer = object : TtsPlayer {
    override fun speak(text: String) {}
}
