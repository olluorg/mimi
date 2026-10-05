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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mimi.core.behavior.GameEvent
import mimi.core.engine.EngineSession
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import mimi.core.world.WorldSnapshot
import org.ollu.mini.feedback.HintFocus
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

private val BG_COLOR = Color(0xFFFFF8E1)   // warm cream

/**
 * What the child is in the middle of doing: a tile held, a line being drawn, a
 * path being traced. It belongs to one play-through and must not outlive it.
 */
private class SceneInteraction {
    var draggedEntityId by mutableStateOf<String?>(null)
    var dragVisualPos   by mutableStateOf<PropertyValue.Vec2?>(null)
    var connectSourceId by mutableStateOf<String?>(null)
    var connectEndPos   by mutableStateOf<PropertyValue.Vec2?>(null)
    var traceActive     by mutableStateOf(false)

    /**
     * The tile touched a moment ago. A repeatable target never changes state, so
     * without this a tap on it produced no visible change at all — fatal in a
     * game whose whole lesson is "I touched it and something happened".
     */
    var justTapped      by mutableStateOf<String?>(null)

    var mazeActive               by mutableStateOf(false)
    var mazeLinePoints           by mutableStateOf<List<PropertyValue.Vec2>>(emptyList())
    var mazeLineBlocked          by mutableStateOf(false)
    var completedMazeLinePoints  by mutableStateOf<List<PropertyValue.Vec2>>(emptyList())
    var completedMazeLineBlocked by mutableStateOf(false)

    /** Where the pointer was last seen; only for delta maths, so no recomposition. */
    var lastPos: PropertyValue.Vec2? = null
}

