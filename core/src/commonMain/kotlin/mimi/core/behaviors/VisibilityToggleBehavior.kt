package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

class VisibilityToggleBehavior(
    override val id:       BehaviorId,
    private val targets:   List<EntityId>,
    private val showState: EntityLifecycleState,
    onEventClass:          KClass<out GameEvent>
) : Behavior {

    override val phase      = Phase.COMMAND_GENERATION
    override val eventTypes = setOf(onEventClass)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> =
        targets.map { targetId ->
            val current = ctx.snapshot.getEntityState(targetId)
            val next = if (current == EntityLifecycleState.INACTIVE) showState
                       else EntityLifecycleState.INACTIVE
            Command.SetEntityState(
                target   = targetId,
                state    = next,
                priority = 0,
                source   = id,
                tick     = ctx.tick
            )
        }
}

class VisibilityTogglePlugin : BehaviorPlugin {
    override val behaviorId = "visibilityToggle"
    override val phase      = Phase.COMMAND_GENERATION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.Tap::class,
        GameEvent.MatchSuccess::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior {
        val targets = config["targets"]?.split(",")?.map { it.trim() }
            ?: listOfNotNull(entityId)
        val onEvent = when (config["on"]) {
            "matchSuccess" -> GameEvent.MatchSuccess::class
            else           -> GameEvent.Tap::class
        }
        return VisibilityToggleBehavior(
            id         = behaviorId,
            targets    = targets,
            showState  = EntityLifecycleState.ACTIVE,
            onEventClass = onEvent
        )
    }
}
