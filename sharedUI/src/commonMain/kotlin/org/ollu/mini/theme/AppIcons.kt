package org.ollu.mini.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The app's own icons, drawn rather than typed as emoji.
 *
 * Compose does not render emoji in the web build, so every ▶, 🏆 and 🔒 in the
 * interface came out blank there — on the very first screen a child sees. These
 * are plain vector drawings, which render identically on every platform, and
 * they also let the icon take the colour the surface needs instead of whatever
 * the font decides.
 */
enum class AppIcon { Play, Star, Trophy, Home, Lock, Person, Forward, Back, Refresh, Check, Warning, Grid, Chart, Book, Speaker, Mic }

@Composable
fun AppIconGlyph(
    icon: AppIcon,
    tint: Color,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(size)) {
        val s = min(this.size.width, this.size.height)
        val stroke = Stroke(width = s * 0.11f, cap = StrokeCap.Round)
        when (icon) {
            AppIcon.Play    -> drawPlay(tint, s)
            AppIcon.Star    -> drawStar(tint, s, filled = true)
            AppIcon.Trophy  -> drawTrophy(tint, s, stroke)
            AppIcon.Home    -> drawHome(tint, s, stroke)
            AppIcon.Lock    -> drawLock(tint, s, stroke)
            AppIcon.Person  -> drawPerson(tint, s)
            AppIcon.Forward -> drawChevron(tint, s, stroke, pointingRight = true)
            AppIcon.Back    -> drawChevron(tint, s, stroke, pointingRight = false)
            AppIcon.Refresh -> drawRefresh(tint, s, stroke)
            AppIcon.Check   -> drawCheck(tint, s, stroke)
            AppIcon.Warning -> drawWarning(tint, s, stroke)
            AppIcon.Grid    -> drawGrid(tint, s)
            AppIcon.Chart   -> drawChart(tint, s)
            AppIcon.Book    -> drawBook(tint, s, stroke)
            AppIcon.Speaker -> drawSpeaker(tint, s, stroke)
            AppIcon.Mic     -> drawMic(tint, s, stroke)
        }
    }
}

private fun DrawScope.drawPlay(tint: Color, s: Float) {
    val h = s * 0.62f
    val w = s * 0.54f
    val cx = size.width / 2f + w * 0.08f
    val cy = size.height / 2f
    drawPath(
        Path().apply {
            moveTo(cx - w / 2f, cy - h / 2f)
            lineTo(cx + w / 2f, cy)
            lineTo(cx - w / 2f, cy + h / 2f)
            close()
        },
        tint
    )
}

private fun DrawScope.drawStar(tint: Color, s: Float, filled: Boolean) {
    val outer = s * 0.44f
    val inner = outer * 0.45f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = (PI / 5f * i - PI / 2f).toFloat()
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    if (filled) drawPath(path, tint) else drawPath(path, tint, style = Stroke(s * 0.1f))
}

private fun DrawScope.drawTrophy(tint: Color, s: Float, stroke: Stroke) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val cupW = s * 0.42f
    val cupH = s * 0.36f
    val top = cy - s * 0.30f
    // Bowl
    drawPath(
        Path().apply {
            moveTo(cx - cupW / 2f, top)
            lineTo(cx + cupW / 2f, top)
            lineTo(cx + cupW * 0.34f, top + cupH)
            lineTo(cx - cupW * 0.34f, top + cupH)
            close()
        },
        tint
    )
    // Handles
    val handle = s * 0.15f
    drawArc(tint, -90f, 180f, false,
        topLeft = Offset(cx - cupW / 2f - handle, top),
        size = Size(handle * 2f, handle * 2f), style = stroke)
    drawArc(tint, -90f, -180f, false,
        topLeft = Offset(cx + cupW / 2f - handle, top),
        size = Size(handle * 2f, handle * 2f), style = stroke)
    // Stem and base
    drawRect(tint, Offset(cx - s * 0.05f, top + cupH), Size(s * 0.10f, s * 0.14f))
    drawRect(tint, Offset(cx - s * 0.20f, top + cupH + s * 0.14f), Size(s * 0.40f, s * 0.09f))
}

private fun DrawScope.drawHome(tint: Color, s: Float, stroke: Stroke) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.62f
    val roof = cy - s * 0.30f
    drawPath(
        Path().apply {
            moveTo(cx, roof)
            lineTo(cx + w / 2f, roof + w * 0.42f)
            lineTo(cx - w / 2f, roof + w * 0.42f)
            close()
        },
        tint
    )
    drawRect(tint, Offset(cx - w * 0.34f, roof + w * 0.42f), Size(w * 0.68f, w * 0.46f), style = stroke)
}

private fun DrawScope.drawLock(tint: Color, s: Float, stroke: Stroke) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val bodyW = s * 0.52f
    val bodyH = s * 0.40f
    val bodyTop = cy - s * 0.02f
    drawArc(tint, 180f, 180f, false,
        topLeft = Offset(cx - bodyW * 0.32f, bodyTop - bodyW * 0.44f),
        size = Size(bodyW * 0.64f, bodyW * 0.64f), style = stroke)
    drawRect(tint, Offset(cx - bodyW / 2f, bodyTop), Size(bodyW, bodyH))
}

private fun DrawScope.drawPerson(tint: Color, s: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawCircle(tint, s * 0.16f, Offset(cx, cy - s * 0.18f))
    drawArc(tint, 180f, 180f, true,
        topLeft = Offset(cx - s * 0.28f, cy + s * 0.02f),
        size = Size(s * 0.56f, s * 0.52f))
}

