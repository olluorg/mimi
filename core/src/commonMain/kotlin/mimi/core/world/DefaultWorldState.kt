package mimi.core.world

import mimi.core.model.*
import mimi.core.command.Command

// ── WorldState Interface ──────────────────────────────────────────────────────

interface WorldState : WorldSnapshot {
    fun apply(commands: List<Command>): WorldState
    fun snapshot(): WorldSnapshot
}

// ── DefaultWorldState ─────────────────────────────────────────────────────────

class DefaultWorldState(
    override val tick:    Tick,
    val entities:         Map<EntityId, EntitySnapshot>,
    val objectives:       Map<ObjectiveId, ObjectiveState>,
    val counters:         Map<String, Long>
) : WorldState {

    // ── WorldSnapshot ─────────────────────────────────────────────────────────

    override fun getEntity(id: EntityId): EntitySnapshot? = entities[id]

    override fun getProperty(entityId: EntityId, key: PropertyKey): PropertyValue? =
        entities[entityId]?.properties?.get(key)

    override fun getEntityState(id: EntityId): EntityLifecycleState =
        entities[id]?.state ?: EntityLifecycleState.INACTIVE

    override fun getObjectiveState(id: ObjectiveId): ObjectiveState =
        objectives[id] ?: ObjectiveState.INACTIVE

    override fun getCounter(name: String): Long =
        counters[name] ?: (entities[SCENE_ENTITY_ID]?.num(name)?.toLong() ?: 0L)

    override fun getNum(entityId: EntityId, key: PropertyKey): Double? =
        entities[entityId]?.num(key)

    override fun getAllEntities(): List<EntitySnapshot> =
        entities.values.filter { it.id != SCENE_ENTITY_ID }

    override fun getEntitiesByTag(key: TagKey, value: String): List<EntitySnapshot> =
        entities.values.filter { it.hasTag(key, value) }

    override fun getEntitiesByState(state: EntityLifecycleState): List<EntitySnapshot> =
        entities.values.filter { it.state == state && it.id != SCENE_ENTITY_ID }

    override fun getEntitiesByComponent(type: ComponentType): List<EntitySnapshot> =
        entities.values.filter { it.hasComponent(type) }

    // ── Apply ─────────────────────────────────────────────────────────────────

    override fun apply(commands: List<Command>): WorldState {
        val newEntities   = entities.toMutableMap()
        val newObjectives = objectives.toMutableMap()
        val newCounters   = counters.toMutableMap()

        for (cmd in commands) {
            when (cmd) {
                is Command.SetProperty -> {
                    val entity = newEntities[cmd.target] ?: continue
                    newEntities[cmd.target] = entity.copy(
                        properties = entity.properties + (cmd.field to cmd.value)
                    )
                }

                is Command.IncrementCounter -> {
                    val current = newCounters[cmd.field] ?: 0L
                    newCounters[cmd.field] = current + cmd.delta
                    val sceneEntity = newEntities[SCENE_ENTITY_ID] ?: continue
                    newEntities[SCENE_ENTITY_ID] = sceneEntity.copy(
                        properties = sceneEntity.properties +
                            (cmd.field to PropertyValue.Num((current + cmd.delta).toDouble()))
                    )
                }

                is Command.MarkMatched -> {
                    val dragged = newEntities[cmd.draggedEntityId] ?: continue
                    if (dragged.state == EntityLifecycleState.LOCKED) continue
                    newEntities[cmd.draggedEntityId] = dragged.copy(
                        state = EntityLifecycleState.TENTATIVE,
                        properties = dragged.properties +
                            ("matchedTo" to PropertyValue.Ref(cmd.target))
                    )
                }

                is Command.SetEntityState -> {
                    val entity = newEntities[cmd.target] ?: continue
                    if (entity.state == EntityLifecycleState.LOCKED) continue
                    newEntities[cmd.target] = entity.copy(state = cmd.state)
                }

                is Command.SetObjectiveState -> {
                    val current = newObjectives[cmd.objectiveId] ?: ObjectiveState.INACTIVE
                    if (current == ObjectiveState.LOCKED_COMPLETE) continue
                    newObjectives[cmd.objectiveId] = cmd.state
                }

                is Command.TriggerFeedback -> Unit // handled by FeedbackRegistry, not applied here
            }
        }

        return DefaultWorldState(
            tick       = this.tick + 1,
            entities   = newEntities,
            objectives = newObjectives,
            counters   = newCounters
        )
    }

    override fun snapshot(): WorldSnapshot = this
}

// ── Factory ───────────────────────────────────────────────────────────────────

object DefaultWorldStateFactory {
    fun fromScene(scene: SceneDefinition): DefaultWorldState {
        val sceneEntity = EntitySnapshot(
            id         = SCENE_ENTITY_ID,
            components = emptyList(),
            properties = mapOf(
                "difficulty"          to PropertyValue.Num(0.5),
                "hintLevel"           to PropertyValue.Num(0.0),
                "successRateWindow"   to PropertyValue.Num(1.0),
                "idleTicks"           to PropertyValue.Num(0.0),
                "mistakes"            to PropertyValue.Num(0.0),
                "matches"             to PropertyValue.Num(0.0),
                "hints"               to PropertyValue.Num(0.0)
            ),
            state      = EntityLifecycleState.ACTIVE,
            tags       = emptyMap()
        )

        val entities = buildMap<EntityId, EntitySnapshot> {
            put(SCENE_ENTITY_ID, sceneEntity)
            for (def in scene.entities) {
                put(def.id, EntitySnapshot(
                    id         = def.id,
                    components = def.components,
                    properties = def.properties,
                    state      = EntityLifecycleState.ACTIVE,
                    tags       = def.tags
                ))
            }
        }

        val objectives = scene.objectives.associate { it.id to ObjectiveState.INACTIVE }

        return DefaultWorldState(tick = 0L, entities = entities, objectives = objectives, counters = emptyMap())
    }
}
