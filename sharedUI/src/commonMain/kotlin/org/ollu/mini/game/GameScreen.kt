package org.ollu.mini.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import mimi.core.engine.EngineBuilder
import mimi.core.engine.withDefaultPlugins
import mimi.core.model.ObjectiveState
import mimi.core.plugin.DefaultConstraintRegistry
import mimi.core.plugin.DefaultFeedbackRegistry
import mimi.core.world.SceneDefinition
import org.ollu.mini.feedback.HintStateChannel
import org.ollu.mini.feedback.LogFeedbackChannel
import org.ollu.mini.feedback.SoundFeedbackChannel
import org.ollu.mini.feedback.TtsFeedbackChannel
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.renderer.SceneRenderer

@Composable
fun GameScreen(
    scene:              SceneDefinition,
    onBack:             (() -> Unit)? = null,
    progressRepository: ProgressRepository? = null
) {
    val scope = rememberCoroutineScope()

    val hintChannel = remember { HintStateChannel() }

    val sessionResult = remember(scene) {
        runCatching {
            val constraintRegistry = DefaultConstraintRegistry().also { it.registerBuiltins() }
            val feedbackRegistry   = DefaultFeedbackRegistry().also {
                it.register(LogFeedbackChannel)
                it.register(SoundFeedbackChannel())
                it.register(TtsFeedbackChannel())
                it.register(hintChannel)
            }
            EngineBuilder()
                .withDefaultPlugins(constraintRegistry)
                .withFeedbackRegistry(feedbackRegistry)
                .build(scene, scope)
        }
    }

    val session    = sessionResult.getOrNull()
    val buildError = sessionResult.exceptionOrNull()

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    if (buildError != null) {
        Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E1))) {
            if (onBack != null) {
                TextButton(
                    onClick  = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) { Text("← Back", color = Color(0xFF666666)) }
            }
            Column(
                modifier            = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Text("⚠️", fontSize = 48.sp)
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
    val hintedEntityId by hintChannel.hintedEntityId.collectAsState()

    val isComplete = scene.objectives.any { obj ->
        snapshot.getObjectiveState(obj.id) == ObjectiveState.LOCKED_COMPLETE
    }

    LaunchedEffect(isComplete) {
        if (isComplete) progressRepository?.markCompleted(scene.sceneId)
    }

    Box(Modifier.fillMaxSize()) {
        SceneRenderer(
            snapshot       = snapshot,
            session        = session,
            scope          = scope,
            hintedEntityId = hintedEntityId
        )

        if (onBack != null) {
            TextButton(
                onClick  = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) { Text("← Back", color = Color(0xFF666666)) }
        }

        AnimatedVisibility(
            visible = isComplete,
            enter   = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.75f)
        ) {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 72.sp)
                    Text(
                        text     = "Молодец!",
                        style    = MaterialTheme.typography.displayMedium,
                        color    = Color.White,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .background(Color(0xFF4CAF50), RoundedCornerShape(16.dp))
                            .padding(horizontal = 32.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
