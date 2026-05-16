package org.ollu.mini.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mimi.core.behavior.GameEvent
import mimi.core.engine.EngineSession
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import mimi.core.world.WorldSnapshot
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

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
    var connectSourceId by remember { mutableStateOf<String?>(null) }
    var connectEndPos   by remember { mutableStateOf<PropertyValue.Vec2?>(null) }
    var traceActive     by remember { mutableStateOf(false) }
    var mazeActive      by remember { mutableStateOf(false) }
    var mazeLinePoints  by remember { mutableStateOf<List<PropertyValue.Vec2>>(emptyList()) }
    var mazeLineBlocked by remember { mutableStateOf(false) }
    var completedMazeLinePoints by remember { mutableStateOf<List<PropertyValue.Vec2>>(emptyList()) }
    var completedMazeLineBlocked by remember { mutableStateOf(false) }
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
                            val logical = mapperRef.screenToLogical(startPos)
                            val hit = hitDraggable(startPos, currentSnapshot.getAllEntities(), mapperRef)
                            if (hit != null) {
                                draggedEntityId = hit.id
                                dragVisualPos   = logical
                                lastLogical.pos = logical
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.DragStart(hit.id, logical, tick = 0L))
                                }
                            } else {
                                val connectHit = hitConnectSource(startPos, currentSnapshot.getAllEntities(), mapperRef)
                                if (connectHit != null) {
                                    connectSourceId = connectHit.id
                                    connectEndPos   = logical
                                    lastLogical.pos = logical
                                } else {
                                    val mazeStart = hitMazeStart(startPos, currentSnapshot.getAllEntities(), mapperRef)
                                    if (mazeStart != null) {
                                        mazeActive = true
                                        mazeLinePoints = listOf(logical)
                                        mazeLineBlocked = false
                                        completedMazeLinePoints = emptyList()
                                        completedMazeLineBlocked = false
                                        lastLogical.pos = logical
                                        currentScope.launch {
                                            currentSession.dispatch(
                                                GameEvent.TracePoint(logical, progress = 0f, tick = 0L, source = mazeStart.id)
                                            )
                                        }
                                    } else if (
                                        currentSnapshot.getAllEntities().any { it.hasComponent("traceTarget") } &&
                                        currentSnapshot.getAllEntities().none { it.hasComponent("mazeStart") }
                                    ) {
                                        traceActive     = true
                                        lastLogical.pos = logical
                                        currentScope.launch {
                                            currentSession.dispatch(GameEvent.TracePoint(logical, progress = 0f, tick = 0L))
                                        }
                                    }
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val current = mapperRef.screenToLogical(change.position)
                            if (traceActive) {
                                lastLogical.pos = current
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.TracePoint(current, progress = 0f, tick = 0L))
                                }
                            } else if (mazeActive) {
                                val prev = lastLogical.pos ?: current
                                mazeLinePoints = mazeLinePoints + current
                                if (segmentHitsMazeWall(prev, current, currentSnapshot.getAllEntities())) {
                                    mazeLineBlocked = true
                                }
                                lastLogical.pos = current
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.TracePoint(current, progress = 0f, tick = 0L))
                                }
                            } else if (connectSourceId != null) {
                                connectEndPos = current
                                lastLogical.pos = current
                            } else {
                                val entityId = draggedEntityId ?: return@detectDragGestures
                                val prev     = lastLogical.pos ?: current
                                val delta    = PropertyValue.Vec2(current.x - prev.x, current.y - prev.y)
                                dragVisualPos   = current
                                lastLogical.pos = current
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.DragMove(entityId, current, delta, tick = 0L))
                                }
                            }
                        },
                        onDragEnd = {
                            if (traceActive) {
                                traceActive     = false
                                lastLogical.pos = null
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.TraceComplete(pathId = "trace_path", accuracy = 1f, tick = 0L))
                                }
                            } else if (mazeActive) {
                                mazeActive = false
                                completedMazeLinePoints = mazeLinePoints
                                completedMazeLineBlocked = mazeLineBlocked
                                mazeLinePoints = emptyList()
                                mazeLineBlocked = false
                                lastLogical.pos = null
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.TraceComplete(pathId = "maze_path", accuracy = 1f, tick = 0L))
                                }
                            } else if (connectSourceId != null) {
                                val sourceId = connectSourceId ?: return@detectDragGestures
                                val endPos = connectEndPos ?: return@detectDragGestures
                                connectSourceId = null
                                connectEndPos = null
                                lastLogical.pos = null
                                val target = hitConnectTarget(endPos, currentSnapshot.getAllEntities())
                                currentScope.launch {
                                    if (target != null) {
                                        currentSession.dispatch(GameEvent.Drop(sourceId, target.id, tick = 0L))
                                    }
                                }
                            } else {
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
                            }
                        },
                        onDragCancel = {
                            draggedEntityId = null
                            dragVisualPos   = null
                            connectSourceId = null
                            connectEndPos   = null
                            traceActive     = false
                            mazeActive      = false
                            mazeLinePoints  = emptyList()
                            mazeLineBlocked = false
                            lastLogical.pos = null
                        }
                    )
                }
        ) {
            MazeLayer(
                snapshot = snapshot,
                mapper = mapper,
                activeLine = mazeLinePoints,
                activeLineBlocked = mazeLineBlocked,
                completedLine = completedMazeLinePoints,
                completedLineBlocked = completedMazeLineBlocked
            )
            ConnectionLines(
                snapshot = snapshot,
                mapper = mapper,
                activeSourceId = connectSourceId,
                activeEndPos = connectEndPos
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
                                val overridePos = if (entity.id == draggedEntityId) dragVisualPos else null
                                val onTap: (() -> Unit)? = if (entity.hasComponent("tap") &&
                                        entity.state == EntityLifecycleState.ACTIVE) {
                                    {
                                        val pos = entity.vec2("position") ?: PropertyValue.Vec2(0f, 0f)
                                        currentScope.launch {
                                            currentSession.dispatch(
                                                GameEvent.Tap(position = pos, tick = 0L, source = entity.id)
                                            )
                                        }
                                        Unit
                                    }
                                } else null
                                EntityRenderer(entity, mapper, overridePos,
                                    isHinted = entity.id == hintedEntityId,
                                    onTap    = onTap)
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun MazeLayer(
    snapshot: WorldSnapshot,
    mapper: CoordinateMapper,
    activeLine: List<PropertyValue.Vec2>,
    activeLineBlocked: Boolean,
    completedLine: List<PropertyValue.Vec2>,
    completedLineBlocked: Boolean
) {
    Canvas(Modifier.fillMaxSize()) {
        snapshot.getAllEntities()
            .filter { it.state != EntityLifecycleState.INACTIVE }
            .filter { it.hasComponent("mazeWall") }
            .forEach { wall ->
                val center = wall.vec2("position") ?: return@forEach
                val width = wall.num("width")?.toFloat() ?: return@forEach
                val height = wall.num("height")?.toFloat() ?: return@forEach
                val screenCenter = mapper.logicalToScreen(center)
                val screenWidth = mapper.logicalLengthToScreen(width)
                val screenHeight = mapper.logicalLengthToScreen(height)
                drawRoundRect(
                    color = Color(0xFF7B5E57),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        screenCenter.x - screenWidth / 2f,
                        screenCenter.y - screenHeight / 2f
                    ),
                    size = Size(screenWidth, screenHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
                )
            }

        drawMazeLine(
            completedLine,
            mapper,
            if (completedLineBlocked) Color(0xFFD62828) else Color(0xFF66BB6A),
            alpha = 0.72f
        )
        drawMazeLine(
            activeLine,
            mapper,
            if (activeLineBlocked) Color(0xFFD62828) else Color(0xFF2D9CDB),
            alpha = 0.92f
        )
    }
}

private fun segmentHitsMazeWall(
    from: PropertyValue.Vec2,
    to: PropertyValue.Vec2,
    entities: List<EntitySnapshot>
): Boolean {
    val length = distance(from, to)
    val steps = max(1, ceil(length / 16f).toInt())
    for (i in 0..steps) {
        val t = i.toFloat() / steps.toFloat()
        val point = PropertyValue.Vec2(
            x = from.x + (to.x - from.x) * t,
            y = from.y + (to.y - from.y) * t
        )
        if (pointHitsMazeWall(point, entities)) return true
    }
    return false
}

private fun pointHitsMazeWall(point: PropertyValue.Vec2, entities: List<EntitySnapshot>): Boolean =
    entities
        .filter { it.state != EntityLifecycleState.INACTIVE }
        .filter { it.hasComponent("mazeWall") }
        .any { wall ->
            val center = wall.vec2("position") ?: return@any false
            val width = wall.num("width")?.toFloat() ?: return@any false
            val height = wall.num("height")?.toFloat() ?: return@any false
            point.x >= center.x - width / 2f &&
                point.x <= center.x + width / 2f &&
                point.y >= center.y - height / 2f &&
                point.y <= center.y + height / 2f
        }

private fun distance(a: PropertyValue.Vec2, b: PropertyValue.Vec2): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMazeLine(
    points: List<PropertyValue.Vec2>,
    mapper: CoordinateMapper,
    color: Color,
    alpha: Float
) {
    if (points.size < 2) return
    points.zipWithNext().forEach { (from, to) ->
        val start = mapper.logicalToScreen(from)
        val end = mapper.logicalToScreen(to)
        drawLine(
            color = color.copy(alpha = alpha),
            start = androidx.compose.ui.geometry.Offset(start.x, start.y),
            end = androidx.compose.ui.geometry.Offset(end.x, end.y),
            strokeWidth = 11f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ConnectionLines(
    snapshot: WorldSnapshot,
    mapper: CoordinateMapper,
    activeSourceId: String?,
    activeEndPos: PropertyValue.Vec2?
) {
    Canvas(Modifier.fillMaxSize()) {
        snapshot.getAllEntities()
            .filter { it.state != EntityLifecycleState.INACTIVE }
            .forEach { source ->
                val targetId = (source.property("connectedTo") as? PropertyValue.Ref)?.entityId ?: return@forEach
                val sourcePos = source.vec2("position") ?: return@forEach
                val targetPos = snapshot.getEntity(targetId)?.vec2("position") ?: return@forEach
                val start = mapper.logicalToScreen(sourcePos)
                val end = mapper.logicalToScreen(targetPos)
                drawLine(
                    color = Color(0xFF2A9D8F),
                    start = androidx.compose.ui.geometry.Offset(start.x, start.y),
                    end = androidx.compose.ui.geometry.Offset(end.x, end.y),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
            }

        val activeSource = activeSourceId?.let { snapshot.getEntity(it) }
        val startLogical = activeSource?.vec2("position")
        if (startLogical != null && activeEndPos != null) {
            val start = mapper.logicalToScreen(startLogical)
            val end = mapper.logicalToScreen(activeEndPos)
            drawLine(
                color = Color(0xFF457B9D),
                start = androidx.compose.ui.geometry.Offset(start.x, start.y),
                end = androidx.compose.ui.geometry.Offset(end.x, end.y),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
        }
    }
}
