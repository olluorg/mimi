package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import mimi.core.plugin.ConstraintRegistry
import kotlin.reflect.KClass

class LineConnectValidatorBehavior(
    override val id: BehaviorId,
    private val constraints: ConstraintRegistry
) : Behavior {
    override val phase = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Drop::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Drop) return emptyList()
        val source = ctx.snapshot.getEntity(event.draggedEntity) ?: return emptyList()
        val target = ctx.snapshot.getEntity(event.dropTargetEntity) ?: return emptyList()
        if (!source.hasComponent("connectSource")) return emptyList()
        if (!target.hasComponent("connectTarget")) return emptyList()
        if (source.state == EntityLifecycleState.LOCKED) return emptyList()

        val matched = evaluateConstraints(source, target)
        return if (matched) {
            listOf(
                Command.MarkMatched(
                    target = target.id,
                    draggedEntityId = source.id,
                    priority = 100,
                    source = id,
                    tick = ctx.tick
                ),
                Command.SetProperty(
                    target = source.id,
                    field = "connectedTo",
                    value = PropertyValue.Ref(target.id),
                    priority = 100,
                    source = id,
                    tick = ctx.tick
                ),
                Command.TriggerFeedback(
                    feedbackType = "matchSuccess",
                    params = mapOf("source" to source.id, "target" to target.id),
                    source = id,
                    tick = ctx.tick
                )
            )
        } else {
            listOf(Command.TriggerFeedback(
                feedbackType = "matchFail",
                params = mapOf("source" to source.id, "target" to target.id),
                source = id,
                tick = ctx.tick
            ))
        }
    }

    private fun evaluateConstraints(source: EntitySnapshot, target: EntitySnapshot): Boolean {
        val constraintType = target.text("constraintType") ?: return true
        val config = target.properties
            .filterValues { it is PropertyValue.Text }
            .mapValues { (it.value as PropertyValue.Text).value }
        return constraints.evaluate(constraintType, source, target, config)
    }
}

class LineConnectValidatorPlugin(private val constraintRegistry: ConstraintRegistry) : BehaviorPlugin {
    override val behaviorId = "lineConnectValidator"
    override val phase = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Drop::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        LineConnectValidatorBehavior(behaviorId, constraintRegistry)

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("lineConnectValidator") {
            text("group")
        }
    }
}
