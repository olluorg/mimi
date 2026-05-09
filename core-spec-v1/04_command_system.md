# Command System

## Definition

A `Command` is an **immutable record of a behavioral intention** — a request to change a specific field of a specific entity.

Commands are:
- produced only by behaviors
- collected into a buffer during a tick
- resolved by CRL
- applied atomically to produce `World(t+1)`

## Command Structure

```
Command {
    target:   EntityId      // which entity to modify
    field:    PropertyKey   // which field to change
    value:    PropertyValue // intended new value
    priority: Int           // resolution weight (higher wins in priority policy)
    source:   BehaviorId    // which behavior produced this command
    tick:     Tick          // engine tick at production time
}
```

## Tick Rules

- `tick` is set by the engine at the moment the behavior is invoked
- `tick` is **never** set by the behavior itself
- Two commands with the same `tick` + `source` are siblings (same behavior invocation)
- `tick` is strictly monotonic: `tick(N+1) > tick(N)` always

## Command Types (Sealed)

All valid commands in the system:

```
SetProperty       — set entity field to a value
IncrementCounter  — add delta to a numeric field
MarkMatched       — record source→target match
SetEntityState    — transition entity lifecycle state
SetObjectiveState — transition objective lifecycle state
TriggerFeedback   — request feedback output (sound/animation/etc.)
```

`TriggerFeedback` is the only command that crosses into the output layer.
It is produced by FEEDBACK-phase behaviors and applied after World commit.

## Command Buffer

During a tick:
1. Engine creates an empty `CommandBuffer`
2. Each behavior appends its commands
3. After all behaviors execute, buffer is passed to CRL
4. CRL returns resolved commands
5. Engine applies resolved commands to produce `World(t+1)`

Rules:
- Buffer is write-only during behavior execution
- Buffer is read-only during CRL
- Buffer is discarded after commit

## Priority

`priority` is an integer declared in behavior config:

```json
{
  "type": "snapToTarget",
  "config": { "priority": 100 }
}
```

Default priority = `0` if not specified.

Priority meaning depends on the resolution policy of the target field.
For `PRIORITY` policy: higher value wins. Equal priority: first command wins (stable sort).

## Invariants

1. Commands are immutable after creation.
2. A command with `tick` older than current engine tick is invalid (must not occur).
3. Commands targeting non-existent entities are silently dropped at apply time.
4. `TriggerFeedback` commands are never sent to CRL — they bypass conflict resolution and are queued directly for the feedback system.
5. No command may reference wall-clock time.

## Example: Drag & Drop Tick

```
Event: Drop { source: "apple", target: "red_basket" }
tick: 42

Behavior: constraintFilter  → []   (no commands, just filtering)
Behavior: snapToTarget      → [Command(target="apple", field="position", value=Vec2(300,400), priority=100, source="snapToTarget", tick=42)]
Behavior: progressTracker   → [Command(target="scene", field="matchCount", value=IncrementCounter(1), priority=0, source="progressTracker", tick=42)]
Behavior: playSoundOnEvent  → [Command(target="audio", field="play", value="success_chime", priority=0, source="playSoundOnEvent", tick=42)]

CommandBuffer: [snap@position, progress@matchCount, audio@play]

CRL: no conflicts detected → all applied

World(43):
  apple.position = Vec2(300,400)
  scene.matchCount = 1
  → audio queued for feedback phase
```
