package mimi.core.objective

import mimi.core.model.EntityLifecycleState
import mimi.core.model.PropertyValue
import mimi.core.model.SCENE_ENTITY_ID
import mimi.core.command.Command
import mimi.core.world.DefaultWorldStateFactory
import mimi.core.world.EntityDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObjectiveProgressTest {

    private fun tile(id: String, group: String = "items") = EntityDefinition(
        id = id, components = listOf("tap"),
        properties = mapOf("position" to PropertyValue.Vec2(0f, 0f)),
        tags = mapOf("group" to group)
    )

    private fun world(
        entities: List<EntityDefinition>,
        settled: List<String> = emptyList(),
        selections: Long = 0
    ) = DefaultWorldStateFactory
        .fromScene(SceneDefinition(sceneId = "p", entities = entities))
        .apply(
            settled.map {
                Command.SetEntityState(it, EntityLifecycleState.TENTATIVE, source = "t", tick = 1)
            } + if (selections > 0) listOf(
                Command.IncrementCounter(SCENE_ENTITY_ID, "selections", selections, source = "t", tick = 1)
            ) else emptyList()
        )
        .snapshot()

    private fun progress(type: String, config: Map<String, String>, world: mimi.core.world.WorldSnapshot) =
        ObjectiveProgress.of(listOf(ObjectiveDefinition("o", type, config)), world)

    @Test
    fun `a game counted in presses reports how many are left`() {
        val w = world(listOf(tile("a")), selections = 3)
        val p = progress("interactionCount", mapOf("count" to "8"), w)
        assertEquals(SceneProgress(3, 8), p)
        assertTrue(!p!!.isComplete)
    }

    @Test
    fun `presses beyond the target do not overflow the display`() {
        val w = world(listOf(tile("a")), selections = 12)
        assertEquals(SceneProgress(8, 8), progress("interactionCount", mapOf("count" to "8"), w))
    }

    @Test
    fun `sorting reports pieces placed out of pieces offered`() {
        val tiles = listOf(tile("a"), tile("b"), tile("c"))
        val p = progress("allMatched", mapOf("group" to "items"), world(tiles, settled = listOf("a", "b")))
        assertEquals(SceneProgress(2, 3), p)
        assertEquals(2f / 3f, p!!.fraction)
    }

    @Test
    fun `an untouched scene starts at zero rather than hiding the goal`() {
        val p = progress("allMatched", mapOf("group" to "items"), world(listOf(tile("a"), tile("b"))))
        assertEquals(SceneProgress(0, 2), p, "the child should see the goal before starting it")
    }

    @Test
    fun `a single decision reports nothing rather than a misleading half`() {
        // "Find the cat" is one choice. A half-filled bar would claim a midpoint
        // the game does not have.
        val w = world(listOf(tile("cat"), tile("car")))
        assertNull(progress("entitySelected", mapOf("property" to "name", "value" to "cat"), w))
        assertNull(progress("anySelected", mapOf("group" to "items"), w))
        assertNull(progress("geometrySelection", mapOf("group" to "items"), w))
    }

    @Test
    fun `a named sequence counts its own steps`() {
        val tiles = listOf(tile("one"), tile("two"), tile("three"))
        val p = progress(
            "sequenceComplete", mapOf("sequence" to "one,two,three"),
            world(tiles, settled = listOf("one"))
        )
        assertEquals(SceneProgress(1, 3), p)
    }

    @Test
    fun `a malformed target is treated as nothing to show`() {
        val w = world(listOf(tile("a")))
        assertNull(progress("interactionCount", mapOf("count" to "0"), w))
        assertNull(progress("interactionCount", mapOf("count" to "many"), w))
        assertNull(progress("allMatched", emptyMap(), w))
        assertNull(progress("allMatched", mapOf("group" to "nobody"), w))
    }

    @Test
    fun `the first objective that can be counted is the one shown`() {
        val tiles = listOf(tile("a"), tile("b"))
        val p = ObjectiveProgress.of(
            listOf(
                ObjectiveDefinition("pick", "entitySelected", mapOf("property" to "n", "value" to "x")),
                ObjectiveDefinition("all", "allMatched", mapOf("group" to "items"))
            ),
            world(tiles, settled = listOf("a"))
        )
        assertEquals(SceneProgress(1, 2), p)
    }
}
