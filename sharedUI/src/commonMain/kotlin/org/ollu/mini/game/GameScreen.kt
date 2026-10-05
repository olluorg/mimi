package org.ollu.mini.game

import org.ollu.mini.sound.SystemPhrases
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mimi.core.constraints.registerBuiltins
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import mimi.core.engine.EngineBuilder
import mimi.core.engine.SessionMetrics
import mimi.core.engine.withDefaultPlugins
import mimi.core.model.ObjectiveState
import mimi.core.model.secondsToTicks
import mimi.core.objective.ObjectiveProgress
import mimi.core.plugin.DefaultConstraintRegistry
import mimi.core.plugin.DefaultFeedbackRegistry
import mimi.core.world.SceneDefinition
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.ollu.mini.feedback.HapticCue
import org.ollu.mini.feedback.HapticFeedbackChannel
import org.ollu.mini.feedback.HintStateChannel
import org.ollu.mini.feedback.LogFeedbackChannel
import org.ollu.mini.feedback.SoundFeedbackChannel
import org.ollu.mini.feedback.TtsFeedbackChannel
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.progress.SessionRecord
import org.ollu.mini.renderer.SceneRenderer
import org.ollu.mini.renderer.CatalogAnimation
import org.ollu.mini.renderer.CONFETTI_ANIMATION
import androidx.compose.animation.core.spring
import org.ollu.mini.sound.rememberTtsPlayer
import androidx.compose.ui.text.style.TextAlign
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph

/** Mistakes in one scene before the child is offered a way past it. */
private const val STUCK_AFTER_MISTAKES = 3L

/** Or this long without touching anything, for the games that cannot be failed. */
private const val STUCK_AFTER_IDLE_SECONDS = 25

/** Above this many steps, pips stop being countable and a bar reads better. */
private const val MAX_PIPS = 10

data class CompletionAction(
    val label:   String,
    val icon:    String = "",
    val primary: Boolean = false,
    val onClick: () -> Unit
)

