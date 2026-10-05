package mimi.core.objective

import mimi.core.model.EntityLifecycleState
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.WorldSnapshot

/**
 * How far through a scene the child is.
 *
 * A game that ends after eight presses looks, from the child's side, exactly
 * like a game that never ends: nothing on screen says a goal exists, how close
 * they are, or that finishing is even possible. This turns the objective into
 * something the screen can show.
 */
data class SceneProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val isComplete: Boolean get() = total > 0 && done >= total
}

object ObjectiveProgress {

    /**
     * Countable progress for [objectives], or null when there is nothing honest
     * to show — "find the cat" is one decision, not a journey, and a half-filled
     * bar would be a lie about it.
     */
    fun of(objectives: List<ObjectiveDefinition>, world: WorldSnapshot): SceneProgress? =
        objectives.firstNotNullOfOrNull { single(it, world) }

    private fun single(objective: ObjectiveDefinition, world: WorldSnapshot): SceneProgress? =
        when (objective.type) {
            "interactionCount" -> {
                val target = objective.config["count"]?.toIntOrNull()
                if (target == null || target <= 0) null
                else SceneProgress(world.getCounter("selections").toInt().coerceAtMost(target), target)
            }

            "allMatched", "allPairsMatched", "sequenceCompleted", "traceCompleted" ->
                groupProgress(objective.config["group"], world)

            "allSorted" -> {
                val tag = objective.config["tag"]
                if (tag == null) null
                else countSettled(world.getAllEntities().filter { tag in it.tags })
            }

            "sequenceComplete" -> {
                val ids = objective.config["sequence"]
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: return null
                if (ids.isEmpty()) null
                else SceneProgress(ids.count { isSettled(world, it) }, ids.size)
            }

            // entitySelected, anySelected, geometrySelection, traceComplete:
            // one decision, with no meaningful midpoint.
            else -> null
        }

    private fun groupProgress(group: String?, world: WorldSnapshot): SceneProgress? {
        if (group == null) return null
        return countSettled(world.getEntitiesByTag("group", group))
    }

    private fun countSettled(entities: List<mimi.core.model.EntitySnapshot>): SceneProgress? {
        if (entities.isEmpty()) return null
        val done = entities.count {
            it.state == EntityLifecycleState.TENTATIVE || it.state == EntityLifecycleState.LOCKED
        }
        return SceneProgress(done, entities.size)
    }

    private fun isSettled(world: WorldSnapshot, id: String): Boolean =
        world.getEntityState(id).let {
            it == EntityLifecycleState.TENTATIVE || it == EntityLifecycleState.LOCKED
        }
}
