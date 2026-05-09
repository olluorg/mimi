package mimi.core.behavior

import mimi.core.model.*
import mimi.core.command.Command
import mimi.core.world.WorldSnapshot
import kotlin.reflect.KClass

// ── Pipeline Phase ────────────────────────────────────────────────────────────

enum class Phase {
    INTERPRETATION,     // constraint evaluation, entity filtering
    RESOLUTION,         // game logic decisions
    COMMAND_GENERATION, // state change intentions
    FEEDBACK            // output triggers (sound, animation, haptic)
}

// ── Behavior Context ──────────────────────────────────────────────────────────

interface BehaviorContext {
    val snapshot: WorldSnapshot
    val tick:     Tick
}

data class DefaultBehaviorContext(
    override val snapshot: WorldSnapshot,
    override val tick:     Tick
) : BehaviorContext

// ── Behavior ──────────────────────────────────────────────────────────────────

interface Behavior {
    val id:         BehaviorId
    val phase:      Phase
    val eventTypes: Set<KClass<out GameEvent>>

    fun handle(event: GameEvent, ctx: BehaviorContext): List<Command>
}

// ── Behavior Plugin ───────────────────────────────────────────────────────────

interface BehaviorPlugin {
    val behaviorId:  BehaviorId
    val phase:       Phase
    val eventTypes:  Set<KClass<out GameEvent>>

    fun create(config: Map<String, String>, entityId: EntityId?): Behavior
    fun validateConfig(config: Map<String, String>): ValidationResult = ValidationResult.Valid
}

// ── Behavior Registry ─────────────────────────────────────────────────────────

interface BehaviorRegistry {
    fun register(plugin: BehaviorPlugin)
    fun create(ref: BehaviorRef, entityId: EntityId?): Behavior
    fun isRegistered(id: BehaviorId): Boolean
    fun validateRef(ref: BehaviorRef): ValidationResult
}

class DefaultBehaviorRegistry : BehaviorRegistry {
    private val plugins = mutableMapOf<BehaviorId, BehaviorPlugin>()

    override fun register(plugin: BehaviorPlugin) {
        require(!plugins.containsKey(plugin.behaviorId)) {
            "Behavior plugin '${plugin.behaviorId}' is already registered"
        }
        plugins[plugin.behaviorId] = plugin
    }

    override fun create(ref: BehaviorRef, entityId: EntityId?): Behavior {
        val plugin = plugins[ref.type]
            ?: error("No behavior plugin registered for type '${ref.type}'")
        return plugin.create(ref.config, entityId)
    }

    override fun isRegistered(id: BehaviorId): Boolean = plugins.containsKey(id)

    override fun validateRef(ref: BehaviorRef): ValidationResult {
        val plugin = plugins[ref.type]
            ?: return ValidationResult.Invalid(listOf("Unknown behavior type: '${ref.type}'"))
        return plugin.validateConfig(ref.config)
    }
}
