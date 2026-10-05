package org.ollu.mini.feedback

import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext
import org.ollu.mini.sound.createAudioFilePlayer
import org.ollu.mini.sound.createTonePlayer
import org.ollu.mini.voice.PlaybackGuard

class SoundFeedbackChannel : FeedbackChannel {
    override val type        = "sound"
    override val isAvailable = true

    private val tonePlayer by lazy { createTonePlayer() }
    private val filePlayer by lazy { createAudioFilePlayer() }

    override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
        val key = when (feedbackType) {
            "matchSuccess" -> "success"
            "matchFail"    -> "fail"
            "hint"         -> "hint"
            // Milestones inside a game. Scenes used to try to bolt a sound onto
            // these through playSoundOnEvent, which only understands events, not
            // feedback — so the sound never played. Giving each its own small
            // tone here is both what the authors meant and a little more variety
            // than one chime for everything.
            "sequenceStepCorrect"    -> "step"
            "traceCheckpointReached" -> "checkpoint"
            "traceCompleted"         -> "complete"
            // Exactly the sound that was modelled: a fanfare, not the chime.
            "bonus"                  -> "fanfare"
            "sound"        -> params["path"] ?: return
            else           -> return
        }
        // File-based sound: path contains a directory separator
        if ('/' in key) {
            // A recording is a word or an animal: long enough to be heard by
            // the microphone as somebody speaking, so it is guarded until the
            // player reports that it has actually stopped.
            val maxMs = params["maxMs"]?.toLongOrNull() ?: 0L
            val token = PlaybackGuard.begin(if (maxMs > 0) maxMs else 15_000L)
            filePlayer.play(key, maxMs) { PlaybackGuard.end(token) }
            return
        }
        PlaybackGuard.markPlaying(TONE_GUARD_MS)
        when (key) {
            "success"  -> tonePlayer.playSequence(listOf(523f to 120, 659f to 220))
            "fail"     -> tonePlayer.playSequence(listOf(330f to 90,  0f to 40, 247f to 180))
            "complete" -> tonePlayer.playSequence(listOf(523f to 110, 659f to 110, 784f to 110, 1047f to 350))
            "hint"     -> tonePlayer.playSequence(listOf(523f to 280))
            // Short and low-key: these fire repeatedly during a game and must not
            // compete with the sound that marks actually finishing it.
            "step"       -> tonePlayer.playSequence(listOf(659f to 90))
            "checkpoint" -> tonePlayer.playSequence(listOf(784f to 70))
            "fanfare"    -> tonePlayer.playSequence(
                listOf(784f to 90, 988f to 90, 1175f to 90, 1568f to 120, 0f to 40, 1568f to 320)
            )
        }
    }
}

private const val TONE_GUARD_MS = 600L
