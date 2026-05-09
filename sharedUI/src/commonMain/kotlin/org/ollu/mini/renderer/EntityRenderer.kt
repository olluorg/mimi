package org.ollu.mini.renderer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    dragOverridePos: PropertyValue.Vec2? = null,
    isHinted:        Boolean             = false,
    onTap:           (() -> Unit)?       = null
) {
    val pos = dragOverridePos ?: entity.vec2("position") ?: return
    if (entity.state == EntityLifecycleState.INACTIVE) return

    val hintScale by if (isHinted) {
        rememberInfiniteTransition(label = "hint")
            .animateFloat(
                initialValue    = 1f,
                targetValue     = 1.18f,
                animationSpec   = infiniteRepeatable(tween(550), RepeatMode.Reverse),
                label           = "hintScale"
            )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
    }

    val isDragging   = dragOverridePos != null
    val isDragTarget = entity.hasComponent("dropTarget")
    val cardState    = entity.text("cardState")
    val emoji        = when (cardState) {
        "hidden" -> "?"
        "revealed", "matched" -> entity.text("face") ?: entity.text("emoji")
        else -> entity.text("emoji")
    }
    val screenPos    = mapper.logicalToScreen(pos)

    val bgColor = when {
        isDragTarget -> Color(0x33795548)          // semi-transparent brown fill
        cardState == "hidden"                          -> Color(0xFF6D8EC7)
        cardState == "revealed"                        -> Color(0xFFFFF3C4)
        cardState == "matched"                         -> Color(0xFF66BB6A)
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

    val widthDp = entity.num("width")?.toFloat()?.dp ?: entity.num("radius")?.let { (it * 2.0).toFloat().dp }
    val heightDp = entity.num("height")?.toFloat()?.dp ?: entity.num("radius")?.let { (it * 2.0).toFloat().dp }
    val sizeModifier = when {
        isDragTarget -> Modifier.size(BASKET_SIZE)
        widthDp != null && heightDp != null -> Modifier.size(widthDp, heightDp)
        widthDp != null -> Modifier.size(widthDp)
        else -> Modifier.size(FRUIT_SIZE)
    }

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 10f else (entity.num("zOrder") ?: 0.0).toFloat())
            .graphicsLayer { scaleX = hintScale; scaleY = hintScale }
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
            .then(sizeModifier)
            .background(bgColor, RoundedCornerShape(CORNER))
            .border(3.dp, borderColor, RoundedCornerShape(CORNER))
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = if (isDragTarget) 48.sp else 40.sp)
        } else {
            Text(entity.id, color = Color.White, fontSize = 12.sp)
        }
    }
}
