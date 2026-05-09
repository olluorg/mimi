package mimi.core.delivery

import mimi.core.model.*

// ── Catalog ───────────────────────────────────────────────────────────────────

data class SceneCatalog(
    val version:  Int,
    val scenes:   List<SceneCatalogEntry>
)

data class SceneCatalogEntry(
    val id:           String,
    val title:        String,
    val description:  String,
    val thumbnailPath: String,
    val ageRange:     IntRange,
    val tags:         List<String>,
    val manifestPath: String,
    val version:      String
)

// ── Asset Manifest ────────────────────────────────────────────────────────────

data class AssetManifest(
    val sceneId:        String,
    val version:        String,
    val totalSizeBytes: Long,
    val files:          List<AssetFile>
)

data class AssetFile(
    val path:       String,   // relative to scene root, e.g. "assets/sounds/success.ogg"
    val sha256:     String,
    val sizeBytes:  Long
)

// ── Download State ────────────────────────────────────────────────────────────

sealed interface DownloadState {
    data object Idle                                           : DownloadState
    data class  InProgress(val bytesTotal: Long,
                           val bytesDownloaded: Long)         : DownloadState
    data class  Verifying(val file: String)                   : DownloadState
    data object Committing                                     : DownloadState
    data object Completed                                      : DownloadState
    data class  Failed(val reason: DownloadFailReason,
                       val detail: String? = null)            : DownloadState
}

enum class DownloadFailReason {
    NETWORK_ERROR,
    CHECKSUM_MISMATCH,
    INSUFFICIENT_STORAGE,
    MANIFEST_PARSE_ERROR,
    FILE_WRITE_ERROR
}

// ── Scene Package (local) ─────────────────────────────────────────────────────

data class ScenePackageMeta(
    val sceneId:        String,
    val version:        String,
    val downloadedAt:   String,     // ISO 8601 UTC
    val totalSizeBytes: Long,
    val checksums:      Map<String, String>  // path → sha256
)

// ── Download Manager Interface ────────────────────────────────────────────────

interface SceneDownloadManager {
    fun downloadScene(sceneId: String, manifest: AssetManifest): kotlinx.coroutines.flow.Flow<DownloadState>
    fun deleteScene(sceneId: String)
    fun isDownloaded(sceneId: String): Boolean
    fun getPackageMeta(sceneId: String): ScenePackageMeta?
    fun resolveAssetPath(sceneId: String, relativePath: String): String
}

// ── Catalog Manager Interface ─────────────────────────────────────────────────

interface CatalogManager {
    suspend fun fetchCatalog(): SceneCatalog
    fun getCachedCatalog(): SceneCatalog?
    fun isCatalogStale(): Boolean  // true if cache older than 24h
}
