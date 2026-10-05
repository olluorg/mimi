package org.ollu.mini.sound

import androidx.compose.runtime.Composable

/** The app's voice: recordings first, the device's speech engine only as a fallback. */
@Composable
fun rememberTtsPlayer(): TtsPlayer = BakedSpeech
