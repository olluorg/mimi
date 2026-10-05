package org.ollu.mini.play

import org.ollu.mini.catalog.CatalogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaySessionPlannerTest {

    private fun entry(
        id: String,
        ageMin: Int,
        requires: List<String> = emptyList()
    ) = CatalogEntry(
        id = id, title = id, description = "", url = "local://$id",
        ageMin = ageMin, ageMax = ageMin + 2, skills = listOf("attention"),
        icon = "", requires = requires
    )

    private val catalog = listOf(
        entry("easy_a", 2), entry("easy_b", 2),
        entry("mid_a", 3), entry("mid_b", 3),
        entry("hard_a", 4), entry("hard_b", 4)
    )

    private fun struggled(id: String) = PlayOutcome(id, mistakes = 3, hintsUsed = 1)
    private fun flew(id: String) = PlayOutcome(id, mistakes = 0, hintsUsed = 0)
    private fun wobbled(id: String) = PlayOutcome(id, mistakes = 1, hintsUsed = 0)

    @Test
    fun `an empty catalog yields nothing rather than crashing`() {
        assertNull(PlaySessionPlanner.nextScene(emptyList(), emptyList(), emptySet(), null))
    }

    @Test
    fun `a session opens at the gentlest level available`() {
        val first = PlaySessionPlanner.nextScene(catalog, emptyList(), emptySet(), null)
        assertNotNull(first)
        assertEquals(2, first.ageMin, "a child arriving cold should win before being stretched")
    }

    @Test
    fun `struggling steps the difficulty down`() {
        val next = PlaySessionPlanner.nextScene(
            catalog, playedNow = listOf("hard_a"), completedIds = emptySet(),
            last = struggled("hard_a")
        )
        assertNotNull(next)
        assertEquals(3, next.ageMin, "after a hard time the next game should be easier")
    }

    @Test
    fun `a clean run steps the difficulty up`() {
        val next = PlaySessionPlanner.nextScene(
            catalog, playedNow = listOf("easy_a"), completedIds = emptySet(),
            last = flew("easy_a")
        )
        assertNotNull(next)
        assertEquals(3, next.ageMin, "a child who needed no help can take more")
    }

    @Test
    fun `a wobble holds the level`() {
        val next = PlaySessionPlanner.nextScene(
            catalog, playedNow = listOf("mid_a"), completedIds = emptySet(),
            last = wobbled("mid_a")
        )
        assertNotNull(next)
        assertEquals(3, next.ageMin, "one mistake is not a reason to move the child")
    }

    @Test
    fun `skipping a game counts as struggling with it`() {
        assertEquals(Momentum.STRUGGLING, PlayOutcome("x", 0, 0, skipped = true).momentum)

        val next = PlaySessionPlanner.nextScene(
            catalog, playedNow = listOf("hard_a"), completedIds = emptySet(),
            last = PlayOutcome("hard_a", mistakes = 0, hintsUsed = 0, skipped = true)
        )
        assertNotNull(next)
        assertEquals(3, next.ageMin, "walking away from a game means it was too much")
    }

    @Test
    fun `difficulty never runs off either end of the catalog`() {
        val easiest = PlaySessionPlanner.nextScene(
            catalog, playedNow = listOf("easy_a"), completedIds = emptySet(),
            last = struggled("easy_a")
        )
        assertNotNull(easiest)
        assertEquals(2, easiest.ageMin, "there is nothing below the easiest level")

        val hardest = PlaySessionPlanner.nextScene(
            catalog, playedNow = listOf("hard_a"), completedIds = emptySet(),
            last = flew("hard_a")
        )
        assertNotNull(hardest)
        assertEquals(4, hardest.ageMin, "there is nothing above the hardest level")
    }

    @Test
    fun `a game is not repeated inside one sitting`() {
        var played = listOf<String>()
        var last: PlayOutcome? = null

        repeat(PlaySessionPlanner.SESSION_LENGTH) {
            val next = PlaySessionPlanner.nextScene(catalog, played, emptySet(), last)
            assertNotNull(next)
            assertTrue(next.id !in played, "'${next.id}' was already played this sitting")
            played = played + next.id
            last = wobbled(next.id)
        }
    }

    @Test
    fun `locked scenes stay out until their prerequisite is finished`() {
        val gated = catalog + entry("sequel", 2, requires = listOf("easy_a"))

        val withoutPrereq = PlaySessionPlanner.nextScene(gated, emptyList(), emptySet(), null)
        assertNotNull(withoutPrereq)
        assertTrue(withoutPrereq.id != "sequel", "a sequel should not open before its first part")

        val unlocked = PlaySessionPlanner.nextScene(
            gated,
            playedNow = listOf("easy_a", "easy_b", "mid_a", "mid_b"),
            completedIds = setOf("easy_a"),
            last = struggled("mid_b")
        )
        assertNotNull(unlocked)
        assertEquals("sequel", unlocked.id, "with its prerequisite done the sequel is fair game")
    }

    @Test
    fun `new ground is preferred over a game already finished before`() {
        val next = PlaySessionPlanner.nextScene(
            catalog, playedNow = emptyList(), completedIds = setOf("easy_a"), last = null
        )
        assertNotNull(next)
        assertEquals("easy_b", next.id, "a session should open up something new when it can")
    }

    @Test
    fun `a struggling child gets a replay rather than a harder game`() {
        // Only two gentle games exist and both are used up this sitting.
        val thin = listOf(entry("tiny_a", 2), entry("tiny_b", 2), entry("steep", 5))

        val next = PlaySessionPlanner.nextScene(
            thin,
            playedNow = listOf("tiny_a", "tiny_b"),
            completedIds = emptySet(),
            last = struggled("tiny_b")
        )
        assertNotNull(next)
        assertEquals(
            2, next.ageMin,
            "with nothing gentle left, replay an easy game rather than escalate on a struggling child"
        )
    }

    @Test
    fun `when everything is locked the planner still finds something playable`() {
        val allGated = listOf(
            entry("a", 2, requires = listOf("missing")),
            entry("b", 3, requires = emptyList())
        )
        val next = PlaySessionPlanner.nextScene(allGated, emptyList(), emptySet(), null)
        assertEquals("b", next?.id, "a child must never be left with an empty screen")
    }
}
