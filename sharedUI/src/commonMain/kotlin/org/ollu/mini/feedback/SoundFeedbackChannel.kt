package org.ollu.mini.feedback

import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext
import org.ollu.mini.sound.createTonePlayer

class SoundFeedbackChannel : FeedbackChannel {
    override val type        = "sound"
    override val isAvailable = true

    private val player by lazy { createTonePlayer() }

    override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
        val key = when (feedbackType) {
            "matchSuccess" -> "success"
            "matchFail"    -> "fail"
            "hint"         -> "hint"
            "sound"        -> params["path"] ?: return
            else           -> return
        }
        when (key) {
            "success"  -> player.playSequence(listOf(523f to 120, 659f to 220))
            "fail"     -> player.playSequence(listOf(330f to 90,  0f to 40, 247f to 180))
            "complete" -> player.playSequence(listOf(523f to 110, 659f to 110, 784f to 110, 1047f to 350))
            "hint"     -> player.playSequence(listOf(523f to 280))
        }
    }
}
