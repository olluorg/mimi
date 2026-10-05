package mimi.core.scene

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.engine.EngineBuilder
import mimi.core.engine.EngineSession
import mimi.core.engine.defaultConstraintRegistry
import mimi.core.engine.withDefaultPlugins
import mimi.core.model.PropertyValue
import mimi.core.world.WorldSnapshot
import mimi.core.plugin.DefaultFeedbackRegistry
import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext
import mimi.core.world.SceneDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * "Найди букву" draws its letter at random.
 *
 * It asked for А on every play, so a child learned where А sat and never met
 * another letter. What has to hold now is that the letter really does change,
 * that the one asked for is always on the screen, and that the question heard,
 * the answer accepted and the goal all agree on which letter it is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RandomLetterTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    private val scene: SceneDefinition =
        SceneLoader.load(File(catalogDir, "find_letter_A_01/find_letter_A_01.json").readText())

    private class Heard : FeedbackChannel {
        val sounds = mutableListOf<String>()
        override val type = "test"
        override val isAvailable = true
        override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
            if (feedbackType == "sound") params["path"]?.let(sounds::add)
        }
    }

    private fun play(seed: Long, heard: Heard, scope: kotlinx.coroutines.CoroutineScope): EngineSession =
        EngineBuilder()
            .withDefaultPlugins(defaultConstraintRegistry())
            .withFeedbackRegistry(DefaultFeedbackRegistry().also { it.register(heard) })
            .build(scene, scope, seed)

    private fun WorldSnapshot.target() =
        getAllEntities().single { it.text("targetLetter") != null }

    private fun WorldSnapshot.shown() =
        getAllEntities().filter { it.tags["group"] == "choices" }

    @Test
    fun `the letter changes from one play to the next`() = runTest {
        val letters = (1L..60L).map { seed ->
            val session = play(seed, Heard(), backgroundScope)
            runCurrent()
            session.worldState.value.target().text("letter").also { session.close() }
        }.toSet()
        assertTrue(letters.size >= 6, "sixty plays met only $letters")
    }

    @Test
    fun `four letters are shown and the one asked for is among them`() = runTest {
        for (seed in 1L..40L) {
            val session = play(seed, Heard(), backgroundScope)
            runCurrent()
            val world = session.worldState.value
            assertEquals(4, world.shown().size, "seed $seed")
            assertTrue(world.target() in world.shown(), "seed $seed asks for a letter that is not on screen")
            session.close()
        }
    }

    @Test
    fun `the question heard names the letter that is accepted`() = runTest {
        for (seed in 1L..40L) {
            val heard = Heard()
            val session = play(seed, heard, backgroundScope)
            runCurrent()
            val target = session.worldState.value.target()
            assertEquals(listOf(target.text("askSound")), heard.sounds, "seed $seed")
            assertNotNull(target.text("askSound"))
            session.close()
        }
    }

    @Test
    fun `pressing the named letter finishes the game whichever it is`() = runTest {
        for (seed in 1L..40L) {
            val session = play(seed, Heard(), backgroundScope)
            runCurrent()
            val target = session.worldState.value.target()
            session.dispatch(GameEvent.Tap(PropertyValue.Vec2(0f, 0f), tick = 0L, source = target.id))
            runCurrent()
            assertEquals(0, session.metrics().mistakes, "seed $seed")
            assertTrue(
                scene.objectives.all { session.worldState.value.getObjectiveState(it.id).name.contains("COMPLETE") },
                "seed $seed: choosing ${target.text("letter")} should complete the goal"
            )
            session.close()
        }
    }

    @Test
    fun `pressing another letter is a mistake`() = runTest {
        for (seed in 1L..20L) {
            val session = play(seed, Heard(), backgroundScope)
            runCurrent()
            val world = session.worldState.value
            val wrong = world.shown().first { it.id != world.target().id }
            session.dispatch(GameEvent.Tap(PropertyValue.Vec2(0f, 0f), tick = 0L, source = wrong.id))
            runCurrent()
            assertEquals(1, session.metrics().mistakes, "seed $seed")
            session.close()
        }
    }

    @Test
    fun `the same seed asks for the same letter`() = runTest {
        // Determinism is what lets a reported problem be replayed exactly.
        val a = play(7L, Heard(), backgroundScope).also { runCurrent() }
        val b = play(7L, Heard(), backgroundScope).also { runCurrent() }
        assertEquals(a.worldState.value.target().id, b.worldState.value.target().id)
        a.close(); b.close()
    }
}
