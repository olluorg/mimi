package org.ollu.mini.catalog

import mimi.core.world.SpeechProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogFilterTest {

    private fun entry(
        id: String,
        ages: IntRange = 2..6,
        skills: List<String> = emptyList(),
        speech: SpeechProfile? = null
    ) = CatalogEntry(
        id = id, title = id, description = "", url = "",
        ageMin = ages.first, ageMax = ages.last, skills = skills, speech = speech
    )

    private val sorting = entry("sorting", ages = 4..7, skills = listOf("logic"))
    private val words   = entry("words", ages = 2..4, speech = SpeechProfile(stage = "words", targets = listOf("apraxia")))
    private val aac     = entry("aac", ages = 2..3, speech = SpeechProfile(stage = "aac", targets = listOf("alalia")))
    private val all     = listOf(sorting, words, aac)

    @Test
    fun `an empty filter shows everything in catalog order`() {
        assertEquals(all, CatalogFilter().apply(all))
    }

    @Test
    fun `an age band shows games whose range overlaps it`() {
        assertEquals(listOf(words, aac), CatalogFilter(age = AgeBand.TODDLER).apply(all))
        assertEquals(listOf(sorting, words), CatalogFilter(age = AgeBand.PRESCHOOL).apply(all))
        assertEquals(listOf(sorting), CatalogFilter(age = AgeBand.SCHOOL).apply(all))
    }

    @Test
    fun `speech only keeps speech games and orders them by stage`() {
        // Pictures come before words, whatever the catalog order.
        assertEquals(listOf(aac, words), CatalogFilter(speechOnly = true).apply(all))
    }

    @Test
    fun `a speech target narrows to the games for it`() {
        assertEquals(listOf(aac), CatalogFilter(speechOnly = true, speechTarget = "alalia").apply(all))
    }

    @Test
    fun `skills match any selected skill`() {
        assertEquals(listOf(sorting), CatalogFilter(skills = setOf("logic", "memory")).apply(all))
    }
}
