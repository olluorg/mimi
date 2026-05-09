package mimi.core.plugin

import mimi.core.model.*

object EngineFieldPolicies {

    private val defaults: Map<PropertyKey, ResolutionPolicy> = mapOf(
        "position"       to ResolutionPolicy.PRIORITY,
        "visibility"     to ResolutionPolicy.MERGE,
        "state"          to ResolutionPolicy.LAST_WINS,
        "animationState" to ResolutionPolicy.LAST_WINS,
        "locked"         to ResolutionPolicy.FIRST_WINS,
        "difficulty"     to ResolutionPolicy.LAST_WINS
    )

    fun resolve(field: PropertyKey, sceneOverrides: Map<PropertyKey, ResolutionPolicy>): ResolutionPolicy =
        sceneOverrides[field]
            ?: defaults[field]
            ?: when {
                field.endsWith("Count") || field.endsWith("Ticks") -> ResolutionPolicy.MERGE
                field.startsWith("objective.")                      -> ResolutionPolicy.LAST_WINS
                else                                                -> ResolutionPolicy.LAST_WINS
            }
}
