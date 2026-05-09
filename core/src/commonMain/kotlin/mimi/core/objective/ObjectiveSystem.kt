package mimi.core.objective

import mimi.core.behavior.GameEvent
import mimi.core.command.Command
import mimi.core.model.*
import mimi.core.world.SceneDefinition
import mimi.core.world.WorldSnapshot

// ── Objective Evaluator ───────────────────────────────────────────────────────

interface ObjectiveEvaluator {
    val type: String
    fun evaluate(
        objectiveId: ObjectiveId,
        definition:  mimi.core.world.ObjectiveDefinition,
        world:       WorldSnapshot
    ): ObjectiveState
}

// ── Built-in Evaluators ───────────────────────────────────────────────────────

object AllMatchedEvaluator : ObjectiveEvaluator {
    override val type = "allMatched"

    override fun evaluate(objectiveId: ObjectiveId, definition: mimi.core.world.ObjectiveDefinition, world: WorldSnapshot): ObjectiveState {
        val group = definition.config["group"] ?: return ObjectiveState.INACTIVE
        val entities = world.getEntitiesByTag("group", group)
        if (entities.isEmpty()) return ObjectiveState.INACTIVE
        val matched = entities.filter { it.state == EntityLifecycleState.TENTATIVE || it.state == EntityLifecycleState.LOCKED }
        return when {
            matched.isEmpty()          -> ObjectiveState.INACTIVE
            matched.size == entities.size -> ObjectiveState.TENTATIVE_COMPLETE
            else                       -> ObjectiveState.PROGRESSING
        }
    }
}

object AllSortedEvaluator : ObjectiveEvaluator {
    override val type = "allSorted"

    override fun evaluate(objectiveId: ObjectiveId, definition: mimi.core.world.ObjectiveDefinition, world: WorldSnapshot): ObjectiveState {
        val tag = definition.config["tag"] ?: return ObjectiveState.INACTIVE
        val entities = world.getEntitiesByTag(tag, "true").ifEmpty {
            world.getAllEntities().filter { it.hasTag(tag) }
        }
        if (entities.isEmpty()) return ObjectiveState.INACTIVE
        val sorted = entities.filter {
            it.state == EntityLifecycleState.TENTATIVE || it.state == EntityLifecycleState.LOCKED
        }
        return when {
            sorted.isEmpty()            -> ObjectiveState.INACTIVE
            sorted.size == entities.size -> ObjectiveState.TENTATIVE_COMPLETE
            else                        -> ObjectiveState.PROGRESSING
        }
    }
}

object SequenceCompleteEvaluator : ObjectiveEvaluator {
    override val type = "sequenceComplete"

    override fun evaluate(objectiveId: ObjectiveId, definition: mimi.core.world.ObjectiveDefinition, world: WorldSnapshot): ObjectiveState {
        val sequenceRaw = definition.config["sequence"] ?: return ObjectiveState.INACTIVE
        val sequence = sequenceRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (sequence.isEmpty()) return ObjectiveState.INACTIVE
        val matched = sequence.filter {
            val state = world.getEntityState(it)
            state == EntityLifecycleState.TENTATIVE || state == EntityLifecycleState.LOCKED
        }
        return when {
            matched.isEmpty()            -> ObjectiveState.INACTIVE
            matched.size == sequence.size -> ObjectiveState.TENTATIVE_COMPLETE
            else                         -> ObjectiveState.PROGRESSING
        }
    }
}

object TraceCompleteEvaluator : ObjectiveEvaluator {
    override val type = "traceComplete"

    override fun evaluate(objectiveId: ObjectiveId, definition: mimi.core.world.ObjectiveDefinition, world: WorldSnapshot): ObjectiveState {
        val pathId      = definition.config["pathId"] ?: return ObjectiveState.INACTIVE
        val minAccuracy = definition.config["minAccuracy"]?.toFloatOrNull() ?: 0.7f
        val progress    = world.getNum(pathId, "progress")?.toFloat()
            ?: world.getNum(SCENE_ENTITY_ID, "traceProgress.$pathId")?.toFloat()
            ?: return ObjectiveState.INACTIVE
        return if (progress >= minAccuracy) ObjectiveState.TENTATIVE_COMPLETE else ObjectiveState.PROGRESSING
    }
}

