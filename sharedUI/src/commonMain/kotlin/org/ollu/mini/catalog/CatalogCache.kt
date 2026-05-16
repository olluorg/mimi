package org.ollu.mini.catalog

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

class CatalogCache {
    private val settings = Settings()
    private val json     = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Catalog index ─────────────────────────────────────────────────────────

    fun getCachedIndex(): CatalogIndex? =
        settings.getStringOrNull(KEY_INDEX)?.let {
            runCatching { json.decodeFromString<CatalogIndex>(it) }.getOrNull()
        }

    fun putIndex(index: CatalogIndex) {
        settings[KEY_INDEX] = json.encodeToString(index)
    }

    // ── Scene JSON blobs ──────────────────────────────────────────────────────

    fun getCachedScene(sceneId: String): String? =
        settings.getStringOrNull("$KEY_SCENE$sceneId")

    fun putScene(sceneId: String, sceneJson: String) {
        settings["$KEY_SCENE$sceneId"] = sceneJson
    }

    // ── Meta extraction ───────────────────────────────────────────────────────

    fun extractMeta(sceneJson: String): SceneMeta =
        runCatching { json.decodeFromString<SceneBundle>(sceneJson).meta }.getOrElse { SceneMeta() }

    fun toCatalogEntry(ref: SceneRef): CatalogEntry? {
        val sceneJson = getCachedScene(ref.id) ?: return null
        val meta      = extractMeta(sceneJson)
        return CatalogEntry(
            id          = ref.id,
            title       = meta.title.ifBlank { ref.id },
            description = meta.description,
            url         = ref.url,
            ageMin      = meta.ageMin,
            ageMax      = meta.ageMax,
            skills      = meta.skills,
            icon        = meta.icon,
            requires    = meta.requires
        )
    }

    fun getDisplayEntries(): List<CatalogEntry> =
        getCachedIndex()?.scenes?.mapNotNull { toCatalogEntry(it) } ?: emptyList()

    // ── Bundle seeding ────────────────────────────────────────────────────────

    @OptIn(ExperimentalResourceApi::class)
    suspend fun seedFromBundle() {
        try {
            val indexJson = Res.readBytes("files/catalog/catalog.json").decodeToString()
            val index     = json.decodeFromString<CatalogIndex>(indexJson)
            putIndex(index)
            for (ref in index.scenes) {
                val sceneJson = try {
                    Res.readBytes("files/catalog/${ref.id}.json").decodeToString()
                } catch (_: Exception) {
                    try {
                        Res.readBytes("files/catalog/${ref.id}/${ref.id}.json").decodeToString()
                    } catch (_: Exception) { null }
                } ?: continue
                putScene(ref.id, sceneJson)
            }
        } catch (_: Exception) { /* no bundle */ }
    }

    companion object {
        private const val KEY_INDEX = "catalog_index_v2"
        private const val KEY_SCENE = "scene_"
    }
}
