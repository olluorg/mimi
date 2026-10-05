package org.ollu.mini.play

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ollu.mini.catalog.CatalogEntry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plays whole sittings against the catalog that actually ships.
 *
 * The planner's unit tests use a tidy synthetic catalog. This one asks the
 * harder question: given the real mix of scenes, does a child left alone get a
 * sensible run — never stranded, never stuck on the same game, and carried
 * gently downhill when they are struggling?
 */
class RealCatalogSessionTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val catalog: List<CatalogEntry> by lazy {
        val dir = File(System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set"))
        dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { sceneDir ->
                val file = File(sceneDir, "${sceneDir.name}.json")
                if (!file.isFile) return@mapNotNull null
                val meta = runCatching {
                    json.parseToJsonElement(file.readText()).jsonObject["meta"]?.jsonObject
                }.getOrNull() ?: return@mapNotNull null

                fun str(key: String) = meta[key]?.jsonPrimitive?.content.orEmpty()
                fun int(key: String, fallback: Int) =
                    meta[key]?.jsonPrimitive?.content?.toIntOrNull() ?: fallback
                fun list(key: String) =
                    meta[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()

                CatalogEntry(
                    id = sceneDir.name, title = str("title"), description = str("description"),
                    url = "local://${sceneDir.name}",
                    ageMin = int("ageMin", 2), ageMax = int("ageMax", 6),
                    skills = list("skills"), icon = str("icon"), requires = list("requires"),
                    tags = list("tags")
                )
            }
            ?.sortedBy { it.id }
            ?: emptyList()
    }

    /** Runs a whole sitting, deciding each outcome with [outcomeFor]. */
    private fun playSession(
        completedIds: Set<String> = emptySet(),
        outcomeFor: (CatalogEntry) -> PlayOutcome
    ): List<CatalogEntry> {
        val played = mutableListOf<CatalogEntry>()
        var last: PlayOutcome? = null
        repeat(PlaySessionPlanner.SESSION_LENGTH) {
            val next = PlaySessionPlanner.nextScene(
                catalog      = catalog,
                playedNow    = played.map { it.id },
                completedIds = completedIds,
                last         = last
            ) ?: return played
            played += next
            last = outcomeFor(next)
        }
        return played
    }

    @Test
    fun `the shipped catalog is big enough to plan against`() {
        assertTrue(catalog.size >= 20, "found only ${catalog.size} scenes")
        assertTrue(
            catalog.any { it.ageMin <= 2 },
            "there must be something gentle enough for the youngest child to start on"
        )
    }

    @Test
    fun `a full sitting never strands the child`() {
        val played = playSession { PlayOutcome(it.id, mistakes = 1, hintsUsed = 0) }
        assertEquals(
            PlaySessionPlanner.SESSION_LENGTH, played.size,
            "every slot in the sitting should be filled"
        )
        assertEquals(
            played.size, played.map { it.id }.distinct().size,
            "no game should repeat inside one sitting"
        )
    }

    @Test
    fun `a sitting opens on something a beginner can do`() {
        val played = playSession { PlayOutcome(it.id, mistakes = 0, hintsUsed = 0) }
        val first = played.firstOrNull()
        assertNotNull(first)
        assertEquals(
            catalog.minOf { it.ageMin }, first.ageMin,
            "the first game of a sitting should be the gentlest on offer"
        )
    }

    @Test
    fun `a child who keeps struggling is never pushed harder`() {
        val played = playSession { PlayOutcome(it.id, mistakes = 4, hintsUsed = 2) }

        played.zipWithNext { previous, next ->
            assertTrue(
                next.ageMin <= previous.ageMin,
                "after struggling with '${previous.id}' (${previous.ageMin}), " +
                    "the child was handed '${next.id}' (${next.ageMin}), which is harder"
            )
        }
        assertEquals(
            catalog.minOf { it.ageMin }, played.last().ageMin,
            "a sitting that goes badly should end on the gentlest level available"
        )
    }

    @Test
    fun `a child who breezes through is offered more`() {
        val played = playSession { PlayOutcome(it.id, mistakes = 0, hintsUsed = 0) }
        assertTrue(
            played.last().ageMin > played.first().ageMin,
            "five flawless games should have stretched the child beyond the opening level"
        )
    }

    @Test
    fun `a sitting does not serve the same kind of game twice in a row`() {
        val played = playSession { PlayOutcome(it.id, mistakes = 1, hintsUsed = 0) }
        played.zipWithNext { previous, next ->
            val a = previous.kind
            val b = next.kind
            if (a != null && b != null) {
                assertTrue(
                    a != b,
                    "'${previous.id}' and '${next.id}' are both '$a' — back to back that is " +
                        "one game played twice as far as a small child is concerned"
                )
            }
        }
    }

    @Test
    fun `a sitting covers more than one kind of game`() {
        val played = playSession { PlayOutcome(it.id, mistakes = 1, hintsUsed = 0) }
        val kinds = played.mapNotNull { it.kind }.toSet()
        assertTrue(
            kinds.size >= 2,
            "a whole sitting used only ${kinds.ifEmpty { setOf("untagged") }}"
        )
    }

    @Test
    fun `scenes with prerequisites never come before their first part`() {
        val played = playSession { PlayOutcome(it.id, mistakes = 1, hintsUsed = 0) }
        played.forEach { entry ->
            assertTrue(
                entry.requires.isEmpty(),
                "'${entry.id}' needs ${entry.requires} finished first, but nothing had been completed"
            )
        }
    }

    @Test
    fun `finished scenes give way to ones the child has not seen`() {
        val gentle = catalog.filter { it.ageMin == catalog.minOf { e -> e.ageMin } }
        // Pretend all but one of the gentlest scenes are already done.
        val alreadyDone = gentle.dropLast(1).map { it.id }.toSet()

        val next = PlaySessionPlanner.nextScene(catalog, emptyList(), alreadyDone, null)
        assertNotNull(next)
        assertEquals(
            gentle.last().id, next.id,
            "the one unplayed scene at that level should be preferred over replays"
        )
    }
}
