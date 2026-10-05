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
import mimi.core.world.LevelDefinition
import mimi.core.world.SceneDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The listening games, played through.
 *
 * These are the first scenes whose question is a recording rather than a
 * sentence, and the ways that can go wrong are invisible to validation: the
 * round can play one animal and expect another, the answer can sit in the same
 * place every time, and the button that repeats the question can be scored as a
 * wrong answer — punishing a child for using the one affordance built for them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnimalSoundScenesTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    private val listening = listOf("animal_sound_match_01", "animal_sound_wild_01")

    private fun load(sceneId: String): SceneDefinition =
        SceneLoader.load(File(File(catalogDir, sceneId), "$sceneId.json").readText())

    private fun play(scene: SceneDefinition, scope: kotlinx.coroutines.CoroutineScope): EngineSession =
        EngineBuilder().withDefaultPlugins(defaultConstraintRegistry()).build(scene, scope)

    private suspend fun EngineSession.tap(entityId: String) =
        dispatch(GameEvent.Tap(PropertyValue.Vec2(0f, 0f), tick = 0L, source = entityId))

    private fun LevelDefinition.text(id: String, key: String): String? =
        entities.firstOrNull { it.id == id }
            ?.properties?.get(key)
            ?.let { (it as? PropertyValue.Text)?.value }

    private fun LevelDefinition.answer(): String = text("listen", "correctAnimal")!!

    @Test
    fun `the voice that plays belongs to the animal the round is asking for`() {
        for (sceneId in listening) {
            for (level in load(sceneId).levels) {
                assertEquals(
                    "_sounds/animals/${level.answer()}.mp3",
                    level.text("listen", "questionSound"),
                    "$sceneId/${level.levelId} plays one animal and wants another"
                )
            }
        }
    }

    @Test
    fun `the animal being asked for is on the board with two others`() {
        for (sceneId in listening) {
            for (level in load(sceneId).levels) {
                val choices = level.entities
                    .filter { it.tags["group"] == "choices" }
                    .mapNotNull { (it.properties["animal"] as? PropertyValue.Text)?.value }
                assertEquals(3, choices.size, "$sceneId/${level.levelId}")
                assertTrue(
                    level.answer() in choices,
                    "$sceneId/${level.levelId} asks for ${level.answer()}, which is not on the board"
                )
            }
        }
    }

    @Test
    fun `the answer does not sit in the same place every round`() {
        for (sceneId in listening) {
            val columns = load(sceneId).levels.map { level ->
                level.entities
                    .first { (it.properties["animal"] as? PropertyValue.Text)?.value == level.answer() }
                    .let { (it.properties["position"] as? PropertyValue.Vec2)?.x }
            }
            assertTrue(
                columns.toSet().size > 1,
                "$sceneId keeps the answer at x=${columns.first()} — a child passes it by pressing there"
            )
        }
    }

    @Test
    fun `choosing the animal that made the sound finishes every round`() = runTest {
        for (sceneId in listening) {
            val scene = load(sceneId)
            val session = play(scene, backgroundScope)
            runCurrent()

            scene.levels.forEachIndexed { index, level ->
                session.tap(level.answer())
                runCurrent()
                if (index < scene.levels.lastIndex) {
                    advanceTimeBy(1_500L)
                    runCurrent()
                }
            }

            assertTrue(
                session.worldState.value.allLevelsComplete(),
                "$sceneId should finish once every voice is matched"
            )
            assertEquals(0, session.metrics().mistakes, "$sceneId: every choice was right")
            session.close()
        }
    }

    @Test
    fun `pressing the ear to hear it again is never a mistake`() = runTest {
        val scene = load("animal_sound_match_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        repeat(4) {
            session.tap("listen")
            runCurrent()
        }

        assertEquals(
            0, session.metrics().mistakes,
            "asking to hear the question again is the button's whole purpose"
        )
        assertTrue(
            !session.worldState.value.allLevelsComplete(),
            "the ear is not an answer"
        )
        session.close()
    }

    @Test
    fun `choosing the wrong animal is counted and does not pass the round`() = runTest {
        val scene = load("animal_sound_match_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        val level = scene.levels.first()
        val wrong = level.entities
            .first {
                it.tags["group"] == "choices" &&
                    (it.properties["animal"] as? PropertyValue.Text)?.value != level.answer()
            }
        session.tap(wrong.id)
        runCurrent()

        assertEquals(1, session.metrics().mistakes)
        assertTrue(!session.worldState.value.allLevelsComplete())
        session.close()
    }

    @Test
    fun `the zoo lets the same animal be pressed over and over`() = runTest {
        val scene = load("animal_zoo_free_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        // A child who presses the cow eight times is doing what this scene is
        // for, so that alone has to be enough to finish it.
        repeat(8) {
            session.tap("cow")
            runCurrent()
        }

        assertEquals(0, session.metrics().mistakes, "the zoo has no wrong answer")
        assertTrue(
            session.worldState.value.allLevelsComplete(),
            "eight presses should be a finished visit"
        )
        session.close()
    }

    @Test
    fun `every animal in the zoo answers with its own voice`() {
        for (entity in load("animal_zoo_free_01").entities) {
            val animal = (entity.properties["animal"] as? PropertyValue.Text)?.value ?: continue
            assertEquals(
                "_sounds/animals/$animal.mp3",
                (entity.properties["sound"] as? PropertyValue.Text)?.value,
                "pressing $animal should play $animal, not the word for it"
            )
        }
    }
}
