package mimi.core.crl

import mimi.core.command.Command
import mimi.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConflictResolutionLayerTest {

    private fun makeSet(target: EntityId, field: PropertyKey, value: Double, priority: Int = 0, tick: Tick = 1L) =
        Command.SetProperty(target, field, PropertyValue.Num(value), priority, "test", tick)

    private fun makeIncrement(target: EntityId, field: PropertyKey, delta: Long, tick: Tick = 1L) =
        Command.IncrementCounter(target, field, delta, 0, "test", tick)

    // ── PRIORITY ──────────────────────────────────────────────────────────────

    @Test
    fun `priority - highest wins`() {
        val commands = listOf(
            makeSet("entity1", "position", 10.0, priority = 10),
            makeSet("entity1", "position", 99.0, priority = 100)
        )
        val (resolved, _) = ConflictResolutionLayer.resolve(commands)
        assertEquals(1, resolved.size)
        assertEquals(99.0, (resolved[0] as Command.SetProperty).let { (it.value as PropertyValue.Num).value })
    }

    @Test
    fun `priority - tie broken by buffer order (first wins)`() {
        val commands = listOf(
            makeSet("entity1", "position", 10.0, priority = 50),
            makeSet("entity1", "position", 99.0, priority = 50)
        )
        val (resolved, _) = ConflictResolutionLayer.resolve(commands)
        assertEquals(1, resolved.size)
        // maxByOrNull is stable — first element with max priority wins
        assertEquals(10.0, ((resolved[0] as Command.SetProperty).value as PropertyValue.Num).value)
    }

    // ── MERGE ─────────────────────────────────────────────────────────────────

    @Test
    fun `merge - increments are summed`() {
        val commands = listOf(
            makeIncrement(SCENE_ENTITY_ID, "mistakes", 1L),
            makeIncrement(SCENE_ENTITY_ID, "mistakes", 1L),
            makeIncrement(SCENE_ENTITY_ID, "mistakes", 1L)
        )
        val (resolved, _) = ConflictResolutionLayer.resolve(commands)
        assertEquals(1, resolved.size)
        assertEquals(3L, (resolved[0] as Command.IncrementCounter).delta)
    }

    // ── LAST_WINS ─────────────────────────────────────────────────────────────

    @Test
    fun `last wins - returns last command in buffer`() {
        val commands = listOf(
            Command.SetEntityState("entity1", EntityLifecycleState.ACTIVE, source = "b1", tick = 1L),
            Command.SetEntityState("entity1", EntityLifecycleState.INACTIVE, source = "b2", tick = 1L)
        )
        val (resolved, _) = ConflictResolutionLayer.resolve(commands)
        assertEquals(1, resolved.size)
        assertEquals(EntityLifecycleState.INACTIVE, (resolved[0] as Command.SetEntityState).state)
    }

    // ── FIRST_WINS ────────────────────────────────────────────────────────────

    @Test
    fun `first wins - returns first command in buffer for locked field`() {
        val commands = listOf(
            Command.SetProperty("entity1", "locked", PropertyValue.Flag(true), source = "b1", tick = 1L),
            Command.SetProperty("entity1", "locked", PropertyValue.Flag(false), source = "b2", tick = 1L)
        )
        val (resolved, _) = ConflictResolutionLayer.resolve(commands)
        assertEquals(1, resolved.size)
        assertEquals(true, ((resolved[0] as Command.SetProperty).value as PropertyValue.Flag).value)
    }

    // ── FEEDBACK BYPASS ───────────────────────────────────────────────────────

    @Test
    fun `feedback commands bypass CRL`() {
        val fb = Command.TriggerFeedback("sound", mapOf("path" to "success.ogg"), source = "b1", tick = 1L)
        val set = makeSet("entity1", "state", 1.0)
        val (resolved, feedback) = ConflictResolutionLayer.resolve(listOf(fb, set))
        assertEquals(1, resolved.size)
        assertEquals(1, feedback.size)
        assertEquals("sound", feedback[0].feedbackType)
    }

    // ── EMPTY BUFFER ──────────────────────────────────────────────────────────

    @Test
    fun `empty buffer returns empty output`() {
        val (resolved, feedback) = ConflictResolutionLayer.resolve(emptyList())
        assertTrue(resolved.isEmpty())
        assertTrue(feedback.isEmpty())
    }

    // ── NO CONFLICT ───────────────────────────────────────────────────────────

    @Test
    fun `no conflict - different fields pass through unchanged`() {
        val commands = listOf(
            makeSet("e1", "position", 10.0),
            makeSet("e2", "position", 20.0),
            makeSet("e1", "visibility", 1.0)
        )
        val (resolved, _) = ConflictResolutionLayer.resolve(commands)
        assertEquals(3, resolved.size)
    }
}
