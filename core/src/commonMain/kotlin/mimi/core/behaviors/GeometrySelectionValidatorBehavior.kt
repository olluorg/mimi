package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.reflect.KClass

class GeometrySelectionValidatorBehavior(
    override val id: BehaviorId,
    private val group: String,
    private val select: String
) : Behavior {
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> {
        if (event !is GameEvent.Tap) return emptyList()
        val tappedId = event.source ?: return emptyList()
        val tapped   = ctx.snapshot.getEntity(tappedId) ?: return emptyList()
        if (tapped.state != EntityLifecycleState.ACTIVE) return emptyList()

        val candidates = ctx.snapshot.getEntitiesByTag("group", group)
            .filter { it.state == EntityLifecycleState.ACTIVE }
        if (candidates.isEmpty()) return emptyList()

        val target = when (select) {
            "smallest" -> candidates.minByOrNull { it.visualArea() }
            else       -> candidates.maxByOrNull { it.visualArea() }
        } ?: return emptyList()

        return if (tapped.id == target.id) {
            listOf(
                Command.SetEntityState(
                    target = tappedId,
                    state  = EntityLifecycleState.TENTATIVE,
                    source = id,
                    tick   = ctx.tick
                ),
                Command.TriggerFeedback(
                    feedbackType = "matchSuccess",
                    params       = mapOf("source" to tappedId),
                    source       = id,
                    tick         = ctx.tick
                )
            )
        } else {
            listOf(
                Command.TriggerFeedback(
                    feedbackType = "matchFail",
                    params       = mapOf("source" to tappedId, "reason" to "wrong_size"),
                    source       = id,
                    tick         = ctx.tick
                )
            )
        }
    }
}

class GeometrySelectionValidatorPlugin : BehaviorPlugin {
    override val behaviorId = "geometrySelectionValidator"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(GameEvent.Tap::class)

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        GeometrySelectionValidatorBehavior(
            id     = behaviorId,
            group  = config["group"] ?: "choices",
            select = config["select"] ?: "largest"
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("geometrySelectionValidator") {
            text("group")
            oneOf("select", "largest", "smallest")
        }
    }
}

internal fun EntitySnapshot.visualArea(): Double {
    val width  = num("width") ?: num("radius")?.let { it * 2.0 } ?: 90.0
    val height = num("height") ?: num("radius")?.let { it * 2.0 } ?: width
    return width * height
}
