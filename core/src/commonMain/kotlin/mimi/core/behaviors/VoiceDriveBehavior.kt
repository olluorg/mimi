package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

/**
 * A picture moved by the loudness and length of the child's voice.
 *
 * For dysarthria the work is the voice itself — loud enough, long enough, on
 * one breath — and for a child who does not talk yet, discovering that their
 * voice *does something* is the first step. Blowing at a screen or at cotton
 * wool trains neither; making a sound does. So the rocket climbs while the
 * child says "а-а-а", sinks slowly when they stop, and launches when it
 * reaches the top.
 *
 * The adult's "сказал(а)" launches it outright, so the game still works on a
 * device with no microphone.
 */
class VoiceDriveBehavior(
    override val id:        BehaviorId,
    private val entityId:   EntityId,
    private val fromY:      Float,
    private val toY:        Float,
    private val rise:       Float,
    private val fall:       Float,
    private val celebrateMs: Long
) : Behavior {

    override val phase      = Phase.RESOLUTION
    override val eventTypes = EVENTS

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        val entity = ctx.snapshot.getEntity(entityId) ?: return emptyList()
        val pos    = entity.vec2("position") ?: return emptyList()
        val scene  = ctx.snapshot.scene()
        val last   = scene?.num(KEY_LAST)?.toLong() ?: ctx.nowMs
        val phase  = scene?.text(KEY_PHASE)
        // A gap in the voice is not a leap: the step is capped at a quarter
        // second, the longest the detector ever goes between reports.
        val dtSec  = (ctx.nowMs - last).coerceIn(0L, MAX_STEP_MS) / 1000f

        if (phase == PHASE_LAUNCHED) {
            if (event !is GameEvent.ClockTick) return emptyList()
            val since = scene?.num(KEY_SINCE)?.toLong() ?: 0L
            if (ctx.nowMs - since < celebrateMs) return emptyList()
            return listOf(
                move(pos.x, fromY, ctx),
                setText(KEY_PHASE, PHASE_READY, ctx),
                setNum(KEY_LAST, ctx.nowMs, ctx)
            )
        }

        return when (event) {
            is GameEvent.VoiceLevel -> {
                val y = (pos.y - rise * event.level.coerceIn(0f, 1f) * dtSec).coerceAtLeast(toY)
                if (y <= toY) launch(pos.x, ctx)
                else listOf(move(pos.x, y, ctx), setNum(KEY_LAST, ctx.nowMs, ctx), setNum(KEY_VOICE, ctx.nowMs, ctx))
            }
            is GameEvent.Vocalization ->
                if (event.byAdult) launch(pos.x, ctx) else emptyList()
            is GameEvent.ClockTick -> {
                val lastVoice = scene?.num(KEY_VOICE)?.toLong() ?: 0L
                if (ctx.nowMs - lastVoice < SETTLE_MS || pos.y >= fromY) {
                    listOf(setNum(KEY_LAST, ctx.nowMs, ctx)).takeIf { scene?.num(KEY_LAST) == null }.orEmpty()
                } else {
                    val y = (pos.y + fall * dtSec).coerceAtMost(fromY)
                    listOf(move(pos.x, y, ctx), setNum(KEY_LAST, ctx.nowMs, ctx))
                }
            }
            else -> emptyList()
        }
    }

    private fun launch(x: Float, ctx: BehaviorContext): List<Command> = listOf(
        move(x, toY, ctx),
        setText(KEY_PHASE, PHASE_LAUNCHED, ctx),
        setNum(KEY_SINCE, ctx.nowMs, ctx),
        setNum(KEY_LAST, ctx.nowMs, ctx),
        Command.IncrementCounter(target = SCENE_ENTITY_ID, field = "selections", source = id, tick = ctx.tick),
        Command.TriggerFeedback("matchSuccess", mapOf("source" to entityId), source = id, tick = ctx.tick)
    )

    private fun move(x: Float, y: Float, ctx: BehaviorContext) =
        Command.SetProperty(entityId, "position", PropertyValue.Vec2(x, y), source = id, tick = ctx.tick)

    private fun setNum(key: String, value: Long, ctx: BehaviorContext) =
        Command.SetProperty(SCENE_ENTITY_ID, key, PropertyValue.Num(value.toDouble()), source = id, tick = ctx.tick)

    private fun setText(key: String, value: String, ctx: BehaviorContext) =
        Command.SetProperty(SCENE_ENTITY_ID, key, PropertyValue.Text(value), source = id, tick = ctx.tick)

    companion object {
        val EVENTS: Set<KClass<out GameEvent>> = setOf(
            GameEvent.VoiceLevel::class,
            GameEvent.Vocalization::class,
            GameEvent.ClockTick::class
        )

        private const val KEY_LAST  = "drive.lastMs"
        private const val KEY_VOICE = "drive.voiceMs"
        private const val KEY_PHASE = "drive.phase"
        private const val KEY_SINCE = "drive.since"
        private const val PHASE_READY    = "ready"
        private const val PHASE_LAUNCHED = "launched"
        private const val MAX_STEP_MS = 250L

        /** A breath between syllables is not the end of the sound. */
        private const val SETTLE_MS = 400L
    }
}

class VoiceDrivePlugin : BehaviorPlugin {
    override val behaviorId = "voiceDrive"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = VoiceDriveBehavior.EVENTS

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        VoiceDriveBehavior(
            id          = behaviorId,
            entityId    = config["entity"] ?: entityId ?: "",
            fromY       = config["fromY"]?.toFloatOrNull() ?: 820f,
            toY         = config["toY"]?.toFloatOrNull() ?: 180f,
            rise        = config["rise"]?.toFloatOrNull() ?: 700f,
            fall        = config["fall"]?.toFloatOrNull() ?: 180f,
            celebrateMs = config["celebrateMs"]?.toDoubleOrNull()?.toLong() ?: 1800L
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult {
        val errors = SPEC.check(config).errorList().toMutableList()
        val from = config["fromY"]?.toFloatOrNull() ?: 820f
        val to   = config["toY"]?.toFloatOrNull() ?: 180f
        if (to >= from) errors += "voiceDrive: toY ($to) must be above fromY ($from) — the voice lifts upwards"
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    private companion object {
        private val SPEC = ConfigSpec.of("voiceDrive") {
            text("entity")
            number("fromY", min = 0.0, max = 1000.0)
            number("toY", min = 0.0, max = 1000.0)
            number("rise", min = 50.0, max = 5000.0)
            number("fall", min = 0.0, max = 2000.0)
            number("celebrateMs", min = 0.0, max = 10000.0)
        }
    }
}
