package mimi.core.plugin

import mimi.core.model.*
import mimi.core.world.WorldSnapshot

// ── Component Definition ──────────────────────────────────────────────────────

interface ComponentDefinition {
    val type:              ComponentType
    val defaultProperties: Map<PropertyKey, PropertyValue>
    val fieldPolicies:     Map<PropertyKey, ResolutionPolicy>  // overrides engine defaults
}

interface ComponentRegistry {
    fun register(definition: ComponentDefinition)
    fun resolve(type: ComponentType): ComponentDefinition
    fun isRegistered(type: ComponentType): Boolean
}

// ── Constraint Evaluator ──────────────────────────────────────────────────────

interface ConstraintEvaluator {
    val type: String

    // Pure function — no side effects
    fun evaluate(
        source: EntitySnapshot,
        target: EntitySnapshot,
        config: Map<String, Any>
    ): Boolean
}

interface ConstraintRegistry {
    fun register(evaluator: ConstraintEvaluator)
    fun evaluate(
        type:   String,
        source: EntitySnapshot,
        target: EntitySnapshot,
        config: Map<String, Any>
    ): Boolean
}

// ── Feedback Channel ──────────────────────────────────────────────────────────

data class FeedbackContext(
    val snapshot: WorldSnapshot,
    val tick:     Tick
)

interface FeedbackChannel {
    val type: String  // "sound", "animation", "haptic", "speech", "particle"
    fun handle(feedbackType: String, params: Map<String, Any>, context: FeedbackContext)
    val isAvailable: Boolean  // platform capability check
}

interface FeedbackRegistry {
    fun register(channel: FeedbackChannel)
    fun dispatch(feedbackType: String, params: Map<String, Any>, context: FeedbackContext)
}

// ── Interaction Handler ───────────────────────────────────────────────────────

interface InteractionHandler {
    val interactionType: String
    fun isAvailable(): Boolean
}

interface InteractionRegistry {
    fun register(handler: InteractionHandler)
    fun isSupported(interactionType: String): Boolean
}

// ── Engine Plugin (root registration point) ───────────────────────────────────

interface EnginePlugin {
    val pluginId: String

    fun onRegister(
        behaviors:    mimi.core.behavior.BehaviorRegistry,
        components:   ComponentRegistry,
        constraints:  ConstraintRegistry,
        feedback:     FeedbackRegistry,
        interactions: InteractionRegistry
    )
}

// ── Built-in Engine Defaults ──────────────────────────────────────────────────

object EngineFieldPolicies {
    val defaults: Map<PropertyKey, ResolutionPolicy> = mapOf(
        "position"       to ResolutionPolicy.PRIORITY,
        "visibility"     to ResolutionPolicy.MERGE,
        "state"          to ResolutionPolicy.LAST_WINS,
        "animationState" to ResolutionPolicy.LAST_WINS,
        "locked"         to ResolutionPolicy.FIRST_WINS
    )

    // Fields matching this suffix use MERGE by default
    const val counterSuffix = "Count"
    val counterPolicy = ResolutionPolicy.MERGE
    val tagPolicy     = ResolutionPolicy.MERGE

    fun resolve(field: PropertyKey, sceneOverrides: Map<PropertyKey, ResolutionPolicy>): ResolutionPolicy {
        return sceneOverrides[field]
            ?: defaults[field]
            ?: if (field.endsWith(counterSuffix)) counterPolicy else ResolutionPolicy.LAST_WINS
    }
}
