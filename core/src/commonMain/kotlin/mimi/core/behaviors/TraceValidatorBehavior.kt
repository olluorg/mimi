package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.math.sqrt
import kotlin.reflect.KClass

class TraceValidatorBehavior(
    override val id:               BehaviorId,
    private val group:             String,
    private val allowBacktracking: Boolean
) : Behavior {

    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.TracePoint::class,
        GameEvent.TraceComplete::class
    )

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> = when (event) {
        is GameEvent.TracePoint    -> handleTracePoint(event, ctx)
        is GameEvent.TraceComplete -> handleTraceComplete(event, ctx)
        else -> emptyList()
    }

    private fun handleTracePoint(event: GameEvent.TracePoint, ctx: BehaviorContext): List<Command> {
        val entities = ctx.snapshot.getEntitiesByTag("group", group)
            .filter { it.hasComponent("traceTarget") }
        val commands = mutableListOf<Command>()

        for (entity in entities) {
            val waypoints = entity.waypoints("waypoints") ?: continue
            val nextIndex = waypoints.indexOfFirst { wp ->
                val prop = entity.property("wp_${waypoints.indexOf(wp)}_done")
                prop == null  // not yet done
            }
            if (nextIndex < 0) continue

            val wp   = waypoints[nextIndex]
            val dist = dist(event.position.x, event.position.y, wp.x, wp.y)
            if (dist <= wp.radius) {
                commands += Command.SetProperty(
                    target = entity.id,
                    field  = "wp_${nextIndex}_done",
                    value  = PropertyValue.Flag(true),
                    source = id,
                    tick   = ctx.tick
                )
                // Track progress as fraction of waypoints reached
                val progress = (nextIndex + 1).toDouble() / waypoints.size
                commands += Command.SetProperty(
                    target = entity.id,
                    field  = "progress",
                    value  = PropertyValue.Num(progress),
                    source = id,
                    tick   = ctx.tick
                )
                commands += Command.TriggerFeedback(
                    feedbackType = "traceCheckpointReached",
                    source       = id,
                    tick         = ctx.tick
                )
                // If all waypoints reached → mark entity as TENTATIVE
                if (nextIndex == waypoints.size - 1) {
                    commands += Command.SetEntityState(
                        target = entity.id,
                        state  = EntityLifecycleState.TENTATIVE,
                        source = id,
                        tick   = ctx.tick
                    )
                }
            }
        }
        return commands
    }

    private fun handleTraceComplete(event: GameEvent.TraceComplete, ctx: BehaviorContext): List<Command> {
        val entities = ctx.snapshot.getEntitiesByTag("group", group)
            .filter { it.hasComponent("traceTarget") }

        return entities.filter { it.state == EntityLifecycleState.TENTATIVE }.map {
            Command.TriggerFeedback(feedbackType = "matchSuccess", source = id, tick = ctx.tick)
        }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}

class TraceValidatorPlugin : BehaviorPlugin {
    override val behaviorId = "traceValidator"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.TracePoint::class,
        GameEvent.TraceComplete::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        TraceValidatorBehavior(
            id               = behaviorId,
            group            = config["group"] ?: "trace",
            allowBacktracking = config["allowBacktracking"]?.toBooleanStrictOrNull() ?: true
        )
}
