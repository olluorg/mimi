package org.ollu.mini.catalog

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mimi.core.scene.SceneLoader
import mimi.core.world.SceneDefinition

data class CatalogState(
    val entries:        List<CatalogEntry> = emptyList(),
    val isRefreshing:   Boolean            = false,
    val offlineWarning: Boolean            = false
)

class SceneRepository {
    val cache = CatalogCache()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient         = true
        coerceInputValues = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    private val seedMutex = Mutex()
    private var seedDone  = false

    private suspend fun ensureSeeded() {
        if (seedDone) return
        seedMutex.withLock {
            if (!seedDone) {
                cache.seedFromBundle()
                seedDone = true
            }
        }
    }

    fun catalogFlow(catalogUrl: String): Flow<CatalogState> = channelFlow {
        ensureSeeded()

        // Instant: bundle is always seeded above
        send(CatalogState(entries = cache.getDisplayEntries(), isRefreshing = true))

        try {
            // Fetch updated index from network
            val networkIndex: CatalogIndex = client.get(catalogUrl).body()

            // Merge: bundled scenes are a floor — network can only add, never remove
            val current    = cache.getCachedIndex()?.scenes ?: emptyList()
            val currentIds = current.map { it.id }.toSet()
            val merged     = current + networkIndex.scenes.filter { it.id !in currentIds }
            cache.putIndex(CatalogIndex(scenes = merged))

            // Prefetch scene JSONs that aren't in cache yet (new scenes not in bundle)
            for (ref in networkIndex.scenes) {
                if (cache.getCachedScene(ref.id) == null) {
                    launch {
                        runCatching {
                            val sceneJson = client.get(ref.url).bodyAsText()
                            cache.putScene(ref.id, sceneJson)
                        }
                    }
                }
            }

            send(CatalogState(entries = cache.getDisplayEntries(), isRefreshing = false))
        } catch (e: Exception) {
            send(CatalogState(
                entries        = cache.getDisplayEntries(),
                isRefreshing   = false,
                offlineWarning = true
            ))
        }
    }

    suspend fun fetchScene(entry: CatalogEntry): SceneDefinition {
        ensureSeeded()
        cache.getCachedScene(entry.id)?.let { return SceneLoader.load(it) }
        val sceneJson = client.get(entry.url).bodyAsText()
        cache.putScene(entry.id, sceneJson)
        return SceneLoader.load(sceneJson)
    }

    fun close() = client.close()
}
