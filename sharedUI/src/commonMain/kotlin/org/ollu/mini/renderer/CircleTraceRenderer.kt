package org.ollu.mini.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue

private val TRACK  = Color(0xFFCFD8DC)
private val ACTIVE = Color(0xFF4A90D9)
private val DONE   = Color(0xFF66BB6A)

@Composable
fun CircleTraceRenderer(entity: EntitySnapshot, mapper: CoordinateMapper) {
    val center     = entity.vec2("center") ?: return
    val radiusLog  = entity.num("radius")?.toFloat() ?: return
    val resolution = entity.num("resolution")?.toInt() ?: 64

    val sectorAngle = 360f / resolution
    val isComplete  = entity.state == EntityLifecycleState.TENTATIVE ||
                      entity.state == EntityLifecycleState.LOCKED

    val doneSectors = (0 until resolution).filter { i ->
        (entity.property("sector_${i}_done") as? PropertyValue.Flag)?.value == true
    }.toSet()

    Canvas(Modifier.fillMaxSize()) {
        val sc          = mapper.logicalToScreen(center)
        val sr          = mapper.logicalLengthToScreen(radiusLog)
        val center2d    = Offset(sc.x, sc.y)
        val strokeWidth = (sr * 0.13f).coerceIn(12f, 32f)
        val arcColor    = if (isComplete) DONE else ACTIVE

        // Grey track (full circle)
        drawCircle(
            color  = TRACK,
            radius = sr,
            center = center2d,
            style  = Stroke(width = strokeWidth)
        )

        // Completed sectors
        for (idx in doneSectors) {
            drawArc(
                color      = arcColor,
                startAngle = idx * sectorAngle,
                sweepAngle = sectorAngle + 1f,   // 1° overlap avoids gaps at sector boundaries
                useCenter  = false,
                topLeft    = Offset(center2d.x - sr, center2d.y - sr),
                size       = Size(sr * 2, sr * 2),
                style      = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
        }

        // Start-point hint dot at 3-o'clock when nothing drawn yet
        if (doneSectors.isEmpty()) {
            drawCircle(
                color  = ACTIVE.copy(alpha = 0.5f),
                radius = strokeWidth,
                center = Offset(center2d.x + sr, center2d.y)
            )
        }
    }
}
