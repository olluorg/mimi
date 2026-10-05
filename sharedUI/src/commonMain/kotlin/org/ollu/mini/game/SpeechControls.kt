package org.ollu.mini.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import mimi.core.behavior.GameEvent
import mimi.core.behaviors.AwaitResponseBehavior
import mimi.core.engine.EngineSession
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import mimi.core.model.PropertyValue
import org.ollu.mini.renderer.CatalogAnimation
import org.ollu.mini.renderer.CoordinateMapper
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph
import org.ollu.mini.voice.PlaybackGuard
import org.ollu.mini.voice.VoiceAnalysis
import org.ollu.mini.voice.VoiceDetector
import org.ollu.mini.voice.rememberMicrophoneLevels

private val SPEECH_GREEN = Color(0xFF43A047)

/** How long the adult's tip stays up before it gets out of the child's way. */
private const val TIP_VISIBLE_MS = 15_000L

private enum class MicStatus { OFF, LISTENING, UNAVAILABLE }

/**
 * One sentence for the adult, at the top of a speech game.
 *
 * The research on children who barely talk is clear that most of the effect
 * comes from the adult beside them — modelling the word, then waiting. A game
 * can set that up, but only if the adult knows it is their turn too.
 */
@Composable
internal fun BoxScope.AdultTip(tip: String) {
    var visible by remember(tip) { mutableStateOf(true) }
    LaunchedEffect(tip) {
        delay(TIP_VISIBLE_MS)
        visible = false
    }
    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(300)),
        exit     = fadeOut(tween(500)),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 84.dp, start = 16.dp, end = 16.dp)
    ) {
        Surface(
            onClick = { visible = false },
            shape   = RoundedCornerShape(14.dp),
            color   = Color(0xFFFFF4D8),
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Text(
                "Взрослому: $tip",
                style    = MaterialTheme.typography.bodyMedium,
                color    = Color(0xFF6B4E00),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * The microphone and the adult's button, for scenes that listen.
 *
 * The microphone is open only while such a scene is on screen and not yet
 * finished, and only its loudness is read. Where there is no microphone, or
 * permission was refused, the game carries on through the adult's button.
 */
@Composable
internal fun BoxScope.SpeechControls(
    session: EngineSession,
    scope:   CoroutineScope,
    active:  Boolean,
    /** The adult's microphone sensitivity, 0..1. */
    sensitivity: Float = 0.5f
) {
    val mic = rememberMicrophoneLevels()
    var status by remember(session) { mutableStateOf(if (mic.isSupported) MicStatus.OFF else MicStatus.UNAVAILABLE) }
    var level  by remember(session) { mutableStateOf(0f) }

    DisposableEffect(session, active) {
        if (active && mic.isSupported) {
            val detector = VoiceDetector.forSensitivity(sensitivity)
            status = MicStatus.LISTENING
            mic.start(
                onFrame = { samples, rate ->
                    // Analysed here, on the microphone's own thread where it
                    // has one, so the interface never waits on the maths.
                    val frame = VoiceAnalysis.analyze(samples, samples.size, rate)
                    scope.launch {
                        val now   = PlaybackGuard.nowMs()
                        val muted = PlaybackGuard.isPlaying(now)
                        level = if (muted) 0f else frame.loudness
                        for (out in detector.feed(frame, now, muted)) {
                            when (out) {
                                is VoiceDetector.Output.Level -> session.dispatch(
                                    GameEvent.VoiceLevel(level = out.level, tick = 0L)
                                )
                                is VoiceDetector.Output.Utterance -> session.dispatch(
                                    GameEvent.Vocalization(
                                        durationMs = out.durationMs, peak = out.peak, sound = out.vowel, tick = 0L
                                    )
                                )
                            }
                        }
                    }
                },
                onUnavailable = { scope.launch { status = MicStatus.UNAVAILABLE; level = 0f } }
            )
        }
        onDispose {
            mic.stop()
            level = 0f
        }
    }

    if (!active) return

    // Under the card, in the scene's own coordinates, so it lands in the same
    // place on every screen: big enough for a child to see it listening, and
    // clear of both the card above and the progress dots along the bottom.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val mapper = with(density) { CoordinateMapper(Size(maxWidth.toPx(), maxHeight.toPx())) }
        val centre = mapper.logicalToScreen(PropertyValue.Vec2(500f, MIC_CENTRE_Y))
        val micSize = with(density) { mapper.logicalLengthToScreen(MIC_SIZE).toDp() }.coerceAtLeast(MIN_MIC_SIZE)
        val centreX = with(density) { centre.x.toDp() }
        val centreY = with(density) { centre.y.toDp() }

        // The mouth that shows the word, beside the microphone that listens
        // for it: the child sees the shape, then answers into it.
        ModelledMouth(
            session  = session,
            size     = micSize * 1.15f,
            modifier = Modifier.offset(
                x = (centreX - micSize / 2 - micSize * 1.3f).coerceAtLeast(8.dp),
                y = centreY - micSize * 0.6f
            )
        )
        Box(Modifier.offset(x = centreX - micSize / 2, y = centreY - micSize / 2)) {
            MicIndicator(status, level, micSize)
        }
        Box(
            Modifier
                .offset(
                    x = (centreX + micSize / 2 + 20.dp).coerceAtMost(maxWidth - ADULT_BUTTON_WIDTH),
                    y = centreY - 28.dp
                )
        ) {
            AdultConfirmButton { scope.launch { session.confirmSpeechAttempt() } }
        }
    }
}

/** Where the microphone sits in the 1000×1000 scene: centred under the card. */
private const val MIC_CENTRE_Y = 770f
private const val MIC_SIZE = 170f
private val MIN_MIC_SIZE = 88.dp
private val ADULT_BUTTON_WIDTH = 132.dp

/**
 * The lips saying the word, while the game says it.
 *
 * Seeing the movement is the strongest cue there is for a child who cannot
 * plan it — in speech therapy for apraxia the child watches the adult's mouth
 * and copies it. A scene supplies the picture per word (`mouth`), and it
 * plays again every time the word is modelled again.
 */
@Composable
private fun ModelledMouth(session: EngineSession, size: Dp, modifier: Modifier) {
    val snapshot by session.worldState.collectAsState()
    val scene = snapshot.scene()
    val current = scene?.text(AwaitResponseBehavior.KEY_CURRENT)
    val phase = scene?.text(AwaitResponseBehavior.KEY_PHASE)
    val since = scene?.num(AwaitResponseBehavior.KEY_SINCE)
    val mouth = current?.let { snapshot.getEntity(it)?.text("mouth") }
    if (mouth.isNullOrBlank() || phase != AwaitResponseBehavior.PHASE_WAIT) return
    // Keyed on the moment the word was spoken, so a second model replays it.
    key(current, since) {
        Box(modifier.size(size)) {
            // Shown a few times over, with a pause between: a child who
            // looks up late still catches the shape of the word.
            CatalogAnimation(mouth, Modifier.fillMaxSize(), iterations = 3)
        }
    }
}

/** Shows the child the microphone is listening — and the adult that it is not. */
@Composable
private fun MicIndicator(status: MicStatus, level: Float, size: androidx.compose.ui.unit.Dp) {
    val ring by animateFloatAsState(
        targetValue   = if (status == MicStatus.LISTENING) 1f + level * 0.35f else 1f,
        animationSpec = tween(90),
        label         = "micRing"
    )
    val colour = when (status) {
        MicStatus.LISTENING   -> SPEECH_GREEN
        MicStatus.OFF         -> Color(0xFF9E9E9E)
        MicStatus.UNAVAILABLE -> Color(0xFFBDBDBD)
    }
    Box(
        modifier = Modifier
            .size(size)
            .semantics {
                contentDescription = when (status) {
                    MicStatus.LISTENING   -> "Микрофон слушает"
                    MicStatus.OFF         -> "Микрофон выключен"
                    MicStatus.UNAVAILABLE -> "Микрофон недоступен — используйте кнопку «Сказал»"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // The ring swells with the child's voice: seeing it move is what tells
        // them their sound is doing something.
        Box(
            Modifier
                .size(size)
                .scale(ring)
                .background(colour.copy(alpha = 0.22f), CircleShape)
        )
        Box(
            Modifier.size(size * 0.8f).background(Color.White, CircleShape).border(4.dp, colour, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AppIconGlyph(AppIcon.Mic, colour, size = size * 0.5f)
        }
    }
}

/**
 * "The child said it" — held, not tapped.
 *
 * The adult hears a whispered «ма» the microphone cannot separate from the
 * room, and on a device without a microphone this button is the only way to
 * answer. A long press keeps a toddler's stray taps from answering for them.
 */
@Composable
private fun AdultConfirmButton(onConfirm: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash) { delay(600); flash = false }
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (flash) SPEECH_GREEN else Color.White.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
        modifier = Modifier
            .semantics { contentDescription = "Ребёнок сказал — удерживайте" }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    flash = true
                    onConfirm()
                })
            }
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Сказал(а)!",
                fontWeight = FontWeight.SemiBold,
                color = if (flash) Color.White else SPEECH_GREEN
            )
            Text(
                "удерживайте",
                fontSize = 11.sp,
                color = if (flash) Color.White else Color(0xFF8B96A3)
            )
        }
    }
}
