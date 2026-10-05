package org.ollu.mini.play

import com.russhwolf.settings.MapSettings
import mimi.core.world.SpeechProfile
import org.ollu.mini.catalog.CatalogEntry
import org.ollu.mini.progress.ProgressRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Speech practice spread through a sitting, and read honestly afterwards. */
class SpeechPlanningTest {

    private fun game(id: String, targets: List<String>? = null) = CatalogEntry(
        id = id, title = id, description = "", url = "local://$id",
        ageMin = 2, ageMax = 6, skills = listOf("attention"), icon = "",
        speech = targets?.let { SpeechProfile(stage = "vocal", targets = it, modes = listOf("voice"), adultTip = "tip") }
    )

    private val catalog = listOf(
        game("sort_a"), game("sort_b"), game("match_a"),
        game("say_apraxia", listOf("apraxia")), game("say_both", listOf("apraxia", "alalia")),
        game("louder", listOf("dysarthria"))
    )

    private fun sitting(focus: List<String>): List<CatalogEntry> {
        val played = mutableListOf<String>()
        val picks = mutableListOf<CatalogEntry>()
        repeat(PlaySessionPlanner.SESSION_LENGTH) {
            val next = PlaySessionPlanner.nextScene(
                catalog, played, emptySet(),
                last = played.lastOrNull()?.let { PlayOutcome(it, 0, 0) },
                speechFocus = focus
            )!!
            picks += next
            played += next.id
        }
        return picks
    }

    @Test
    fun `with a focus every other game is a speech game suited to it`() {
        val picks = sitting(listOf("apraxia"))
        assertEquals(listOf(true, false, true, false, true), picks.map { it.isSpeechGame })
        assertTrue(picks.filter { it.isSpeechGame }.all { "apraxia" in it.speech!!.targets })
    }

    @Test
    fun `without a focus play is unchanged`() {
        assertEquals(
            PlaySessionPlanner.nextScene(catalog, emptyList(), emptySet(), null),
            PlaySessionPlanner.nextScene(catalog, emptyList(), emptySet(), null, speechFocus = emptyList())
        )
    }

    @Test
    fun `a focus with no suited games falls back to ordinary play`() {
        val picks = sitting(listOf("stuttering"))
        assertEquals(PlaySessionPlanner.SESSION_LENGTH, picks.size)
    }

    @Test
    fun `a silent speech game is not read as flying`() {
        val silent = PlayOutcome("say_apraxia", mistakes = 0, hintsUsed = 0, responses = 1, unanswered = 4)
        assertEquals(Momentum.STRUGGLING, silent.momentum)
        val partly = PlayOutcome("say_apraxia", mistakes = 0, hintsUsed = 0, responses = 4, unanswered = 1)
        assertEquals(Momentum.STEADY, partly.momentum)
        val all = PlayOutcome("say_apraxia", mistakes = 0, hintsUsed = 0, responses = 5)
        assertEquals(Momentum.FLYING, all.momentum)
    }

    @Test
    fun `speech focus and microphone sensitivity are remembered`() {
        val repo = ProgressRepository(MapSettings())
        val child = repo.addProfile("Миша", "cat")
        repo.setSpeechFocus(child.id, listOf("apraxia", "apraxia", "alalia"))
        assertEquals(listOf("apraxia", "alalia"), repo.currentSpeechFocus())

        assertEquals(0.5f, repo.micSensitivity)
        repo.micSensitivity = 1.4f
        assertEquals(1f, repo.micSensitivity)
        assertFalse(repo.getProfiles().isEmpty())
    }
}
