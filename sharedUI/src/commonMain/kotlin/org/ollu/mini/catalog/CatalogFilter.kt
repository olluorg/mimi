package org.ollu.mini.catalog

import mimi.core.world.SpeechVocabulary

/** The age chips above the catalog; a game is shown if its range overlaps the band. */
enum class AgeBand(val label: String, val range: IntRange?) {
    ALL("Все", null),
    TODDLER("2–3 года", 2..3),
    PRESCHOOL("4–5 лет", 4..5),
    SCHOOL("6–7 лет", 6..7);

    fun admits(entry: CatalogEntry): Boolean =
        range == null || (entry.ageMin <= range.last && entry.ageMax >= range.first)
}

/** Everything the catalog's filter bars can narrow the list by. */
data class CatalogFilter(
    val speechOnly:   Boolean     = false,
    /** Who the speech games are for; only meaningful with [speechOnly]. */
    val speechTarget: String?     = null,
    val age:          AgeBand     = AgeBand.ALL,
    val skills:       Set<String> = emptySet()
) {
    fun apply(entries: List<CatalogEntry>): List<CatalogEntry> {
        // Only games that passed the speech check: the validator will not
        // publish a scene labelled as speech without a speech mechanic.
        val bySpeech = if (!speechOnly) entries else entries
            .filter { entry ->
                val speech = entry.speech ?: return@filter false
                speechTarget == null || speechTarget in speech.targets
            }
            // First steps first: pictures, then voice, then sounds, then words.
            .sortedBy { entry -> SpeechVocabulary.STAGES.keys.indexOf(entry.speech?.stage).takeIf { it >= 0 } ?: 99 }
        return bySpeech
            .filter(age::admits)
            .filter { entry -> skills.isEmpty() || entry.skills.any { it in skills } }
    }
}
