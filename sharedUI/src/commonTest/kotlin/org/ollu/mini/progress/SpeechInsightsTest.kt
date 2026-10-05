package org.ollu.mini.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechInsightsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun speech(daysAgo: Long, vocal: Int, adult: Int = 0, responses: Int = 0, unanswered: Int = 0) =
        SessionRecord(
            sceneId = "speech_animal_voices_01", atEpochMs = now - daysAgo * day,
            vocalAttempts = vocal, adultConfirmed = adult,
            responses = responses, unanswered = unanswered, speechGame = true
        )

    @Test
    fun `no speech games means no speech card`() {
        val insights = buildProgressInsights(listOf(SessionRecord("sort_food_01", now)), now)
        assertNull(insights.speech)
    }

    @Test
    fun `attempts are compared week on week`() {
        val insights = buildProgressInsights(
            listOf(speech(10, vocal = 2), speech(9, vocal = 1), speech(2, vocal = 4, adult = 2)),
            now
        )
        val speech = insights.speech!!
        assertEquals(6, speech.attemptsThisWeek)
        assertEquals(3, speech.attemptsLastWeek)
        assertEquals(Trend.IMPROVING, speech.trend)
    }

    @Test
    fun `answer rate counts every kind of answer`() {
        val insights = buildProgressInsights(listOf(speech(1, vocal = 3, responses = 3, unanswered = 1)), now)
        assertEquals(0.75, insights.speech!!.answerRate)
    }

    @Test
    fun `words are reported hardest first`() {
        val insights = buildProgressInsights(
            listOf(
                speech(2, vocal = 3).copy(turns = listOf("му|voice", "му|voice", "мяу|none", "мяу|none")),
                speech(1, vocal = 2).copy(turns = listOf("му|exact", "мяу|none", "ав|voice"))
            ),
            now
        )
        val words = insights.speech!!.words
        assertEquals(listOf("мяу", "му", "ав"), words.map { it.word },
            "the word the child never answers belongs at the top; among equals, the one asked most")
        val moo = words.single { it.word == "му" }
        assertEquals(3, moo.asked)
        assertEquals(3, moo.answered)
        assertEquals(1, moo.exact)
        assertEquals(0, words.single { it.word == "мяу" }.answered)
    }
}
