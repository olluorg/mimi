package org.ollu.mini.feedback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext

/**
 * What the screen should currently be drawing attention to.
 *
 * Two entities, not one: after a child has been stuck for a while the hint shows
 * the piece *and* where it goes, which is the nearest thing the app has to
 * demonstrating the task to someone who cannot be told it in words.
 */
data class HintFocus(
    val entityId:      String? = null,
    val destinationId: String? = null
) {
    val isEmpty: Boolean get() = entityId == null && destinationId == null
}

class HintStateChannel : FeedbackChannel {
    override val type        = "hint_state"
    override val isAvailable = true

    private val _focus = MutableStateFlow(HintFocus())
    val focus: StateFlow<HintFocus> = _focus.asStateFlow()

    override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
        when (feedbackType) {
            "hint" -> _focus.value = HintFocus(
                entityId      = params["entityId"]?.ifBlank { null },
                destinationId = params["destinationId"]?.ifBlank { null }
            )
            // Once the child acts, the prompt has done its job and gets out of the way.
            "matchSuccess", "matchFail", "complete" -> _focus.value = HintFocus()
        }
    }
}
