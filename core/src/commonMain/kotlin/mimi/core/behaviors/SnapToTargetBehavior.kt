package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import mimi.core.plugin.ConstraintRegistry
import kotlin.reflect.KClass

class SnapToTargetBehavior(
    override val id:        BehaviorId,
    private val entityId:   EntityId?,
    private val snapRadius: Float,
    private val priority:   Int,
    private val constraints: ConstraintRegistry
) : Behavior {

    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Drop::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Drop) return emptyList()

        // If bound to a specific entity, only handle drops involving it
        if (entityId != null && event.draggedEntity != entityId) return emptyList()

        val dragged = ctx.snapshot.getEntity(event.draggedEntity) ?: return emptyList()
        val target  = ctx.snapshot.getEntity(event.dropTargetEntity) ?: return emptyList()

        // Skip if already locked
        if (dragged.state == EntityLifecycleState.LOCKED) return emptyList()
        if (!target.hasComponent("dropTarget")) return emptyList()

        // Evaluate constraints declared on the target entity
        val constraintDefs = target.properties["constraints"]
        val matched = evaluateConstraints(dragged, target, ctx)

        return if (matched) {
            val targetPos = target.vec2("position") ?: PropertyValue.Vec2(0f, 0f)
            listOf(
                Command.MarkMatched(
                    target          = event.dropTargetEntity,
                    draggedEntityId = event.draggedEntity,
                    priority        = priority,
                    source          = id,
                    tick            = ctx.tick
                ),
                Command.SetProperty(
                    target   = event.draggedEntity,
                    field    = "position",
                    value    = targetPos,
                    priority = priority,
                    source   = id,
                    tick     = ctx.tick
                )
            )
        } else {
            listOf(
                Command.TriggerFeedback(
                    feedbackType = "matchFail",
                    params       = mapOf("source" to event.draggedEntity, "target" to event.dropTargetEntity),
                    source       = id,
                    tick         = ctx.tick
                )
            )
        }
    }

    private fun evaluateConstraints(
        dragged: EntitySnapshot,
        target:  EntitySnapshot,
        ctx:     BehaviorContext
    ): Boolean {
        val constraintType = target.text("constraintType") ?: return true
        val config = target.properties
            .filterValues { it is PropertyValue.Text }
            .mapValues { (it.value as PropertyValue.Text).value }
        return constraints.evaluate(constraintType, dragged, target, config)
    }
}

class SnapToTargetPlugin(private val constraintRegistry: ConstraintRegistry) : BehaviorPlugin {
    override val behaviorId = "snapToTarget"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Drop::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        SnapToTargetBehavior(
            id          = behaviorId,
            entityId    = entityId,
            snapRadius  = config["snapRadius"]?.toFloatOrNull() ?: 80f,
            priority    = config["priority"]?.toIntOrNull() ?: 100,
            constraints = constraintRegistry
        )
}
