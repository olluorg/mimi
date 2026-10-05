package org.ollu.mini.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/**
 * Pictures the card library does not have, drawn rather than fetched.
 *
 * The PECS set covers objects a child points at — food, animals, clothes — but
 * not parts of their own face, and no icon pack covers them either: Font Awesome
 * has an eye and an accessibility ear, no nose and no mouth. Emoji had them and
 * are unusable, because the web build ships one font with no glyphs for them.
 *
 * So these are drawn. A scene names one with `pictogram: "nose"`, and it renders
 * identically on every platform, in colour, at any size. Adding another is a
 * dozen lines here rather than a dependency.
 */
enum class Pictogram { NOSE, EYE, MOUTH, EAR, HAND, FOOT, FAUCET }

private val PICTOGRAM_BY_NAME = mapOf(
    "nose" to Pictogram.NOSE,
    "eye"  to Pictogram.EYE,
    "mouth" to Pictogram.MOUTH,
    "ear"  to Pictogram.EAR,
    "hand" to Pictogram.HAND,
    "foot" to Pictogram.FOOT,
    "faucet" to Pictogram.FAUCET
)

fun pictogramNamed(name: String?): Pictogram? = name?.let { PICTOGRAM_BY_NAME[it.lowercase()] }

/** Warm, friendly tones — these are pictures of a child's own face. */
private val SKIN    = Color(0xFFF0BE9C)
private val OUTLINE = Color(0xFF8C5A3C)
private val LIP     = Color(0xFFE0736F)
private val IRIS    = Color(0xFF5B8FB9)
private val WHITE   = Color(0xFFFDFBF7)
private val PUPIL   = Color(0xFF2E2A28)

@Composable
fun PictogramTile(pictogram: Pictogram, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = min(size.width, size.height)
        when (pictogram) {
            Pictogram.NOSE  -> drawNose(s)
            Pictogram.EYE   -> drawEye(s)
            Pictogram.MOUTH -> drawMouth(s)
            Pictogram.EAR   -> drawEar(s)
            Pictogram.HAND  -> drawHand(s)
            Pictogram.FOOT  -> drawFoot(s)
            Pictogram.FAUCET -> drawFaucet(s)
        }
    }
}

private fun DrawScope.drawNose(s: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.42f
    val h = s * 0.56f
    // A front-on nose: a soft wedge widening to the nostrils.
    val body = Path().apply {
        moveTo(cx, cy - h / 2f)
        cubicTo(cx + w * 0.22f, cy - h * 0.10f, cx + w * 0.55f, cy + h * 0.18f, cx + w * 0.44f, cy + h * 0.38f)
        cubicTo(cx + w * 0.20f, cy + h * 0.56f, cx - w * 0.20f, cy + h * 0.56f, cx - w * 0.44f, cy + h * 0.38f)
        cubicTo(cx - w * 0.55f, cy + h * 0.18f, cx - w * 0.22f, cy - h * 0.10f, cx, cy - h / 2f)
        close()
    }
    drawPath(body, SKIN)
    drawPath(body, OUTLINE, style = Stroke(s * 0.05f))
    // Nostrils
    drawOval(OUTLINE, Offset(cx - w * 0.30f, cy + h * 0.24f), Size(w * 0.22f, h * 0.13f))
    drawOval(OUTLINE, Offset(cx + w * 0.08f, cy + h * 0.24f), Size(w * 0.22f, h * 0.13f))
}

private fun DrawScope.drawEye(s: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.62f
    val h = s * 0.34f
    val almond = Path().apply {
        moveTo(cx - w / 2f, cy)
        cubicTo(cx - w * 0.28f, cy - h, cx + w * 0.28f, cy - h, cx + w / 2f, cy)
        cubicTo(cx + w * 0.28f, cy + h, cx - w * 0.28f, cy + h, cx - w / 2f, cy)
        close()
    }
    drawPath(almond, WHITE)
    drawCircle(IRIS, h * 0.62f, Offset(cx, cy))
    drawCircle(PUPIL, h * 0.28f, Offset(cx, cy))
    drawPath(almond, OUTLINE, style = Stroke(s * 0.055f))
}

