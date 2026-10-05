package org.ollu.mini.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import mimi.core.world.WorldSnapshot
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/** Connection lines, in scene units so they scale with the board. */
private const val ACTIVE_LINE_WIDTH = 12f

@Composable
internal fun MazeLayer(
    snapshot: WorldSnapshot,
    mapper: CoordinateMapper,
    activeLine: List<PropertyValue.Vec2>,
    activeLineBlocked: Boolean,
    completedLine: List<PropertyValue.Vec2>,
    completedLineBlocked: Boolean
) {
    Canvas(Modifier.fillMaxSize()) {
        mazeWalls(snapshot.getAllEntities()).forEach { wall ->
                val screenCenter = mapper.logicalToScreen(wall.center)
                val screenWidth = mapper.logicalLengthToScreen(wall.width)
                val screenHeight = mapper.logicalLengthToScreen(wall.height)
                drawRoundRect(
                    color = Color(0xFF7B5E57),
                    topLeft = Offset(
                        screenCenter.x - screenWidth / 2f,
                        screenCenter.y - screenHeight / 2f
                    ),
                    size = Size(screenWidth, screenHeight),
                    cornerRadius = CornerRadius(14f, 14f)
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

internal fun segmentHitsMazeWall(
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
    mazeWalls(entities).any { it.contains(point) }

/** A maze wall as the axis-aligned box it occupies, in scene units. */
private class MazeWall(val center: PropertyValue.Vec2, val width: Float, val height: Float) {
    fun contains(point: PropertyValue.Vec2): Boolean =
        point.x >= center.x - width / 2f && point.x <= center.x + width / 2f &&
            point.y >= center.y - height / 2f && point.y <= center.y + height / 2f
}

/** The walls on screen; one missing its position or size is not drawn and blocks nothing. */
private fun mazeWalls(entities: List<EntitySnapshot>): List<MazeWall> =
    entities
        .filter { it.state != EntityLifecycleState.INACTIVE && it.hasComponent("mazeWall") }
        .mapNotNull { wall ->
            val center = wall.vec2("position") ?: return@mapNotNull null
            val width  = wall.num("width")?.toFloat() ?: return@mapNotNull null
            val height = wall.num("height")?.toFloat() ?: return@mapNotNull null
            MazeWall(center, width, height)
        }

private fun distance(a: PropertyValue.Vec2, b: PropertyValue.Vec2): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun DrawScope.drawMazeLine(
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
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = 11f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun ConnectionLines(
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
                    start = Offset(start.x, start.y),
                    end = Offset(end.x, end.y),
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
                start = Offset(start.x, start.y),
                end = Offset(end.x, end.y),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFF457B9D),
                radius = width * 1.3f,
                center = Offset(end.x, end.y)
            )
        }
    }
}
