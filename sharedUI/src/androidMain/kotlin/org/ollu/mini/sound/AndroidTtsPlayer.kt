package org.ollu.mini.sound

import android.speech.tts.TextToSpeech
import java.util.Locale

actual fun createTtsPlayer(): TtsPlayer = AndroidTtsPlayer()

private class AndroidTtsPlayer : TtsPlayer {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        val ctx = AndroidAppContext.context
        if (ctx != null) {
            tts = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("ru")
                    ready = true
                }
            }
        }
    }

    override fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}
