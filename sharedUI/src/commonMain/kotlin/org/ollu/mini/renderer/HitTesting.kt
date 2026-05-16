package org.ollu.mini.renderer

import androidx.compose.ui.geometry.Offset
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot

private const val DRAG_HIT_RADIUS = 80f   // logical units
private const val DROP_HIT_RADIUS  = 150f  // logical units
private const val CONNECT_HIT_RADIUS = 90f
private const val MAZE_START_HIT_RADIUS = 95f

fun hitDraggable(screenPos: Offset, entities: List<EntitySnapshot>, mapper: CoordinateMapper): EntitySnapshot? {
    val logical = mapper.screenToLogical(screenPos)
    return entities
        .filter { it.state == EntityLifecycleState.ACTIVE || it.state == EntityLifecycleState.TENTATIVE }
        .filter { it.hasComponent("drag") }
        .filter { entity ->
            val pos = entity.vec2("position") ?: return@filter false
            val dx = logical.x - pos.x
            val dy = logical.y - pos.y
            (dx * dx + dy * dy) <= DRAG_HIT_RADIUS * DRAG_HIT_RADIUS
        }
        .maxByOrNull { it.num("zOrder") ?: 0.0 }
}

fun hitDropTarget(logicalPos: mimi.core.model.PropertyValue.Vec2, entities: List<EntitySnapshot>): EntitySnapshot? =
    entities
        .filter { it.hasComponent("dropTarget") }
        .firstOrNull { entity ->
            val pos = entity.vec2("position") ?: return@firstOrNull false
            val dx = logicalPos.x - pos.x
            val dy = logicalPos.y - pos.y
            (dx * dx + dy * dy) <= DROP_HIT_RADIUS * DROP_HIT_RADIUS
        }

fun hitConnectSource(screenPos: Offset, entities: List<EntitySnapshot>, mapper: CoordinateMapper): EntitySnapshot? {
    val logical = mapper.screenToLogical(screenPos)
    return entities
        .filter { it.state == EntityLifecycleState.ACTIVE }
        .filter { it.hasComponent("connectSource") }
        .filter { entity ->
            val pos = entity.vec2("position") ?: return@filter false
            val dx = logical.x - pos.x
            val dy = logical.y - pos.y
            (dx * dx + dy * dy) <= CONNECT_HIT_RADIUS * CONNECT_HIT_RADIUS
        }
        .maxByOrNull { it.num("zOrder") ?: 0.0 }
}

fun hitConnectTarget(logicalPos: mimi.core.model.PropertyValue.Vec2, entities: List<EntitySnapshot>): EntitySnapshot? =
    entities
        .filter { it.state == EntityLifecycleState.ACTIVE }
        .filter { it.hasComponent("connectTarget") }
        .firstOrNull { entity ->
            val pos = entity.vec2("position") ?: return@firstOrNull false
            val dx = logicalPos.x - pos.x
            val dy = logicalPos.y - pos.y
            (dx * dx + dy * dy) <= CONNECT_HIT_RADIUS * CONNECT_HIT_RADIUS
        }

fun hitMazeStart(screenPos: Offset, entities: List<EntitySnapshot>, mapper: CoordinateMapper): EntitySnapshot? {
    val logical = mapper.screenToLogical(screenPos)
    return entities
        .filter { it.state == EntityLifecycleState.ACTIVE }
        .filter { it.hasComponent("mazeStart") }
        .firstOrNull { entity ->
            val pos = entity.vec2("position") ?: return@firstOrNull false
            val radius = entity.num("radius")?.toFloat() ?: MAZE_START_HIT_RADIUS
            val dx = logical.x - pos.x
            val dy = logical.y - pos.y
            (dx * dx + dy * dy) <= radius * radius
        }
}
