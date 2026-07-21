package org.ollu.mini.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberTtsPlayer(): TtsPlayer = remember { createTtsPlayer() }
