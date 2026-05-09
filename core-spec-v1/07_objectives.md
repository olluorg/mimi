# Objective System

## Definition

An `Objective` is a declarative win condition evaluated against World State after each commit.

Objectives:
- are declared in scene JSON
- are evaluated automatically by the engine after each tick
- never contain imperative logic
- emit system events when their state changes

## Objective Lifecycle

```
INACTIVE
   │
   │  (relevant interaction begins)
   ▼
PROGRESSING
   │                    ▲
   │  (condition met)   │  (condition no longer met)
   ▼                    │
TENTATIVE_COMPLETE ──────
   │
   │  (lock event: scene complete or explicit lock)
   ▼
LOCKED_COMPLETE
```

### State Descriptions

| State | Meaning |
|---|---|
| `INACTIVE` | Objective not yet started (may have prerequisite unmet) |
| `PROGRESSING` | At least one contributing entity is matched/placed |
| `TENTATIVE_COMPLETE` | Win condition is currently satisfied, not yet locked |
| `LOCKED_COMPLETE` | Win condition confirmed, cannot be reversed |

### Key Principle: Reversibility

`TENTATIVE_COMPLETE → PROGRESSING` is a valid and expected transition.

This happens when a child picks up a correctly placed object.

UX rule: **this must never feel like punishment**.
- No negative sound on reversal
- No progress bar decrease animation (freeze or slow fade only)
- No visual "loss" state

### Lock Conditions

An objective transitions `TENTATIVE_COMPLETE → LOCKED_COMPLETE` when:
- All objectives in the scene reach `TENTATIVE_COMPLETE` simultaneously → scene completes → all lock
- An explicit `LockObjective` command is issued (advanced scenes only)

There is no partial locking in v1: either all objectives lock together, or none.

## Objective Types (v1)

### `allMatched`
All entities in a group must be in `TENTATIVE` or `LOCKED` state.

```json
{
  "type": "allMatched",
  "group": "fruits"
}
```

### `allSorted`
All entities tagged with a given tag must be placed in correct targets.

```json
{
  "type": "allSorted",
  "tag": "sortable"
}
```

### `sequenceComplete`
Entities must be matched in a specific order.

```json
{
  "type": "sequenceComplete",
  "sequence": ["step_1", "step_2", "step_3"]
}
```

### `traceComplete`
A trace path must be completed with minimum accuracy.

```json
{
  "type": "traceComplete",
  "pathId": "letter_A",
  "minAccuracy": 0.7
}
```

### `composite`
Multiple objectives must all be satisfied (AND logic).

```json
{
  "type": "composite",
  "operator": "allOf",
  "objectives": ["obj_1", "obj_2"]
}
```

## Multi-Step Objectives

Scenes may declare ordered sequences of objectives (chapters):

```json
{
  "objectives": [
    { "id": "phase_1", "type": "allMatched", "group": "vegetables" },
    { "id": "phase_2", "type": "allSorted", "tag": "by_color", "requires": "phase_1" }
  ]
}
```

`requires` means `phase_2` stays `INACTIVE` until `phase_1` reaches `LOCKED_COMPLETE`.

## System Events from Objectives

| Transition | Event emitted |
|---|---|
| `INACTIVE → PROGRESSING` | `ObjectiveProgressed` |
| `PROGRESSING → TENTATIVE_COMPLETE` | `ObjectiveCompleted` |
| `TENTATIVE_COMPLETE → PROGRESSING` | `ObjectiveReverted` |
| `TENTATIVE_COMPLETE → LOCKED_COMPLETE` | `ObjectiveLocked` |
| All objectives `LOCKED_COMPLETE` | `SceneCompleted` |

These events may trigger behaviors in the next tick.

## Invariants

1. Objectives are evaluated after each World commit, not during.
2. Objective evaluation is read-only: it reads WorldState but produces only state transitions and events.
3. `LOCKED_COMPLETE` is a terminal state within a session.
4. No objective may reference another objective's internal state directly.
5. `SceneCompleted` is emitted exactly once per scene session.
