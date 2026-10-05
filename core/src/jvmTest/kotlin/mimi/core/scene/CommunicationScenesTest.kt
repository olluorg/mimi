package mimi.core.scene

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.engine.EngineBuilder
import mimi.core.engine.EngineSession
import mimi.core.engine.defaultConstraintRegistry
import mimi.core.engine.withDefaultPlugins
import mimi.core.model.EntityLifecycleState
import mimi.core.model.PropertyValue
import mimi.core.model.SCENE_ENTITY_ID
import mimi.core.world.SceneDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plays the communication-ladder scenes for real.
 *
 * Validation proves a scene is well-formed; it cannot prove a child can finish
 * it. These games are the entry point for children who have no other way to be
 * understood, so each one is driven to completion the way a child would.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunicationScenesTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    private fun load(sceneId: String): SceneDefinition =
        SceneLoader.load(File(File(catalogDir, sceneId), "$sceneId.json").readText())

    private fun play(scene: SceneDefinition, scope: CoroutineScope): EngineSession =
        EngineBuilder().withDefaultPlugins(defaultConstraintRegistry()).build(scene, scope)

    private suspend fun EngineSession.tap(entityId: String) =
        dispatch(GameEvent.Tap(PropertyValue.Vec2(0f, 0f), tick = 0L, source = entityId))

    @Test
    fun `popping every bubble finishes the cause and effect scene`() = runTest {
        val scene = load("cause_effect_bubbles_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        val bubbles = scene.entities.filter { it.tags["group"] == "bubbles" }
        assertTrue(bubbles.size >= 3, "a starter scene needs several targets to touch")

        bubbles.forEach { session.tap(it.id); runCurrent() }

        assertTrue(session.worldState.value.allLevelsComplete(), "every bubble popped should end the game")
        assertEquals(0, session.metrics().mistakes, "there is nothing to get wrong here")

        // Popping has to be visible, otherwise the child never sees they did it.
        bubbles.forEach {
            assertEquals(
                EntityLifecycleState.INACTIVE,
                session.worldState.value.getEntityState(it.id),
                "bubble '${it.id}' should disappear when touched"
            )
        }
    }

    @Test
    fun `each choice scene accepts every option on offer`() = runTest {
        val choiceScenes = listOf("choice_drink_01", "choice_snack_01", "choice_play_01", "yes_no_01")

        for (sceneId in choiceScenes) {
            val scene = load(sceneId)
            val options = scene.entities.filter { it.tags["group"] == "choices" }
            assertTrue(options.size >= 2, "$sceneId must offer a real choice")

            for (option in options) {
                val session = play(scene, backgroundScope)
                runCurrent()
                session.tap(option.id)
                runCurrent()

                assertTrue(
                    session.worldState.value.allLevelsComplete(),
                    "$sceneId: choosing '${option.id}' must be accepted — a choice game has no wrong answer"
                )
                assertEquals(
                    0, session.metrics().mistakes,
                    "$sceneId: choosing '${option.id}' must not be scored against the child"
                )
                session.close()
            }
        }
    }

    @Test
    fun `a choice is recorded so the adult can see a preference`() = runTest {
        val scene = load("choice_drink_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        session.tap("water")
        runCurrent()

        assertEquals(
            PropertyValue.Text("вода"),
            session.worldState.value.getProperty(SCENE_ENTITY_ID, "lastChoice"),
            "which drink was chosen is the entire output of this game"
        )
    }

    @Test
    fun `each request brings one more balloon into view`() = runTest {
        val scene = load("request_more_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        val rewards = scene.entities.filter { it.tags["group"] == "reward" }.map { it.id }
        assertTrue(rewards.isNotEmpty(), "asking has to produce something")

        fun visible() = rewards.count {
            session.worldState.value.getEntityState(it) != EntityLifecycleState.INACTIVE
        }

        assertEquals(0, visible(), "nothing should arrive before the child asks")

        session.tap("more")
        runCurrent()
        assertEquals(1, visible(), "one request, one balloon")

        session.tap("more")
        runCurrent()
        assertEquals(2, visible(), "asking again brings another")

        repeat(rewards.size - 2) { session.tap("more"); runCurrent() }
        assertTrue(session.worldState.value.allLevelsComplete(), "the scene ends once all are earned")
    }

    @Test
    fun `routine scenes only accept their steps in the real world order`() = runTest {
        val routines = listOf("routine_handwash_01", "routine_morning_01", "routine_bedtime_01")

        for (sceneId in routines) {
            val scene = load(sceneId)
            val steps = scene.entities
                .filter { it.tags["group"] == "steps" }
                .sortedBy { (it.properties["sequenceIndex"] as PropertyValue.Num).value }
            assertTrue(steps.size >= 3, "$sceneId should teach a real sequence")

            // Out of order first: the last step cannot come first.
            val wrongFirst = play(scene, backgroundScope)
            runCurrent()
            wrongFirst.tap(steps.last().id)
            runCurrent()
            assertEquals(
                1, wrongFirst.metrics().mistakes,
                "$sceneId: doing the last step first is a mistake worth recording"
            )
            wrongFirst.close()

            // Then in order: the scene completes.
            val inOrder = play(scene, backgroundScope)
            runCurrent()
            steps.forEach { inOrder.tap(it.id); runCurrent() }
            assertTrue(
                inOrder.worldState.value.allLevelsComplete(),
                "$sceneId: following the routine in order must finish it"
            )
            inOrder.close()
        }
    }

    @Test
    fun `finding the happy face works and a wrong face is corrected`() = runTest {
        val scene = load("emotion_find_happy_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        session.tap("sad")
        runCurrent()
        assertEquals(1, session.metrics().mistakes)
        assertTrue(!session.worldState.value.allLevelsComplete())

        session.tap("happy")
        runCurrent()
        assertTrue(session.worldState.value.allLevelsComplete())
    }

    @Test
    fun `matching every face by mood completes the emotions scene`() = runTest {
        val scene = load("emotion_match_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        val faces = scene.entities.filter { it.tags["group"] == "faces" }
        for (face in faces) {
            val mood = (face.properties["mood"] as PropertyValue.Text).value
            session.dispatch(GameEvent.Drop(face.id, "slot_$mood", tick = 0L))
            runCurrent()
        }

        assertTrue(session.worldState.value.allLevelsComplete(), "every face placed should finish the scene")
        assertEquals(0, session.metrics().mistakes, "each face went straight to its own slot")
    }

    @Test
    fun `the needs board accepts every need and never scores the child`() = runTest {
        val scene = load("daily_needs_board_01")
        val needs = scene.entities.filter { it.tags["group"] == "needs" }
        assertTrue(needs.size >= 4, "a board with too few needs is not worth opening")

        // Every tile must carry the word the device will say aloud — a silent
        // tile on a communication board is a need the child cannot voice.
        needs.forEach { need ->
            val speech = (need.properties["speech"] as? PropertyValue.Text)?.value
            assertTrue(
                !speech.isNullOrBlank(),
                "need '${need.id}' has nothing to say out loud"
            )
        }

        val session = play(scene, backgroundScope)
        runCurrent()

        // The board stays usable: pressing a need does not consume it.
        needs.forEach { session.tap(it.id); runCurrent() }
        needs.forEach {
            assertEquals(
                EntityLifecycleState.ACTIVE,
                session.worldState.value.getEntityState(it.id),
                "need '${it.id}' must stay available to press again"
            )
        }
        assertEquals(0, session.metrics().mistakes, "saying what you need is never wrong")
    }

    @Test
    fun `first-then puts the reward after the task on every round`() = runTest {
        val scene = load("first_then_01")
        assertTrue(scene.isMultiLevel, "first-then should rehearse the structure more than once")

        val session = play(scene, backgroundScope)
        runCurrent()

        scene.levels.forEachIndexed { index, level ->
            val activities = level.entities.filter { it.tags["group"] == "activities" }
            assertEquals(2, activities.size, "level '${level.levelId}' should offer exactly two cards")

            activities.forEach { card ->
                val order = (card.properties["order"] as PropertyValue.Text).value
                session.dispatch(GameEvent.Drop(card.id, "slot_${if (order == "1") "first" else "then"}", tick = 0L))
                runCurrent()
            }
            if (index < scene.levels.lastIndex) {
                advanceTimeBy(1_500L)
                runCurrent()
            }
        }

        assertTrue(session.worldState.value.allLevelsComplete(), "all three rounds should finish")
    }

    @Test
    fun `placing a card in the wrong slot is corrected`() = runTest {
        val scene = load("first_then_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        val firstLevel = scene.levels.first()
        // Slots carry an `order` property too — it is what they accept — so the
        // card is picked out by its group, not by the property alone.
        val reward = firstLevel.entities.single {
            it.tags["group"] == "activities" &&
                (it.properties["order"] as? PropertyValue.Text)?.value == "2"
        }

        // The treat cannot go first — that is the whole lesson.
        session.dispatch(GameEvent.Drop(reward.id, "slot_first", tick = 0L))
        runCurrent()

        assertEquals(1, session.metrics().mistakes)
        assertTrue(!session.worldState.value.allLevelsComplete())
    }

    @Test
    fun `matching two pictures of the same thing completes the symbols scene`() = runTest {
        val scene = load("symbol_same_thing_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        val pictures = scene.entities.filter { it.tags["group"] == "symbols" }
        pictures.forEach { picture ->
            val thing = (picture.properties["thing"] as PropertyValue.Text).value
            session.dispatch(GameEvent.Drop(picture.id, "slot_$thing", tick = 0L))
            runCurrent()
        }

        assertTrue(session.worldState.value.allLevelsComplete())
        assertEquals(0, session.metrics().mistakes)
    }

    @Test
    fun `body parts scene responds to the part it asks for`() = runTest {
        val scene = load("body_parts_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        session.tap("nose")
        runCurrent()

        assertTrue(session.worldState.value.allLevelsComplete(), "tapping the nose answers 'где носик?'")
    }
}
