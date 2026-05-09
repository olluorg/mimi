package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

class SequenceValidatorBehavior(
    override val id:      BehaviorId,
    private val group:    String,
    private val property: String
) : Behavior {

    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Tap) return emptyList()
        val tappedId = event.source ?: return emptyList()
        val tapped   = ctx.snapshot.getEntity(tappedId) ?: return emptyList()

        if (!tapped.hasComponent("tap"))          return emptyList()
        if (tapped.tag("group") != group)         return emptyList()
        if (tapped.state != EntityLifecycleState.ACTIVE) return emptyList()

        // Next expected index = min sequenceIndex among still-ACTIVE entities in group
        val allActive = ctx.snapshot.getEntitiesByTag("group", group)
            .filter { it.state == EntityLifecycleState.ACTIVE }
        val nextIndex = allActive.mapNotNull { it.num(property)?.toInt() }.minOrNull()
            ?: return emptyList()

        val tappedIndex = tapped.num(property)?.toInt() ?: return emptyList()

        return if (tappedIndex == nextIndex) {
            listOf(
                Command.SetEntityState(
                    target = tappedId,
                    state  = EntityLifecycleState.TENTATIVE,
                    source = id,
                    tick   = ctx.tick
                ),
                Command.TriggerFeedback(
                    feedbackType = "sequenceStepCorrect",
                    source = id,
                    tick   = ctx.tick
                )
            )
        } else {
            listOf(
                Command.TriggerFeedback(
                    feedbackType = "matchFail",
                    source = id,
                    tick   = ctx.tick
                )
            )
        }
    }
}

class SequenceValidatorPlugin : BehaviorPlugin {
    override val behaviorId = "sequenceValidator"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        SequenceValidatorBehavior(
            id       = behaviorId,
            group    = config["group"]    ?: "sequence",
            property = config["property"] ?: "sequenceIndex"
        )
}
