package mimi.core.scene

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mimi.core.behavior.GameEvent
import mimi.core.engine.EngineBuilder
import mimi.core.engine.EngineSession
import mimi.core.engine.defaultConstraintRegistry
import mimi.core.engine.withDefaultPlugins
import mimi.core.model.EntityLifecycleState
import mimi.core.model.EntitySnapshot
import mimi.core.model.PropertyValue
import mimi.core.world.SceneDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Randomised scenes have to stay winnable.
 *
 * A scene that deals itself differently each play can deal itself a dead end —
 * the card the prompt names dropped from the draw, or a basket left with nothing
 * that belongs in it. A child alone cannot report that; they just sit in front of
 * a game that will not finish. So every scene declaring a variation is played to
 * completion across many seeds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VariedScenesPlayableTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    private val variedScenes: List<Pair<String, SceneDefinition>> by lazy {
        catalogDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith("_") }
            ?.sortedBy { it.name }
            ?.mapNotNull { dir ->
                val file = File(dir, "${dir.name}.json")
                if (!file.isFile) return@mapNotNull null
                val scene = runCatching { SceneLoader.load(file.readText()) }.getOrNull()
                    ?: return@mapNotNull null
                if (scene.variation == null) null else dir.name to scene
            }
            ?: emptyList()
    }

    private fun play(scene: SceneDefinition, seed: Long, scope: kotlinx.coroutines.CoroutineScope) =
        EngineBuilder().withDefaultPlugins(defaultConstraintRegistry()).build(scene, scope, seed)

    /**
     * Plays a scene the way a child who knows the answer would, reading the world
     * the engine actually built rather than the scene file — which is the point,
     * since the file is only a template once variation is involved.
     */
    private suspend fun solve(session: EngineSession, scene: SceneDefinition) {
        repeat(MAX_MOVES) {
            if (session.worldState.value.allLevelsComplete()) return
            val world = session.worldState.value
            val live = world.getAllEntities().filter { it.state == EntityLifecycleState.ACTIVE }

            val draggable = live.filter { it.hasComponent("drag") }
            if (draggable.isNotEmpty()) {
                val targets = world.getAllEntities().filter { it.hasComponent("dropTarget") }
                val item = draggable.first()
                val match = targets.firstOrNull { accepts(it, item) } ?: targets.firstOrNull()
                if (match != null) {
                    session.dispatch(GameEvent.Drop(item.id, match.id, tick = 0L))
                    return@repeat
                }
            }

            val tappable = live.filter { it.hasComponent("tap") }
            if (tappable.isNotEmpty()) {
                // Prefer whatever an entitySelected objective is waiting for —
                // named in the scene, or drawn at play time and read off the
                // tile it was copied to.
                val goal = scene.objectivesForLevel(world.currentLevel())
                    .firstOrNull { it.type == "entitySelected" }
                val wantedValue = goal?.config?.get("value")
                    ?: goal?.config?.get("valueFrom")?.let { key ->
                        world.getAllEntities().firstNotNullOfOrNull { it.text(key) }
                    }
                val wanted = goal?.config?.get("property")
                    ?.let { property -> tappable.firstOrNull { it.text(property) == wantedValue } }
                    ?: tappable.firstOrNull { it.text("target") == "yes" }
                    ?: tappable.first()
                session.dispatch(GameEvent.Tap(PropertyValue.Vec2(0f, 0f), tick = 0L, source = wanted.id))
                return@repeat
            }
            return
        }
    }

    /** Mirrors the propertyEquals constraint the card scenes use. */
    private fun accepts(target: EntitySnapshot, item: EntitySnapshot): Boolean {
        val sourceKey = target.text("source") ?: return false
        val targetKey = target.text("target") ?: return false
        return item.properties[sourceKey] != null &&
            item.properties[sourceKey] == target.properties[targetKey]
    }

    @Test
    fun `the catalog actually contains scenes that vary`() {
        assertTrue(
            variedScenes.size >= 5,
            "expected several randomised scenes, found ${variedScenes.size}"
        )
    }

    @Test
    fun `every randomised scene can be finished on any seed`() = runTest {
        val broken = mutableListOf<String>()

        for ((id, scene) in variedScenes) {
            for (seed in 1L..SEEDS) {
                val session = play(scene, seed, backgroundScope)
                runCurrent()
                solve(session, scene)
                runCurrent()
                if (!session.worldState.value.allLevelsComplete()) {
                    broken += "$id: seed $seed could not be completed"
                }
                session.close()
            }
        }

        if (broken.isNotEmpty()) {
            fail(
                "Randomised scenes that deal an unwinnable board:\n" +
                    broken.take(20).joinToString("\n") { "  • $it" }
            )
        }
    }

    @Test
    fun `a randomised scene really does look different between seeds`() = runTest {
        val unchanged = mutableListOf<String>()

        for ((id, scene) in variedScenes) {
            val layouts = mutableSetOf<String>()
            for (seed in 1L..8L) {
                val session = play(scene, seed, backgroundScope)
                runCurrent()
                layouts += session.worldState.value.getAllEntities()
                    .map { "${it.id}@${it.vec2("position")}" }
                    .sorted()
                    .joinToString("|")
                session.close()
            }
            if (layouts.size == 1) {
                unchanged += "$id declares a variation but deals an identical board every time"
            }
        }

        if (unchanged.isNotEmpty()) {
            fail(unchanged.joinToString("\n") { "  • $it" })
        }
    }

    private companion object {
        /** Enough seeds to catch a draw that only occasionally goes wrong. */
        const val SEEDS = 25L

        /** A generous ceiling: no card scene needs anywhere near this many moves. */
        const val MAX_MOVES = 60
    }
}
