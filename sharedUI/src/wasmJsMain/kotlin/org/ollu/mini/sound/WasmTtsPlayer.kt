package org.ollu.mini.sound

/**
 * Web Speech synthesis for the wasm build.
 *
 * The web build was silent before this: a no-op player shipped where the child
 * should have been hearing prompts, which for a non-verbal user removes the
 * point of the app.
 */
actual fun createTtsPlayer(): TtsPlayer = WasmTtsPlayer()

private class WasmTtsPlayer : TtsPlayer {
    override fun speak(text: String) {
        if (text.isBlank()) return
        // Speech is only ever a reaction to the child's last action, so a new
        // prompt replaces the previous one instead of queueing behind it.
        runCatching { ttsSpeak(text, LANG) }
    }

    private companion object {
        const val LANG = "ru-RU"
    }
}

private fun ttsSpeak(text: String, lang: String): Unit = js(
    """{
        if (typeof window === 'undefined' || !window.speechSynthesis) return;
        var utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = lang;
        window.speechSynthesis.cancel();
        window.speechSynthesis.speak(utterance);
    }"""
)
