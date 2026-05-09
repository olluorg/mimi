package org.ollu.mini.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import mimi.core.model.Waypoint

private val COLOR_TRACK    = Color(0xFFCFD8DC)   // light grey track
private val COLOR_DONE     = Color(0xFF66BB6A)   // green — reached
private val COLOR_NEXT     = Color(0xFF4A90D9)   // blue — next waypoint to hit
private val COLOR_PENDING  = Color(0xFFB0BEC5)   // grey — not yet reached

@Composable
fun TracePathRenderer(entity: EntitySnapshot, mapper: CoordinateMapper) {
    val waypoints = entity.waypoints("waypoints") ?: return

    // Count how many waypoints are done (by checking wp_N_done properties)
    val doneCount = waypoints.indices.count { i ->
        (entity.property("wp_${i}_done") as? PropertyValue.Flag)?.value == true
    }

    Canvas(Modifier.fillMaxSize()) {
        val screenPoints = waypoints.map { wp ->
            val s = mapper.logicalToScreen(PropertyValue.Vec2(wp.x, wp.y))
            Offset(s.x, s.y)
        }

        // Draw connecting track line
        if (screenPoints.size >= 2) {
            val path = Path().apply {
                moveTo(screenPoints[0].x, screenPoints[0].y)
                screenPoints.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, COLOR_TRACK, style = Stroke(width = 12f, cap = StrokeCap.Round))
        }

        // Draw done segment in green
        if (doneCount >= 2) {
            val donePath = Path().apply {
                moveTo(screenPoints[0].x, screenPoints[0].y)
                screenPoints.take(doneCount).drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(donePath, COLOR_DONE, style = Stroke(width = 12f, cap = StrokeCap.Round))
        }

        // Draw circles for each waypoint
        waypoints.forEachIndexed { i, wp ->
            val center = screenPoints[i]
            val radius = mapper.logicalLengthToScreen(wp.radius * 0.6f)
            val color = when {
                i < doneCount       -> COLOR_DONE
                i == doneCount      -> COLOR_NEXT
                else                -> COLOR_PENDING
            }
            drawCircle(color.copy(alpha = 0.25f), radius, center)
            drawCircle(color, radius, center, style = Stroke(width = 4f))
        }
    }
}