/** Connection lines, in scene units so they scale with the board. */
private const val ACTIVE_LINE_WIDTH = 12f

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
                    detectDragGestures(
                        onDragStart = { startPos ->
                            val logical = mapperRef.screenToLogical(startPos)
                            val hit = hitDraggable(startPos, currentSnapshot.getAllEntities(), mapperRef)
                            if (hit != null) {
                                ui.draggedEntityId = hit.id
                                ui.dragVisualPos   = logical
                                ui.lastPos = logical
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.DragStart(hit.id, logical, tick = 0L))
                                }
                            } else {
                                val connectHit = hitConnectEnd(startPos, currentSnapshot.getAllEntities(), mapperRef)
                                if (connectHit != null) {
                                    ui.connectSourceId = connectHit.id
                                    ui.connectEndPos   = logical
                                    ui.lastPos = logical
                                } else {
                                    val mazeStart = hitMazeStart(startPos, currentSnapshot.getAllEntities(), mapperRef)
                                    if (mazeStart != null) {
                                        ui.mazeActive = true
                                        ui.mazeLinePoints = listOf(logical)
                                        ui.mazeLineBlocked = false
                                        ui.completedMazeLinePoints = emptyList()
                                        ui.completedMazeLineBlocked = false
                                        ui.lastPos = logical
                                        currentScope.launch {
                                            currentSession.dispatch(
                                                GameEvent.TracePoint(logical, progress = 0f, tick = 0L, source = mazeStart.id)
                                            )
                                        }
                                    } else if (
                                        currentSnapshot.getAllEntities().any { it.hasComponent("traceTarget") } &&
                                        currentSnapshot.getAllEntities().none { it.hasComponent("mazeStart") }
                                    ) {
                                        ui.traceActive     = true
                                        ui.lastPos = logical
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
                            if (ui.traceActive) {
                                ui.lastPos = current
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.TracePoint(current, progress = 0f, tick = 0L))
                                }
                            } else if (ui.mazeActive) {
                                val prev = ui.lastPos ?: current
                                ui.mazeLinePoints = ui.mazeLinePoints + current
                                if (segmentHitsMazeWall(prev, current, currentSnapshot.getAllEntities())) {
                                    ui.mazeLineBlocked = true
                                }
                                ui.lastPos = current
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.TracePoint(current, progress = 0f, tick = 0L))
                                }
                            } else if (ui.connectSourceId != null) {
                                ui.connectEndPos = current
                                ui.lastPos = current
                            } else {
                                val entityId = ui.draggedEntityId ?: return@detectDragGestures
                                val prev     = ui.lastPos ?: current
                                val delta    = PropertyValue.Vec2(current.x - prev.x, current.y - prev.y)
                                ui.dragVisualPos   = current
                                ui.lastPos = current
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.DragMove(entityId, current, delta, tick = 0L))
                                }
                            }
                        },
                        onDragEnd = {
                            if (ui.traceActive) {
                                ui.traceActive     = false
                                ui.lastPos = null
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.TraceComplete(pathId = "trace_path", accuracy = 1f, tick = 0L))
                                }
                            } else if (ui.mazeActive) {
                                ui.mazeActive = false
                                ui.completedMazeLinePoints = ui.mazeLinePoints
                                ui.completedMazeLineBlocked = ui.mazeLineBlocked
                                ui.mazeLinePoints = emptyList()
                                ui.mazeLineBlocked = false
                                ui.lastPos = null
                                currentScope.launch {
                                    currentSession.dispatch(GameEvent.TraceComplete(pathId = "maze_path", accuracy = 1f, tick = 0L))
                                }
                            } else if (ui.connectSourceId != null) {
                                val sourceId = ui.connectSourceId ?: return@detectDragGestures
                                val endPos = ui.connectEndPos ?: return@detectDragGestures
                                ui.connectSourceId = null
                                ui.connectEndPos = null
                                ui.lastPos = null
                                val link = connectionFrom(sourceId, endPos, currentSnapshot.getAllEntities())
                                currentScope.launch {
                                    if (link != null) {
                                        currentSession.dispatch(GameEvent.Drop(link.first, link.second, tick = 0L))
                                    }
                                }
                            } else {
                                val entityId = ui.draggedEntityId ?: return@detectDragGestures
                                val dropPos  = ui.dragVisualPos  ?: return@detectDragGestures
                                ui.draggedEntityId = null
                                ui.dragVisualPos   = null
                                ui.lastPos = null
                                val target = hitDropTarget(dropPos, currentSnapshot.getAllEntities())
                                currentScope.launch {
                                    if (target != null) {
                                        currentSession.dispatch(GameEvent.Drop(entityId, target.id, tick = 0L))
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            ui.draggedEntityId = null
                            ui.dragVisualPos   = null
                            ui.connectSourceId = null
                            ui.connectEndPos   = null
                            ui.traceActive     = false
                            ui.mazeActive      = false
                            ui.mazeLinePoints  = emptyList()
                            ui.mazeLineBlocked = false
                            ui.lastPos = null
                        }
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
                    strokeWidth = mapper.logicalLengthToScreen(ACTIVE_LINE_WIDTH),
                    cap = StrokeCap.Round
                )
            }

        // The line being drawn follows the finger, thick enough to see under
        // it, with a dot where the finger is so the child can tell the line is
        // theirs and still attached.
        val activeSource = activeSourceId?.let { snapshot.getEntity(it) }
        val startLogical = activeSource?.vec2("position")
        if (startLogical != null && activeEndPos != null) {
            val start = mapper.logicalToScreen(startLogical)
            val end = mapper.logicalToScreen(activeEndPos)
            val width = mapper.logicalLengthToScreen(ACTIVE_LINE_WIDTH)
            drawLine(
                color = Color(0xFF457B9D),
                start = androidx.compose.ui.geometry.Offset(start.x, start.y),
                end = androidx.compose.ui.geometry.Offset(end.x, end.y),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFF457B9D),
                radius = width * 1.3f,
                center = androidx.compose.ui.geometry.Offset(end.x, end.y)
            )
        }
    }
}
