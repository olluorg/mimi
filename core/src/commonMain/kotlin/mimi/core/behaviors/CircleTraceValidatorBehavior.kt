package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.math.*
import kotlin.reflect.KClass

class CircleTraceValidatorBehavior(
    override val id:       BehaviorId,
    private val group:     String,
    private val tolerance: Float,
    private val minProgress: Double
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
            .filter { it.hasComponent("traceTarget") && it.state == EntityLifecycleState.ACTIVE }
        val commands = mutableListOf<Command>()

        for (entity in entities) {
            val center     = entity.vec2("center") ?: continue
            val radius     = entity.num("radius")?.toFloat() ?: continue
            val tol        = entity.num("tolerance")?.toFloat() ?: tolerance
            val resolution = entity.num("resolution")?.toInt() ?: 64

            val dx   = event.position.x - center.x
            val dy   = event.position.y - center.y
            val dist = sqrt(dx * dx + dy * dy)

            if (abs(dist - radius) > tol) continue

            // Sector index: 0 = 3 o'clock, going clockwise (matches atan2 in screen coords)
            val angle      = atan2(dy, dx).toDouble()
            val normalized = if (angle < 0) angle + 2 * PI else angle
            val sectorIdx  = (normalized / (2 * PI / resolution)).toInt().coerceIn(0, resolution - 1)
            val sectorKey  = "sector_${sectorIdx}_done"

            if (entity.property(sectorKey) != null) continue

            commands += Command.SetProperty(
                target = entity.id,
                field  = sectorKey,
                value  = PropertyValue.Flag(true),
                source = id,
                tick   = ctx.tick
            )

            val doneSectors = (0 until resolution).count { i ->
                i == sectorIdx ||
                (entity.property("sector_${i}_done") as? PropertyValue.Flag)?.value == true
            }
            val progress = doneSectors.toDouble() / resolution

            commands += Command.SetProperty(
                target = entity.id,
                field  = "progress",
                value  = PropertyValue.Num(progress),
                source = id,
                tick   = ctx.tick
            )

            if (progress >= minProgress) {
                commands += Command.SetEntityState(
                    target = entity.id,
                    state  = EntityLifecycleState.TENTATIVE,
                    source = id,
                    tick   = ctx.tick
                )
            }
        }
        return commands
    }

    private fun handleTraceComplete(event: GameEvent.TraceComplete, ctx: BehaviorContext): List<Command> {
        val done = ctx.snapshot.getEntitiesByTag("group", group)
            .filter { it.hasComponent("traceTarget") && it.state == EntityLifecycleState.TENTATIVE }
        return done.flatMap { entity ->
            listOf(
                Command.TriggerFeedback(feedbackType = "traceCompleted", source = id, tick = ctx.tick),
                // Also a match, so a finished circle reaches the counters and the
                // success chime the way every other mechanic's win does.
                Command.TriggerFeedback(
                    feedbackType = "matchSuccess",
                    params       = mapOf("source" to entity.id),
                    source       = id,
                    tick         = ctx.tick
                )
            )
        }
    }
}

class CircleTraceValidatorPlugin : BehaviorPlugin {
    override val behaviorId = "circleTraceValidator"
    override val phase      = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.TracePoint::class,
        GameEvent.TraceComplete::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        CircleTraceValidatorBehavior(
            id          = behaviorId,
            group       = config["group"] ?: "trace",
            tolerance   = config["snapTolerance"]?.toFloatOrNull() ?: 60f,
            minProgress = config["minProgress"]?.toDoubleOrNull() ?: 0.9
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("circleTraceValidator") {
            text("group")
            number("minProgress", min = 0.0, max = 1.0)
            number("snapTolerance", min = 0.0)
        }
    }
}
