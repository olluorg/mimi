package mimi.core.plugin

import mimi.core.model.*
import mimi.core.world.WorldSnapshot

// ── Component Registry ────────────────────────────────────────────────────────

interface ComponentDefinition {
    val type:              ComponentType
    val defaultProperties: Map<PropertyKey, PropertyValue>
    val fieldPolicies:     Map<PropertyKey, ResolutionPolicy>
}

interface ComponentRegistry {
    fun register(definition: ComponentDefinition)
    fun resolve(type: ComponentType): ComponentDefinition?
    fun isRegistered(type: ComponentType): Boolean
}

class DefaultComponentRegistry : ComponentRegistry {
    private val definitions = mutableMapOf<ComponentType, ComponentDefinition>()

    override fun register(definition: ComponentDefinition) {
        definitions[definition.type] = definition
    }

    override fun resolve(type: ComponentType): ComponentDefinition? = definitions[type]
    override fun isRegistered(type: ComponentType): Boolean = definitions.containsKey(type)
}

// ── Constraint Registry ───────────────────────────────────────────────────────

interface ConstraintEvaluator {
    val type: String
    fun evaluate(source: EntitySnapshot, target: EntitySnapshot, config: Map<String, String>): Boolean
}

interface ConstraintRegistry {
    fun register(evaluator: ConstraintEvaluator)
    fun evaluate(type: String, source: EntitySnapshot, target: EntitySnapshot, config: Map<String, String>): Boolean
    fun isRegistered(type: String): Boolean
}

class DefaultConstraintRegistry : ConstraintRegistry {
    private val evaluators = mutableMapOf<String, ConstraintEvaluator>()

    override fun register(evaluator: ConstraintEvaluator) {
        evaluators[evaluator.type] = evaluator
    }

    override fun evaluate(type: String, source: EntitySnapshot, target: EntitySnapshot, config: Map<String, String>): Boolean {
        val evaluator = evaluators[type] ?: error("No constraint evaluator registered for type '$type'")
        return evaluator.evaluate(source, target, config)
    }

    override fun isRegistered(type: String): Boolean = evaluators.containsKey(type)
}

// ── Feedback Registry ─────────────────────────────────────────────────────────

data class FeedbackContext(
    val snapshot: WorldSnapshot,
    val tick:     Tick
)

interface FeedbackChannel {
    val type:        String
    val isAvailable: Boolean
    fun handle(feedbackType: String, params: Map<String, String>, context: FeedbackContext)
}

interface FeedbackRegistry {
    fun register(channel: FeedbackChannel)
    fun dispatch(feedbackType: String, params: Map<String, String>, context: FeedbackContext)
}

class DefaultFeedbackRegistry : FeedbackRegistry {
    private val channels = mutableListOf<FeedbackChannel>()

    override fun register(channel: FeedbackChannel) { channels.add(channel) }

    override fun dispatch(feedbackType: String, params: Map<String, String>, context: FeedbackContext) {
        channels.filter { it.isAvailable }.forEach { it.handle(feedbackType, params, context) }
    }
}

// ── Interaction Registry ──────────────────────────────────────────────────────

interface InteractionHandler {
    val interactionType: String
    fun isAvailable(): Boolean
}

interface InteractionRegistry {
    fun register(handler: InteractionHandler)
    fun isSupported(interactionType: String): Boolean
}

class DefaultInteractionRegistry : InteractionRegistry {
    private val handlers = mutableMapOf<String, InteractionHandler>()

    override fun register(handler: InteractionHandler) { handlers[handler.interactionType] = handler }
    override fun isSupported(interactionType: String): Boolean =
        handlers[interactionType]?.isAvailable() == true
}
