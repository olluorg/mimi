package org.ollu.mini.play

import org.ollu.mini.sound.SystemPhrases
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mimi.core.engine.SessionMetrics
import mimi.core.world.SceneDefinition
import org.ollu.mini.catalog.CatalogEntry
import org.ollu.mini.catalog.CatalogState
import org.ollu.mini.catalog.DEFAULT_CATALOG_URL
import org.ollu.mini.catalog.SceneRepository
import org.ollu.mini.game.GameScreen
import org.ollu.mini.learning.applyLearningSceneCompletion
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.sound.rememberTtsPlayer
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph

private val BG = Color(0xFFFFF8E1)

/** How long the child gets to enjoy finishing before the next game arrives. */
private const val CELEBRATION_MS = 1_800L

/**
 * A whole sitting of play, with no menus in the middle.
 *
 * Built for a child using the app alone. Every screen between two games is a
 * screen a non-reading child has to be helped past, so there are none: one game
 * flows into the next, difficulty follows how they are doing, and the sitting
 * ends by itself after a handful of games rather than running forever.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun PlaySessionScreen(
    progressRepository: ProgressRepository,
    onFinished: () -> Unit,
    catalogUrl: String = DEFAULT_CATALOG_URL
) {
    val repo = remember { SceneRepository() }
    DisposableEffect(repo) { onDispose { repo.close() } }

    var catalog   by remember { mutableStateOf(CatalogState(isRefreshing = true)) }
    var current   by remember { mutableStateOf<Pair<CatalogEntry, SceneDefinition>?>(null) }
    var playedNow by remember { mutableStateOf(listOf<String>()) }
    var lastOutcome by remember { mutableStateOf<PlayOutcome?>(null) }
    var completedScene by remember { mutableStateOf<String?>(null) }
    var sessionOver by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val childId = remember { progressRepository.currentChildId() }
    val sessionSeed = remember { Clock.System.now().toEpochMilliseconds() }

    LaunchedEffect(catalogUrl) {
        repo.catalogFlow(catalogUrl).collect { catalog = it }
    }

    suspend fun advance() {
        completedScene = null
        if (playedNow.size >= PlaySessionPlanner.SESSION_LENGTH) {
            sessionOver = true
            return
        }
        val next = PlaySessionPlanner.nextScene(
            catalog      = catalog.entries,
            playedNow    = playedNow,
            completedIds = progressRepository.getCompletedSceneIds(childId),
            last         = lastOutcome,
            speechFocus  = progressRepository.currentSpeechFocus()
        )
        if (next == null) {
            sessionOver = true
            return
        }
        // A scene that will not load must not strand the child on a blank screen:
        // drop it and take the next one instead.
        val scene = runCatching { repo.fetchScene(next) }.getOrNull()
        if (scene == null) {
            playedNow = playedNow + next.id
            if (playedNow.size >= PlaySessionPlanner.SESSION_LENGTH) {
                loadFailed = true
                sessionOver = true
            } else {
                advance()
            }
            return
        }
        current = next to scene
        playedNow = playedNow + next.id
    }

    // First game, once the catalog has something in it.
    LaunchedEffect(catalog.entries.isNotEmpty()) {
        if (current == null && !sessionOver && catalog.entries.isNotEmpty()) advance()
    }

    // The pause after a win, then straight into the next game.
    LaunchedEffect(completedScene) {
        if (completedScene != null) {
            delay(CELEBRATION_MS)
            advance()
        }
    }

    when {
        sessionOver -> SessionFinished(
            played = playedNow.size,
            failedToLoad = loadFailed,
            onDone = onFinished
        )

        current == null -> Loading()

        else -> {
            val (entry, scene) = current!!
            Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E1)).safeDrawingPadding()) {
                GameScreen(
                    // Keyed by scene so each game starts a fresh engine session.
                    scene              = scene,
                    // A different arrangement every time the child meets a game.
                    seed               = sessionSeed + playedNow.size,
                    progressRepository = progressRepository,
                    onBack             = onFinished,
                    completionActions  = emptyList(),
                    onSkip = {
                        lastOutcome = PlayOutcome(entry.id, mistakes = 0, hintsUsed = 0, skipped = true)
                        scope.launch { advance() }
                    },
                    onCompleted = { metrics: SessionMetrics ->
                        lastOutcome = PlayOutcome(
                            sceneId    = entry.id,
                            mistakes   = metrics.mistakes,
                            hintsUsed  = metrics.hintsUsed,
                            responses  = metrics.responses,
                            unanswered = metrics.unanswered
                        )
                        applyLearningSceneCompletion(
                            progressRepository = progressRepository,
                            childId            = childId,
                            entry              = entry,
                            scene              = scene,
                            metrics            = metrics
                        )
                        completedScene = entry.id
                    }
                )

                SessionProgress(
                    done  = (playedNow.size - 1).coerceAtLeast(0),
                    total = PlaySessionPlanner.SESSION_LENGTH,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                )
            }
        }
    }
}

/**
 * How far through the sitting the child is, as filled dots.
 *
 * Wordless on purpose: it is the only thing on screen telling a non-reading
 * child that this has an end, which is what makes the last game bearable.
 */
@Composable
private fun SessionProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val filled = index < done
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.72f,
                animationSpec = tween(300),
                label = "dot"
            )
            Box(
                Modifier
                    .size((14 * scale).dp)
                    .background(
                        if (filled) Color(0xFF2A9D8F) else Color(0x33000000),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize().background(BG).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFF2A9D8F), strokeWidth = 5.dp)
    }
}

@Composable
private fun SessionFinished(played: Int, failedToLoad: Boolean, onDone: () -> Unit) {
    val tts = rememberTtsPlayer()
    LaunchedEffect(Unit) { tts.speak(SystemPhrases.SESSION_DONE) }

    Box(Modifier.fillMaxSize().background(BG).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            AppIconGlyph(AppIcon.Trophy, Color(0xFFE9A13B), size = 108.dp)
            Text(
                "Молодец!",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D3557),
                textAlign = TextAlign.Center
            )
            // Earned stars, one per game — countable without reading.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(played.coerceAtMost(PlaySessionPlanner.SESSION_LENGTH)) {
                    AppIconGlyph(AppIcon.Star, Color(0xFFE9A13B), size = 38.dp)
                }
            }
            if (failedToLoad) {
                Text(
                    "Некоторые игры не загрузились — проверьте интернет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF687381),
                    textAlign = TextAlign.Center
                )
            }
            Surface(
                onClick = onDone,
                shape = CircleShape,
                color = Color(0xFF2A9D8F),
                shadowElevation = 4.dp,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AppIconGlyph(AppIcon.Home, Color.White, size = 44.dp)
                }
            }
        }
    }
}
