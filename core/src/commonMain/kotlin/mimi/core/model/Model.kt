package mimi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Type Aliases ──────────────────────────────────────────────────────────────

typealias EntityId      = String
typealias BehaviorId    = String
typealias ComponentType = String
typealias PropertyKey   = String
typealias TagKey        = String
typealias ObjectiveId   = String
typealias Tick          = Long

const val SCENE_ENTITY_ID: EntityId = "_scene"
const val TICKS_PER_SECOND: Long    = 60L

fun secondsToTicks(seconds: Int): Long = seconds * TICKS_PER_SECOND
fun secondsToTicks(seconds: Double): Long = (seconds * TICKS_PER_SECOND).toLong()

// ── Property Value ────────────────────────────────────────────────────────────

@Serializable
sealed class PropertyValue {
    @Serializable @SerialName("text")
    data class Text(val value: String)              : PropertyValue()

    @Serializable @SerialName("num")
    data class Num(val value: Double)               : PropertyValue()

    @Serializable @SerialName("flag")
    data class Flag(val value: Boolean)             : PropertyValue()

    @Serializable @SerialName("vec2")
    data class Vec2(val x: Float, val y: Float)     : PropertyValue()

    @Serializable @SerialName("ref")
    data class Ref(val entityId: EntityId)          : PropertyValue()

    @Serializable @SerialName("list")
    data class TextList(val values: List<String>)   : PropertyValue()
}

// ── Entity Snapshot ───────────────────────────────────────────────────────────

data class EntitySnapshot(
    val id:         EntityId,
    val components: List<ComponentType>,
    val properties: Map<PropertyKey, PropertyValue>,
    val state:      EntityLifecycleState,
    val tags:       Map<TagKey, String>
) {
    fun property(key: PropertyKey): PropertyValue? = properties[key]
    fun num(key: PropertyKey): Double?             = (properties[key] as? PropertyValue.Num)?.value
    fun text(key: PropertyKey): String?            = (properties[key] as? PropertyValue.Text)?.value
    fun flag(key: PropertyKey): Boolean?           = (properties[key] as? PropertyValue.Flag)?.value
    fun vec2(key: PropertyKey): PropertyValue.Vec2? = properties[key] as? PropertyValue.Vec2
    fun tag(key: TagKey): String?                  = tags[key]
    fun hasComponent(type: ComponentType): Boolean = type in components
    fun hasTag(key: TagKey): Boolean               = key in tags
    fun hasTag(key: TagKey, value: String): Boolean = tags[key] == value
}

// ── Lifecycle States ──────────────────────────────────────────────────────────

enum class EntityLifecycleState { ACTIVE, INACTIVE, TENTATIVE, LOCKED }

enum class ObjectiveState { INACTIVE, PROGRESSING, TENTATIVE_COMPLETE, LOCKED_COMPLETE }

// ── Resolution Policy ─────────────────────────────────────────────────────────

enum class ResolutionPolicy { PRIORITY, MERGE, LAST_WINS, FIRST_WINS }

// ── Validation ────────────────────────────────────────────────────────────────

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()

    val isValid: Boolean get() = this is Valid

    fun errorList(): List<String> = (this as? Invalid)?.errors ?: emptyList()

    companion object {
        fun combine(results: List<ValidationResult>): ValidationResult {
            val errors = results.filterIsInstance<Invalid>().flatMap { it.errors }
            return if (errors.isEmpty()) Valid else Invalid(errors)
        }
    }
}

// ── Behavior Ref ──────────────────────────────────────────────────────────────

@Serializable
data class BehaviorRef(
    val type:   BehaviorId,
    val config: Map<String, String> = emptyMap()
)
