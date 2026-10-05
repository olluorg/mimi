package org.ollu.mini.renderer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import mimi.core.behavior.GameEvent
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue

/**
 * What the child is in the middle of doing: a tile held, a line being drawn, a
 * path being traced. It belongs to one play-through and must not outlive it.
 *
 * Each gesture step updates this state and returns the event, if any, that the
 * engine should hear about it — so the gesture handler only has to forward it.
 */
internal class SceneInteraction {
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

    /** A finger went down: work out what it grabbed, in order of precedence. */
    fun start(screenPos: Offset, entities: List<EntitySnapshot>, mapper: CoordinateMapper): GameEvent? {
        val logical = mapper.screenToLogical(screenPos)

        hitDraggable(screenPos, entities, mapper)?.let { hit ->
            draggedEntityId = hit.id
            dragVisualPos   = logical
            lastPos         = logical
            return GameEvent.DragStart(hit.id, logical, tick = 0L)
        }

        hitConnectEnd(screenPos, entities, mapper)?.let { hit ->
            connectSourceId = hit.id
            connectEndPos   = logical
            lastPos         = logical
            return null
        }

        hitMazeStart(screenPos, entities, mapper)?.let { start ->
            mazeActive               = true
            mazeLinePoints           = listOf(logical)
            mazeLineBlocked          = false
            completedMazeLinePoints  = emptyList()
            completedMazeLineBlocked = false
            lastPos                  = logical
            return GameEvent.TracePoint(logical, progress = 0f, tick = 0L, source = start.id)
        }

        val tracing = entities.any { it.hasComponent("traceTarget") } &&
            entities.none { it.hasComponent("mazeStart") }
        if (tracing) {
            traceActive = true
            lastPos     = logical
            return GameEvent.TracePoint(logical, progress = 0f, tick = 0L)
        }
        return null
    }

    /** The finger moved to [current], in scene units. */
    fun move(current: PropertyValue.Vec2, entities: List<EntitySnapshot>): GameEvent? {
        val prev = lastPos ?: current
        lastPos = current
        return when {
            traceActive -> GameEvent.TracePoint(current, progress = 0f, tick = 0L)
            mazeActive -> {
                mazeLinePoints = mazeLinePoints + current
                if (segmentHitsMazeWall(prev, current, entities)) mazeLineBlocked = true
                GameEvent.TracePoint(current, progress = 0f, tick = 0L)
            }
            connectSourceId != null -> {
                connectEndPos = current
                null
            }
            else -> {
                val entityId = draggedEntityId ?: return null
                dragVisualPos = current
                GameEvent.DragMove(entityId, current, PropertyValue.Vec2(current.x - prev.x, current.y - prev.y), tick = 0L)
            }
        }
    }

    /** The finger lifted: finish whatever it was doing. */
    fun end(entities: List<EntitySnapshot>): GameEvent? = when {
        traceActive -> {
            traceActive = false
            lastPos     = null
            GameEvent.TraceComplete(pathId = "trace_path", accuracy = 1f, tick = 0L)
        }
        mazeActive -> {
            mazeActive               = false
            completedMazeLinePoints  = mazeLinePoints
            completedMazeLineBlocked = mazeLineBlocked
            mazeLinePoints           = emptyList()
            mazeLineBlocked          = false
            lastPos                  = null
            GameEvent.TraceComplete(pathId = "maze_path", accuracy = 1f, tick = 0L)
        }
        connectSourceId != null -> {
            val sourceId = connectSourceId
            val endPos   = connectEndPos
            connectSourceId = null
            connectEndPos   = null
            lastPos         = null
            if (sourceId == null || endPos == null) null
            else connectionFrom(sourceId, endPos, entities)?.let { (source, target) ->
                GameEvent.Drop(source, target, tick = 0L)
            }
        }
        else -> {
            val entityId = draggedEntityId
            val dropPos  = dragVisualPos
            if (entityId == null || dropPos == null) null
            else {
                draggedEntityId = null
                dragVisualPos   = null
                lastPos         = null
                hitDropTarget(dropPos, entities)?.let { GameEvent.Drop(entityId, it.id, tick = 0L) }
            }
        }
    }

    /** The gesture was taken away (another pointer, the system): drop everything in hand. */
    fun cancel() {
        draggedEntityId = null
        dragVisualPos   = null
        connectSourceId = null
        connectEndPos   = null
        traceActive     = false
        mazeActive      = false
        mazeLinePoints  = emptyList()
        mazeLineBlocked = false
        lastPos         = null
    }
}
