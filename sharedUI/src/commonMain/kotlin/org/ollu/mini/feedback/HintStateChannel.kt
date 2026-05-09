package org.ollu.mini.feedback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext

class HintStateChannel : FeedbackChannel {
    override val type        = "hint_state"
    override val isAvailable = true

    private val _hintedEntityId = MutableStateFlow<String?>(null)
    val hintedEntityId: StateFlow<String?> = _hintedEntityId.asStateFlow()

    override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
        when (feedbackType) {
            "hint"         -> _hintedEntityId.value = params["entityId"]?.ifBlank { null }
            "matchSuccess",
            "complete"     -> _hintedEntityId.value = null
        }
    }
}
