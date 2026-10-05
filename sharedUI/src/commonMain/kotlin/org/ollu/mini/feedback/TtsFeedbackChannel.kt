package org.ollu.mini.feedback

import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext
import org.ollu.mini.sound.BakedSpeech

class TtsFeedbackChannel : FeedbackChannel {
    override val type        = "tts"
    override val isAvailable = true

    private val player = BakedSpeech

    override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
        if (feedbackType != "tts") return
        val text = params["text"] ?: return
        player.speak(text)
    }
}
