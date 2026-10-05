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
import mimi.core.world.SceneDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The yes/no games, played through.
 *
 * The thing that has to hold is that the right answer changes from round to
 * round. A scene where "да" is always correct teaches a child to press the left
 * card without looking, which is the exact habit these games exist to break —
 * and nothing in validation can see it, because such a scene is perfectly
 * well-formed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YesNoScenesTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    private fun load(sceneId: String): SceneDefinition =
        SceneLoader.load(File(File(catalogDir, sceneId), "$sceneId.json").readText())

    private fun play(scene: SceneDefinition, scope: kotlinx.coroutines.CoroutineScope): EngineSession =
        EngineBuilder().withDefaultPlugins(defaultConstraintRegistry()).build(scene, scope)

    private suspend fun EngineSession.tap(entityId: String) =
        dispatch(GameEvent.Tap(PropertyValue.Vec2(0f, 0f), tick = 0L, source = entityId))

    private val asking = listOf(
        "yes_no_is_it_01",
        "yes_no_is_it_food_01",
        "yes_no_is_it_big_01",
        "yes_no_same_01"
    )

    private fun correctAnswerOf(level: mimi.core.world.LevelDefinition): String =
        level.entities.firstNotNullOf {
            (it.properties["correctAnswer"] as? PropertyValue.Text)?.value
        }

    @Test
    fun `every asking scene changes its answer between rounds`() {
        for (sceneId in asking) {
            val answers = load(sceneId).levels.map { correctAnswerOf(it) }
            assertTrue(
                answers.toSet().size > 1,
                "$sceneId always wants '${answers.first()}' — a child can pass it without looking"
            )
        }
    }

    @Test
    fun `both answers are offered in the same places every round`() {
        for (sceneId in asking + "yes_no_want_01") {
            for (level in load(sceneId).levels) {
                val answers = level.entities.filter { it.tags["group"] == "answers" }
                assertEquals(
                    2, answers.size,
                    "$sceneId/${level.levelId} should offer exactly two answers"
                )
                // Consistent placement is what lets the answer become automatic.
                assertEquals(
                    listOf(320f, 680f),
                    answers.mapNotNull { (it.properties["position"] as? PropertyValue.Vec2)?.x },
                    "$sceneId/${level.levelId}: the answers moved"
                )
            }
        }
    }

    @Test
    fun `answering every round correctly finishes the scene`() = runTest {
        for (sceneId in asking) {
            val scene = load(sceneId)
            val session = play(scene, backgroundScope)
            runCurrent()

            scene.levels.forEachIndexed { index, level ->
                val want = correctAnswerOf(level)
                val tile = level.entities.first {
                    it.tags["group"] == "answers" &&
                        (it.properties["answer"] as? PropertyValue.Text)?.value == want
                }
                session.tap(tile.id)
                runCurrent()
                if (index < scene.levels.lastIndex) {
                    advanceTimeBy(1_500L)
                    runCurrent()
                }
            }

            assertTrue(
                session.worldState.value.allLevelsComplete(),
                "$sceneId should finish once every question is answered right"
            )
            assertEquals(0, session.metrics().mistakes, "$sceneId: every answer was correct")
            session.close()
        }
    }

    @Test
    fun `the wrong answer is not accepted and is counted`() = runTest {
        val scene = load("yes_no_is_it_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        val level = scene.levels.first()
        val wrong = level.entities.first {
            it.tags["group"] == "answers" &&
                (it.properties["answer"] as? PropertyValue.Text)?.value != correctAnswerOf(level)
        }
        session.tap(wrong.id)
        runCurrent()

        assertEquals(1, session.metrics().mistakes)
        assertTrue(!session.worldState.value.allLevelsComplete())
    }

    @Test
    fun `wanting something has no wrong answer`() = runTest {
        val scene = load("yes_no_want_01")
        // Either card must finish the round: the child is telling the adult
        // something, not being tested on it.
        for (answer in listOf("yes", "no")) {
            val session = play(scene, backgroundScope)
            runCurrent()
            val tile = scene.levels.first().entities.first {
                (it.properties["answer"] as? PropertyValue.Text)?.value == answer
            }
            session.tap(tile.id)
            runCurrent()
            assertEquals(0, session.metrics().mistakes, "answering '$answer' must not be a mistake")
            session.close()
        }
    }

    @Test
    fun `the answer the child gave is recorded for the adult`() = runTest {
        val scene = load("yes_no_want_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        val no = scene.levels.first().entities.first {
            (it.properties["answer"] as? PropertyValue.Text)?.value == "no"
        }
        session.tap(no.id)
        runCurrent()

        assertEquals(
            "Не хочу",
            session.metrics().choice,
            "what the child answered is the whole output of this game"
        )
    }

    @Test
    fun `every round is recorded and not only the last one`() = runTest {
        val scene = load("yes_no_want_01")
        val session = play(scene, backgroundScope)
        runCurrent()

        // Answering the levels rebuilds the world each time. If the record were
        // read from the world, four of these five answers would be gone — and a
        // game whose only output is what the child said would report one word.
        val wanted = listOf("yes", "no", "yes", "yes", "no")
        scene.levels.forEachIndexed { index, level ->
            val tile = level.entities.first {
                (it.properties["answer"] as? PropertyValue.Text)?.value == wanted[index]
            }
            session.tap(tile.id)
            runCurrent()
            if (index < scene.levels.lastIndex) {
                advanceTimeBy(1_500L)
                runCurrent()
            }
        }

        assertEquals(
            wanted.map { if (it == "yes") "Хочу" else "Не хочу" },
            session.metrics().choices,
            "every answer the child gave should reach the adult"
        )
        session.close()
    }
}
