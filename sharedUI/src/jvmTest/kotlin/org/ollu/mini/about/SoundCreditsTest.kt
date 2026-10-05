package org.ollu.mini.about

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The credits shown in the app have to be the credits of the files it plays.
 *
 * The list is generated from the catalog, so the two can only differ if
 * somebody adds, replaces or removes a recording without regenerating it —
 * and the result is an app that names the wrong author, which is worse than
 * naming none. Cheaper to fail here than to notice later.
 */
class SoundCreditsTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    /** The sounds manifest, read without pulling in a JSON dependency for one file. */
    private fun installedSounds(): Map<String, String> {
        val text = File(catalogDir, "_sounds/animals/sounds.json").readText()
        // One entry per animal; the innermost braces are the ones with no
        // braces inside them, which is what tells them from the wrapper.
        val entry = Regex(
            """"([a-z_]+)"\s*:\s*\{[^{}]*?"licence"\s*:\s*"([^"]*)"[^{}]*}""",
            RegexOption.DOT_MATCHES_ALL
        )
        return entry.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun `every installed recording is credited`() {
        val installed = installedSounds()
        assertTrue(installed.isNotEmpty(), "no sounds were read from the catalog")
        assertEquals(
            installed.size, ANIMAL_SOUND_CREDITS.size,
            "the catalog holds ${installed.size} recordings but ${ANIMAL_SOUND_CREDITS.size} are credited — " +
                "regenerate SoundCredits.kt"
        )
        assertEquals(
            installed.values.sorted(),
            ANIMAL_SOUND_CREDITS.map { it.licence }.sorted(),
            "the credited licences are not the ones the files were taken under"
        )
    }

    @Test
    fun `no recording is credited to nobody`() {
        for (credit in ANIMAL_SOUND_CREDITS) {
            assertTrue(credit.title.isNotBlank(), "${credit.label} has no source file named")
            assertTrue(credit.licence.isNotBlank(), "${credit.label} has no licence")
            assertTrue(
                credit.page.startsWith("https://"),
                "${credit.label} has no link back to where it came from"
            )
            // Public-domain files often have no author, which is fine; anything
            // under a Creative Commons licence has someone to name.
            if (credit.licence.startsWith("CC BY")) {
                assertTrue(
                    credit.author.isNotBlank() && credit.author != "—",
                    "${credit.label} is ${credit.licence}, which requires naming the author"
                )
            }
        }
    }
}
