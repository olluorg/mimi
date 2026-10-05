package org.ollu.mini.renderer

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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
        remember { mutableStateOf(0f) }
    }

    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) 1.16f else 1f,
        animationSpec = tween(140),
        label         = "press"
    )
    val reward = rememberReward(entity)
    val pose   = reward.pose

    val isDragging   = dragOverridePos != null
    val isDragTarget = entity.hasComponent("dropTarget")
    val face         = tileFaceOf(entity)
    val targetPos    = mapper.logicalToScreen(pos)
    // A piece moved by the game rather than a finger — a rocket lifted by the
    // child's voice — glides between positions instead of jumping, because it
    // is updated a few times a second and would otherwise stutter.
    val glides       = entity.flag("glide") == true && dragOverridePos == null
    val glideX by animateFloatAsState(targetPos.x, if (glides) tween(180) else snap(), label = "glideX")
    val glideY by animateFloatAsState(targetPos.y, if (glides) tween(180) else snap(), label = "glideY")
    val screenPos    = if (glides) Offset(glideX, glideY) else targetPos

    val colors = tileColorsOf(entity, face, isPressed, isDestination) ?: return

    // Sized in the scene's own units and scaled like everything else, so what is
    // drawn and what responds to a finger are the same square at any window size.
    val density = LocalDensity.current
    val tileSide = with(density) { mapper.logicalLengthToScreen(TileMetrics.sizeOf(entity)).toDp() }
    val tileHeight = entity.num("height")?.toFloat()
        ?.let { with(density) { mapper.logicalLengthToScreen(it).toDp() } }
    val sizeModifier = if (tileHeight != null) Modifier.size(tileSide, tileHeight)
                       else Modifier.size(tileSide)

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 10f else (entity.num("zOrder") ?: 0.0).toFloat())
            .centredAt(screenPos)
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
            .background(colors.background, RoundedCornerShape(CORNER))
            .border(
                width = when {
                    isHinted                  -> (3 + 4 * hintRing).dp
                    isDestination || isPressed -> 6.dp
                    else                      -> 3.dp
                },
                color = if (isHinted) HINT_ACCENT.copy(alpha = 0.45f + 0.55f * hintRing) else colors.border,
                shape = RoundedCornerShape(CORNER)
            )
            // After the border, so only the contents are clipped. Canvas paints
            // outside its own bounds quite happily: one card back's pattern once
            // spread across half the board.
            .clip(RoundedCornerShape(CORNER))
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .semantics {
                contentDescription = describeEntity(entity, face.emoji, face.label)
                describeState(entity, face.cardState)?.let { stateDescription = it }
            },
        contentAlignment = Alignment.Center
    ) {
        // The Box above carries the description; without this a screen reader
        // would also read the raw emoji codepoint after it.
        Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            TileContent(face, tileSide, outline = isDragTarget, background = colors.background)
        }
    }

    // Stars bursting out over the card — above everything, never clipped by
    // the tile, and gone again by the time the next turn begins.
    if (reward.celebrations > 0) {
        val burstSide = tileSide * if (reward.bonus) 3.2f else 2.4f
        key(reward.celebrations) {
            Box(Modifier.zIndex(30f).centredAt(screenPos).size(burstSide)) {
                CatalogAnimation(if (reward.bonus) BONUS_ANIMATION else BURST_ANIMATION, Modifier.fillMaxSize())
            }
        }
    }
}

/** Everything on a tile is a picture, set in the same square with the same margin on every side. */
@Composable
private fun TileContent(face: TileFace, tileSide: Dp, outline: Boolean, background: Color) {
    // Ink is chosen against the tile it sits on. It used to be white
    // everywhere, which on the pale drop targets left digits and dot-groups
    // effectively invisible — and those dots are the puzzle, not a caption.
    val ink     = inkOn(background)
    // There are no captions: the children these games are for do not read, a
    // word under a picture was noise to them, and it pushed the picture
    // off-centre and out of its card.
    val picture = Modifier.size(tileSide * PICTURE_FRACTION)

    when {
        // Face down: a drawn back, so the child sees a card rather than a
        // question mark that means nothing to a non-reader.
        face.hidden -> CardBack(Modifier.size(tileSide * CONTENT_FRACTION))

        face.pictogram != null -> PictogramTile(pictogram = face.pictogram, modifier = picture)

        face.dotCount != null -> DotsTile(count = face.dotCount, color = face.swatchColor ?: ink, modifier = picture)

        face.drawnShape != null -> ShapeTile(
            shape    = face.drawnShape,
            color    = face.swatchColor ?: Color.White,
            // A drop target is the hollow outline the piece is meant to fill.
            outline  = outline,
            modifier = picture
        )

        // A basket holds its example rather than being it, so a sorting game
        // does not present the child with two identical cats.
        face.imagePath != null && outline ->
            ContainerTile(Modifier.size(tileSide * 0.94f)) {
                CatalogImage(face.imagePath, contentDescription = null, modifier = Modifier.size(tileSide * 0.42f))
            }

        face.imagePath != null -> CatalogImage(path = face.imagePath, contentDescription = null, modifier = picture)

        // A letter or a digit that is itself the thing being learned — "А",
        // "3". Anything longer is a word, which a child here cannot read, so
        // it is not drawn; validation keeps such tiles out of the catalog.
        else -> {
            val content = (face.emoji ?: face.label)?.trim().orEmpty()
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

/** A card's reward: how many times it has been won, and where its motion is. */
private class RewardState {
    val motion       = Animatable(0f)
    var celebrations by mutableStateOf(0)
    var bonus        by mutableStateOf(false)
    /** Set from the scene on every composition; not state, it only shapes [pose]. */
    var rewardMotion: RewardMotion? = null

    /** Read during composition, so the card redraws on every frame of the motion. */
    val pose: Pose get() = rewardMotion?.poseAt(motion.value) ?: Pose.REST
}

/**
 * The reward: a card comes alive when the child gets it right. Detected here,
 * from the state change, so every game gets it — the scene says only how this
 * card moves (`rewardMotion`), if at all.
 */
@Composable
private fun rememberReward(entity: EntitySnapshot): RewardState {
    var wasState     by remember(entity.id) { mutableStateOf(entity.state) }
    val reward       = remember(entity.id) { RewardState() }
    val rewardMotion = RewardMotion.named(entity.text("rewardMotion"))
    reward.rewardMotion = rewardMotion
    LaunchedEffect(entity.state) {
        val justWon = entity.state == EntityLifecycleState.TENTATIVE && wasState != EntityLifecycleState.TENTATIVE
        wasState = entity.state
        if (justWon) {
            reward.bonus = entity.text("celebrate") == "bonus"
            reward.celebrations++
            if (rewardMotion != null) {
                reward.motion.snapTo(0f)
                reward.motion.animateTo(1f, tween(REWARD_MOTION_MS, easing = LinearEasing))
            }
        }
    }
    return reward
}

/** Places the element so its centre, not its corner, sits on [screenPos]. */
private fun Modifier.centredAt(screenPos: Offset): Modifier = layout { measurable, constraints ->
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

/** How much of its card a picture fills: the rest is an even margin on every side. */
private const val PICTURE_FRACTION = 0.84f

/** A card back fills a little less, leaving its frame visible. */
private const val CONTENT_FRACTION = 0.76f

/** Text standing in for a picture is sized to fill the tile, not to caption it. */
private fun bigTextSize(text: String): TextUnit = when {
    text.length <= 2  -> 46.sp
    text.length <= 4  -> 34.sp
    text.length <= 8  -> 26.sp
    else              -> 19.sp
}
