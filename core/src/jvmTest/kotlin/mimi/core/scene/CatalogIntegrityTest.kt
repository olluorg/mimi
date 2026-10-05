package mimi.core.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mimi.core.engine.defaultBehaviorRegistry
import mimi.core.engine.defaultConstraintRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The content gate.
 *
 * `catalog/` is an over-the-air channel: whatever lands on main is published to
 * GitHub Pages and picked up by every installed app without a release. These
 * tests are the only thing standing between a malformed scene and a child
 * staring at a screen that cannot be completed, so they run on every push.
 */
class CatalogIntegrityTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val catalogDir: File = File(
        System.getProperty("mimi.catalogDir")
            ?: error("mimi.catalogDir system property is not set — see core/build.gradle.kts")
    )

    // Folders starting with "_" hold shared assets — the card library — rather
    // than a scene, so they are not expected to contain a scene file.
    private val sceneDirs: List<File>
        get() = catalogDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith("_") }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun sceneFileOf(dir: File) = File(dir, "${dir.name}.json")

    @Test
    fun `catalog directory is present and populated`() {
        assertTrue(catalogDir.isDirectory, "catalog/ not found at ${catalogDir.absolutePath}")
        assertTrue(sceneDirs.isNotEmpty(), "catalog/ contains no scene directories")
    }

    @Test
    fun `every scene directory holds a scene file named after it`() {
        val missing = sceneDirs.filterNot { sceneFileOf(it).isFile }.map { it.name }
        if (missing.isNotEmpty()) {
            fail("Scene directories without a matching <id>.json: ${missing.joinToString(", ")}")
        }
    }

    @Test
    fun `every scene parses into a SceneDefinition`() {
        val failures = mutableListOf<String>()
        for (dir in sceneDirs) {
            val file = sceneFileOf(dir)
            if (!file.isFile) continue
            runCatching { SceneLoader.load(file.readText()) }
                .onFailure { failures += "${dir.name}: ${it.message ?: it::class.simpleName}" }
        }
        if (failures.isNotEmpty()) {
            fail("Scenes that do not parse:\n" + failures.joinToString("\n") { "  • $it" })
        }
    }

    @Test
    fun `sceneId matches the directory name`() {
        val mismatched = mutableListOf<String>()
        for (dir in sceneDirs) {
            val file = sceneFileOf(dir)
            if (!file.isFile) continue
            val scene = runCatching { SceneLoader.load(file.readText()) }.getOrNull() ?: continue
            if (scene.sceneId != dir.name) {
                mismatched += "${dir.name}/ declares sceneId '${scene.sceneId}'"
            }
        }
        if (mismatched.isNotEmpty()) {
            fail(
                "sceneId must equal the directory name — the published URL is built from it:\n" +
                    mismatched.joinToString("\n") { "  • $it" }
            )
        }
    }

    @Test
    fun `every scene passes engine validation`() {
        val constraintRegistry = defaultConstraintRegistry()
        val behaviorRegistry   = defaultBehaviorRegistry(constraintRegistry)

        val broken   = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        for (dir in sceneDirs) {
            val file = sceneFileOf(dir)
            if (!file.isFile) continue
            val scene = runCatching { SceneLoader.load(file.readText()) }.getOrNull() ?: continue

            val report = SceneValidator.inspect(
                scene              = scene,
                behaviorRegistry   = behaviorRegistry,
                constraintRegistry = constraintRegistry
            )
            if (!report.isValid) {
                broken += "${dir.name}:\n" + report.errors.joinToString("\n") { "      - $it" }
            }
            warnings += report.warnings.map { "${dir.name}: $it" }
        }

        // Warnings do not fail the gate, but they are the early signal for content
        // drift, so they are printed where CI and local runs both surface them.
        if (warnings.isNotEmpty()) {
            println("Scene validation warnings (${warnings.size}):")
            warnings.forEach { println("  • $it") }
        }

        if (broken.isNotEmpty()) {
            fail("Scenes failing validation:\n" + broken.joinToString("\n") { "  • $it" })
        }
    }

    @Test
    fun `every scene carries display metadata`() {
        val problems = mutableListOf<String>()
        for (dir in sceneDirs) {
            val file = sceneFileOf(dir)
            if (!file.isFile) continue
            val root = runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
                ?: continue
            val meta = root["meta"]?.jsonObject
            if (meta == null) {
                problems += "${dir.name}: no 'meta' block — it would show up unnamed in the catalog"
                continue
            }
            fun text(key: String) = meta[key]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
            if (text("title").isBlank())       problems += "${dir.name}: meta.title is empty"
            if (text("description").isBlank()) problems += "${dir.name}: meta.description is empty"

            val skills = meta["skills"]?.jsonArray?.size ?: 0
            if (skills == 0) {
                problems += "${dir.name}: meta.skills is empty — the learning planner cannot place this scene"
            }
            val ageMin = meta["ageMin"]?.jsonPrimitive?.content?.toIntOrNull()
            val ageMax = meta["ageMax"]?.jsonPrimitive?.content?.toIntOrNull()
            if (ageMin != null && ageMax != null && ageMin > ageMax) {
                problems += "${dir.name}: meta.ageMin ($ageMin) is above meta.ageMax ($ageMax)"
            }
        }
        if (problems.isNotEmpty()) {
            fail("Scene metadata problems:\n" + problems.joinToString("\n") { "  • $it" })
        }
    }

    @Test
    fun `scene prerequisites point at scenes that exist`() {
        val knownIds = sceneDirs.map { it.name }.toSet()
        val dangling = mutableListOf<String>()
        for (dir in sceneDirs) {
            val file = sceneFileOf(dir)
            if (!file.isFile) continue
            val root = runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
                ?: continue
            val requires = root["meta"]?.jsonObject?.get("requires")?.jsonArray ?: continue
            for (req in requires) {
                val id = req.jsonPrimitive.contentOrNullSafe() ?: continue
                if (id !in knownIds) {
                    dangling += "${dir.name} requires '$id', which is not in the catalog"
                }
                if (id == dir.name) {
                    dangling += "${dir.name} requires itself — it would never unlock"
                }
            }
        }
        if (dangling.isNotEmpty()) {
            fail("Broken prerequisites:\n" + dangling.joinToString("\n") { "  • $it" })
        }
    }

    @Test
    fun `referenced animations exist and parse`() {
        val problems = mutableListOf<String>()
        val animationRef = Regex("""_anim/[\w./-]+\.json""")
        for (dir in sceneDirs) {
            val file = sceneFileOf(dir)
            if (!file.isFile) continue
            for (match in animationRef.findAll(file.readText()).map { it.value }.distinct()) {
                val animation = File(catalogDir, match)
                if (!animation.isFile) {
                    problems += "${dir.name}: references '$match', which is not in the catalog"
                    continue
                }
                val layers = runCatching {
                    json.parseToJsonElement(animation.readText()).jsonObject["layers"]?.jsonArray?.size
                }.getOrNull()
                if (layers == null || layers == 0) {
                    problems += "${dir.name}: '$match' has no layers — it would play as nothing"
                }
            }
        }
        if (problems.isNotEmpty()) {
            fail("Animation problems (run tools/generate_animations.py):\n" +
                problems.joinToString("\n") { "  • $it" })
        }
    }

    @Test
    fun `catalog index lists exactly the scenes on disk`() {
        val indexFile = File(catalogDir, "catalog.json")
        assertTrue(indexFile.isFile, "catalog/catalog.json is missing")

        val index = json.parseToJsonElement(indexFile.readText()).jsonObject
        val listed = index["scenes"]?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNullSafe() }
            ?.toSet()
            ?: emptySet()
        val onDisk = sceneDirs.map { it.name }.toSet()

        val missing = (onDisk - listed).sorted()
        val extra   = (listed - onDisk).sorted()

        val problems = buildList {
            if (missing.isNotEmpty()) add("present on disk but not in catalog.json: ${missing.joinToString(", ")}")
            if (extra.isNotEmpty())   add("listed in catalog.json but absent on disk: ${extra.joinToString(", ")}")
        }
        if (problems.isNotEmpty()) {
            fail(
                "catalog.json is out of sync (CI regenerates it, so regenerate locally too):\n" +
                    problems.joinToString("\n") { "  • $it" }
            )
        }
    }

    @Test
    fun `referenced media files exist on disk`() {
        val missing = mutableListOf<String>()
        // Sounds and pictures alike: a scene that points at a file which is not
        // there ships a silent prompt or a blank tile to every installed app.
        val mediaRef = Regex("""[\w./-]+\.(?:mp3|ogg|wav|png|jpg|jpeg|webp)""", RegexOption.IGNORE_CASE)

        for (dir in sceneDirs) {
            val file = sceneFileOf(dir)
            if (!file.isFile) continue
            for (match in mediaRef.findAll(file.readText())) {
                // Paths are written relative to the catalog root, so resolve them
                // from there and fall back to this scene's own media folder.
                val fromRoot = File(catalogDir, match.value)
                val fromScene = File(File(dir, "media"), match.value.substringAfterLast('/'))
                if (!fromRoot.isFile && !fromScene.isFile) {
                    missing += "${dir.name}: references '${match.value}' but no such file exists"
                }
            }
        }
        if (missing.isNotEmpty()) {
            fail("Missing media assets:\n" + missing.joinToString("\n") { "  • $it" })
        }
    }
}

/** `jsonPrimitive.content` throws on JSON null; this keeps the checks total. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()
