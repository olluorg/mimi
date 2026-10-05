package mimi.core.scene

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.engine.EngineBuilder
import mimi.core.engine.EngineSession
import mimi.core.engine.defaultConstraintRegistry
import mimi.core.engine.withDefaultPlugins
import mimi.core.model.PropertyValue
import mimi.core.plugin.DefaultFeedbackRegistry
import mimi.core.plugin.FeedbackChannel
import mimi.core.plugin.FeedbackContext
import mimi.core.world.SceneDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "повторить задание" button, against real scenes.
 *
 * It must say exactly what the scene said when it opened — in a listening game
 * that is the current round's animal, not the first round's — and it must cost
 * the child nothing: no mistake, no progress, no restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepeatInstructionTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    private fun load(sceneId: String): SceneDefinition =
        SceneLoader.load(File(File(catalogDir, sceneId), "$sceneId.json").readText())

    /** Every sound and spoken line the engine asks for, in order. */
    private class Heard : FeedbackChannel {
        val lines = mutableListOf<String>()
        override val type = "test"
        override val isAvailable = true
        override fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
            when (feedbackType) {
                "sound" -> params["path"]?.let(lines::add)
                "tts"   -> params["text"]?.let(lines::add)
            }
        }
    }

    private fun play(scene: SceneDefinition, heard: Heard, scope: kotlinx.coroutines.CoroutineScope): EngineSession =
        EngineBuilder()
            .withDefaultPlugins(defaultConstraintRegistry())
            .withFeedbackRegistry(DefaultFeedbackRegistry().also { it.register(heard) })
            .build(scene, scope)

    @Test
    fun `the button says the opening instruction again`() = runTest {
        val heard = Heard()
        val session = play(load("sort_food_01"), heard, backgroundScope)
        runCurrent()
        val opening = heard.lines.toList()
        assertTrue(opening.isNotEmpty(), "the scene should speak when it opens")

        heard.lines.clear()
        session.repeatInstruction()
        runCurrent()

        assertEquals(opening, heard.lines, "repeating should say exactly what was said at the start")
        session.close()
    }

    @Test
    fun `asking again is never a mistake and moves nothing`() = runTest {
        val scene = load("sort_food_01")
        val session = play(scene, Heard(), backgroundScope)
        runCurrent()
        val before = session.worldState.value

        repeat(5) { session.repeatInstruction(); runCurrent() }

        assertEquals(0, session.metrics().mistakes)
        for (objective in scene.objectives) {
            assertEquals(
                before.getObjectiveState(objective.id),
                session.worldState.value.getObjectiveState(objective.id),
                "asking to hear the task again must not advance it"
            )
        }
        session.close()
    }

    @Test
    fun `in a listening game it replays the current round`() = runTest {
        val scene = load("animal_sound_match_01")
        val heard = Heard()
        val session = play(scene, heard, backgroundScope)
        runCurrent()

        // Finish round one, so the question has changed.
        val first = scene.levels.first()
        val answer = (first.entities.first { it.id == "listen" }.properties["correctAnimal"] as PropertyValue.Text).value
        session.dispatch(GameEvent.Tap(PropertyValue.Vec2(0f, 0f), tick = 0L, source = answer))
        runCurrent()
        advanceTimeBy(1_500L)
        runCurrent()

        heard.lines.clear()
        session.repeatInstruction()
        runCurrent()

        val second = scene.levels[1]
        val secondVoice = (second.entities.first { it.id == "listen" }.properties["questionSound"] as PropertyValue.Text).value
        assertEquals(listOf(secondVoice), heard.lines, "the second round's animal, not the first")
        session.close()
    }
}
