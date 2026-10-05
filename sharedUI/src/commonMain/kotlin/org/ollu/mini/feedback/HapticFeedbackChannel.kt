package org.ollu.mini.feedback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext

enum class HapticCue { SUCCESS, FAIL, COMPLETE }

/**
 * Touch feedback, the third reinforcement channel alongside sound and visuals.
 *
 * The engine runs outside Compose, so the channel only records what happened;
 * the screen collects these and asks the platform to buzz. Replay is disabled
 * so a cue that fires while the screen is away is not felt late.
 */
class HapticFeedbackChannel : FeedbackChannel {
    override val type        = "haptic"
    override val isAvailable = true

    private val _cues = MutableSharedFlow<HapticCue>(extraBufferCapacity = 4)
    val cues: SharedFlow<HapticCue> = _cues.asSharedFlow()

    override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
        val cue = when (feedbackType) {
            "matchSuccess" -> HapticCue.SUCCESS
            "matchFail"    -> HapticCue.FAIL
            "complete"     -> HapticCue.COMPLETE
            else           -> return
        }
        _cues.tryEmit(cue)
    }
}
