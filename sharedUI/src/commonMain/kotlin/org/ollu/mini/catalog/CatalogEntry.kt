package org.ollu.mini.catalog

import kotlinx.serialization.Serializable
import mimi.core.world.SpeechProfile

// ── catalog.json ─────────────────────────────────────────────────────────────
// Minimal generated index: just ids and URLs. Never edit by hand.

@Serializable
data class CatalogIndex(
    val version: Int           = 2,
    val scenes:  List<SceneRef> = emptyList()
)

@Serializable
data class SceneRef(
    val id:  String,
    val url: String
)

// ── scene JSON top-level meta ─────────────────────────────────────────────────
// Embedded in every scene JSON file. This is the source of truth for display info.

@Serializable
data class SceneMeta(
    val title:       String       = "",
    val description: String       = "",
    val ageMin:      Int          = 2,
    val ageMax:      Int          = 6,
    val version:     Int          = 1,
    val tags:        List<String> = emptyList(),
    val skills:      List<String> = emptyList(),
    val icon:        String       = "",
    val requires:    List<String> = emptyList(),
    /** Present on speech games only; what puts a scene under «Запуск речи». */
    val speech:      SpeechProfile? = null
)

@Serializable
data class SceneBundle(
    val sceneId: String,
    val meta:    SceneMeta = SceneMeta()
)

// ── UI model ──────────────────────────────────────────────────────────────────
// Built from SceneBundle + SceneRef. Never serialized.

data class CatalogEntry(
    val id:          String,
    val title:       String,
    val description: String,
    val url:         String,
    val ageMin:      Int,
    val ageMax:      Int,
    val skills:      List<String> = emptyList(),
    val icon:        String       = "",
    val requires:    List<String> = emptyList(),
    /** Scene tags, e.g. "sorting", "cards", "choice" — used to vary what a sitting feels like. */
    val tags:        List<String> = emptyList(),
    /** Set only for speech games, which the validator has checked really are. */
    val speech:      SpeechProfile? = null
) {
    val isSpeechGame: Boolean get() = speech != null
}

const val DEFAULT_CATALOG_URL = "https://olluorg.github.io/mimi/catalog.json"
