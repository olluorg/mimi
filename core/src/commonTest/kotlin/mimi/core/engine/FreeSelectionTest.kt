package mimi.core.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.model.BehaviorRef
import mimi.core.model.PropertyValue
import mimi.core.model.SCENE_ENTITY_ID
import mimi.core.world.EntityDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneBehaviorBinding
import mimi.core.world.SceneDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mechanics behind games where nothing can be got wrong: choosing, and
 * cause-and-effect play.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FreeSelectionTest {

    private fun option(id: String, name: String, x: Float, group: String = "choices") = EntityDefinition(
        id         = id,
        components = listOf("tap"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 400f),
            "name"     to PropertyValue.Text(name)
        ),
        tags = mapOf("group" to group)
    )

    private fun session(scene: SceneDefinition, scope: kotlinx.coroutines.CoroutineScope) =
        EngineBuilder().withDefaultPlugins(defaultConstraintRegistry()).build(scene, scope)

    private fun choiceScene() = SceneDefinition(
        sceneId  = "choice_test",
        entities = listOf(option("juice", "juice", 300f), option("water", "water", 700f)),
        objectives = listOf(
            ObjectiveDefinition(id = "chose", type = "anySelected", config = mapOf("group" to "choices"))
        ),
        behaviors = listOf(
            SceneBehaviorBinding(BehaviorRef("progressTracker")),
            SceneBehaviorBinding(BehaviorRef("freeSelection", mapOf("group" to "choices")))
        )
    )

    @Test
    fun `either option completes the scene`() = runTest {
        val session = session(choiceScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(700f, 400f), tick = 0L, source = "water"))
        runCurrent()

        assertTrue(session.worldState.value.allLevelsComplete(), "choosing water is a valid answer")
    }

    @Test
    fun `choosing is never counted as a mistake`() = runTest {
        val session = session(choiceScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(300f, 400f), tick = 0L, source = "juice"))
        runCurrent()

        assertEquals(
            0, session.metrics().mistakes,
            "a choice game has no wrong answer, so nothing may be scored against the child"
        )
    }

    @Test
    fun `what the child chose is recorded for the adult`() = runTest {
        val session = session(choiceScene(), backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(300f, 400f), tick = 0L, source = "juice"))
        runCurrent()

        assertEquals(
            PropertyValue.Text("juice"),
            session.worldState.value.getProperty(SCENE_ENTITY_ID, "lastChoice"),
            "the point of a choice game is knowing which option was picked"
        )
    }

    @Test
    fun `a tap outside the offered group is ignored`() = runTest {
        val scene = choiceScene().copy(
            entities = choiceScene().entities + option("decor", "decor", 900f, group = "background")
        )
        val session = session(scene, backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(900f, 400f), tick = 0L, source = "decor"))
        runCurrent()

        assertTrue(!session.worldState.value.allLevelsComplete(), "scenery is not an option")
    }

    @Test
    fun `cause and effect play ends after enough turns`() = runTest {
        val scene = SceneDefinition(
            sceneId  = "pop_test",
            entities = listOf(
                option("b1", "bubble", 250f, group = "bubbles"),
                option("b2", "bubble", 500f, group = "bubbles"),
                option("b3", "bubble", 750f, group = "bubbles")
            ),
            objectives = listOf(
                ObjectiveDefinition(id = "played", type = "interactionCount", config = mapOf("count" to "3"))
            ),
            behaviors = listOf(
                SceneBehaviorBinding(BehaviorRef("progressTracker")),
                SceneBehaviorBinding(
                    BehaviorRef("freeSelection", mapOf("group" to "bubbles", "repeatable" to "true"))
                )
            )
        )
        val session = session(scene, backgroundScope)
        runCurrent()

        repeat(2) {
            session.dispatch(GameEvent.Tap(PropertyValue.Vec2(250f, 400f), tick = 0L, source = "b1"))
            runCurrent()
        }
        assertTrue(!session.worldState.value.allLevelsComplete(), "two turns is not yet three")

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(250f, 400f), tick = 0L, source = "b1"))
        runCurrent()

        assertTrue(
            session.worldState.value.allLevelsComplete(),
            "a repeatable target can be played with over and over"
        )
    }
}
