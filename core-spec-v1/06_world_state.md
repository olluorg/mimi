# World State

## Definition

`WorldState` is the complete, authoritative description of the scene at a given tick.

```
World(t) = {
    tick:       Tick
    entities:   Map<EntityId, EntitySnapshot>
    objectives: Map<ObjectiveId, ObjectiveState>
    counters:   Map<String, Long>
    metadata:   Map<String, PropertyValue>
}
```

## Immutability Model

`World(t)` is **never modified**.

When commands are applied:
```
World(t+1) = apply(World(t), resolvedCommands)
```

`World(t)` remains intact. `World(t+1)` is a new object.

This enables:
- deterministic replay (store commands, replay from `World(0)`)
- debug stepping (inspect any `World(N)`)
- undo (revert to `World(t-1)`)

## Entity Snapshot

Each entity in World has:

```
EntitySnapshot {
    id:         EntityId
    components: List<ComponentType>
    properties: Map<PropertyKey, PropertyValue>
    state:      EntityLifecycleState
    tags:       Map<TagKey, String>
}
```

Properties are the runtime-mutable fields.
Components and tags are declared in scene JSON and do not change at runtime in v1.

## Entity Lifecycle States

```
ACTIVE       — default, interactive
INACTIVE     — not interactive, not rendered
TENTATIVE    — placed/matched but not confirmed
LOCKED       — confirmed, immutable
```

Transitions:

```
ACTIVE → TENTATIVE   (on match, pending objective completion)
TENTATIVE → ACTIVE   (on unmatch, child picks object back up)
TENTATIVE → LOCKED   (on scene complete or explicit lock event)
ACTIVE → INACTIVE    (visibility behavior)
INACTIVE → ACTIVE    (visibility behavior)
```

`LOCKED` is terminal — no transitions out of LOCKED in v1.

## World State Initialization

`World(0)` is constructed from scene JSON at load time:

```json
{
  "sceneId": "sort_fruits_01",
  "entities": [...],
  "objectives": [...],
  "counters": {
    "mistakes": 0,
    "matches": 0
  }
}
```

All entities start in `ACTIVE` state.
All objectives start in `INACTIVE` state.
All counters start at declared initial value (default: 0).

## WorldSnapshot (Read Interface)

Behaviors receive a `WorldSnapshot` — a read-only projection of `WorldState`:

```
WorldSnapshot {
    tick:                    Tick
    getEntity(id):           EntitySnapshot?
    getProperty(id, key):    PropertyValue?
    getEntityState(id):      EntityLifecycleState
    getObjectiveState(id):   ObjectiveState
    getCounter(name):        Long
    getAllEntities():         List<EntitySnapshot>
    getEntitiesByTag(key, value): List<EntitySnapshot>
}
```

Behaviors have no access to `World(t-1)` or future states.

## Counters

Global counters track adaptive metrics:

| Counter | Description |
|---|---|
| `mistakes` | Total wrong drop attempts |
| `matches` | Total correct matches |
| `hints` | Total hints triggered |
| `idleTicks` | Ticks since last interaction |
| `completionTicks` | Ticks from scene start to completion |

Counters are updated via `IncrementCounter` commands.
CRL applies `MERGE` policy to all counter fields by default.

## Tick Rate

The engine runs at **60 ticks per second** (1 tick ≈ 16ms).

Frame rate and tick rate are decoupled:
- Engine loop: fixed 60 ticks/sec
- Render loop: display refresh rate (60–120fps)

Time conversions:
| Duration | Ticks |
|---|---|
| 1 second | 60 |
| 4 seconds (hint L1) | 240 |
| 30 seconds (idle timeout default) | 1800 |
| 20 seconds (min difficulty window) | 1200 |

## `_scene` — Virtual Scene Entity

A special entity with `id = "_scene"` is always present in every World State.

Properties:
- Not rendered
- Not interactive
- Not declared in scene.json (injected by engine at load time)
- Holds scene-global counters and adaptive state

Default fields on `_scene`:

| Field | Type | Initial |
|---|---|---|
| `mistakes` | Number | 0 |
| `matches` | Number | 0 |
| `hints` | Number | 0 |
| `idleTicks` | Number | 0 |
| `difficulty` | Number | 0.5 |
| `hintLevel` | Number | 0 |
| `successRateWindow` | Number | 1.0 |

`idleTicks` is incremented by the engine each tick with no input event. Reset to 0 on any input.

## Invariants

1. `World(t)` is read-only from the moment it is captured as a snapshot.
2. `World(t+1)` is always constructed from `World(t)` + resolved commands — never from partial state.
3. Entity `id` is globally unique within a scene.
4. `LOCKED` state is irreversible within a session.
5. No entity may be created or destroyed at runtime in v1 (entity set is fixed from scene JSON).
6. `tick` in WorldState always equals the engine's current tick at commit time.
