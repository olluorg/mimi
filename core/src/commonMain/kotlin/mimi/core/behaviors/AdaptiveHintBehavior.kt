package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

class AdaptiveHintBehavior(
    override val id:          BehaviorId,
    private val entityId:     EntityId?,
    private val cooldownMin:  Long,
    private val cooldownMax:  Long,
    private val thresholds:   HintThresholds
) : Behavior {

    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.MatchFail::class,
        GameEvent.Tap::class,
        GameEvent.DragStart::class,
        GameEvent.IdleCheck::class
    )

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        val snap = ctx.snapshot

        val mistakes   = snap.mistakes().toInt()
        val idleTicks  = snap.idleTicks()
        val hintLevel  = snap.getNum(SCENE_ENTITY_ID, "hintLevel")?.toInt() ?: 0
        val lastHintTick = snap.getNum(SCENE_ENTITY_ID, "lastHintTick")?.toLong() ?: 0L

        val newLevel = thresholds.evaluate(mistakes, idleTicks)
        val cooldown = cooldownMin + ((cooldownMax - cooldownMin) / 2)
        val cooldownElapsed = (ctx.tick - lastHintTick) >= cooldown

        if (newLevel == HintLevel.NONE || !cooldownElapsed) return emptyList()

        val commands = mutableListOf<Command>()

        // Update hint level in world state
        if (newLevel.value != hintLevel) {
            commands.add(Command.SetProperty(
                target   = SCENE_ENTITY_ID,
                field    = "hintLevel",
                value    = PropertyValue.Num(newLevel.value.toDouble()),
                source   = id,
                tick     = ctx.tick
            ))
        }

        commands.add(Command.SetProperty(
            target = SCENE_ENTITY_ID,
            field  = "lastHintTick",
            value  = PropertyValue.Num(ctx.tick.toDouble()),
            source = id,
            tick   = ctx.tick
        ))

        commands.add(Command.IncrementCounter(
            target = SCENE_ENTITY_ID,
            field  = "hints",
            delta  = 1L,
            source = id,
            tick   = ctx.tick
        ))

        // Point at something. A hint bound to no entity — which is every scene in
        // the catalog — used to name nothing, so the child heard a chime and saw
        // no change at all. The next move is worked out from the world instead.
        val move = HintTargets.nextMove(snap)
        val pointAt = entityId ?: move?.source
        val params = buildMap {
            put("level", newLevel.value.toString())
            put("entityId", pointAt ?: "")
            // Only once the child has been stuck a while is the answer shown as
            // well as the question: where the piece belongs, not just which piece.
            if (newLevel == HintLevel.ASSIST && entityId == null) {
                move?.destination?.let { put("destinationId", it) }
            }
        }
        commands.add(Command.TriggerFeedback(
            feedbackType = "hint",
            params       = params,
            source = id,
            tick   = ctx.tick
        ))

        return commands
    }
}

enum class HintLevel(val value: Int) { NONE(0), SUBTLE(1), DIRECTIONAL(2), ASSIST(3) }

data class HintThresholds(
    val level1IdleTicks: Long = secondsToTicks(4),
    val level1Mistakes:  Int  = 1,
    val level2IdleTicks: Long = secondsToTicks(8),
    val level2Mistakes:  Int  = 2,
    val level3IdleTicks: Long = secondsToTicks(12),
    val level3Mistakes:  Int  = 3
) {
    fun evaluate(mistakes: Int, idleTicks: Long): HintLevel = when {
        mistakes >= level3Mistakes || idleTicks >= level3IdleTicks -> HintLevel.ASSIST
        mistakes >= level2Mistakes || idleTicks >= level2IdleTicks -> HintLevel.DIRECTIONAL
        mistakes >= level1Mistakes || idleTicks >= level1IdleTicks -> HintLevel.SUBTLE
        else                                                        -> HintLevel.NONE
    }
}

class AdaptiveHintPlugin : BehaviorPlugin {
    override val behaviorId = "adaptiveHint"
    override val phase      = Phase.FEEDBACK
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.MatchFail::class,
        GameEvent.Tap::class,
        GameEvent.DragStart::class,
        GameEvent.IdleCheck::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        AdaptiveHintBehavior(
            id          = behaviorId,
            entityId    = entityId,
            cooldownMin = config["cooldownMin"]?.toLongOrNull() ?: secondsToTicks(10),
            cooldownMax = config["cooldownMax"]?.toLongOrNull() ?: secondsToTicks(15),
            thresholds  = HintThresholds()
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("adaptiveHint") {
            number("cooldownMin", min = 0.0)
            number("cooldownMax", min = 0.0)
        }
    }
}
