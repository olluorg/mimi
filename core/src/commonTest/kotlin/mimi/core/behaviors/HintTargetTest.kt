package mimi.core.behaviors

import mimi.core.model.PropertyValue
import mimi.core.world.DefaultWorldStateFactory
import mimi.core.world.EntityDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A hint has to point at something.
 *
 * Every scene in the catalog binds the hint globally, with no entity attached,
 * so it named nothing and the screen highlighted nothing: the child who was
 * stuck heard a chime and saw no change. These check that the next move is
 * worked out from the world, and that it stays quiet when it cannot be sure.
 */
class HintTargetTest {

    private fun world(vararg entities: EntityDefinition) =
        DefaultWorldStateFactory.fromScene(
            SceneDefinition(
                sceneId = "hint_test",
                entities = entities.toList(),
                objectives = listOf(ObjectiveDefinition("win", "allMatched", mapOf("group" to "items")))
            )
        ).snapshot()

    private fun item(id: String, components: List<String>, props: Map<String, PropertyValue> = emptyMap()) =
        EntityDefinition(
            id = id, components = components,
            properties = mapOf("position" to PropertyValue.Vec2(100f, 100f)) + props,
            tags = mapOf("group" to "items")
        )

    private fun basket(id: String, key: String, value: String, component: String = "dropTarget") =
        EntityDefinition(
            id = id, components = listOf(component),
            properties = mapOf(
                "position"       to PropertyValue.Vec2(500f, 700f),
                "constraintType" to PropertyValue.Text("propertyEquals"),
                "source"         to PropertyValue.Text(key),
                "target"         to PropertyValue.Text(key),
                key              to PropertyValue.Text(value)
            )
        )

    @Test
    fun `a piece to drag is offered along with the basket it belongs in`() {
        val move = HintTargets.nextMove(
            world(
                item("apple", listOf("drag"), mapOf("kind" to PropertyValue.Text("food"))),
                basket("food_bin", "kind", "food"),
                basket("toy_bin", "kind", "toys")
            )
        )
        assertNotNull(move)
        assertEquals("apple", move.source)
        assertEquals("food_bin", move.destination, "the hint should show where the piece goes")
    }

    @Test
    fun `a line to draw starts from its source end`() {
        val move = HintTargets.nextMove(
            world(
                item("rabbit", listOf("connectSource"), mapOf("eats" to PropertyValue.Text("carrot"))),
                basket("carrot", "eats", "carrot", component = "connectTarget")
            )
        )
        assertNotNull(move)
        assertEquals("rabbit", move.source)
        assertEquals("carrot", move.destination)
    }

    @Test
    fun `a sequence points at the step that comes next`() {
        val move = HintTargets.nextMove(
            world(
                item("third",  listOf("tap"), mapOf("sequenceIndex" to PropertyValue.Num(3.0))),
                item("first",  listOf("tap"), mapOf("sequenceIndex" to PropertyValue.Num(1.0))),
                item("second", listOf("tap"), mapOf("sequenceIndex" to PropertyValue.Num(2.0)))
            )
        )
        assertEquals("first", move?.source, "the lowest untouched index is what comes next")
    }

    @Test
    fun `a path to trace is pointed at`() {
        val move = HintTargets.nextMove(
            world(
                EntityDefinition(
                    id = "path", components = listOf("traceTarget"),
                    properties = mapOf(
                        "waypoints" to PropertyValue.PointList(
                            listOf(mimi.core.model.Waypoint(10f, 10f), mimi.core.model.Waypoint(90f, 90f))
                        )
                    )
                )
            )
        )
        assertEquals("path", move?.source)
    }

    @Test
    fun `nothing is pointed at when the answer is not in the entities`() {
        // "Find the cat": which tile is right lives in the objective, not on the
        // tiles. Pointing confidently at a wrong one is worse than staying quiet.
        val move = HintTargets.nextMove(
            world(
                item("cat", listOf("tap"), mapOf("name" to PropertyValue.Text("cat"))),
                item("car", listOf("tap"), mapOf("name" to PropertyValue.Text("car")))
            )
        )
        assertNull(move)
    }

    @Test
    fun `no destination is offered when no target accepts the piece`() {
        val move = HintTargets.nextMove(
            world(
                item("apple", listOf("drag"), mapOf("kind" to PropertyValue.Text("food"))),
                basket("toy_bin", "kind", "toys")
            )
        )
        assertEquals("apple", move?.source)
        assertNull(move?.destination, "a guessed pairing would teach the wrong one")
    }

    @Test
    fun `an empty scene yields no hint`() {
        assertNull(HintTargets.nextMove(world()))
    }
}
