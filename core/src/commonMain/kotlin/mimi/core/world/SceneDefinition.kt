package mimi.core.world

import mimi.core.model.*
import kotlinx.serialization.Serializable

// ── Scene Definition (loaded from JSON) ───────────────────────────────────────

@Serializable
data class LevelDefinition(
    val levelId:    String,
    val entities:   List<EntityDefinition>    = emptyList(),
    val objectives: List<ObjectiveDefinition> = emptyList()
)

@Serializable
data class SceneDefinition(
    val sceneId:       String,
    val entities:      List<EntityDefinition>             = emptyList(),
    val objectives:    List<ObjectiveDefinition>          = emptyList(),
    val behaviors:     List<SceneBehaviorBinding>         = emptyList(),
    val fieldPolicies: Map<PropertyKey, ResolutionPolicy> = emptyMap(),
    val stateMachine:  SceneStateMachineDefinition?        = null,
    val idleTimeout:   Int                                = 30,
    val onIdle:        OnIdleAction                       = OnIdleAction.HINT,
    val metadata:      Map<String, String>                = emptyMap(),
    val levels:        List<LevelDefinition>              = emptyList(),
    /** How this scene should differ between plays; null means always identical. */
    val variation:     SceneVariation?                    = null,
    /** The engine-relevant part of the scene's `meta` block — its speech profile. */
    val meta:          SceneAnnotations                   = SceneAnnotations()
) {
    // If multi-level, level 0's entities/objectives override top-level fields.
    fun entitiesForLevel(index: Int): List<EntityDefinition> =
        levels.getOrNull(index)?.entities ?: entities

    fun objectivesForLevel(index: Int): List<ObjectiveDefinition> =
        levels.getOrNull(index)?.objectives ?: objectives

    val isMultiLevel: Boolean get() = levels.isNotEmpty()
}

@Serializable
data class EntityDefinition(
    val id:         EntityId,
    val components: List<ComponentType>                    = emptyList(),
    val behaviors:  List<BehaviorRef>                     = emptyList(),
    val properties: Map<PropertyKey, PropertyValue>       = emptyMap(),
    val tags:       Map<TagKey, String>                   = emptyMap(),
    val zOrder:     Int                                   = 0,
    /**
     * What the entity starts as. Defaults to on-screen and interactive.
     * Declaring INACTIVE lets a scene hold something back — a reward that
     * appears only once the child asks for it, an item revealed step by step —
     * without needing a state machine to hide it first.
     */
    val initialState: EntityLifecycleState                = EntityLifecycleState.ACTIVE
)

@Serializable
data class ObjectiveDefinition(
    val id:       ObjectiveId,
    val type:     String,
    val config:   Map<String, String>  = emptyMap(),
    val requires: ObjectiveId?         = null
)

@Serializable
data class SceneBehaviorBinding(
    val behaviorRef: BehaviorRef,
    val entityId:    EntityId? = null   // null = scene-global
)

@Serializable
data class SceneStateMachineDefinition(
    val initialState: String,
    val states:       List<SceneFsmStateDefinition> = emptyList(),
    val transitions:  List<SceneFsmTransitionDefinition> = emptyList()
)

@Serializable
data class SceneFsmStateDefinition(
    val id:          String,
    val onEnter:     List<SceneFsmActionDefinition> = emptyList(),
    val durationMs:  Long? = null,
    val inputEnabled: Boolean = true
)

@Serializable
data class SceneFsmTransitionDefinition(
    val from:    String,
    val to:      String,
    val onEvent: String? = null,
    val afterMs: Long? = null,
    val actions: List<SceneFsmActionDefinition> = emptyList()
)

@Serializable
data class SceneFsmActionDefinition(
    val type:     String,
    val entityId: EntityId? = null,
    val field:    PropertyKey? = null,
    val value:    PropertyValue? = null,
    val state:    EntityLifecycleState? = null,
    val feedbackType: String? = null,
    val params: Map<String, String> = emptyMap()
)

@Serializable
enum class OnIdleAction { HINT, RESET, NONE }

// ── Constraint Definition (within entity's dropTarget component) ───────────────

@Serializable
data class ConstraintDefinition(
    val type:   String,
    val config: Map<String, String> = emptyMap()
)
