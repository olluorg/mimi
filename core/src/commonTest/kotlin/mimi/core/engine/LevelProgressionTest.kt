package mimi.core.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.model.BehaviorRef
import mimi.core.model.PropertyValue
import mimi.core.world.EntityDefinition
import mimi.core.world.LevelDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneBehaviorBinding
import mimi.core.world.SceneDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LevelProgressionTest {

    private fun dragItem(id: String, category: String) = EntityDefinition(
        id         = id,
        components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(200f, 250f),
            "category" to PropertyValue.Text(category)
        ),
        tags = mapOf("group" to "items")
    )

    private fun basket(id: String, category: String) = EntityDefinition(
        id         = id,
        components = listOf("dropTarget"),
        properties = mapOf(
            "position"        to PropertyValue.Vec2(500f, 700f),
            "constraintType"  to PropertyValue.Text("propertyEquals"),
            "source"          to PropertyValue.Text("category"),
            "target"          to PropertyValue.Text("acceptsCategory"),
            "acceptsCategory" to PropertyValue.Text(category)
        )
    )

    /** Each level also carries a basket the item does not belong in, so a wrong drop is possible. */
    private fun level(id: String, itemId: String, basketId: String) = LevelDefinition(
        levelId    = id,
        entities   = listOf(
            dragItem(itemId, "fruit"),
            basket(basketId, "fruit"),
            basket("${basketId}_wrong", "veg")
        ),
        objectives = listOf(ObjectiveDefinition(id = "win", type = "allMatched", config = mapOf("group" to "items")))
    )

    private fun twoLevelScene() = SceneDefinition(
        sceneId   = "levels_test",
        behaviors = listOf(
            SceneBehaviorBinding(BehaviorRef("progressTracker")),
            SceneBehaviorBinding(BehaviorRef("snapToTarget"))
        ),
        levels = listOf(
            level("l1", "apple", "basket_a"),
            level("l2", "pear", "basket_b")
        )
    )

    private fun singleLevelScene() = SceneDefinition(
        sceneId   = "single_test",
        entities  = listOf(
            dragItem("apple", "fruit"),
            basket("basket_a", "fruit"),
            basket("basket_wrong", "veg")
        ),
        objectives = listOf(ObjectiveDefinition(id = "win", type = "allMatched", config = mapOf("group" to "items"))),
        behaviors = listOf(
            SceneBehaviorBinding(BehaviorRef("progressTracker")),
            SceneBehaviorBinding(BehaviorRef("snapToTarget"))
        )
    )

    private fun session(scene: SceneDefinition, scope: kotlinx.coroutines.CoroutineScope): EngineSession =
        EngineBuilder()
            .withDefaultPlugins(defaultConstraintRegistry())
            .build(scene, scope)

    @Test
    fun `completing the last level marks the scene complete without extra input`() = runTest {
        val session = session(twoLevelScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Drop("apple", "basket_a", tick = 0L))
        runCurrent()

        // The level hand-off holds for a beat so the child sees the success.
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(1, session.worldState.value.currentLevel(), "should be on the second level")

        session.dispatch(GameEvent.Drop("pear", "basket_b", tick = 0L))
        runCurrent()

        assertTrue(
            session.worldState.value.allLevelsComplete(),
            "the scene should report completion once the final level is solved, " +
                "without waiting for another tap"
        )
    }

    @Test
    fun `single level scene completes on its own`() = runTest {
        val session = session(singleLevelScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Drop("apple", "basket_a", tick = 0L))
        runCurrent()

        assertTrue(session.worldState.value.allLevelsComplete(), "scene should be complete")
    }

    @Test
    fun `mistakes and hints accumulate across level boundaries`() = runTest {
        val session = session(twoLevelScene(), backgroundScope)
        runCurrent()

        // Wrong basket on level one: the constraint rejects it and it counts as a mistake.
        session.dispatch(GameEvent.Drop("apple", "basket_a_wrong", tick = 0L))
        runCurrent()

        session.dispatch(GameEvent.Drop("apple", "basket_a", tick = 0L))
        runCurrent()
        advanceTimeBy(1_500L)
        runCurrent()

        assertEquals(1, session.worldState.value.currentLevel())
        assertEquals(
            1, session.metrics().mistakes,
            "a mistake made on level one must survive the level reset"
        )
        assertEquals(1, session.metrics().levelsCompleted)
    }

    @Test
    fun `reset clears accumulated metrics`() = runTest {
        val session = session(singleLevelScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Drop("apple", "basket_wrong", tick = 0L))
        runCurrent()
        assertEquals(1, session.metrics().mistakes)

        session.reset()
        runCurrent()

        assertEquals(0, session.metrics().mistakes, "reset should start the attempt over")
    }
}
