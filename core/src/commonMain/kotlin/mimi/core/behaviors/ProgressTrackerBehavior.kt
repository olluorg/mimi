package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

class ProgressTrackerBehavior(override val id: BehaviorId) : Behavior {
    override val phase      = Phase.COMMAND_GENERATION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.MatchSuccess::class,
        GameEvent.MatchFail::class
    )

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> = when (event) {
        is GameEvent.MatchSuccess -> listOf(
            Command.IncrementCounter(
                target = SCENE_ENTITY_ID, field = "matches",
                delta = 1L, source = id, tick = ctx.tick
            )
        )
        is GameEvent.MatchFail -> listOf(
            Command.IncrementCounter(
                target = SCENE_ENTITY_ID, field = "mistakes",
                delta = 1L, source = id, tick = ctx.tick
            )
        )
        else -> emptyList()
    }
}

class ProgressTrackerPlugin : BehaviorPlugin {
    override val behaviorId = "progressTracker"
    override val phase      = Phase.COMMAND_GENERATION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.MatchSuccess::class,
        GameEvent.MatchFail::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        ProgressTrackerBehavior(behaviorId)
}
