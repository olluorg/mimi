package org.ollu.mini.sound

import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance

/**
 * Speech on iOS, which shipped silent before this.
 *
 * Compiles for the iOS targets, but has not been run on a device or simulator —
 * that needs a Mac. Worth hearing once before release.
 */
actual fun createTtsPlayer(): TtsPlayer = IosTtsPlayer()

private class IosTtsPlayer : TtsPlayer {
    // One synthesiser for the app: creating one per utterance drops speech that
    // is still in flight when the object is collected.
    private val synthesizer = AVSpeechSynthesizer()

    override fun speak(text: String) {
        if (text.isBlank()) return

        // Speech always reacts to the child's last action, so a new prompt
        // replaces whatever is still being spoken rather than queueing behind it.
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }

        val utterance = AVSpeechUtterance.speechUtteranceWithString(text).apply {
            AVSpeechSynthesisVoice.voiceWithLanguage(LANGUAGE)?.let { setVoice(it) }
            // A shade under the default: the target audience follows speech more
            // easily when it is not rushed.
            setRate(0.45f)
        }
        synthesizer.speakUtterance(utterance)
    }

    private companion object {
        const val LANGUAGE = "ru-RU"
    }
}
