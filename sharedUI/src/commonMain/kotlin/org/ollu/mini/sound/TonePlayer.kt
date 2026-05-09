package org.ollu.mini.sound

// List of (frequencyHz, durationMs) pairs. frequencyHz=0 → silence.
typealias NoteSequence = List<Pair<Float, Int>>

interface TonePlayer {
    fun playSequence(notes: NoteSequence)
}

expect fun createTonePlayer(): TonePlayer
