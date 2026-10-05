package org.ollu.mini.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mimi.core.constraints.registerBuiltins
import mimi.core.engine.EngineBuilder
import mimi.core.engine.SessionMetrics
import mimi.core.engine.withDefaultPlugins
import mimi.core.model.ObjectiveState
import mimi.core.model.secondsToTicks
import mimi.core.objective.ObjectiveProgress
import mimi.core.plugin.DefaultConstraintRegistry
import mimi.core.plugin.DefaultFeedbackRegistry
import mimi.core.world.SceneDefinition
import org.ollu.mini.feedback.HapticCue
import org.ollu.mini.feedback.HapticFeedbackChannel
import org.ollu.mini.feedback.HintStateChannel
import org.ollu.mini.feedback.LogFeedbackChannel
import org.ollu.mini.feedback.SoundFeedbackChannel
import org.ollu.mini.feedback.TtsFeedbackChannel
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.progress.SessionRecord
import org.ollu.mini.renderer.SceneRenderer
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Mistakes in one scene before the child is offered a way past it. */
private const val STUCK_AFTER_MISTAKES = 3L

/** Or this long without touching anything, for the games that cannot be failed. */
private const val STUCK_AFTER_IDLE_SECONDS = 25

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
        SceneErrorScreen(buildError, onBack)
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
                    record  = SessionRecord.completed(
                        sceneId    = scene.sceneId,
                        atEpochMs  = Clock.System.now().toEpochMilliseconds(),
                        metrics    = metrics,
                        speechGame = scene.meta.speech != null
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

        if (onBack != null) BackButton(onBack, Modifier.align(Alignment.TopStart))

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

        val totalLevels = snapshot.totalLevels()
        if (totalLevels > 1) {
            LevelDots(
                total    = totalLevels,
                current  = snapshot.currentLevel(),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            )
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
