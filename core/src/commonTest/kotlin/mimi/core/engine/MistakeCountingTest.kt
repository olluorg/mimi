package mimi.core.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.model.BehaviorRef
import mimi.core.model.PropertyValue
import mimi.core.world.EntityDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneBehaviorBinding
import mimi.core.world.SceneDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every mechanic has to feed the same counters.
 *
 * The adaptive layer and the adult progress view are both scored on mistakes and
 * hints. A mechanic that stays silent about them looks effortless in the
 * reports no matter how much the child struggled, so each one is checked here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MistakeCountingTest {

    private fun tapChoice(id: String, name: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("tap"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 400f),
            "name"     to PropertyValue.Text(name)
        ),
        tags = mapOf("group" to "choices")
    )

    private fun session(scene: SceneDefinition, scope: kotlinx.coroutines.CoroutineScope) =
        EngineBuilder().withDefaultPlugins(defaultConstraintRegistry()).build(scene, scope)

    @Test
    fun `tapping the wrong choice counts as a mistake`() = runTest {
        val scene = SceneDefinition(
            sceneId  = "find_test",
            entities = listOf(tapChoice("cat", "cat", 200f), tapChoice("car", "car", 500f)),
            objectives = listOf(
                ObjectiveDefinition(
                    id     = "win",
                    type   = "entitySelected",
                    config = mapOf("property" to "name", "value" to "cat")
                )
            ),
            behaviors = listOf(
                SceneBehaviorBinding(BehaviorRef("progressTracker")),
                SceneBehaviorBinding(
                    BehaviorRef("propertySelectionValidator", mapOf("property" to "name", "expected" to "cat"))
                )
            )
        )
        val session = session(scene, backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(500f, 400f), tick = 0L, source = "car"))
        runCurrent()
        assertEquals(1, session.metrics().mistakes, "a wrong tap must be recorded")

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(200f, 400f), tick = 0L, source = "cat"))
        runCurrent()
        assertEquals(1, session.metrics().mistakes)
        assertEquals(1, session.metrics().matches, "the correct tap must be recorded too")
    }

    @Test
    fun `tapping out of sequence counts as a mistake`() = runTest {
        fun step(id: String, index: Int, x: Float) = EntityDefinition(
            id         = id,
            components = listOf("tap"),
            properties = mapOf(
                "position"      to PropertyValue.Vec2(x, 400f),
                "sequenceIndex" to PropertyValue.Num(index.toDouble())
            ),
            tags = mapOf("group" to "steps")
        )

        val scene = SceneDefinition(
            sceneId  = "sequence_test",
            entities = listOf(step("one", 1, 200f), step("two", 2, 500f), step("three", 3, 800f)),
            objectives = listOf(
                ObjectiveDefinition(id = "win", type = "sequenceCompleted", config = mapOf("group" to "steps"))
            ),
            behaviors = listOf(
                SceneBehaviorBinding(BehaviorRef("progressTracker")),
                SceneBehaviorBinding(BehaviorRef("sequenceValidator", mapOf("group" to "steps")))
            )
        )
        val session = session(scene, backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(800f, 400f), tick = 0L, source = "three"))
        runCurrent()

        assertEquals(1, session.metrics().mistakes, "skipping ahead in a sequence is a mistake")
    }

    @Test
    fun `a wrong memory pair is counted once rather than twice`() = runTest {
        fun card(id: String, pair: String, x: Float) = EntityDefinition(
            id         = id,
            components = listOf("tap"),
            properties = mapOf(
                "position"  to PropertyValue.Vec2(x, 400f),
                "pairId"    to PropertyValue.Text(pair),
                "cardState" to PropertyValue.Text("hidden")
            ),
            tags = mapOf("group" to "cards")
        )

        val scene = SceneDefinition(
            sceneId  = "memory_test",
            entities = listOf(
                card("a1", "a", 200f), card("a2", "a", 400f),
                card("b1", "b", 600f), card("b2", "b", 800f)
            ),
            objectives = listOf(
                ObjectiveDefinition(id = "win", type = "allPairsMatched", config = mapOf("group" to "cards"))
            ),
            behaviors = listOf(
                SceneBehaviorBinding(BehaviorRef("progressTracker")),
                SceneBehaviorBinding(BehaviorRef("memoryFlipController", mapOf("group" to "cards")))
            )
        )
        val session = session(scene, backgroundScope)
        runCurrent()

        // Two cards that do not pair.
        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(200f, 400f), tick = 0L, source = "a1"))
        runCurrent()
        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(600f, 400f), tick = 0L, source = "b1"))
        runCurrent()

        assertEquals(
            1, session.metrics().mistakes,
            "the behavior used to increment the counter itself as well as emitting the " +
                "feedback the tracker counts, so every miss was scored twice"
        )
    }

    @Test
    fun `picking the wrong size counts as a mistake`() = runTest {
        fun sized(id: String, radius: Double, x: Float) = EntityDefinition(
            id         = id,
            components = listOf("tap"),
            properties = mapOf(
                "position" to PropertyValue.Vec2(x, 400f),
                "radius"   to PropertyValue.Num(radius)
            ),
            tags = mapOf("group" to "choices")
        )

        val scene = SceneDefinition(
            sceneId  = "size_test",
            entities = listOf(sized("big", 120.0, 250f), sized("small", 40.0, 700f)),
            objectives = listOf(
                ObjectiveDefinition(
                    id     = "win",
                    type   = "geometrySelection",
                    config = mapOf("group" to "choices", "select" to "largest")
                )
            ),
            behaviors = listOf(
                SceneBehaviorBinding(BehaviorRef("progressTracker")),
                SceneBehaviorBinding(
                    BehaviorRef("geometrySelectionValidator", mapOf("group" to "choices", "select" to "largest"))
                )
            )
        )
        val session = session(scene, backgroundScope)
        runCurrent()

        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(700f, 400f), tick = 0L, source = "small"))
        runCurrent()

        assertEquals(1, session.metrics().mistakes, "choosing the smaller shape when asked for the largest")
    }
}