private fun DrawScope.drawMouth(s: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.62f
    val h = s * 0.30f
    // A closed, smiling mouth: two peaks on top, one curve below.
    val lips = Path().apply {
        moveTo(cx - w / 2f, cy)
        cubicTo(cx - w * 0.30f, cy - h * 0.80f, cx - w * 0.08f, cy - h * 0.34f, cx, cy - h * 0.16f)
        cubicTo(cx + w * 0.08f, cy - h * 0.34f, cx + w * 0.30f, cy - h * 0.80f, cx + w / 2f, cy)
        cubicTo(cx + w * 0.30f, cy + h * 0.95f, cx - w * 0.30f, cy + h * 0.95f, cx - w / 2f, cy)
        close()
    }
    drawPath(lips, LIP)
    drawPath(lips, OUTLINE, style = Stroke(s * 0.045f))
    drawLine(OUTLINE, Offset(cx - w / 2f, cy), Offset(cx + w / 2f, cy), s * 0.04f, StrokeCap.Round)
}

private fun DrawScope.drawEar(s: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.44f
    val h = s * 0.60f
    val outer = Path().apply {
        moveTo(cx + w * 0.30f, cy + h * 0.42f)
        cubicTo(cx - w * 0.60f, cy + h * 0.40f, cx - w * 0.62f, cy - h * 0.52f, cx + w * 0.12f, cy - h * 0.46f)
        cubicTo(cx + w * 0.64f, cy - h * 0.42f, cx + w * 0.52f, cy + h * 0.10f, cx + w * 0.30f, cy + h * 0.42f)
        close()
    }
    drawPath(outer, SKIN)
    drawPath(outer, OUTLINE, style = Stroke(s * 0.055f))
    // The inner fold, which is what makes it read as an ear rather than a bean.
    val inner = Path().apply {
        moveTo(cx + w * 0.10f, cy + h * 0.20f)
        cubicTo(cx - w * 0.22f, cy + h * 0.12f, cx - w * 0.24f, cy - h * 0.24f, cx + w * 0.06f, cy - h * 0.22f)
    }
    drawPath(inner, OUTLINE, style = Stroke(s * 0.05f, cap = StrokeCap.Round))
}

private fun DrawScope.drawHand(s: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val palmW = s * 0.40f
    val palmH = s * 0.34f
    drawRoundRectPath(cx - palmW / 2f, cy - palmH * 0.10f, palmW, palmH, s * 0.10f, SKIN, OUTLINE, s * 0.05f)
    val fingerW = palmW * 0.20f
    for (i in 0 until 4) {
        val x = cx - palmW / 2f + palmW * (0.08f + i * 0.25f)
        val len = palmH * (if (i == 1 || i == 2) 0.92f else 0.74f)
        drawRoundRectPath(x, cy - palmH * 0.10f - len, fingerW, len, fingerW / 2f, SKIN, OUTLINE, s * 0.045f)
    }
    // Thumb
    drawRoundRectPath(cx + palmW * 0.44f, cy + palmH * 0.02f, palmH * 0.46f, fingerW,
        fingerW / 2f, SKIN, OUTLINE, s * 0.045f)
}

