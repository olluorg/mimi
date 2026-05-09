package mimi.core.world

import mimi.core.model.*
import mimi.core.command.Command

// ── World Snapshot (read-only, given to behaviors) ────────────────────────────

interface WorldSnapshot {
    val tick: Tick

    fun getEntity(id: EntityId): EntitySnapshot?
    fun getProperty(entityId: EntityId, key: PropertyKey): PropertyValue?
    fun getEntityState(id: EntityId): EntityLifecycleState
    fun getObjectiveState(id: ObjectiveId): ObjectiveState
    fun getCounter(name: String): Long

    fun getAllEntities(): List<EntitySnapshot>
    fun getEntitiesByTag(key: TagKey, value: String): List<EntitySnapshot>
    fun getEntitiesByState(state: EntityLifecycleState): List<EntitySnapshot>
    fun getEntitiesByComponent(type: ComponentType): List<EntitySnapshot>
}

// ── World State (engine-internal, produces snapshots) ─────────────────────────

interface WorldState : WorldSnapshot {
    fun apply(commands: List<Command>): WorldState
    fun snapshot(): WorldSnapshot
}

// ── World State Factory ────────────────────────────────────────────────────────

interface WorldStateFactory {
    fun fromScene(sceneDefinition: SceneDefinition): WorldState
}

// ── Scene Definition (loaded from JSON) ───────────────────────────────────────

data class SceneDefinition(
    val sceneId:      String,
    val entities:     List<EntityDefinition>,
    val objectives:   List<ObjectiveDefinition>,
    val behaviors:    List<SceneBehaviorBinding>,
    val fieldPolicies: Map<PropertyKey, ResolutionPolicy> = emptyMap(),
    val metadata:     Map<String, Any>                    = emptyMap()
)

data class EntityDefinition(
    val id:         EntityId,
    val components: List<ComponentType>,
    val behaviors:  List<BehaviorRef>,
    val properties: Map<PropertyKey, PropertyValue> = emptyMap(),
    val tags:       Map<TagKey, String>             = emptyMap()
)

data class ObjectiveDefinition(
    val id:       ObjectiveId,
    val type:     String,
    val config:   Map<String, Any>   = emptyMap(),
    val requires: ObjectiveId?       = null
)

data class SceneBehaviorBinding(
    val behaviorRef: BehaviorRef,
    val entityId:    EntityId?     = null  // null = scene-global behavior
)
