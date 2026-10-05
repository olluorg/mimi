package org.ollu.mini.renderer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import kotlin.math.roundToInt

private val CORNER = 16.dp

@Composable
fun EntityRenderer(
    entity:          EntitySnapshot,
    mapper:          CoordinateMapper,
    dragOverridePos: PropertyValue.Vec2? = null,
    isHinted:        Boolean             = false,
    /** Where the hinted piece belongs; drawn as a steady ring rather than a pulse. */
    isDestination:   Boolean             = false,
    /** Touched a moment ago: every tap gets an answer, even on a tile that never changes state. */
    isPressed:       Boolean             = false,
    onTap:           (() -> Unit)?       = null
) {
    val pos = dragOverridePos ?: entity.vec2("position") ?: return
    if (entity.state == EntityLifecycleState.INACTIVE) return

    // The hint pulses a ring around the tile rather than the tile itself. Scaling
    // a piece the child is trying to grab moves the target under their finger,
    // which made the hinted item the hardest one on screen to pick up.
    val hintRing by if (isHinted) {
        rememberInfiniteTransition(label = "hint")
            .animateFloat(
                initialValue    = 0.25f,
                targetValue     = 1f,
                animationSpec   = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                label           = "hintRing"
            )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) 1.16f else 1f,
        animationSpec = tween(140),
        label         = "press"
    )
    // ── The reward: a card comes alive when the child gets it right ─────────
    // Detected here, from the state change, so every game gets it — the
    // scene says only how this card moves (`rewardMotion`), if at all.
    var wasState     by remember(entity.id) { mutableStateOf(entity.state) }
    var celebrations by remember(entity.id) { mutableStateOf(0) }
    var bonus        by remember(entity.id) { mutableStateOf(false) }
    val motion       = remember(entity.id) { Animatable(0f) }
    val rewardMotion = RewardMotion.named(entity.text("rewardMotion"))
    LaunchedEffect(entity.state) {
        val justWon = entity.state == EntityLifecycleState.TENTATIVE && wasState != EntityLifecycleState.TENTATIVE
        wasState = entity.state
        if (justWon) {
            bonus = entity.text("celebrate") == "bonus"
            celebrations++
            if (rewardMotion != null) {
                motion.snapTo(0f)
                motion.animateTo(1f, tween(REWARD_MOTION_MS, easing = LinearEasing))
            }
        }
    }
    val pose = rewardMotion?.poseAt(motion.value) ?: Pose.REST

    val isDragging   = dragOverridePos != null
    val isDragTarget = entity.hasComponent("dropTarget")
    val cardState    = entity.text("cardState")
    val entityColor  = entity.text("color")
    val faceUp       = cardState == "revealed" || cardState == "matched"
    val emoji        = when {
        cardState == "hidden" -> null
        faceUp                -> entity.text("face") ?: entity.text("emoji")
        else                  -> entity.text("emoji")
    }
    val label        = if (faceUp) entity.text("faceLabel") ?: entity.text("label")
                       else if (cardState == "hidden") null
                       else entity.text("label")
    // A scene may ship a picture instead of an emoji — a photo of the child's
    // own cup reads as that cup, where a generic glyph does not.
    // A memory card's picture, shown only once it is turned over.
    val imagePath    = when {
        cardState == "hidden" -> null
        faceUp                -> entity.text("faceImage") ?: entity.text("image")
        else                  -> entity.text("image")
    }

    // A tile that describes itself as a shape or a plain colour is drawn from
    // those properties. Emoji were standing in for both, which the web build
    // cannot render at all and which lost the colour the scene actually meant.
    val shapeName    = entity.text("shape")?.lowercase()?.takeIf { it in DRAWABLE_SHAPES }
    // A tile that states a quantity draws that many dots.
    val dotCount     = entity.num("count")?.toInt()?.takeIf { it in 1..12 }
    // Pictures the card library has no card for, drawn in place.
    val pictogram    = pictogramNamed(entity.text("pictogram"))
    val swatchColor  = namedColor(entityColor)
    val drawnShape   = when {
        shapeName != null -> shapeName
        swatchColor != null && imagePath == null && emoji.isColourSwatch() -> "circle"
        else -> null
    }
    val targetPos    = mapper.logicalToScreen(pos)
    // A piece moved by the game rather than a finger — a rocket lifted by the
    // child's voice — glides between positions instead of jumping, because it
    // is updated a few times a second and would otherwise stutter.
    val glides       = entity.flag("glide") == true && dragOverridePos == null
    val glideX by animateFloatAsState(targetPos.x, if (glides) tween(180) else snap(), label = "glideX")
    val glideY by animateFloatAsState(targetPos.y, if (glides) tween(180) else snap(), label = "glideY")
    val screenPos    = if (glides) androidx.compose.ui.geometry.Offset(glideX, glideY) else targetPos

    val bgColor = when {
        isDragTarget && entityColor != null -> targetTint(entityColor).copy(alpha = 0.22f)
        // A basket that draws itself needs no tinted plate behind it.
        isDragTarget && entity.text("image") != null -> Color.Transparent
        isDragTarget -> Color(0x33795548)          // semi-transparent brown fill
        cardState == "hidden"                          -> Color(0xFF6D8EC7)
        cardState == "revealed"                        -> Color(0xFFFFF3C4)
        cardState == "matched"                         -> Color(0xFF66BB6A)
        // A picture card is white, so the margin around the picture reads as a
        // margin and not as a blue frame of uneven width.
        entity.state == EntityLifecycleState.ACTIVE && (imagePath != null || pictogram != null) -> Color.White
        entity.state == EntityLifecycleState.ACTIVE    -> Color(0xFF5C9FD4)
        entity.state == EntityLifecycleState.TENTATIVE -> Color(0xFF66BB6A)
        entity.state == EntityLifecycleState.LOCKED    -> Color(0xFFB0BEC5)
        else -> return
    }
    val borderColor = when {
        isPressed     -> HINT_ACCENT
        isDestination -> HINT_ACCENT
        isDragTarget && entity.text("image") != null -> Color.Transparent
        isDragTarget && entityColor != null -> targetTint(entityColor)
        isDragTarget -> Color(0xFF795548)
        entity.state == EntityLifecycleState.ACTIVE && (imagePath != null || pictogram != null) && !isDragTarget -> Color(0xFF5C9FD4)
        entity.state == EntityLifecycleState.TENTATIVE -> Color(0xFF388E3C)
        entity.state == EntityLifecycleState.LOCKED    -> Color(0xFF90A4AE)
        else -> Color.Transparent
    }

    // Sized in the scene's own units and scaled like everything else, so what is
    // drawn and what responds to a finger are the same square at any window size.
    val density = LocalDensity.current
    val tileSide = with(density) { mapper.logicalLengthToScreen(TileMetrics.sizeOf(entity)).toDp() }
    val tileHeight = entity.num("height")?.toFloat()
        ?.let { with(density) { mapper.logicalLengthToScreen(it).toDp() } }
    val sizeModifier = if (tileHeight != null) Modifier.size(tileSide, tileHeight)
                       else Modifier.size(tileSide)
    // Contents are a fraction of the tile, so they grow and shrink with it.
    val contentSide = tileSide * 0.76f

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
            // After placement, so the layer sits on the tile and a press grows it
            // around its own centre. Before the offset, the layer was anchored at
            // the board's corner and a pressed card jumped across the screen.
            .graphicsLayer {
                scaleX = pressScale * pose.scale
                scaleY = pressScale * pose.scale
                translationX = mapper.logicalLengthToScreen(pose.dx)
                translationY = mapper.logicalLengthToScreen(pose.dy)
                rotationZ = pose.rotation
            }
            .then(sizeModifier)
            .background(bgColor, RoundedCornerShape(CORNER))
            .border(
                width = when {
                    isHinted                  -> (3 + 4 * hintRing).dp
                    isDestination || isPressed -> 6.dp
                    else                      -> 3.dp
                },
                color = if (isHinted) HINT_ACCENT.copy(alpha = 0.45f + 0.55f * hintRing) else borderColor,
                shape = RoundedCornerShape(CORNER)
            )
            // After the border, so only the contents are clipped. Canvas paints
            // outside its own bounds quite happily: one card back's pattern once
            // spread across half the board.
            .clip(RoundedCornerShape(CORNER))
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .semantics {
                contentDescription = describeEntity(entity, emoji, label)
                describeState(entity, cardState)?.let { stateDescription = it }
            },
        contentAlignment = Alignment.Center
    ) {
        // The Box above carries the description; without this a screen reader
        // would also read the raw emoji codepoint after it.
        Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        // Ink is chosen against the tile it sits on. It used to be white
        // everywhere, which on the pale drop targets left digits and dot-groups
        // effectively invisible — and those dots are the puzzle, not a caption.
        val ink     = inkOn(bgColor)
        // Everything on a tile is a picture, set in the same square with the same
        // margin on every side. There are no captions: the children these games
        // are for do not read, a word under a picture was noise to them, and it
        // pushed the picture off-centre and out of its card.
        val picture = Modifier.size(tileSide * PICTURE_FRACTION)

        when {
            // Face down: a drawn back, so the child sees a card rather than a
            // question mark that means nothing to a non-reader.
            cardState == "hidden" ->
                CardBack(Modifier.size(contentSide))

            pictogram != null -> PictogramTile(pictogram = pictogram, modifier = picture)

            dotCount != null -> DotsTile(count = dotCount, color = swatchColor ?: ink, modifier = picture)

            drawnShape != null -> ShapeTile(
                shape    = drawnShape,
                color    = swatchColor ?: Color.White,
                // A drop target is the hollow outline the piece is meant to fill.
                outline  = isDragTarget,
                modifier = picture
            )

            // A basket holds its example rather than being it, so a sorting game
            // does not present the child with two identical cats.
            imagePath != null && isDragTarget ->
                ContainerTile(Modifier.size(tileSide * 0.94f)) {
                    CatalogImage(imagePath, contentDescription = null, modifier = Modifier.size(tileSide * 0.42f))
                }

            imagePath != null -> CatalogImage(path = imagePath, contentDescription = null, modifier = picture)

            // A letter or a digit that is itself the thing being learned — "А",
            // "3". Anything longer is a word, which a child here cannot read, so
            // it is not drawn; validation keeps such tiles out of the catalog.
            else -> {
                val content = (emoji ?: label)?.trim().orEmpty()
                if (content.isNotEmpty() && content.length <= 2) {
                    Text(
                        text       = content,
                        color      = ink,
                        fontSize   = bigTextSize(content),
                        fontWeight = FontWeight.SemiBold,
                        textAlign  = TextAlign.Center,
                        lineHeight = bigTextSize(content) * 1.05f
                    )
                }
            }
        }
        }
    }

    // Stars bursting out over the card — above everything, never clipped by
    // the tile, and gone again by the time the next turn begins.
    if (celebrations > 0) {
        val burstSide = tileSide * if (bonus) 3.2f else 2.4f
        key(celebrations) {
            Box(
                Modifier
                    .zIndex(30f)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(
                                IntOffset(
                                    (screenPos.x - placeable.width / 2f).roundToInt(),
                                    (screenPos.y - placeable.height / 2f).roundToInt()
                                )
                            )
                        }
                    }
                    .size(burstSide)
            ) {
                CatalogAnimation(if (bonus) BONUS_ANIMATION else BURST_ANIMATION, Modifier.fillMaxSize())
            }
        }
    }
}

