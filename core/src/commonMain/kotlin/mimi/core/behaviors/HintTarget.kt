package mimi.core.behaviors

import mimi.core.model.EntityId
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import mimi.core.world.WorldSnapshot

/**
 * What a hint should point at.
 *
 * Every scene binds `adaptiveHint` globally, with no entity attached, so the
 * hint had nothing to name and highlighted nothing: a stuck child heard a chime
 * and saw no change. Working out the next thing to touch from the world itself
 * is what turns the hint into an answer to "what do I do".
 */
data class HintTarget(
    /** The piece the child should act on. */
    val source: EntityId,
    /** Where it belongs, when the scene makes that knowable. */
    val destination: EntityId? = null
)

object HintTargets {

    /**
     * The next move, read off the current world.
     *
     * Only moves the engine can be sure of are offered. In a "find the cat" scene
     * the right answer lives in the objective, not in the entities, so nothing is
     * pointed at rather than risk pointing at a wrong tile — a confident wrong
     * hint is worse than none.
     */
    fun nextMove(world: WorldSnapshot): HintTarget? {
        val active = world.getAllEntities().filter { it.state == EntityLifecycleState.ACTIVE }

        // A line to draw: the source end is unambiguous.
        active.firstOrNull { it.hasComponent("connectSource") }?.let { source ->
            return HintTarget(source.id, matchingTargetFor(source, world, "connectTarget"))
        }

        // A piece to drag: any unplaced one will do, and its basket is knowable
        // whenever the target states which property it accepts.
        active.firstOrNull { it.hasComponent("drag") }?.let { item ->
            return HintTarget(item.id, matchingTargetFor(item, world, "dropTarget"))
        }

        // A sequence: the next step is the lowest index still untouched.
        val nextStep = active
            .filter { it.hasComponent("tap") && it.num("sequenceIndex") != null }
            .minByOrNull { it.num("sequenceIndex")!! }
        if (nextStep != null) return HintTarget(nextStep.id)

        // A path to trace: point at the path itself.
        world.getEntitiesByComponent("traceTarget")
            .firstOrNull { it.state == EntityLifecycleState.ACTIVE }
            ?.let { return HintTarget(it.id) }

        return null
    }

    /**
     * The target that accepts [item], for the constraint the catalog actually
     * uses. Anything more elaborate is left alone: a hint that guesses wrong
     * teaches the wrong pairing.
     */
    private fun matchingTargetFor(
        item:      EntitySnapshot,
        world:     WorldSnapshot,
        component: String
    ): EntityId? = world.getEntitiesByComponent(component).firstOrNull { target ->
        if (target.state == EntityLifecycleState.LOCKED) return@firstOrNull false
        if (target.text("constraintType") != "propertyEquals") return@firstOrNull false
        val sourceKey = target.text("source") ?: return@firstOrNull false
        val targetKey = target.text("target") ?: return@firstOrNull false
        val itemValue = item.properties[sourceKey] ?: return@firstOrNull false
        itemValue == target.properties[targetKey]
    }?.id
}

/** Reads a numeric property, tolerating scenes that wrote it as text. */
private fun EntitySnapshot.num(key: String): Double? =
    (properties[key] as? PropertyValue.Num)?.value
        ?: (properties[key] as? PropertyValue.Text)?.value?.toDoubleOrNull()
