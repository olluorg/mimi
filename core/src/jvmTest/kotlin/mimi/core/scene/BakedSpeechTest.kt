package mimi.core.scene

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * No game may open in a synthesised voice.
 *
 * The instruction is the first thing a child hears, and a system speech engine
 * says it differently on every device, mispronounces half of it, and on the
 * desktop build is often missing altogether — so the scene opens in silence to
 * a child who cannot read the title. Every line is recorded once with voicy and
 * shipped; `text` stays only as the fallback for a line not yet baked.
 *
 * This test is what stops a new scene from quietly shipping the robot again.
 */
class BakedSpeechTest {

    private val catalogDir = File(
        System.getProperty("mimi.catalogDir") ?: error("mimi.catalogDir is not set")
    )

    private fun scenes(): List<Pair<String, String>> =
        catalogDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith("_") }
            .mapNotNull { dir ->
                val file = File(dir, "${dir.name}.json")
                if (file.isFile) dir.name to file.readText() else null
            }

    /** Each audioPrompt binding as a flat blob, which is all these checks need. */
    private val promptBlock = Regex(
        """\{\s*"type"\s*:\s*"audioPrompt"\s*,\s*"config"\s*:\s*\{([^{}]*)}""",
        RegexOption.DOT_MATCHES_ALL
    )

    private fun value(config: String, key: String): String? =
        Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(config)
            ?.groupValues?.get(1)
            ?.replace("\\\"", "\"")

    @Test
    fun `every spoken instruction has a recording`() {
        val unbaked = mutableListOf<String>()
        for ((sceneId, text) in scenes()) {
            for (match in promptBlock.findAll(text)) {
                val config = match.groupValues[1]
                val spoken = value(config, "text") ?: continue
                val sound = value(config, "sound")
                if (sound.isNullOrBlank()) {
                    unbaked += "$sceneId: \"$spoken\" would be read by a speech engine"
                }
            }
        }
        if (unbaked.isNotEmpty()) {
            fail(
                "These lines are not recorded — run tools/bake_speech.py:\n" +
                    unbaked.joinToString("\n") { "  • $it" }
            )
        }
    }

    @Test
    fun `every recording named by a scene is on disk`() {
        val missing = mutableListOf<String>()
        for ((sceneId, text) in scenes()) {
            for (match in promptBlock.findAll(text)) {
                val sound = value(match.groupValues[1], "sound") ?: continue
                // Built-in names are tones, not files; only paths are checked.
                if ('/' !in sound) continue
                if (!File(catalogDir, sound).isFile) {
                    missing += "$sceneId: '$sound'"
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing prompt recordings:\n" + missing.joinToString("\n"))
    }

    @Test
    fun `a recording is short enough to hold a child still`() {
        val manifest = File(catalogDir, "_sounds/speech/speech.json").readText()
        val long = Regex(""""seconds"\s*:\s*([0-9.]+)""")
            .findAll(manifest)
            .map { it.groupValues[1].toDouble() }
            .filter { it > 6.0 }
            .toList()
        assertTrue(
            long.isEmpty(),
            "instructions of ${long.joinToString()} seconds are a speech, not an instruction"
        )
    }
}