// ── Objective System ──────────────────────────────────────────────────────────

data class ObjectiveEvalResult(
    val newStates:     Map<ObjectiveId, ObjectiveState>,
    val stateCommands: List<Command>,
    val systemEvents:  List<GameEvent>
)

class ObjectiveSystem(
    private val evaluators: Map<String, ObjectiveEvaluator> = defaultEvaluators()
) {
    fun evaluate(
        definitions:    List<mimi.core.world.ObjectiveDefinition>,
        world:          WorldSnapshot,
        previousStates: Map<ObjectiveId, ObjectiveState>,
        tick:           Tick
    ): ObjectiveEvalResult {
        val newStates     = mutableMapOf<ObjectiveId, ObjectiveState>()
        val commands      = mutableListOf<Command>()
        val events        = mutableListOf<GameEvent>()

        // Build prereq map
        val prereqMet = mutableMapOf<ObjectiveId, Boolean>()
        fun isPrereqMet(def: mimi.core.world.ObjectiveDefinition): Boolean {
            val req = def.requires ?: return true
            return (previousStates[req] ?: ObjectiveState.INACTIVE) == ObjectiveState.LOCKED_COMPLETE
        }

        for (def in definitions) {
            val prevState = previousStates[def.id] ?: ObjectiveState.INACTIVE

            // Terminal — never re-evaluate
            if (prevState == ObjectiveState.LOCKED_COMPLETE) {
                newStates[def.id] = ObjectiveState.LOCKED_COMPLETE
                continue
            }

            // Prerequisite not met
            if (!isPrereqMet(def)) {
                newStates[def.id] = ObjectiveState.INACTIVE
                continue
            }

            val evaluator = evaluators[def.type]
            val nextState = evaluator?.evaluate(def.id, def, world) ?: ObjectiveState.INACTIVE
            newStates[def.id] = nextState

            if (nextState != prevState) {
                commands.add(Command.SetObjectiveState(
                    objectiveId = def.id,
                    state       = nextState,
                    source      = "objective_system",
                    tick        = tick
                ))
                events.add(transitionEvent(def.id, prevState, nextState, tick))
            }
        }

        // Check if all objectives are TENTATIVE_COMPLETE → lock all + SceneCompleted
        val allComplete = definitions.all { newStates[it.id] == ObjectiveState.TENTATIVE_COMPLETE }
        if (allComplete && definitions.isNotEmpty()) {
            for (def in definitions) {
                newStates[def.id] = ObjectiveState.LOCKED_COMPLETE
                commands.add(Command.SetObjectiveState(
                    objectiveId = def.id,
                    state       = ObjectiveState.LOCKED_COMPLETE,
                    source      = "objective_system",
                    tick        = tick
                ))
                events.add(GameEvent.ObjectiveLocked(objectiveId = def.id, tick = tick))
            }
            events.add(GameEvent.SceneCompleted(tick = tick))
        }

        return ObjectiveEvalResult(newStates, commands, events)
    }

    private fun transitionEvent(id: ObjectiveId, from: ObjectiveState, to: ObjectiveState, tick: Tick): GameEvent =
        when {
            from == ObjectiveState.INACTIVE && to == ObjectiveState.PROGRESSING ->
                GameEvent.ObjectiveProgressed(id, tick)
            to == ObjectiveState.TENTATIVE_COMPLETE ->
                GameEvent.ObjectiveCompleted(id, tick)
            from == ObjectiveState.TENTATIVE_COMPLETE && to == ObjectiveState.PROGRESSING ->
                GameEvent.ObjectiveReverted(id, tick)
            else ->
                GameEvent.ObjectiveProgressed(id, tick)
        }

    companion object {
        fun defaultEvaluators(): Map<String, ObjectiveEvaluator> = mapOf(
            AllMatchedEvaluator.type       to AllMatchedEvaluator,
            AllSortedEvaluator.type        to AllSortedEvaluator,
            SequenceCompleteEvaluator.type to SequenceCompleteEvaluator,
            TraceCompleteEvaluator.type    to TraceCompleteEvaluator
        )
    }
}
