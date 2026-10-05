package mimi.core.behaviors

import mimi.core.behavior.*
import mimi.core.command.Command
import mimi.core.model.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.reflect.KClass

class MazeValidatorBehavior(
    override val id: BehaviorId,
    private val pathId: EntityId,
    private val startEntityId: EntityId,
    private val exitEntityId: EntityId,
    private val wallGroup: String,
    private val wallPadding: Float
) : Behavior {

    override val phase = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.TracePoint::class,
        GameEvent.TraceComplete::class
    )

    override fun handle(event: GameEvent, ctx: BehaviorContext): List<Command> = when (event) {
        is GameEvent.TracePoint -> handleTracePoint(event, ctx)
        is GameEvent.TraceComplete -> handleTraceComplete(event, ctx)
        else -> emptyList()
    }

    private fun handleTracePoint(event: GameEvent.TracePoint, ctx: BehaviorContext): List<Command> {
        val path = ctx.snapshot.getEntity(pathId) ?: return emptyList()
        if (path.state == EntityLifecycleState.TENTATIVE || path.state == EntityLifecycleState.LOCKED) {
            return emptyList()
        }

        val commands = mutableListOf<Command>()
        val startsNewAttempt = event.source == startEntityId
        if (startsNewAttempt) {
            commands += Command.SetProperty(pathId, "mazeBlocked", PropertyValue.Flag(false), source = id, tick = ctx.tick)
            commands += Command.SetProperty(pathId, "progress", PropertyValue.Num(0.0), source = id, tick = ctx.tick)
        }

        val previous = if (startsNewAttempt) null else readLastPoint(path)
        val hitWall = previous?.let { segmentHitsWall(it, event.position, ctx) }
            ?: pointHitsWall(event.position, ctx)

        if (hitWall) {
            commands += Command.SetProperty(pathId, "mazeBlocked", PropertyValue.Flag(true), source = id, tick = ctx.tick)
            commands += Command.TriggerFeedback("matchFail", source = id, tick = ctx.tick)
        }

        commands += Command.SetProperty(pathId, "lastX", PropertyValue.Num(event.position.x.toDouble()), source = id, tick = ctx.tick)
        commands += Command.SetProperty(pathId, "lastY", PropertyValue.Num(event.position.y.toDouble()), source = id, tick = ctx.tick)
        return commands
    }

    private fun handleTraceComplete(event: GameEvent.TraceComplete, ctx: BehaviorContext): List<Command> {
        if (event.pathId != pathId) return emptyList()
        val path = ctx.snapshot.getEntity(pathId) ?: return emptyList()
        val exit = ctx.snapshot.getEntity(exitEntityId) ?: return emptyList()
        val last = readLastPoint(path) ?: return fail(ctx)
        val exitPos = exit.vec2("position") ?: return fail(ctx)
        val exitRadius = exit.num("radius")?.toFloat() ?: 90f
        val blocked = path.flag("mazeBlocked") == true

        if (!blocked && distance(last, exitPos) <= exitRadius) {
            return listOf(
                Command.SetProperty(pathId, "progress", PropertyValue.Num(1.0), source = id, tick = ctx.tick),
                Command.SetEntityState(pathId, EntityLifecycleState.TENTATIVE, source = id, tick = ctx.tick),
                Command.TriggerFeedback("matchSuccess", source = id, tick = ctx.tick)
            )
        }

        return fail(ctx)
    }

    private fun fail(ctx: BehaviorContext): List<Command> = listOf(
        Command.TriggerFeedback("matchFail", source = id, tick = ctx.tick)
    )

    private fun readLastPoint(entity: EntitySnapshot): PropertyValue.Vec2? {
        val x = entity.num("lastX")?.toFloat() ?: return null
        val y = entity.num("lastY")?.toFloat() ?: return null
        return PropertyValue.Vec2(x, y)
    }

    private fun segmentHitsWall(from: PropertyValue.Vec2, to: PropertyValue.Vec2, ctx: BehaviorContext): Boolean {
        val length = distance(from, to)
        val steps = max(1, ceil(length / 16f).toInt())
        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val point = PropertyValue.Vec2(
                x = from.x + (to.x - from.x) * t,
                y = from.y + (to.y - from.y) * t
            )
            if (pointHitsWall(point, ctx)) return true
        }
        return false
    }

    private fun pointHitsWall(point: PropertyValue.Vec2, ctx: BehaviorContext): Boolean =
        ctx.snapshot.getEntitiesByTag("group", wallGroup).any { wall ->
            val center = wall.vec2("position") ?: return@any false
            val width = wall.num("width")?.toFloat() ?: 0f
            val height = wall.num("height")?.toFloat() ?: 0f
            point.x >= center.x - width / 2f - wallPadding &&
                point.x <= center.x + width / 2f + wallPadding &&
                point.y >= center.y - height / 2f - wallPadding &&
                point.y <= center.y + height / 2f + wallPadding
        }

    private fun distance(a: PropertyValue.Vec2, b: PropertyValue.Vec2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

class MazeValidatorPlugin : BehaviorPlugin {
    override val behaviorId = "mazeValidator"
    override val phase = Phase.RESOLUTION
    override val eventTypes = setOf<KClass<out GameEvent>>(
        GameEvent.TracePoint::class,
        GameEvent.TraceComplete::class
    )

    override fun create(config: Map<String, String>, entityId: EntityId?): Behavior =
        MazeValidatorBehavior(
            id = behaviorId,
            pathId = config["pathId"] ?: "maze_path",
            startEntityId = config["startEntity"] ?: "maze_start",
            exitEntityId = config["exitEntity"] ?: "maze_exit",
            wallGroup = config["wallGroup"] ?: "maze_wall",
            wallPadding = config["wallPadding"]?.toFloatOrNull() ?: 8f
        )

    override fun validateConfig(config: Map<String, String>): ValidationResult =
        SPEC.check(config)

    private companion object {
        private val SPEC = ConfigSpec.of("mazeValidator") {
            text("pathId", "startEntity", "exitEntity", "wallGroup")
            number("wallPadding", min = 0.0)
        }
    }
}
