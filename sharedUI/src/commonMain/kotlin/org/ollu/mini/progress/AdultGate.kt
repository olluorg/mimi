package org.ollu.mini.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph

private const val HOLD_MS = 1_500L

/**
 * A hold-to-enter gate in front of the adult screens.
 *
 * The app is built for children who explore by tapping everything, so the
 * grown-up view is put behind a sustained press — long enough that it will not
 * be opened by accident, with no reading or arithmetic that would lock out an
 * adult in a hurry.
 */
@Composable
fun AdultGate(
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    var holding by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(durationMillis = if (holding) HOLD_MS.toInt() else 200),
        label = "holdProgress"
    )

    LaunchedEffect(holding) {
        if (!holding) return@LaunchedEffect
        delay(HOLD_MS)
        if (!unlocked) {
            unlocked = true
            onUnlocked()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) { Text("⬅  Назад", color = Color(0xFF666666)) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Только для взрослых",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D3557),
                textAlign = TextAlign.Center
            )
            Text(
                "Удерживайте кнопку, пока круг не заполнится.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF687381),
                textAlign = TextAlign.Center
            )

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(148.dp),
                    strokeWidth = 8.dp,
                    color = Color(0xFF457B9D),
                    trackColor = Color(0xFFE5EAF0)
                )
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF457B9D),
                    modifier = Modifier
                        .size(112.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    holding = true
                                    // Returns when the finger lifts or the gesture is cancelled.
                                    tryAwaitRelease()
                                    holding = false
                                }
                            )
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        AppIconGlyph(AppIcon.Lock, Color.White, size = 46.dp)
                    }
                }
            }
        }
    }
}

/** Small entry point for the home screen, styled to read as secondary to the play modes. */
@Composable
fun AdultEntryButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AppIconGlyph(AppIcon.Person, Color(0xFF687381), size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Для взрослых",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF687381),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
