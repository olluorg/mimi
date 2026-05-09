package mimi.core.world

import mimi.core.command.Command
import mimi.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class DefaultWorldStateTest {

    private fun makeScene() = SceneDefinition(
        sceneId  = "test_scene",
        entities = listOf(
            EntityDefinition(
                id         = "apple",
                components = listOf("drag"),
                properties = mapOf("category" to PropertyValue.Text("fruit")),
                tags       = mapOf("group" to "fruits")
            ),
            EntityDefinition(
                id         = "basket",
                components = listOf("dropTarget"),
                properties = mapOf(
                    "acceptsCategory"  to PropertyValue.Text("fruit"),
                    "constraintType"   to PropertyValue.Text("propertyEquals"),
                    "position"         to PropertyValue.Vec2(300f, 400f)
                )
            )
        ),
        objectives = listOf(
            ObjectiveDefinition(id = "obj1", type = "allMatched", config = mapOf("group" to "fruits"))
        )
    )

    // ── Immutability ──────────────────────────────────────────────────────────

    @Test
    fun `apply returns new instance, original unchanged`() {
        val world = DefaultWorldStateFactory.fromScene(makeScene())
        val cmd   = Command.SetProperty(
            target = "apple", field = "color",
            value  = PropertyValue.Text("red"),
            source = "test", tick = 1L
        )
        val world2 = world.apply(listOf(cmd))

        assertNotSame(world, world2)
        assertNull(world.getProperty("apple", "color"))
        assertEquals(PropertyValue.Text("red"), world2.getProperty("apple", "color"))
    }

    // ── _scene entity ─────────────────────────────────────────────────────────

    @Test
    fun `_scene entity exists with default adaptive fields`() {
        val world = DefaultWorldStateFactory.fromScene(makeScene())
        val scene = world.getEntity(SCENE_ENTITY_ID)
        assertEquals(0.5, scene?.num("difficulty"))
        assertEquals(0.0, scene?.num("mistakes"))
        assertEquals(0.0, scene?.num("idleTicks"))
    }

    // ── SetProperty ───────────────────────────────────────────────────────────

    @Test
    fun `SetProperty updates entity property`() {
        val world = DefaultWorldStateFactory.fromScene(makeScene())
        val cmd   = Command.SetProperty("apple", "color", PropertyValue.Text("green"), source = "t", tick = 1L)
        val world2 = world.apply(listOf(cmd))
        assertEquals(PropertyValue.Text("green"), world2.getProperty("apple", "color"))
    }

    @Test
    fun `SetProperty on unknown entity is silently skipped`() {
        val world  = DefaultWorldStateFactory.fromScene(makeScene())
        val cmd    = Command.SetProperty("unknown_entity", "x", PropertyValue.Num(1.0), source = "t", tick = 1L)
        val world2 = world.apply(listOf(cmd))
        assertEquals(world.tick + 1, world2.tick)
    }

    // ── IncrementCounter ──────────────────────────────────────────────────────

    @Test
    fun `IncrementCounter accumulates correctly`() {
        val world  = DefaultWorldStateFactory.fromScene(makeScene())
        val cmd1   = Command.IncrementCounter(SCENE_ENTITY_ID, "mistakes", 1L, source = "t", tick = 1L)
        val world2 = world.apply(listOf(cmd1))
        val world3 = world2.apply(listOf(cmd1.copy(tick = 2L)))
        assertEquals(2L, world3.getCounter("mistakes"))
    }

    // ── MarkMatched ───────────────────────────────────────────────────────────

    @Test
    fun `MarkMatched sets dragged entity to TENTATIVE and records matchedTo`() {
        val world = DefaultWorldStateFactory.fromScene(makeScene())
        val cmd   = Command.MarkMatched(
            target = "basket", draggedEntityId = "apple", source = "snap", tick = 1L
        )
        val world2 = world.apply(listOf(cmd))
        assertEquals(EntityLifecycleState.TENTATIVE, world2.getEntityState("apple"))
        assertEquals(PropertyValue.Ref("basket"), world2.getProperty("apple", "matchedTo"))
    }

    // ── SetEntityState ────────────────────────────────────────────────────────

    @Test
    fun `LOCKED state cannot be overridden`() {
        val world  = DefaultWorldStateFactory.fromScene(makeScene())
        val lock   = Command.SetEntityState("apple", EntityLifecycleState.LOCKED, source = "t", tick = 1L)
        val active = Command.SetEntityState("apple", EntityLifecycleState.ACTIVE, source = "t", tick = 2L)
        val world2 = world.apply(listOf(lock))
        val world3 = world2.apply(listOf(active))
        assertEquals(EntityLifecycleState.LOCKED, world3.getEntityState("apple"))
    }

    // ── SetObjectiveState ─────────────────────────────────────────────────────

    @Test
    fun `SetObjectiveState transitions correctly`() {
        val world = DefaultWorldStateFactory.fromScene(makeScene())
        val cmd   = Command.SetObjectiveState("obj1", ObjectiveState.PROGRESSING, source = "t", tick = 1L)
        val world2 = world.apply(listOf(cmd))
        assertEquals(ObjectiveState.PROGRESSING, world2.getObjectiveState("obj1"))
    }

    @Test
    fun `LOCKED_COMPLETE objective cannot be overridden`() {
        val world  = DefaultWorldStateFactory.fromScene(makeScene())
        val lock   = Command.SetObjectiveState("obj1", ObjectiveState.LOCKED_COMPLETE, source = "t", tick = 1L)
        val revert = Command.SetObjectiveState("obj1", ObjectiveState.PROGRESSING, source = "t", tick = 2L)
        val world3 = world.apply(listOf(lock)).apply(listOf(revert))
        assertEquals(ObjectiveState.LOCKED_COMPLETE, world3.getObjectiveState("obj1"))
    }

    // ── Tick increment ────────────────────────────────────────────────────────

    @Test
    fun `tick increments on each apply`() {
        val world  = DefaultWorldStateFactory.fromScene(makeScene())
        assertEquals(0L, world.tick)
        val world2 = world.apply(emptyList())
        assertEquals(1L, world2.tick)
        val world3 = world2.apply(emptyList())
        assertEquals(2L, world3.tick)
    }
}
