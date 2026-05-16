package org.ollu.mini.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

data class CompletionAction(
    val label:   String,
    val icon:    String = "",
    val primary: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun GameScreen(
    scene:             SceneDefinition,
    onBack:            (() -> Unit)? = null,
    progressRepository: ProgressRepository? = null,
    onCompleted:       (() -> Unit)? = null,
    completionActions: List<CompletionAction> = emptyList()
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
        Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
            if (onBack != null) {
                TextButton(
                    onClick  = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) { Text("⬅  Назад", color = Color(0xFF666666)) }
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

    val isComplete = snapshot.allLevelsComplete() || scene.objectives.any { obj ->
        snapshot.getObjectiveState(obj.id) == ObjectiveState.LOCKED_COMPLETE
    }

    var completionReported by remember(scene.sceneId) { mutableStateOf(false) }
    LaunchedEffect(isComplete) {
        if (isComplete && !completionReported) {
            completionReported = true
            progressRepository?.markCompleted(scene.sceneId)
            onCompleted?.invoke()
        }
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
            ) { Text("⬅  Назад", color = Color(0xFF666666)) }
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

        AnimatedVisibility(
            visible = isComplete,
            enter   = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.75f)
        ) {
            Box(
                modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Готово", style = MaterialTheme.typography.displaySmall, color = Color(0xFF1D3557), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Сцена завершена", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF687381))
                            if (completionActions.isNotEmpty()) {
                                Spacer(Modifier.height(18.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    completionActions.forEach { action ->
                                        if (action.primary) {
                                            Button(
                                                onClick = action.onClick,
                                                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A9D8F)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (action.icon.isNotBlank()) {
                                                    Text(action.icon, fontSize = 18.sp)
                                                    Spacer(Modifier.width(6.dp))
                                                }
                                                Text(action.label)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick  = action.onClick,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (action.icon.isNotBlank()) {
                                                    Text(action.icon, fontSize = 18.sp)
                                                    Spacer(Modifier.width(6.dp))
                                                }
                                                Text(action.label)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
