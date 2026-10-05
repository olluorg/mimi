package org.ollu.mini.voice

import androidx.compose.runtime.Composable

/**
 * Not wired on iOS yet — catalog audio is not played there either. Speech
 * games still run: the adult's "сказал(а)" button answers every turn.
 */
@Composable
actual fun rememberMicrophoneLevels(): MicrophoneLevels = NoMicrophone
