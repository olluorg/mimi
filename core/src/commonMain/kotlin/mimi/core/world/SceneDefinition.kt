package mimi.core.world

import mimi.core.model.*
import kotlinx.serialization.Serializable

// ── Scene Definition (loaded from JSON) ───────────────────────────────────────

@Serializable
data class SceneDefinition(
    val sceneId:       String,
    val entities:      List<EntityDefinition>             = emptyList(),
    val objectives:    List<ObjectiveDefinition>          = emptyList(),
    val behaviors:     List<SceneBehaviorBinding>         = emptyList(),
    val fieldPolicies: Map<PropertyKey, ResolutionPolicy> = emptyMap(),
    val idleTimeout:   Int                                = 30,
    val onIdle:        OnIdleAction                       = OnIdleAction.HINT,
    val metadata:      Map<String, String>                = emptyMap()
)

@Serializable
data class EntityDefinition(
    val id:         EntityId,
    val components: List<ComponentType>                    = emptyList(),
    val behaviors:  List<BehaviorRef>                     = emptyList(),
    val properties: Map<PropertyKey, PropertyValue>       = emptyMap(),
    val tags:       Map<TagKey, String>                   = emptyMap(),
    val zOrder:     Int                                   = 0
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
enum class OnIdleAction { HINT, RESET, NONE }

// ── Constraint Definition (within entity's dropTarget component) ───────────────

@Serializable
data class ConstraintDefinition(
    val type:   String,
    val config: Map<String, String> = emptyMap()
)
