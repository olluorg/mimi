package org.ollu.mini.catalog

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mimi.core.scene.SceneLoader
import org.ollu.mini.scenes.BuiltinScenes

class CatalogCache {
    private val settings = Settings()
    private val json     = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Catalog ───────────────────────────────────────────────────────────────

    fun getCachedCatalog(): Catalog? =
        settings.getStringOrNull(KEY_CATALOG)?.let {
            runCatching { json.decodeFromString<Catalog>(it) }.getOrNull()
        }

    fun putCatalog(catalog: Catalog) {
        settings[KEY_CATALOG] = json.encodeToString(catalog)
    }

    // ── Scenes ────────────────────────────────────────────────────────────────

    fun getCachedScene(sceneId: String): String? =
        settings.getStringOrNull("$KEY_SCENE$sceneId")

    fun putScene(sceneId: String, sceneJson: String) {
        settings["$KEY_SCENE$sceneId"] = sceneJson
    }

    // ── Builtins ──────────────────────────────────────────────────────────────

    // Always overwrite so code edits to BuiltinScenes are reflected on next run
    fun seedBuiltins() {
        for ((_, scene) in BuiltinScenes.all) {
            putScene(scene.sceneId, SceneLoader.serialize(scene))
        }
    }

    // Network entries (if fetched) + any builtin entries not present in network catalog
    fun getDisplayCatalog(): Catalog {
        val network    = getCachedCatalog()
        val builtins   = BuiltinScenes.entries
        if (network == null) return Catalog(scenes = builtins)
        val networkIds = network.scenes.map { it.id }.toSet()
        val extra      = builtins.filter { it.id !in networkIds }
        return Catalog(scenes = network.scenes + extra)
    }

    companion object {
        private const val KEY_CATALOG = "catalog_json"
        private const val KEY_SCENE   = "scene_"
    }
}
