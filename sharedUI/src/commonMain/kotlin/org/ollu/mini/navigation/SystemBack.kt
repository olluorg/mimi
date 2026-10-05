package org.ollu.mini.navigation

import androidx.compose.runtime.Composable

/**
 * The phone's own back button or gesture.
 *
 * Only Android has one; elsewhere this does nothing. When [enabled] is false
 * the press goes to the system, which on the home screen means leaving the app
 * — the one place that is what a person expects back to do.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
