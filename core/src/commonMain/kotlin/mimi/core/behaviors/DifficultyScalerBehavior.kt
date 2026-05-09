package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

class DifficultyScalerBehavior(
    override val id:               BehaviorId,
    private val initialDifficulty: Double,
    private val updateIntervalMin: Long,
    private val updateIntervalMax: Long
) : Behavior {

    override val phase      = Phase.COMMAND_GENERATION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.MatchSuccess::class,
        GameEvent.MatchFail::class
    )

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        val snap = ctx.snapshot
        val nextUpdateTick = snap.getNum(SCENE_ENTITY_ID, "nextDifficultyUpdateTick")?.toLong()
            ?: return emptyList()

        if (ctx.tick < nextUpdateTick) return emptyList()

        val current    = snap.difficulty().coerceIn(0.1, 1.0)
        val mistakes   = snap.mistakes()
        val matches    = snap.matches()
        val idleTicks  = snap.idleTicks()
        val total      = mistakes + matches
        val successRate = if (total > 0) matches.toDouble() / total else 1.0

        var delta = 0.0

        // Decrease triggers
        if (successRate < 0.40) delta -= 0.10
        if (mistakes >= 3)      delta -= 0.15
        if (idleTicks > secondsToTicks(10)) delta -= 0.10

        // Increase triggers
        if (successRate > 0.80) delta += 0.05
        if (mistakes == 0L && matches >= 3) delta += 0.05

        val newDifficulty = (current + delta).coerceIn(0.1, 1.0)

        // Schedule next update using a pseudo-fixed interval (mid-point of range for determinism)
        val interval     = (updateIntervalMin + updateIntervalMax) / 2
        val nextTick     = ctx.tick + interval

        return listOf(
            Command.SetProperty(
                target   = SCENE_ENTITY_ID,
                field    = "difficulty",
                value    = PropertyValue.Num(newDifficulty),
                priority = 50,
                source   = id,
                tick     = ctx.tick
            ),
            Command.SetProperty(
                target   = SCENE_ENTITY_ID,
                field    = "nextDifficultyUpdateTick",
                value    = PropertyValue.Num(nextTick.toDouble()),
                priority = 50,
                source   = id,
                tick     = ctx.tick
            ),
            Command.SetProperty(
                target   = SCENE_ENTITY_ID,
                field    = "successRateWindow",
                value    = PropertyValue.Num(successRate),
                priority = 0,
                source   = id,
                tick     = ctx.tick
            )
        )
    }
}

class DifficultyScalerPlugin : BehaviorPlugin {
    override val behaviorId = "difficultyScaler"
    override val phase      = Phase.COMMAND_GENERATION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.MatchSuccess::class,
        GameEvent.MatchFail::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        DifficultyScalerBehavior(
            id                = behaviorId,
            initialDifficulty = config["initialDifficulty"]?.toDoubleOrNull() ?: 0.5,
            updateIntervalMin = config["updateIntervalMin"]?.toLongOrNull() ?: secondsToTicks(20),
            updateIntervalMax = config["updateIntervalMax"]?.toLongOrNull() ?: secondsToTicks(30)
        )
}
