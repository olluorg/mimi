package mimi.core.world

import mimi.core.model.*

interface WorldSnapshot {
    val tick: Tick

    fun getEntity(id: EntityId): EntitySnapshot?
    fun getProperty(entityId: EntityId, key: PropertyKey): PropertyValue?
    fun getEntityState(id: EntityId): EntityLifecycleState
    fun getObjectiveState(id: ObjectiveId): ObjectiveState
    fun getCounter(name: String): Long
    fun getNum(entityId: EntityId, key: PropertyKey): Double?

    fun getAllEntities(): List<EntitySnapshot>
    fun getEntitiesByTag(key: TagKey, value: String): List<EntitySnapshot>
    fun getEntitiesByState(state: EntityLifecycleState): List<EntitySnapshot>
    fun getEntitiesByComponent(type: ComponentType): List<EntitySnapshot>

    fun scene(): EntitySnapshot? = getEntity(SCENE_ENTITY_ID)
    fun difficulty(): Double     = getNum(SCENE_ENTITY_ID, "difficulty") ?: 0.5
    fun idleTicks(): Long        = getCounter("idleTicks")
    fun mistakes(): Long         = getCounter("mistakes")
    fun matches(): Long          = getCounter("matches")
}
