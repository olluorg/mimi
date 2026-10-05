package org.ollu.mini.renderer

import androidx.compose.ui.graphics.Color
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot

/**
 * What a tile shows, decided from its properties alone. Kept apart from the
 * drawing so the many fallbacks — memory cards, shapes, swatches, baskets —
 * can be read in one place.
 */
internal data class TileFace(
    val cardState:   String?,
    val emoji:       String?,
    val label:       String?,
    val imagePath:   String?,
    val dotCount:    Int?,
    val pictogram:   Pictogram?,
    val swatchColor: Color?,
    val drawnShape:  String?
) {
    val hidden: Boolean get() = cardState == "hidden"
}

internal fun tileFaceOf(entity: EntitySnapshot): TileFace {
    val cardState = entity.text("cardState")
    val hidden    = cardState == "hidden"
    val faceUp    = cardState == "revealed" || cardState == "matched"
    val emoji = when {
        hidden -> null
        faceUp -> entity.text("face") ?: entity.text("emoji")
        else   -> entity.text("emoji")
    }
    val label = when {
        hidden -> null
        faceUp -> entity.text("faceLabel") ?: entity.text("label")
        else   -> entity.text("label")
    }
    // A scene may ship a picture instead of an emoji — a photo of the child's
    // own cup reads as that cup, where a generic glyph does not.
    // A memory card's picture, shown only once it is turned over.
    val imagePath = when {
        hidden -> null
        faceUp -> entity.text("faceImage") ?: entity.text("image")
        else   -> entity.text("image")
    }

    // A tile that describes itself as a shape or a plain colour is drawn from
    // those properties. Emoji were standing in for both, which the web build
    // cannot render at all and which lost the colour the scene actually meant.
    val shapeName   = entity.text("shape")?.lowercase()?.takeIf { it in DRAWABLE_SHAPES }
    val swatchColor = namedColor(entity.text("color"))
    val drawnShape  = when {
        shapeName != null -> shapeName
        swatchColor != null && imagePath == null && emoji.isColourSwatch() -> "circle"
        else -> null
    }
    return TileFace(
        cardState   = cardState,
        emoji       = emoji,
        label       = label,
        imagePath   = imagePath,
        // A tile that states a quantity draws that many dots.
        dotCount    = entity.num("count")?.toInt()?.takeIf { it in 1..12 },
        // Pictures the card library has no card for, drawn in place.
        pictogram   = pictogramNamed(entity.text("pictogram")),
        swatchColor = swatchColor,
        drawnShape  = drawnShape
    )
}

internal data class TileColors(val background: Color, val border: Color)

/** The plate and frame of a tile; null for a state that is not drawn at all. */
internal fun tileColorsOf(
    entity:        EntitySnapshot,
    face:          TileFace,
    isPressed:     Boolean,
    isDestination: Boolean
): TileColors? {
    val isDragTarget = entity.hasComponent("dropTarget")
    val entityColor  = entity.text("color")
    val basketImage  = isDragTarget && entity.text("image") != null
    val pictureCard  = entity.state == EntityLifecycleState.ACTIVE && (face.imagePath != null || face.pictogram != null)

    val background = when {
        isDragTarget && entityColor != null -> targetTint(entityColor).copy(alpha = 0.22f)
        // A basket that draws itself needs no tinted plate behind it.
        basketImage -> Color.Transparent
        isDragTarget -> Color(0x33795548)          // semi-transparent brown fill
        face.cardState == "hidden"                     -> Color(0xFF6D8EC7)
        face.cardState == "revealed"                   -> Color(0xFFFFF3C4)
        face.cardState == "matched"                    -> Color(0xFF66BB6A)
        // A picture card is white, so the margin around the picture reads as a
        // margin and not as a blue frame of uneven width.
        pictureCard                                    -> Color.White
        entity.state == EntityLifecycleState.ACTIVE    -> Color(0xFF5C9FD4)
        entity.state == EntityLifecycleState.TENTATIVE -> Color(0xFF66BB6A)
        entity.state == EntityLifecycleState.LOCKED    -> Color(0xFFB0BEC5)
        else -> return null
    }
    val border = when {
        isPressed || isDestination -> HINT_ACCENT
        basketImage -> Color.Transparent
        isDragTarget && entityColor != null -> targetTint(entityColor)
        isDragTarget -> Color(0xFF795548)
        pictureCard -> Color(0xFF5C9FD4)
        entity.state == EntityLifecycleState.TENTATIVE -> Color(0xFF388E3C)
        entity.state == EntityLifecycleState.LOCKED    -> Color(0xFF90A4AE)
        else -> Color.Transparent
    }
    return TileColors(background, border)
}

/**
 * What this tile is, in words.
 *
 * Emoji are the whole visual vocabulary of these scenes, so without a spoken
 * name a tile is unreachable for a child using a screen reader or an adult
 * reading the screen aloud. Falls back through the explicit label, the entity's
 * name property, then its id — never to a bare emoji codepoint.
 */
internal fun describeEntity(entity: EntitySnapshot, emoji: String?, label: String?): String =
    label
        ?: entity.text("name")
        ?: entity.text("title")
        ?: emoji
        ?: entity.id.replace('_', ' ')

/** Whether the tile is still in play, already placed, or finished with. */
internal fun describeState(entity: EntitySnapshot, cardState: String?): String? = when {
    cardState == "hidden"   -> "закрыто"
    cardState == "revealed" -> "открыто"
    cardState == "matched"  -> "найдена пара"
    entity.state == EntityLifecycleState.TENTATIVE -> "на месте"
    entity.state == EntityLifecycleState.LOCKED    -> "готово"
    else -> null
}

/**
 * Whether this glyph was only ever a coloured blob — a filled square or circle
 * used because the scene had no other way to say "a red thing".
 */
private fun String?.isColourSwatch(): Boolean {
    val glyph = this?.trim() ?: return true      // no glyph at all: the colour is the content
    if (glyph.isEmpty()) return true
    return glyph.all { it in COLOUR_SWATCH_GLYPHS }
}

private const val COLOUR_SWATCH_GLYPHS =
    "🔴🔵🟡🟢🟠🟣" +
        "🟤⚫⚪🟥🟦🟨🟩" +
        "🟧🟪🟫⬛⬜️"

/**
 * Readable ink for a given tile colour. Perceived brightness, not a plain
 * average: a saturated yellow is far brighter to the eye than its RGB mean.
 */
internal fun inkOn(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    // Tiles are drawn over the scene's cream ground, so a translucent fill reads
    // lighter than its own colour suggests.
    val effective = luminance * background.alpha + 0.95f * (1f - background.alpha)
    return if (effective > 0.55f) Color(0xFF243142) else Color.White
}

/** The colour that says "here" — used for both ends of a hint. */
internal val HINT_ACCENT = Color(0xFFFFA726)

private fun targetTint(color: String): Color = when (color) {
    "red"    -> Color(0xFFE53935)
    "blue"   -> Color(0xFF1E88E5)
    "yellow" -> Color(0xFFFBC02D)
    "green"  -> Color(0xFF43A047)
    else     -> Color(0xFF795548)
}
