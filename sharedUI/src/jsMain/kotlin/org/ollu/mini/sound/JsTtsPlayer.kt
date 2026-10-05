package org.ollu.mini.sound

actual fun createTtsPlayer(): TtsPlayer = JsTtsPlayer()

private class JsTtsPlayer : TtsPlayer {
    override fun speak(text: String) {
        if (text.isBlank()) return
        runCatching { ttsSpeak(text, LANG) }
    }

    private companion object {
        const val LANG = "ru-RU"
    }
}

// Speech always reacts to the child's last action, so a new prompt cancels the
// previous one — otherwise repeated taps queue up minutes of backlogged speech.
private fun ttsSpeak(text: String, lang: String) {
    js("""
        (function() {
            if (typeof window === 'undefined' || !window.speechSynthesis) return;
            var utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = lang;
            window.speechSynthesis.cancel();
            window.speechSynthesis.speak(utterance);
        })()
    """)
}
