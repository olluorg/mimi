package mimi.core.scene

import kotlinx.serialization.json.Json
import mimi.core.world.SceneDefinition

object SceneLoader {
    private val json = Json {
        ignoreUnknownKeys    = true
        isLenient            = true
        coerceInputValues    = true
        encodeDefaults       = true
    }

    fun load(jsonString: String): SceneDefinition =
        json.decodeFromString(SceneDefinition.serializer(), jsonString)

    fun serialize(scene: SceneDefinition): String =
        json.encodeToString(SceneDefinition.serializer(), scene)
}