@OptIn(ExperimentalTime::class)
@Composable
fun GameScreen(
    scene:             SceneDefinition,
    onBack:            (() -> Unit)? = null,
    progressRepository: ProgressRepository? = null,
    onCompleted:       ((SessionMetrics) -> Unit)? = null,
    completionActions: List<CompletionAction> = emptyList(),
    onSkip:            (() -> Unit)? = null,
    /** Picks this play-through's arrangement; a new value deals a different screen. */
    seed:              Long = 0L
) {
    val scope = rememberCoroutineScope()

    val hintChannel   = remember { HintStateChannel() }
    val hapticChannel = remember { HapticFeedbackChannel() }

    val sessionResult = remember(scene, seed) {
        runCatching {
            val constraintRegistry = DefaultConstraintRegistry().also { it.registerBuiltins() }
            val feedbackRegistry   = DefaultFeedbackRegistry().also {
                it.register(LogFeedbackChannel)
                it.register(SoundFeedbackChannel())
                it.register(TtsFeedbackChannel())
                it.register(hintChannel)
                it.register(hapticChannel)
            }
            EngineBuilder()
                .withDefaultPlugins(constraintRegistry)
                .withFeedbackRegistry(feedbackRegistry)
                .build(scene, scope, seed)
        }
    }

    val session    = sessionResult.getOrNull()
    val buildError = sessionResult.exceptionOrNull()

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    if (buildError != null) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).safeDrawingPadding()) {
            if (onBack != null) {
                TextButton(
                    onClick  = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) { AppIconGlyph(AppIcon.Back, Color(0xFF666666), size = 18.dp); Text("  Назад", color = Color(0xFF666666)) }
            }
            Column(
                modifier            = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
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
                        buildError.message ?: buildError.toString(),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = Color(0xFFB00020),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        return
    }

    val snapshot       by session!!.worldState.collectAsState()
    val hintFocus      by hintChannel.focus.collectAsState()

    // Multi-modal reinforcement: a correct match is felt as well as heard, which
    // matters when the child is not tracking the audio.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(hapticChannel) {
        hapticChannel.cues.collect { cue ->
            haptics.performHapticFeedback(
                when (cue) {
                    HapticCue.SUCCESS, HapticCue.COMPLETE -> HapticFeedbackType.LongPress
                    HapticCue.FAIL                        -> HapticFeedbackType.TextHandleMove
                }
            )
        }
    }

    // A multi-level scene is only done once the engine says every level is, so the
    // overlay cannot appear between levels. A single-level scene may also be read
    // straight off its objectives.
    val isComplete = snapshot.allLevelsComplete() || (
        !scene.isMultiLevel &&
            scene.objectives.isNotEmpty() &&
            scene.objectives.all { snapshot.getObjectiveState(it.id) == ObjectiveState.LOCKED_COMPLETE }
        )

    var completionReported by remember(scene.sceneId) { mutableStateOf(false) }
    LaunchedEffect(isComplete) {
        if (isComplete && !completionReported) {
            completionReported = true
            val metrics = session.metrics()
            progressRepository?.let { repo ->
                val childId = repo.currentChildId()
                repo.markCompleted(childId, scene.sceneId)
                // Every attempt is logged, in free play as well as in the learning
                // mode, because the adult reviewing progress wants the whole picture.
                repo.recordSession(
                    childId = childId,
                    record  = SessionRecord(
                        sceneId    = scene.sceneId,
                        atEpochMs  = Clock.System.now().toEpochMilliseconds(),
                        durationMs = metrics.durationMs,
                        mistakes   = metrics.mistakes,
                        hintsUsed  = metrics.hintsUsed,
                        completed  = true,
                        choices    = metrics.choices,
                        vocalAttempts  = metrics.vocalAttempts,
                        adultConfirmed = metrics.adultConfirmed,
                        unanswered     = metrics.unanswered,
                        responses      = metrics.responses,
                        exactResponses = metrics.exactResponses,
                        turns          = metrics.turns.map { "${it.word}|${it.outcome}" },
                        speechGame     = scene.meta.speech != null
                    )
                )
            }
            onCompleted?.invoke(metrics)
        }
    }

    // The scene's own cream runs under the status bar; the scene and every
    // control start below it, so "Назад" and the replay button are not hidden
    // behind the clock and the battery on Android.
    Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E1)).safeDrawingPadding()) {
        SceneRenderer(
            snapshot       = snapshot,
            session        = session,
            scope          = scope,
            hintFocus      = hintFocus
        )

        if (onBack != null) {
            TextButton(
                onClick  = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) { AppIconGlyph(AppIcon.Back, Color(0xFF666666), size = 18.dp); Text("  Назад", color = Color(0xFF666666)) }
        }

        // "Say it again". A child who looked away during the instruction, or did
        // not catch it, has no other way back to it — they cannot read the
        // title. Big and at the top centre, clear of the back button and the
        // level dots, and never a mistake to press.
        if (!isComplete) {
            Surface(
                onClick  = { scope.launch { session.repeatInstruction() } },
                shape    = CircleShape,
                color    = Color.White.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(64.dp)
                    .semantics { contentDescription = "Повторить задание" }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppIconGlyph(AppIcon.Speaker, Color(0xFF2A9D8F), size = 38.dp)
                }
            }
        }

        scene.meta.speech?.adultTip?.takeIf { it.isNotBlank() && !isComplete }?.let { AdultTip(it) }
        if (session.listensForVoice) {
            SpeechControls(
                session     = session,
                scope       = scope,
                active      = !isComplete,
                sensitivity = progressRepository?.micSensitivity ?: 0.5f
            )
        }

        // The goal, made visible. Without it a game that ends after eight presses
        // is indistinguishable from one that never ends.
        val progress = remember(snapshot) {
            ObjectiveProgress.of(scene.objectivesForLevel(snapshot.currentLevel()), snapshot)
        }
        if (progress != null && !isComplete) {
            GoalProgress(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
            )
        }

        val totalLevels  = snapshot.totalLevels()
        val currentLevel = snapshot.currentLevel()
        if (totalLevels > 1) {
            Row(
                modifier              = Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                repeat(totalLevels) { i ->
                    Box(
                        Modifier
                            .size(if (i == currentLevel) 12.dp else 8.dp)
                            .background(
                                if (i <= currentLevel) Color(0xFF2A9D8F) else Color.White.copy(alpha = 0.45f),
                                CircleShape
                            )
                    )
                }
            }
        }

        // A way out, shown only once the child is visibly stuck. Offering it from
        // the start would invite a toddler to skip every game; withholding it
        // entirely leaves them failing the same screen with no way forward.
        // Mistakes are not the only way to be stuck. A game with no wrong answer
        // produces none, so without the idle route those scenes had no exit at
        // all except a small "Назад" a child cannot read.
        val stuck = !isComplete && (
            snapshot.mistakes() >= STUCK_AFTER_MISTAKES ||
                snapshot.idleTicks() >= secondsToTicks(STUCK_AFTER_IDLE_SECONDS)
            )
        AnimatedVisibility(
            visible  = stuck && onSkip != null,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter    = fadeIn(tween(600))
        ) {
            Surface(
                onClick         = { onSkip?.invoke() },
                shape           = CircleShape,
                color           = Color(0xFF457B9D),
                shadowElevation = 4.dp,
                modifier        = Modifier.padding(20.dp).size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AppIconGlyph(AppIcon.Forward, Color.White, size = 38.dp)
                }
            }
        }

        AnimatedVisibility(
            visible = isComplete,
            enter   = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.75f)
        ) {
            CompletionOverlay(actions = completionActions)
        }
    }
}

/**
 * The goal as a row of pips that fill in, or a bar when there are too many to
 * count at a glance. Wordless: it is read by a child who cannot read.
 */
@Composable
private fun GoalProgress(
    progress: mimi.core.objective.SceneProgress,
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
private fun CompletionOverlay(actions: List<CompletionAction>) {
    val tts = rememberTtsPlayer()
    LaunchedEffect(Unit) { tts.speak(SystemPhrases.WELL_DONE) }
    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        CatalogAnimation(
            CONFETTI_ANIMATION, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
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