private fun DrawScope.drawFoot(s: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = s * 0.40f
    val h = s * 0.54f
    val sole = Path().apply {
        moveTo(cx - w * 0.36f, cy + h * 0.40f)
        cubicTo(cx - w * 0.62f, cy + h * 0.02f, cx - w * 0.44f, cy - h * 0.34f, cx + w * 0.04f, cy - h * 0.30f)
        cubicTo(cx + w * 0.52f, cy - h * 0.26f, cx + w * 0.46f, cy + h * 0.24f, cx + w * 0.22f, cy + h * 0.42f)
        cubicTo(cx + w * 0.04f, cy + h * 0.52f, cx - w * 0.20f, cy + h * 0.52f, cx - w * 0.36f, cy + h * 0.40f)
        close()
    }
    drawPath(sole, SKIN)
    drawPath(sole, OUTLINE, style = Stroke(s * 0.055f))
    for (i in 0 until 4) {
        val r = s * (0.045f - i * 0.005f)
        drawCircle(SKIN, r, Offset(cx - w * 0.22f + w * 0.24f * i, cy - h * 0.40f))
        drawCircle(OUTLINE, r, Offset(cx - w * 0.22f + w * 0.24f * i, cy - h * 0.40f),
            style = Stroke(s * 0.02f))
    }
}

private fun DrawScope.drawRoundRectPath(
    x: Float, y: Float, w: Float, h: Float, radius: Float,
    fill: Color, stroke: Color, strokeWidth: Float
) {
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = x, top = y, right = x + w, bottom = y + h,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
        )
    }
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(strokeWidth))
}

private val METAL       = Color(0xFFA9B4BE)
private val METAL_DARK  = Color(0xFF6E7B87)
private val WATER       = Color(0xFF5BB3E6)

/**
 * A water tap with water running.
 *
 * Drawn because the picture generator, given «Кран», produced a truck crane —
 * the same word in Russian — and a child washing their hands was asked to
 * "turn on the water" by pressing a construction vehicle.
 */
private fun DrawScope.drawFaucet(s: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val stroke = Stroke(s * 0.035f)
    // Wall mount on the left, the spout reaching right and curving down.
    val mountLeft = cx - s * 0.36f
    drawRoundRect(METAL_DARK, Offset(mountLeft, cy - s * 0.26f), Size(s * 0.08f, s * 0.26f),
        androidx.compose.ui.geometry.CornerRadius(s * 0.02f))
    val spout = Path().apply {
        moveTo(mountLeft + s * 0.08f, cy - s * 0.20f)
        lineTo(cx + s * 0.10f, cy - s * 0.20f)
        quadraticTo(cx + s * 0.24f, cy - s * 0.20f, cx + s * 0.24f, cy - s * 0.06f)
        lineTo(cx + s * 0.24f, cy + s * 0.00f)
        lineTo(cx + s * 0.14f, cy + s * 0.00f)
        lineTo(cx + s * 0.14f, cy - s * 0.04f)
        quadraticTo(cx + s * 0.14f, cy - s * 0.10f, cx + s * 0.08f, cy - s * 0.10f)
        lineTo(mountLeft + s * 0.08f, cy - s * 0.10f)
        close()
    }
    drawPath(spout, METAL)
    drawPath(spout, METAL_DARK, style = stroke)
    // The handle on top.
    drawRoundRect(METAL_DARK, Offset(cx - s * 0.10f, cy - s * 0.30f), Size(s * 0.05f, s * 0.10f),
        androidx.compose.ui.geometry.CornerRadius(s * 0.02f))
    drawRoundRect(Color(0xFFE05A4F), Offset(cx - s * 0.20f, cy - s * 0.35f), Size(s * 0.25f, s * 0.06f),
        androidx.compose.ui.geometry.CornerRadius(s * 0.03f))
    // Running water and a few drops splashing below.
    val waterX = cx + s * 0.19f
    drawLine(WATER, Offset(waterX, cy + s * 0.02f), Offset(waterX, cy + s * 0.32f), s * 0.07f, StrokeCap.Round)
    drawCircle(WATER, s * 0.03f, Offset(waterX - s * 0.10f, cy + s * 0.34f))
    drawCircle(WATER, s * 0.025f, Offset(waterX + s * 0.10f, cy + s * 0.30f))
    drawCircle(WATER, s * 0.02f, Offset(waterX + s * 0.05f, cy + s * 0.38f))
}
