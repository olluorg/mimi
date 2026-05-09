package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

class LockOnCompleteBehavior(override val id: BehaviorId) : Behavior {
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.SceneCompleted::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.SceneCompleted) return emptyList()
        return ctx.snapshot
            .getEntitiesByState(EntityLifecycleState.TENTATIVE)
            .map { entity ->
                Command.SetEntityState(
                    target   = entity.id,
                    state    = EntityLifecycleState.LOCKED,
                    priority = 200,
                    source   = id,
                    tick     = ctx.tick
                )
            }
    }
}

class LockOnCompletePlugin : BehaviorPlugin {
    override val behaviorId = "lockOnComplete"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.SceneCompleted::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        LockOnCompleteBehavior(behaviorId)
}
