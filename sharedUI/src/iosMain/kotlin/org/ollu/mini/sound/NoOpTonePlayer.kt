package org.ollu.mini.sound

actual fun createTonePlayer(): TonePlayer = object : TonePlayer {
    override fun playSequence(notes: NoteSequence) {}
}
