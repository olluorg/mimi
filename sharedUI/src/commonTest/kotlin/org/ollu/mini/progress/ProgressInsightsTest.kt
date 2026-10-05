package org.ollu.mini.progress

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressInsightsTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun record(
        sceneId: String,
        daysAgo: Long,
        mistakes: Int = 0,
        hints: Int = 0,
        durationMs: Long = 30_000L
    ) = SessionRecord(
        sceneId    = sceneId,
        atEpochMs  = now - daysAgo * day,
        durationMs = durationMs,
        mistakes   = mistakes,
        hintsUsed  = hints
    )

    @Test
    fun `no history reports empty`() {
        val insights = buildProgressInsights(emptyList(), now)
        assertTrue(insights.isEmpty)
        assertEquals(0, insights.totalSessions)
    }

    @Test
    fun `totals cover every attempt`() {
        val insights = buildProgressInsights(
            listOf(
                record("a", daysAgo = 1, durationMs = 10_000),
                record("a", daysAgo = 2, durationMs = 20_000),
                record("b", daysAgo = 3, durationMs = 30_000)
            ),
            now
        )
        assertEquals(3, insights.totalSessions)
        assertEquals(2, insights.distinctScenes)
        assertEquals(60_000L, insights.totalTimeMs)
    }

    @Test
    fun `only the last seven days count as recent`() {
        val insights = buildProgressInsights(
            listOf(
                record("a", daysAgo = 1),
                record("a", daysAgo = 6),
                record("a", daysAgo = 8),
                record("a", daysAgo = 30)
            ),
            now
        )
        assertEquals(2, insights.sessionsLast7Days)
    }

    @Test
    fun `needing steadily less help reads as improving`() {
        val insights = buildProgressInsights(
            listOf(
                record("tricky", daysAgo = 5, mistakes = 4, hints = 2),
                record("tricky", daysAgo = 4, mistakes = 3, hints = 2),
                record("tricky", daysAgo = 3, mistakes = 1, hints = 0),
                record("tricky", daysAgo = 2, mistakes = 0, hints = 0)
            ),
            now
        )
        val scene = insights.perScene.single()
        assertEquals(Trend.IMPROVING, scene.trend)
    }

    @Test
    fun `needing more help over time reads as struggling`() {
        val insights = buildProgressInsights(
            listOf(
                record("hard", daysAgo = 5, mistakes = 0, hints = 0),
                record("hard", daysAgo = 4, mistakes = 1, hints = 0),
                record("hard", daysAgo = 3, mistakes = 3, hints = 2),
                record("hard", daysAgo = 2, mistakes = 4, hints = 2)
            ),
            now
        )
        assertEquals(Trend.STRUGGLING, insights.perScene.single().trend)
    }

    @Test
    fun `too few attempts is reported as new rather than guessed at`() {
        val insights = buildProgressInsights(
            listOf(
                record("fresh", daysAgo = 2, mistakes = 5, hints = 3),
                record("fresh", daysAgo = 1, mistakes = 0, hints = 0)
            ),
            now
        )
        assertEquals(Trend.NEW, insights.perScene.single().trend)
    }

    @Test
    fun `one bad day does not flip a steady scene to struggling`() {
        val insights = buildProgressInsights(
            listOf(
                record("steady", daysAgo = 5, mistakes = 1, hints = 0),
                record("steady", daysAgo = 4, mistakes = 1, hints = 0),
                record("steady", daysAgo = 3, mistakes = 1, hints = 0),
                record("steady", daysAgo = 2, mistakes = 1, hints = 0),
                record("steady", daysAgo = 1, mistakes = 2, hints = 0)
            ),
            now
        )
        assertEquals(Trend.STEADY, insights.perScene.single().trend)
    }

    @Test
    fun `scenes that keep costing help are surfaced for an adult`() {
        val insights = buildProgressInsights(
            listOf(
                record("easy", daysAgo = 3, mistakes = 0, hints = 0),
                record("easy", daysAgo = 2, mistakes = 0, hints = 0),
                record("easy", daysAgo = 1, mistakes = 0, hints = 0),

                record("costly", daysAgo = 3, mistakes = 3, hints = 1),
                record("costly", daysAgo = 2, mistakes = 3, hints = 1),
                record("costly", daysAgo = 1, mistakes = 3, hints = 2)
            ),
            now
        )
        assertContains(insights.needsAttention.map { it.sceneId }, "costly")
        assertTrue(
            insights.needsAttention.none { it.sceneId == "easy" },
            "a scene solved unaided should not be flagged"
        )
        assertContains(insights.strongest.map { it.sceneId }, "easy")
    }

    @Test
    fun `a hard scene that is improving is not flagged for attention`() {
        val insights = buildProgressInsights(
            listOf(
                record("climbing", daysAgo = 4, mistakes = 6, hints = 3),
                record("climbing", daysAgo = 3, mistakes = 5, hints = 2),
                record("climbing", daysAgo = 2, mistakes = 1, hints = 0),
                record("climbing", daysAgo = 1, mistakes = 0, hints = 0)
            ),
            now
        )
        assertEquals(Trend.IMPROVING, insights.perScene.single().trend)
        assertTrue(
            insights.needsAttention.isEmpty(),
            "progress is the point; a scene getting easier needs no intervention"
        )
    }

    @Test
    fun `per scene view is ordered by most recent attempt`() {
        val insights = buildProgressInsights(
            listOf(
                record("old", daysAgo = 10),
                record("newest", daysAgo = 1),
                record("middle", daysAgo = 5)
            ),
            now
        )
        assertEquals(listOf("newest", "middle", "old"), insights.perScene.map { it.sceneId })
    }
}

