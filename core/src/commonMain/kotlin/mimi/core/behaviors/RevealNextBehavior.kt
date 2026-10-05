package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

/**
 * Brings the next hidden item in a group into view.
 *
 * This is what makes requesting teachable: the child asks, and something
 * actually arrives. A reward that is simply present from the start teaches
 * nothing about asking, and one that only plays a sound is too faint to read as
 * a consequence — the child needs to see their request produce the thing.
 *
 * Items are revealed in declaration order, so a scene controls the sequence by
 * how it lists them.
 */
class RevealNextBehavior(
    override val id:   BehaviorId,
    private val group: String,
    onEventClass:      KClass<out GameEvent>
) : Behavior {

    override val phase      = Phase.COMMAND_GENERATION
    override val eventTypes = setOf(onEventClass)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        val next = ctx.snapshot.getEntitiesByTag("group", group)
            .firstOrNull { it.state == EntityLifecycleState.INACTIVE }
            ?: return emptyList()

        return listOf(
            Command.SetEntityState(
                target = next.id,
                state  = EntityLifecycleState.ACTIVE,
                source = id,
                tick   = ctx.tick
            )
        )
    }
}

class RevealNextPlugin : BehaviorPlugin {
    override val behaviorId = "revealNext"
    override val phase      = Phase.COMMAND_GENERATION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.MatchSuccess::class,
        GameEvent.Tap::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        RevealNextBehavior(
            id    = behaviorId,
            group = config["group"] ?: "reward",
            onEventClass = when (config["on"]) {
                "tap" -> GameEvent.Tap::class
                else  -> GameEvent.MatchSuccess::class
            }
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("revealNext") {
            text("group")
            oneOf("on", "tap", "matchSuccess")
        }
    }
}
