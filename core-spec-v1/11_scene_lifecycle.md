# Scene Lifecycle

## States

```
NOT_DOWNLOADED
      ↓  (user adds scene)
DOWNLOADING
      ↓  (atomic download complete)
DOWNLOADED
      ↓  (user opens scene)
LOADING        ← assets loaded into memory, World(0) constructed
      ↓
STARTED        ← SceneStarted event fired, engine loop begins
      ↓
[interactions]
      ↓
COMPLETED      ← SceneCompleted event fired, all objectives LOCKED_COMPLETE
      ↓
UNLOADED       ← assets released from memory
```

`STARTED → STARTED` via `SceneReset` (restarts without unloading assets).

## Lifecycle Events

| Event | When | Notes |
|---|---|---|
| `SceneLoading` | Assets begin loading into memory | Before World(0) |
| `SceneStarted` | World(0) ready, engine loop begins | First event behaviors see |
| `SceneReset` | World(0) reconstructed, loop restarts | Assets stay in memory |
| `SceneCompleted` | All objectives `LOCKED_COMPLETE` | Emitted once per session |
| `SceneUnloaded` | Assets released | After user exits |

`SceneReset` is triggered:
- explicitly by the user (restart button)
- automatically after `idleTimeout` if `onIdle: "reset"` is configured

## Idle System

```json
{
  "sceneId": "sort_fruits_01",
  "idleTimeout": 30,
  "onIdle": "hint"
}
```

| `onIdle` value | Behavior |
|---|---|
| `"hint"` | Fires `HintTriggered` event → `adaptiveHint` behavior handles it |
| `"reset"` | Fires `SceneReset` |
| `"none"` | No action (default) |

`idleTimeout` is in seconds. Converted to ticks: `idleTimeout × 60`.
Idle counter resets on any input event.

## Reset Semantics

`SceneReset`:
- Reconstructs `World(0)` from scene.json (all entity states, counters reset)
- Does NOT re-download assets
- Does NOT reset session persistence (mistake history is kept across resets)
- Fires `SceneStarted` again after reset

## Scene Loading Validation

At load time (before `SceneStarted`), the engine validates:
- All referenced behavior IDs exist in registry
- All behavior configs pass `validateConfig()`
- All asset paths resolve to local files
- All constraint types exist in constraint registry
- All objective prerequisite chains are acyclic

If any validation fails → `SceneLoadFailed` event, scene does not start.

## Invariants

1. `SceneStarted` is always the first event behaviors receive in a session.
2. `SceneCompleted` fires at most once per session (reset starts a new session).
3. Idle counter is measured in ticks, reset on any `GameEvent` with a non-null `source`.
4. Asset loading happens outside the engine loop — `tick` does not advance during loading.
5. `SceneReset` never fires during objective evaluation or CRL.