/** How much of its card a picture fills: the rest is an even margin on every side. */
private const val PICTURE_FRACTION = 0.84f

/**
 * What this tile is, in words.
 *
 * Emoji are the whole visual vocabulary of these scenes, so without a spoken
 * name a tile is unreachable for a child using a screen reader or an adult
 * reading the screen aloud. Falls back through the explicit label, the entity's
 * name property, then its id — never to a bare emoji codepoint.
 */
private fun describeEntity(entity: EntitySnapshot, emoji: String?, label: String?): String =
    label
        ?: entity.text("name")
        ?: entity.text("title")
        ?: emoji
        ?: entity.id.replace('_', ' ')

/** Whether the tile is still in play, already placed, or finished with. */
private fun describeState(entity: EntitySnapshot, cardState: String?): String? = when {
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
    "\uD83D\uDD34\uD83D\uDD35\uD83D\uDFE1\uD83D\uDFE2\uD83D\uDFE0\uD83D\uDFE3" +
        "\uD83D\uDFE4\u26AB\u26AA\uD83D\uDFE5\uD83D\uDFE6\uD83D\uDFE8\uD83D\uDFE9" +
        "\uD83D\uDFE7\uD83D\uDFEA\uD83D\uDFEB\u2B1B\u2B1C\uFE0F"

/**
 * Readable ink for a given tile colour. Perceived brightness, not a plain
 * average: a saturated yellow is far brighter to the eye than its RGB mean.
 */
private fun inkOn(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    // Tiles are drawn over the scene's cream ground, so a translucent fill reads
    // lighter than its own colour suggests.
    val effective = luminance * background.alpha + 0.95f * (1f - background.alpha)
    return if (effective > 0.55f) Color(0xFF243142) else Color.White
}

/** Text standing in for a picture is sized to fill the tile, not to caption it. */
private fun bigTextSize(text: String): TextUnit = when {
    text.length <= 2  -> 46.sp
    text.length <= 4  -> 34.sp
    text.length <= 8  -> 26.sp
    else              -> 19.sp
}

/** The colour that says "here" — used for both ends of a hint. */
private val HINT_ACCENT = Color(0xFFFFA726)

private fun targetTint(color: String): Color = when (color) {
    "red"    -> Color(0xFFE53935)
    "blue"   -> Color(0xFF1E88E5)
    "yellow" -> Color(0xFFFBC02D)
    "green"  -> Color(0xFF43A047)
    else     -> Color(0xFF795548)
}
