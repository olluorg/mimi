package org.ollu.mini.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mimi.core.behavior.GameEvent
import mimi.core.engine.EngineSession
import mimi.core.model.EntityLifecycleState
import mimi.core.model.PropertyValue
import mimi.core.world.WorldSnapshot
import org.ollu.mini.feedback.HintFocus

private val BG_COLOR = Color(0xFFFFF8E1)   // warm cream

/** How long a touched tile stays lit. Long enough to notice, short enough to keep up with. */
private const val TAP_FLASH_MS = 260L

@Composable
fun SceneRenderer(
    snapshot:        WorldSnapshot,
    session:         EngineSession,
    scope:           CoroutineScope,
    hintFocus:       HintFocus = HintFocus()
) {
    val currentSession  by rememberUpdatedState(session)
    val currentScope    by rememberUpdatedState(scope)
    val currentSnapshot by rememberUpdatedState(snapshot)

    // Everything the child is part-way through doing, held together and keyed to
    // the session. It used to be eleven separate `remember` blocks with no key,
    // so none of it was cleared when the next game loaded: the maze line drawn
    // in one scene stayed on screen over the next one, and a half-made
    // connection or a grabbed tile carried over just as silently. Keeping it in
    // one object means a new field cannot forget the key.
    val ui = remember(session) { SceneInteraction() }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG_COLOR)) {
        val density    = LocalDensity.current
        val canvasSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val mapper     = remember(canvasSize) { CoordinateMapper(canvasSize) }
        val mapperRef  by rememberUpdatedState(mapper)

        Box(
            Modifier
                .fillMaxSize()
                // Keyed to this play-through's state. With a constant key the
                // gesture handler kept the state of the first game it was
                // created for, so after "Ещё раз" or the next game a dragged
                // card stayed where it was while the finger moved on.
                .pointerInput(ui) {
                    fun send(event: GameEvent?) {
                        if (event != null) currentScope.launch { currentSession.dispatch(event) }
                    }
                    detectDragGestures(
                        onDragStart  = { send(ui.start(it, currentSnapshot.getAllEntities(), mapperRef)) },
                        onDrag       = { change, _ ->
                            change.consume()
                            send(ui.move(mapperRef.screenToLogical(change.position), currentSnapshot.getAllEntities()))
                        },
                        onDragEnd    = { send(ui.end(currentSnapshot.getAllEntities())) },
                        onDragCancel = { ui.cancel() }
                    )
                }
        ) {
            MazeLayer(
                snapshot = snapshot,
                mapper = mapper,
                activeLine = ui.mazeLinePoints,
                activeLineBlocked = ui.mazeLineBlocked,
                completedLine = ui.completedMazeLinePoints,
                completedLineBlocked = ui.completedMazeLineBlocked
            )
            ConnectionLines(
                snapshot = snapshot,
                mapper = mapper,
                activeSourceId = ui.connectSourceId,
                activeEndPos = ui.connectEndPos
            )
            snapshot.getAllEntities()
                .filter { it.state != EntityLifecycleState.INACTIVE }
                .sortedBy { it.num("zOrder") ?: 0.0 }
                .forEach { entity ->
                    key(entity.id) {
                        when {
                            entity.hasComponent("mazeWall") -> Unit
                            entity.hasComponent("mazePath") -> Unit
                            entity.hasComponent("traceTarget") -> {
                                if (entity.vec2("center") != null)
                                    CircleTraceRenderer(entity, mapper)
                                else
                                    TracePathRenderer(entity, mapper)
                            }
                            else -> {
                                val overridePos = if (entity.id == ui.draggedEntityId) ui.dragVisualPos else null
                                val onTap: (() -> Unit)? = if (entity.hasComponent("tap") &&
                                        entity.state == EntityLifecycleState.ACTIVE) {
                                    {
                                        val pos = entity.vec2("position") ?: PropertyValue.Vec2(0f, 0f)
                                        val tappedId = entity.id
                                        ui.justTapped = tappedId
                                        currentScope.launch {
                                            currentSession.dispatch(
                                                GameEvent.Tap(position = pos, tick = 0L, source = tappedId)
                                            )
                                            delay(TAP_FLASH_MS)
                                            if (ui.justTapped == tappedId) ui.justTapped = null
                                        }
                                        Unit
                                    }
                                } else null
                                EntityRenderer(entity, mapper, overridePos,
                                    isHinted     = entity.id == hintFocus.entityId,
                                    isPressed    = entity.id == ui.justTapped,
                                    isDestination = entity.id == hintFocus.destinationId,
                                    onTap    = onTap)
                            }
                        }
                    }
                }
        }
    }
}
