package mimi.core.model

// ── Primitives ────────────────────────────────────────────────────────────────

typealias EntityId    = String
typealias BehaviorId  = String
typealias ComponentType = String
typealias PropertyKey = String
typealias TagKey      = String
typealias ObjectiveId = String
typealias Tick        = Long

// ── Property Values ───────────────────────────────────────────────────────────

sealed interface PropertyValue {
    data class Text(val value: String)              : PropertyValue
    data class Number(val value: Double)            : PropertyValue
    data class Flag(val value: Boolean)             : PropertyValue
    data class Vec2(val x: Float, val y: Float)     : PropertyValue
    data class Ref(val entityId: EntityId)          : PropertyValue
    data class TextList(val values: List<String>)   : PropertyValue
}

// ── Entity ────────────────────────────────────────────────────────────────────

data class EntitySnapshot(
    val id:         EntityId,
    val components: List<ComponentType>,
    val properties: Map<PropertyKey, PropertyValue>,
    val state:      EntityLifecycleState,
    val tags:       Map<TagKey, String>
)

enum class EntityLifecycleState {
    ACTIVE,      // default, interactive
    INACTIVE,    // not interactive, not rendered
    TENTATIVE,   // placed/matched, pending lock
    LOCKED       // confirmed, immutable (terminal)
}

// ── Behavior Reference (as declared in scene JSON) ───────────────────────────

data class BehaviorRef(
    val type:   BehaviorId,
    val config: Map<String, Any> = emptyMap()
)

// ── Objective ─────────────────────────────────────────────────────────────────

enum class ObjectiveState {
    INACTIVE,           // not yet started
    PROGRESSING,        // at least partially satisfied
    TENTATIVE_COMPLETE, // condition met, not yet locked
    LOCKED_COMPLETE     // confirmed, terminal
}

// ── Resolution Policy ─────────────────────────────────────────────────────────

enum class ResolutionPolicy {
    PRIORITY,    // highest priority wins
    MERGE,       // values are combined (sum for numbers, union for lists)
    LAST_WINS,   // latest tick wins
    FIRST_WINS   // earliest tick wins
}

// ── Validation ────────────────────────────────────────────────────────────────

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class  Invalid(val errors: List<String>) : ValidationResult
}
