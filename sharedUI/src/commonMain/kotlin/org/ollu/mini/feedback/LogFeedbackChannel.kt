package org.ollu.mini.feedback

import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext

object LogFeedbackChannel : FeedbackChannel {
    override val type        = "log"
    override val isAvailable = true

    override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
        println("[feedback] $feedbackType $params tick=${context.tick}")
    }
}
