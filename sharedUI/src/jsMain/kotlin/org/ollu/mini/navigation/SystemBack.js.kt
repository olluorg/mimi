package org.ollu.mini.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back button on this platform; the on-screen ones cover it.
}