private fun DrawScope.drawChevron(tint: Color, s: Float, stroke: Stroke, pointingRight: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = s * 0.16f * if (pointingRight) 1f else -1f
    val dy = s * 0.22f
    drawPath(
        Path().apply {
            moveTo(cx - dx, cy - dy)
            lineTo(cx + dx, cy)
            lineTo(cx - dx, cy + dy)
        },
        tint, style = stroke
    )
}

private fun DrawScope.drawRefresh(tint: Color, s: Float, stroke: Stroke) {
    val r = s * 0.30f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawArc(tint, 40f, 280f, false,
        topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = stroke)
    // Arrowhead closing the loop.
    val a = (40f * PI / 180f).toFloat()
    val hx = cx + r * cos(a)
    val hy = cy + r * sin(a)
    val h = s * 0.13f
    drawPath(
        Path().apply {
            moveTo(hx + h, hy - h * 0.2f)
            lineTo(hx - h * 0.3f, hy - h)
            lineTo(hx - h * 0.1f, hy + h * 0.8f)
            close()
        },
        tint
    )
}

private fun DrawScope.drawCheck(tint: Color, s: Float, stroke: Stroke) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawPath(
        Path().apply {
            moveTo(cx - s * 0.24f, cy)
            lineTo(cx - s * 0.06f, cy + s * 0.19f)
            lineTo(cx + s * 0.26f, cy - s * 0.20f)
        },
        tint, style = stroke
    )
}

private fun DrawScope.drawWarning(tint: Color, s: Float, stroke: Stroke) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.62f
    drawPath(
        Path().apply {
            moveTo(cx, cy - s * 0.30f)
            lineTo(cx + w / 2f, cy + s * 0.26f)
            lineTo(cx - w / 2f, cy + s * 0.26f)
            close()
        },
        tint, style = stroke
    )
    drawLine(tint, Offset(cx, cy - s * 0.08f), Offset(cx, cy + s * 0.08f), stroke.width, StrokeCap.Round)
    drawCircle(tint, s * 0.035f, Offset(cx, cy + s * 0.17f))
}

private fun DrawScope.drawGrid(tint: Color, s: Float) {
    val cell = s * 0.22f
    val gap = s * 0.08f
    val start = Offset(size.width / 2f - cell - gap / 2f, size.height / 2f - cell - gap / 2f)
    for (row in 0..1) for (col in 0..1) {
        drawRect(
            tint,
            Offset(start.x + col * (cell + gap), start.y + row * (cell + gap)),
            Size(cell, cell)
        )
    }
}

private fun DrawScope.drawChart(tint: Color, s: Float) {
    val base = size.height / 2f + s * 0.26f
    val w = s * 0.15f
    val gap = s * 0.07f
    val left = size.width / 2f - (w * 1.5f + gap)
    listOf(0.28f, 0.46f, 0.62f).forEachIndexed { i, h ->
        val height = s * h
        drawRect(tint, Offset(left + i * (w + gap), base - height), Size(w, height))
    }
}

private fun DrawScope.drawBook(tint: Color, s: Float, stroke: Stroke) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.58f
    val h = s * 0.44f
    drawRect(tint, Offset(cx - w / 2f, cy - h / 2f), Size(w, h), style = stroke)
    drawLine(tint, Offset(cx, cy - h / 2f), Offset(cx, cy + h / 2f), stroke.width, StrokeCap.Round)
}

/** A loudspeaker with two sound waves — "say it again". */
private fun DrawScope.drawSpeaker(tint: Color, s: Float, stroke: Stroke) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val left = cx - s * 0.36f
    drawPath(
        Path().apply {
            moveTo(left, cy - s * 0.11f)
            lineTo(left + s * 0.16f, cy - s * 0.11f)
            lineTo(left + s * 0.36f, cy - s * 0.30f)
            lineTo(left + s * 0.36f, cy + s * 0.30f)
            lineTo(left + s * 0.16f, cy + s * 0.11f)
            lineTo(left, cy + s * 0.11f)
            close()
        },
        tint
    )
    for (radius in listOf(s * 0.16f, s * 0.30f)) {
        val arcLeft = left + s * 0.36f - radius * 0.35f
        drawArc(
            color      = tint,
            startAngle = -50f,
            sweepAngle = 100f,
            useCenter  = false,
            topLeft    = Offset(arcLeft, cy - radius),
            size       = Size(radius * 2f, radius * 2f),
            style      = Stroke(width = stroke.width * 0.8f, cap = StrokeCap.Round)
        )
    }
}

/** A microphone: the capsule, the cradle under it and its stand. */
private fun DrawScope.drawMic(tint: Color, s: Float, stroke: Stroke) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.26f
    val h = s * 0.44f
    val top = cy - s * 0.40f
    drawRoundRect(
        color        = tint,
        topLeft      = Offset(cx - w / 2f, top),
        size         = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f, w / 2f)
    )
    val cradle = s * 0.25f
    drawArc(
        color      = tint,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter  = false,
        topLeft    = Offset(cx - cradle, top + h - cradle * 1.05f),
        size       = Size(cradle * 2f, cradle * 2f),
        style      = Stroke(width = stroke.width * 0.8f, cap = StrokeCap.Round)
    )
    val standTop = top + h - cradle * 0.05f + cradle
    drawLine(tint, Offset(cx, standTop), Offset(cx, cy + s * 0.40f), stroke.width * 0.8f, StrokeCap.Round)
    drawLine(tint, Offset(cx - s * 0.14f, cy + s * 0.40f), Offset(cx + s * 0.14f, cy + s * 0.40f),
        stroke.width * 0.8f, StrokeCap.Round)
}
