package mimi.core.objective

import mimi.core.behavior.GameEvent
import mimi.core.command.Command
import mimi.core.model.*
import mimi.core.world.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectiveSystemTest {

    private val system = ObjectiveSystem()

    private fun worldWithEntities(vararg entities: Pair<EntityId, EntityLifecycleState>): DefaultWorldState {
        val scene = SceneDefinition(
            sceneId  = "test",
            entities = entities.map { (id, _) ->
                EntityDefinition(id = id, tags = mapOf("group" to "fruits"))
            },
            objectives = listOf(
                ObjectiveDefinition(id = "obj1", type = "allMatched", config = mapOf("group" to "fruits"))
            )
        )
        val base = DefaultWorldStateFactory.fromScene(scene)
        val cmds = entities
            .filter { (_, state) -> state != EntityLifecycleState.ACTIVE }
            .map { (id, state) ->
                Command.SetEntityState(id, state, source = "test", tick = 1L)
            }
        return (if (cmds.isEmpty()) base else base.apply(cmds)) as DefaultWorldState
    }

    // ── allMatched ────────────────────────────────────────────────────────────

    @Test
    fun `allMatched - INACTIVE when no entities matched`() {
        val world = worldWithEntities("apple" to EntityLifecycleState.ACTIVE, "banana" to EntityLifecycleState.ACTIVE)
        val defs  = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "fruits")))
        val result = system.evaluate(defs, world, mapOf("obj1" to ObjectiveState.INACTIVE), tick = 1L)
        assertEquals(ObjectiveState.INACTIVE, result.newStates["obj1"])
    }

    @Test
    fun `allMatched - PROGRESSING when some entities matched`() {
        val world = worldWithEntities("apple" to EntityLifecycleState.TENTATIVE, "banana" to EntityLifecycleState.ACTIVE)
        val defs  = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "fruits")))
        val result = system.evaluate(defs, world, mapOf("obj1" to ObjectiveState.INACTIVE), tick = 1L)
        assertEquals(ObjectiveState.PROGRESSING, result.newStates["obj1"])
    }

    @Test
    fun `allMatched - LOCKED_COMPLETE when all entities matched (single objective auto-locks)`() {
        val world = worldWithEntities("apple" to EntityLifecycleState.TENTATIVE, "banana" to EntityLifecycleState.TENTATIVE)
        val defs  = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "fruits")))
        val result = system.evaluate(defs, world, mapOf("obj1" to ObjectiveState.PROGRESSING), tick = 1L)
        // Single objective reaching TENTATIVE_COMPLETE immediately transitions to LOCKED_COMPLETE
        // because allComplete=true → scene locks in the same evaluation pass
        assertEquals(ObjectiveState.LOCKED_COMPLETE, result.newStates["obj1"])
    }

    // ── Reversion ─────────────────────────────────────────────────────────────

    @Test
    fun `allMatched - TENTATIVE_COMPLETE reverts to PROGRESSING when entity un-matched`() {
        val world = worldWithEntities("apple" to EntityLifecycleState.TENTATIVE, "banana" to EntityLifecycleState.ACTIVE)
        val defs  = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "fruits")))
        val result = system.evaluate(defs, world, mapOf("obj1" to ObjectiveState.TENTATIVE_COMPLETE), tick = 2L)
        assertEquals(ObjectiveState.PROGRESSING, result.newStates["obj1"])
        val revertedEvent = result.systemEvents.filterIsInstance<GameEvent.ObjectiveReverted>()
        assertTrue(revertedEvent.isNotEmpty())
    }

    // ── Lock on all complete ──────────────────────────────────────────────────

    @Test
    fun `SceneCompleted emitted when all objectives reach TENTATIVE_COMPLETE`() {
        val world = worldWithEntities("apple" to EntityLifecycleState.TENTATIVE, "banana" to EntityLifecycleState.TENTATIVE)
        val defs  = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "fruits")))
        val result = system.evaluate(defs, world, mapOf("obj1" to ObjectiveState.PROGRESSING), tick = 5L)
        val completed = result.systemEvents.filterIsInstance<GameEvent.SceneCompleted>()
        assertTrue(completed.isNotEmpty())
        assertEquals(ObjectiveState.LOCKED_COMPLETE, result.newStates["obj1"])
    }

    // ── LOCKED_COMPLETE is terminal ───────────────────────────────────────────

    @Test
    fun `LOCKED_COMPLETE objective is never re-evaluated`() {
        val world = worldWithEntities("apple" to EntityLifecycleState.ACTIVE)
        val defs  = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "fruits")))
        val result = system.evaluate(defs, world, mapOf("obj1" to ObjectiveState.LOCKED_COMPLETE), tick = 10L)
        assertEquals(ObjectiveState.LOCKED_COMPLETE, result.newStates["obj1"])
        assertTrue(result.systemEvents.isEmpty())
    }

    // ── Prerequisite chain ────────────────────────────────────────────────────

    @Test
    fun `objective with unmet prerequisite stays INACTIVE`() {
        val world = worldWithEntities("apple" to EntityLifecycleState.TENTATIVE, "banana" to EntityLifecycleState.TENTATIVE)
        val defs = listOf(
            ObjectiveDefinition("phase1", "allMatched", mapOf("group" to "fruits")),
            ObjectiveDefinition("phase2", "allMatched", mapOf("group" to "fruits"), requires = "phase1")
        )
        val prevStates = mapOf("phase1" to ObjectiveState.PROGRESSING, "phase2" to ObjectiveState.INACTIVE)
        val result = system.evaluate(defs, world, prevStates, tick = 1L)
        assertEquals(ObjectiveState.INACTIVE, result.newStates["phase2"])
    }
}
