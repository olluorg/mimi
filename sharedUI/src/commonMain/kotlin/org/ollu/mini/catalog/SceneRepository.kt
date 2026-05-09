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

    init { cache.seedBuiltins() }

    // Emits cached state immediately, then refreshes from network in background
    fun catalogFlow(catalogUrl: String): Flow<CatalogState> = channelFlow {
        // Step 1: instant — whatever is in cache (builtins always present after seedBuiltins)
        send(CatalogState(entries = cache.getDisplayCatalog().scenes, isRefreshing = true))

        // Step 2: network refresh
        try {
            val networkCatalog: Catalog = client.get(catalogUrl).body()
            cache.putCatalog(networkCatalog)
            send(CatalogState(entries = cache.getDisplayCatalog().scenes, isRefreshing = false))

            // Prefetch new scenes into cache in the background (non-blocking for the UI)
            for (entry in networkCatalog.scenes) {
                if (cache.getCachedScene(entry.id) == null) {
                    launch {
                        runCatching {
                            val text = client.get(entry.url).bodyAsText()
                            cache.putScene(entry.id, text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            send(CatalogState(
                entries        = cache.getDisplayCatalog().scenes,
                isRefreshing   = false,
                offlineWarning = true
            ))
        }
    }

    suspend fun fetchScene(entry: CatalogEntry): SceneDefinition {
        // Cache hit covers both local:// builtins and previously fetched network scenes
        cache.getCachedScene(entry.id)?.let { return SceneLoader.load(it) }
        // Network fetch + cache for the future
        val text = client.get(entry.url).bodyAsText()
        cache.putScene(entry.id, text)
        return SceneLoader.load(text)
    }

    fun close() = client.close()
}
