package org.ollu.mini.sound

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Everything the app says for itself has a recording.
 *
 * Every scene instruction was baked and "Молодец" still played in the robot
 * voice, because the app's own phrases lived at call sites nobody collected.
 * They live in [SystemPhrases] now, and this fails if one of them has not
 * been baked.
 */
class SystemPhrasesRecordedTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    private val manifest by lazy { File(catalogDir, "_sounds/speech/speech.json").readText() }

    private fun isRecorded(text: String): Boolean = manifest.contains("\"${text.trim()}\": {")

    private val phrases = listOf(
        SystemPhrases.WELL_DONE, SystemPhrases.SESSION_DONE, SystemPhrases.REWARDS
    )

    @Test
    fun `every phrase the app says for itself is recorded`() {
        val missing = phrases.filterNot(::isRecorded)
        assertTrue(missing.isEmpty(), "not baked, run tools/bake_speech.py: $missing")
    }

    @Test
    fun `every phrase is declared where the baker looks for it`() {
        // The baker reads SystemPhrases.kt as text; a phrase used here but
        // declared some other way would never be baked.
        val source = File(
            catalogDir.parentFile,
            "sharedUI/src/commonMain/kotlin/org/ollu/mini/sound/SystemPhrases.kt"
        ).readText()
        val body = source.substringAfter("object SystemPhrases").substringBefore("}")
        val declared = Regex("""const val \w+\s*=\s*"([^"]+)"""")
            .findAll(body).map { it.groupValues[1] }.toSet()
        assertTrue(declared == phrases.toSet(), "SystemPhrases.kt and this test disagree: $declared vs $phrases")
    }
}
