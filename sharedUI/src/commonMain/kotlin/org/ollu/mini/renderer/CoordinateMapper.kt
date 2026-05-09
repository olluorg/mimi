package org.ollu.mini.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import mimi.core.model.PropertyValue

private const val LOGICAL_SIZE = 1000f

class CoordinateMapper(val canvasSize: Size) {
    val scale: Float = minOf(canvasSize.width / LOGICAL_SIZE, canvasSize.height / LOGICAL_SIZE)

    val letterboxRect: Rect = Rect(
        left   = (canvasSize.width  - LOGICAL_SIZE * scale) / 2f,
        top    = (canvasSize.height - LOGICAL_SIZE * scale) / 2f,
        right  = (canvasSize.width  + LOGICAL_SIZE * scale) / 2f,
        bottom = (canvasSize.height + LOGICAL_SIZE * scale) / 2f
    )

    fun logicalToScreen(logical: PropertyValue.Vec2): Offset = Offset(
        x = letterboxRect.left + logical.x * scale,
        y = letterboxRect.top  + logical.y * scale
    )

    fun screenToLogical(screen: Offset): PropertyValue.Vec2 = PropertyValue.Vec2(
        x = ((screen.x - letterboxRect.left) / scale).coerceIn(0f, LOGICAL_SIZE),
        y = ((screen.y - letterboxRect.top)  / scale).coerceIn(0f, LOGICAL_SIZE)
    )
}
