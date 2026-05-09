# Behavior Contract

## Definition

A `Behavior` is a **pure, stateless function** that maps an event + world snapshot to a list of intended state changes (Commands).

```
Behavior: (Event, WorldSnapshot) → List<Command>
```

## What a Behavior IS

- A named, parameterized logic unit
- Registered in the engine behavior registry
- Instantiated per entity or per scene, configured via JSON
- Assigned to a specific pipeline phase
- Reads world snapshot; produces commands

## What a Behavior IS NOT

- Not a script
- Not a state machine with internal mutable state
- Not aware of other behaviors
- Not responsible for conflict resolution
- Not allowed to read wall-clock time
- Not allowed to produce I/O side effects

## Phases

Each behavior declares which pipeline phase it operates in:

| Phase | Purpose | Behavior examples |
|---|---|---|
| `INTERPRETATION` | Evaluate constraints, filter entities | `constraintFilter`, `tagMatcher` |
| `RESOLUTION` | Core game logic decisions | `snapToTarget`, `sequenceValidator` |
| `COMMAND_GENERATION` | Produce state change intentions | `progressTracker`, `difficultyScaler` |
| `FEEDBACK` | Trigger output (sound, animation) | `playSoundOnEvent`, `adaptiveHint` |

Rules:
- A behavior belongs to exactly one phase
- Phases execute in order: INTERPRETATION → RESOLUTION → COMMAND_GENERATION → FEEDBACK
- Behaviors within the same phase may execute in any order (by design: no dependency allowed)

## Contract Rules

### Rule 1: No Direct Mutation
A behavior must never write to WorldState directly.
It returns `List<Command>`. The engine applies them.

### Rule 2: Read-Only Snapshot
A behavior reads only from the `WorldSnapshot` passed to it.
It never calls external services, databases, or system time.

### Rule 3: Determinism
Same `event` + same `snapshot` → same `List<Command>`.
No randomness, no hidden state, no clock.

### Rule 4: Isolation
A behavior has no knowledge of other behaviors.
It cannot check what commands other behaviors produced.
It cannot cancel or modify another behavior's output.

### Rule 5: Config-Only Parameterization
Behavior logic is fixed. Only `config` values differ between instances.

```json
{
  "type": "adaptiveHint",
  "config": {
    "afterMistakes": 3,
    "hintType": "highlight"
  }
}
```

## Built-in Behavior Registry (v1)

| Behavior | Phase | Description |
|---|---|---|
| `snapToTarget` | RESOLUTION | Moves entity to matched target position |
| `adaptiveHint` | FEEDBACK | Triggers hint after N mistakes |
| `playSoundOnEvent` | FEEDBACK | Plays audio on specified event |
| `progressTracker` | COMMAND_GENERATION | Updates mistake/success counters |
| `sequenceValidator` | RESOLUTION | Validates ordered interaction sequence |
| `difficultyScaler` | COMMAND_GENERATION | Adjusts parameters based on performance |
| `visibilityToggle` | COMMAND_GENERATION | Shows/hides entities on event |
| `lockOnComplete` | RESOLUTION | Locks entity after objective completion |

## Behavior as Data

Every behavior instance is serializable:

```json
{
  "entityId": "apple",
  "behaviors": [
    {
      "type": "snapToTarget",
      "phase": "RESOLUTION",
      "config": {
        "snapRadius": 60,
        "animationDuration": 200
      }
    }
  ]
}
```

## Invariants

1. `Behavior.handle()` is called with a frozen snapshot — it cannot observe tick-level mutations.
2. A behavior returning an empty list is valid and means "no change intended".
3. Behavior registration is immutable at runtime — no dynamic behavior loading.
4. Config is validated at scene load time, not at runtime.
