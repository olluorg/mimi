package org.ollu.mini.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

/**
 * Shapes and colour swatches, drawn rather than typed.
 *
 * A sorting-by-colour game spelled its red circle as the emoji 🔴. That was
 * always the wrong tool — the tile *is* a red circle, and the scene already says
 * so in its `color` property — and it fails outright on the web build, where
 * Compose does not render emoji at all. Drawing from the scene's own properties
 * is both truer to the data and the only version that works everywhere.
 */

/** Colour names scenes use, as the child would be told them. */
private val NAMED_COLORS = mapOf(
    "red"    to Color(0xFFE53935),
    "blue"   to Color(0xFF1E88E5),
    "yellow" to Color(0xFFFDD835),
    "green"  to Color(0xFF43A047),
    "orange" to Color(0xFFFB8C00),
    "purple" to Color(0xFF8E24AA),
    "pink"   to Color(0xFFEC407A),
    "brown"  to Color(0xFF6D4C41),
    "black"  to Color(0xFF37474F),
    "white"  to Color(0xFFFAFAFA),
    "grey"   to Color(0xFF9E9E9E),
    "gray"   to Color(0xFF9E9E9E)
)

fun namedColor(name: String?): Color? = name?.let { NAMED_COLORS[it.lowercase()] }

/** Shapes the renderer can draw. Anything else falls back to the tile's picture or text. */
val DRAWABLE_SHAPES = setOf("circle", "square", "triangle", "star", "rectangle", "oval")

/**
 * A quantity, drawn as that many dots.
 *
 * Counting games spelled their groups with the character ●, which the web build
 * has no glyph for — so the thing being counted was invisible in exactly the
 * game about counting it. A quantity is a picture, not a character.
 */
@Composable
fun DotsTile(
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (count <= 0) return@Canvas
        // Up to three to a row: a child counts a short row at a glance, where a
        // long one has to be tracked with a finger.
        val perRow = minOf(3, count)
        val rows   = (count + perRow - 1) / perRow
        val cellW  = size.width / perRow
        val cellH  = size.height / rows
        val radius = minOf(cellW, cellH) * 0.30f

        var drawn = 0
        for (row in 0 until rows) {
            val inRow = minOf(perRow, count - drawn)
            // Short last row stays centred rather than hugging the left edge.
            val offsetX = (size.width - inRow * cellW) / 2f
            for (col in 0 until inRow) {
                drawCircle(
                    color,
                    radius,
                    Offset(offsetX + cellW * (col + 0.5f), cellH * (row + 0.5f))
                )
                drawn++
            }
        }
    }
}

/**
 * The back of a face-down card: a simple drawn pattern.
 *
 * It used to be the character "?", which says nothing to a child who cannot
 * read and, being a glyph, was one more thing that could fail to render.
 */
@Composable
fun CardBack(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = min(size.width, size.height)
        // Canvas does not clip what is drawn to its bounds, so everything here is
        // kept inside deliberately — an earlier lattice ran two tile-widths past
        // the card in both directions and covered the board.
        val inset = s * 0.14f
        val ring  = s * 0.26f
        drawCircle(Color(0x33FFFFFF), ring + inset * 0.5f, center)
        drawCircle(Color(0xFFFDFBF7), ring, center)
        drawCircle(Color(0xFF5B7FB0), ring, center, style = Stroke(s * 0.05f))
    }
}

/**
 * An open container for a sorting game.
 *
 * A drop target used to be drawn as a plain card showing an example of what
 * belongs in it — which made the animals basket look exactly like the cat the
 * child was supposed to put into it. A basket has to read as a place, not as
 * another thing to pick up, so it is drawn as one: wider than a card, open at
 * the top, with the example tucked inside.
 */
@Composable
fun ContainerTile(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val rim = h * 0.20f
            val body = Path().apply {
                moveTo(w * 0.04f, rim)
                lineTo(w * 0.96f, rim)
                lineTo(w * 0.84f, h * 0.96f)
                lineTo(w * 0.16f, h * 0.96f)
                close()
            }
            drawPath(body, BASKET_FILL)
            drawPath(body, BASKET_EDGE, style = Stroke(h * 0.055f))
            // The rim, drawn as a bar across the opening: it is what makes the
            // shape read as something you can drop into.
            drawRoundRect(
                BASKET_EDGE,
                topLeft = Offset(0f, rim - h * 0.085f),
                size = Size(w, h * 0.17f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.085f, h * 0.085f)
            )
        }
        // The example sits low in the basket, so it looks held rather than offered.
        Box(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private val BASKET_FILL = Color(0xFFE8D3B5)
private val BASKET_EDGE = Color(0xFF9A7247)

/**
 * Draws [shape] in [color]. [outline] renders the shape as a hollow target, which
 * is how a scene shows the slot a piece belongs in.
 */
@Composable
fun ShapeTile(
    shape: String,
    color: Color,
    outline: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val stroke = Stroke(width = side * 0.12f)
        when (shape.lowercase()) {
            "circle", "oval" -> drawCircleShape(color, side, outline, stroke)
            "square", "rectangle" -> drawSquareShape(color, side, outline, stroke)
            "triangle" -> drawPath(trianglePath(side), color, outline, stroke)
            "star" -> drawPath(starPath(side), color, outline, stroke)
        }
    }
}

private fun DrawScope.drawCircleShape(color: Color, side: Float, outline: Boolean, stroke: Stroke) {
    val radius = side / 2f * if (outline) 0.86f else 0.92f
    if (outline) drawCircle(color, radius, center, style = stroke)
    else drawCircle(color, radius, center)
}

private fun DrawScope.drawSquareShape(color: Color, side: Float, outline: Boolean, stroke: Stroke) {
    val length = side * if (outline) 0.78f else 0.84f
    val topLeft = Offset((size.width - length) / 2f, (size.height - length) / 2f)
    if (outline) drawRect(color, topLeft, Size(length, length), style = stroke)
    else drawRect(color, topLeft, Size(length, length))
}

private fun DrawScope.drawPath(path: Path, color: Color, outline: Boolean, stroke: Stroke) {
    if (outline) drawPath(path, color, style = stroke) else drawPath(path, color)
}

private fun DrawScope.trianglePath(side: Float): Path {
    val length = side * 0.86f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val half = length / 2f
    // Slightly squat, so it reads as a triangle rather than a spike at tile size.
    return Path().apply {
        moveTo(cx, cy - half * 0.92f)
        lineTo(cx + half, cy + half * 0.72f)
        lineTo(cx - half, cy + half * 0.72f)
        close()
    }
}

private fun DrawScope.starPath(side: Float): Path {
    val outer = side * 0.46f
    val inner = outer * 0.44f
    val cx = size.width / 2f
    val cy = size.height / 2f
    return Path().apply {
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            // Start at the top so the star sits upright.
            val angle = (PI / 5f * i - PI / 2f).toFloat()
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
