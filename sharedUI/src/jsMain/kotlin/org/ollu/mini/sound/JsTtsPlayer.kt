package org.ollu.mini.sound

actual fun createTtsPlayer(): TtsPlayer = object : TtsPlayer {
    override fun speak(text: String) {
        ttsSpeak(text)
    }
}

private fun ttsSpeak(text: String) {
    js("window.speechSynthesis.speak(new SpeechSynthesisUtterance(text))")
}
