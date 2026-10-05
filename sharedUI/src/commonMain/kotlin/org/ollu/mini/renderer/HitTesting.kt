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
            // A square test, matching the square tile: a radius left the corners
            // of a card outside the area that responded to a press.
            val reach = TileMetrics.reachOf(entity)
            kotlin.math.abs(logical.x - pos.x) <= reach &&
                kotlin.math.abs(logical.y - pos.y) <= reach
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

private fun isConnectEnd(entity: EntitySnapshot) =
    entity.hasComponent("connectSource") || entity.hasComponent("connectTarget")

private fun within(entity: EntitySnapshot, logical: mimi.core.model.PropertyValue.Vec2, radius: Float): Boolean {
    val pos = entity.vec2("position") ?: return false
    val dx = logical.x - pos.x
    val dy = logical.y - pos.y
    return dx * dx + dy * dy <= radius * radius
}

/**
 * Where a line may start: either card of a pair.
 *
 * Lines could only be drawn from the left-hand card, so a child who reached for
 * the fish first got nothing at all — no line, no response — and learned that
 * the game does not work. Which end a line starts from carries no meaning.
 */
fun hitConnectEnd(screenPos: Offset, entities: List<EntitySnapshot>, mapper: CoordinateMapper): EntitySnapshot? {
    val logical = mapper.screenToLogical(screenPos)
    return entities
        .filter { it.state == EntityLifecycleState.ACTIVE && isConnectEnd(it) }
        .filter { within(it, logical, CONNECT_HIT_RADIUS) }
        .maxByOrNull { it.num("zOrder") ?: 0.0 }
}

/**
 * The connection a finished line makes, as (source, target) — the order the
 * engine checks — whichever end the child started from. Null when the line
 * ends on nothing, or on a card of the same kind it started from.
 */
fun connectionFrom(
    startId: String,
    endLogical: mimi.core.model.PropertyValue.Vec2,
    entities: List<EntitySnapshot>
): Pair<String, String>? {
    val start = entities.firstOrNull { it.id == startId } ?: return null
    val startedAtSource = start.hasComponent("connectSource")
    val wanted = if (startedAtSource) "connectTarget" else "connectSource"
    val end = entities
        .filter { it.id != startId && it.state == EntityLifecycleState.ACTIVE && it.hasComponent(wanted) }
        .firstOrNull { within(it, endLogical, CONNECT_HIT_RADIUS) }
        ?: return null
    return if (startedAtSource) start.id to end.id else end.id to start.id
}
