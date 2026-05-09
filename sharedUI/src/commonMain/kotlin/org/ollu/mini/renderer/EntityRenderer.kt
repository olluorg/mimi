package org.ollu.mini.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import kotlin.math.roundToInt

private val FRUIT_SIZE  = 90.dp
private val BASKET_SIZE = 110.dp
private val CORNER      = 16.dp

@Composable
fun EntityRenderer(
    entity:          EntitySnapshot,
    mapper:          CoordinateMapper,
    dragOverridePos: PropertyValue.Vec2? = null
) {
    val pos = dragOverridePos ?: entity.vec2("position") ?: return
    if (entity.state == EntityLifecycleState.INACTIVE) return

    val isDragging   = dragOverridePos != null
    val isDragTarget = entity.hasComponent("dropTarget")
    val emoji        = entity.text("emoji")
    val screenPos    = mapper.logicalToScreen(pos)

    val bgColor = when {
        isDragTarget -> Color(0x33795548)          // semi-transparent brown fill
        entity.state == EntityLifecycleState.ACTIVE    -> Color(0xFF5C9FD4)
        entity.state == EntityLifecycleState.TENTATIVE -> Color(0xFF66BB6A)
        entity.state == EntityLifecycleState.LOCKED    -> Color(0xFFB0BEC5)
        else -> return
    }
    val borderColor = when {
        isDragTarget -> Color(0xFF795548)
        entity.state == EntityLifecycleState.TENTATIVE -> Color(0xFF388E3C)
        entity.state == EntityLifecycleState.LOCKED    -> Color(0xFF90A4AE)
        else -> Color.Transparent
    }

    val size = if (isDragTarget) BASKET_SIZE else FRUIT_SIZE

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 10f else (entity.num("zOrder") ?: 0.0).toFloat())
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(
                        IntOffset(
                            (screenPos.x - placeable.width  / 2f).roundToInt(),
                            (screenPos.y - placeable.height / 2f).roundToInt()
                        )
                    )
                }
            }
            .size(size)
            .background(bgColor, RoundedCornerShape(CORNER))
            .border(3.dp, borderColor, RoundedCornerShape(CORNER)),
        contentAlignment = Alignment.Center
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = if (isDragTarget) 48.sp else 40.sp)
        } else {
            Text(entity.id, color = Color.White, fontSize = 12.sp)
        }
    }
}