class ChoiceTallyTest {

    private val now = 1_700_000_000_000L

    private fun choice(sceneId: String, vararg picked: String, index: Int) = SessionRecord(
        sceneId   = sceneId,
        atEpochMs = now - index * 1000L,
        choices   = picked.toList()
    )

    @Test
    fun `preferences are counted per scene with the most picked first`() {
        val insights = buildProgressInsights(
            listOf(
                choice("choice_drink_01", "сок", index = 1),
                choice("choice_drink_01", "сок", index = 2),
                choice("choice_drink_01", "вода", index = 3),
                choice("choice_drink_01", "сок", index = 4)
            ),
            now
        )
        val tally = insights.choices.single()
        assertEquals("choice_drink_01", tally.sceneId)
        assertEquals(4, tally.total)
        assertEquals("сок", tally.favourite)
        assertEquals(listOf("сок", "вода"), tally.counts.keys.toList())
        assertEquals(3, tally.counts["сок"])
    }

    @Test
    fun `every answer in one attempt is counted and not only the last`() {
        // A yes/no scene asks five times in a single sitting. Counting one answer
        // per attempt would throw away four things the child said.
        val insights = buildProgressInsights(
            listOf(choice("yes_no_want_01", "Хочу", "Хочу", "Не хочу", index = 1)),
            now
        )
        val tally = insights.choices.single()
        assertEquals(3, tally.total)
        assertEquals("Хочу", tally.favourite)
        assertEquals(2, tally.counts["Хочу"])
    }

    @Test
    fun `scenes without a choice are left out of the tally`() {
        val insights = buildProgressInsights(
            listOf(
                choice("sort_fruits_01", index = 1),
                choice("choice_drink_01", "вода", index = 2)
            ),
            now
        )
        assertEquals(listOf("choice_drink_01"), insights.choices.map { it.sceneId })
    }

    @Test
    fun `a choice game contributes no mistakes to its scene insight`() {
        val insights = buildProgressInsights(
            listOf(
                choice("choice_snack_01", "печенье", index = 1),
                choice("choice_snack_01", "яблоко", index = 2),
                choice("choice_snack_01", "печенье", index = 3)
            ),
            now
        )
        val scene = insights.perScene.single { it.sceneId == "choice_snack_01" }
        assertEquals(0.0, scene.avgHelp)
        assertTrue(
            insights.needsAttention.isEmpty(),
            "a game with no wrong answer can never need remediation"
        )
    }
}
