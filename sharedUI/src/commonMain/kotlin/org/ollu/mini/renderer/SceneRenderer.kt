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
import kotlinx.coroutines.launch
import mimi.core.behavior.GameEvent
import mimi.core.engine.EngineSession
import mimi.core.model.EntityLifecycleState
import mimi.core.model.PropertyValue
import mimi.core.world.WorldSnapshot

private val BG_COLOR = Color(0xFFFFF8E1)   // warm cream

@Composable
fun SceneRenderer(
    snapshot:        WorldSnapshot,
    session:         EngineSession,
    scope:           CoroutineScope,
    hintedEntityId:  String? = null
) {
    val currentSession  by rememberUpdatedState(session)
    val currentScope    by rememberUpdatedState(scope)
    val currentSnapshot by rememberUpdatedState(snapshot)

    // MutableState → triggers recomposition so the dragged entity follows the pointer
    var draggedEntityId by remember { mutableStateOf<String?>(null) }
    var dragVisualPos   by remember { mutableStateOf<PropertyValue.Vec2?>(null) }
    // plain holder — only for delta calculation, no recomposition needed
    val lastLogical = remember { object { var pos: PropertyValue.Vec2? = null } }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG_COLOR)) {
        val density    = LocalDensity.current
        val canvasSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val mapper     = remember(canvasSize) { CoordinateMapper(canvasSize) }
        val mapperRef  by rememberUpdatedState(mapper)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startPos ->
                            val hit = hitDraggable(startPos, currentSnapshot.getAllEntities(), mapperRef)
                            if (hit != null) {
                                val logical     = mapperRef.screenToLogical(startPos)
                                draggedEntityId = hit.id
                                dragVisualPos   = logical
                                lastLogical.pos = logical
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.DragStart(hit.id, logical, tick = 0L))
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val entityId = draggedEntityId ?: return@detectDragGestures
                            val current  = mapperRef.screenToLogical(change.position)
                            val prev     = lastLogical.pos ?: current
                            val delta    = PropertyValue.Vec2(current.x - prev.x, current.y - prev.y)
                            dragVisualPos   = current
                            lastLogical.pos = current
                            currentScope.launch {
                                currentSession.dispatch(GameEvent.DragMove(entityId, current, delta, tick = 0L))
                            }
                        },
                        onDragEnd = {
                            val entityId = draggedEntityId ?: return@detectDragGestures
                            val dropPos  = dragVisualPos  ?: return@detectDragGestures
                            draggedEntityId = null
                            dragVisualPos   = null
                            lastLogical.pos = null

                            val target = hitDropTarget(dropPos, currentSnapshot.getAllEntities())
                            currentScope.launch {
                                if (target != null) {
                                    currentSession.dispatch(GameEvent.Drop(entityId, target.id, tick = 0L))
                                }
                            }
                        },
                        onDragCancel = {
                            draggedEntityId = null
                            dragVisualPos   = null
                            lastLogical.pos = null
                        }
                    )
                }
        ) {
            snapshot.getAllEntities()
                .filter { it.state != EntityLifecycleState.INACTIVE }
                .sortedBy { it.num("zOrder") ?: 0.0 }
                .forEach { entity ->
                    val overridePos = if (entity.id == draggedEntityId) dragVisualPos else null
                    key(entity.id) {
                        EntityRenderer(entity, mapper, overridePos,
                            isHinted = entity.id == hintedEntityId)
                    }
                }
        }
    }
}
