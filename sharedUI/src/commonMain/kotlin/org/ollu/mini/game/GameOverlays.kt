package org.ollu.mini.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mimi.core.objective.SceneProgress
import org.ollu.mini.renderer.CONFETTI_ANIMATION
import org.ollu.mini.renderer.CatalogAnimation
import org.ollu.mini.sound.SystemPhrases
import org.ollu.mini.sound.rememberTtsPlayer
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph

/** Above this many steps, pips stop being countable and a bar reads better. */
private const val MAX_PIPS = 10

@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onBack, modifier = modifier.padding(8.dp)) {
        AppIconGlyph(AppIcon.Back, Color(0xFF666666), size = 18.dp)
        Text("  Назад", color = Color(0xFF666666))
    }
}

/** Shown instead of the scene when the engine could not be built from it. */
@Composable
internal fun SceneErrorScreen(error: Throwable, onBack: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).safeDrawingPadding()) {
        if (onBack != null) BackButton(onBack, Modifier.align(Alignment.TopStart))
        Column(
            modifier            = Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconGlyph(AppIcon.Warning, Color(0xFFB00020), size = 46.dp)
            Text(
                "Сцена не запустилась",
                style      = MaterialTheme.typography.titleMedium,
                color      = Color(0xFFB00020),
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape    = RoundedCornerShape(8.dp),
                color    = Color(0xFFFFEBEE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    error.message ?: error.toString(),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color(0xFFB00020),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/** One dot per level: the current one larger, the ones reached filled in. */
@Composable
internal fun LevelDots(total: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == current) 12.dp else 8.dp)
                    .background(
                        if (i <= current) Color(0xFF2A9D8F) else Color.White.copy(alpha = 0.45f),
                        CircleShape
                    )
            )
        }
    }
}

/**
 * The goal as a row of pips that fill in, or a bar when there are too many to
 * count at a glance. Wordless: it is read by a child who cannot read.
 */
@Composable
internal fun GoalProgress(
    progress: SceneProgress,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF2A9D8F)
    if (progress.total <= MAX_PIPS) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(progress.total) { index ->
                val filled = index < progress.done
                // Each step done is a star collected — something to earn and
                // to see growing, not just a dot changing colour.
                val scale by animateFloatAsState(
                    targetValue = if (filled) 1f else 0.7f,
                    animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
                    label = "pip"
                )
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    if (filled) {
                        AppIconGlyph(AppIcon.Star, Color(0xFFFFC107), size = (30 * scale).dp)
                    } else {
                        Box(
                            Modifier
                                .size((18 * scale).dp)
                                .background(Color.Black.copy(alpha = 0.16f), CircleShape)
                        )
                    }
                }
            }
        }
    } else {
        val fraction by animateFloatAsState(
            targetValue = progress.fraction,
            animationSpec = tween(260),
            label = "bar"
        )
        Box(
            modifier
                .width(220.dp)
                .height(12.dp)
                .background(Color.Black.copy(alpha = 0.14f), CircleShape)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(accent, CircleShape)
            )
        }
    }
}

@Composable
internal fun CompletionOverlay(actions: List<CompletionAction>) {
    val tts = rememberTtsPlayer()
    LaunchedEffect(Unit) { tts.speak(SystemPhrases.WELL_DONE) }
    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        CatalogAnimation(
            CONFETTI_ANIMATION, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconGlyph(AppIcon.Star, Color(0xFFE9A13B), size = 92.dp)
                Text(
                    "Молодец!",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color(0xFF1D3557),
                    fontWeight = FontWeight.Bold
                )
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        actions.forEach { action ->
                            // Not spoken: "Ещё раз" restarts the game, whose
                            // instruction would talk over it.
                            CompletionActionTile(action) { action.onClick() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionActionTile(action: CompletionAction, onClick: () -> Unit) {
    val accent = if (action.primary) Color(0xFF2A9D8F) else Color(0xFF457B9D)
    val fillColor = if (action.primary) accent else accent.copy(alpha = 0.14f)
    val iconColor = if (action.primary) Color.White else accent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.widthIn(min = 92.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = fillColor,
            shadowElevation = if (action.primary) 4.dp else 1.dp,
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    action.icon.ifBlank { "▶" },
                    fontSize = 44.sp,
                    color = iconColor
                )
            }
        }
        Text(
            action.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF263445),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
