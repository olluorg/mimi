package org.ollu.mini.sound

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.ollu.mini.voice.PlaybackGuard

/**
 * The one way the app speaks.
 *
 * Every line the app says — a scene's instruction, "Молодец", the name of a
 * game, a button — goes through here. If voicy has recorded it, the recording
 * plays; only a line nobody has baked yet falls through to the device's speech
 * engine. Fixing this per call site is how "Молодец" stayed robotic after every
 * instruction had been recorded, so there are no call sites left to fix.
 */
object BakedSpeech : TtsPlayer {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val synthesis by lazy { createTtsPlayer() }
    private val files by lazy { createAudioFilePlayer() }

    private val recordings = CompletableDeferred<Map<String, String>>().also { deferred ->
        scope.launch { deferred.complete(runCatching { load() }.getOrDefault(emptyMap())) }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun load(): Map<String, String> {
        val raw = Res.readBytes("files/catalog/$MANIFEST").decodeToString()
        val lines = Json.parseToJsonElement(raw).jsonObject["lines"]?.jsonObject ?: return emptyMap()
        return lines.mapNotNull { (text, entry) ->
            entry.jsonObject["file"]?.jsonPrimitive?.content?.let { key(text) to it }
        }.toMap()
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        // Keep the microphone from hearing the app's own voice as the child's:
        // guarded from now, before the recording is even looked up, until the
        // player reports the end.
        val estimate = SPEECH_GUARD_MS_PER_CHAR * text.length + SPEECH_GUARD_BASE_MS
        val token = PlaybackGuard.begin(estimate)
        scope.launch {
            val file = recordings.await()[key(text)]
            if (file != null && files.isAvailable) {
                files.play(file) { PlaybackGuard.end(token) }
            } else {
                // A system voice gives no signal when it finishes; guess.
                synthesis.speak(text)
                PlaybackGuard.markPlaying(estimate)
                PlaybackGuard.end(token)
            }
        }
    }

    /** Whether a recording exists — for tests and the review checklist. */
    suspend fun isRecorded(text: String): Boolean = key(text) in recordings.await()

    private fun key(text: String) = text.trim()

    private const val MANIFEST = "_sounds/speech/speech.json"

    // A slow children's voice: roughly 70 ms a character, plus the start-up
    // of the player. Over-guessing only delays hearing the child by a moment.
    private const val SPEECH_GUARD_MS_PER_CHAR = 70L
    private const val SPEECH_GUARD_BASE_MS = 900L
}
