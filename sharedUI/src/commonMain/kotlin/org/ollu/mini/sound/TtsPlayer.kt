package org.ollu.mini.sound

interface TtsPlayer {
    fun speak(text: String)
}

expect fun createTtsPlayer(): TtsPlayer
