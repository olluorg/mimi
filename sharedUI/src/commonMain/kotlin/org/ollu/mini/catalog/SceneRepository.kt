package org.ollu.mini.catalog

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import mimi.core.scene.SceneLoader
import mimi.core.world.SceneDefinition

class SceneRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient         = true
        coerceInputValues = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun fetchCatalog(url: String): Catalog =
        client.get(url).body()

    suspend fun fetchScene(entry: CatalogEntry): SceneDefinition {
        val text = client.get(entry.url).bodyAsText()
        return SceneLoader.load(text)
    }

    fun close() = client.close()
}
